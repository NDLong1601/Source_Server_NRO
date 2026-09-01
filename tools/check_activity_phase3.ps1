param(
    [switch]$FailOnIssue
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ConfigPath = Join-Path $Root "Config.properties"
$JarPath = Join-Path $Root "20.jar"

function Get-PropertyMap([string]$Path) {
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match "^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$") { $map[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $map
}
function Require-Value([hashtable]$Map, [string]$Key) {
    if (-not $Map.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Map[$Key])) { throw "Missing required configuration: $Key" }
    return [string]$Map[$Key]
}
function Invoke-MySqlQuery([string]$Sql, [hashtable]$Database) {
    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Database.Password
    try {
        $output = & $Database.MySql "--protocol=TCP" "--host=$($Database.Host)" "--port=$($Database.Port)" "--user=$($Database.User)" "--batch" "--skip-column-names" "--default-character-set=utf8mb4" "--execute=$Sql" $Database.Name 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL query failed: $(($output | Select-Object -Last 3) -join ' | ')" }
        return @($output)
    } finally {
        if ($hadPassword) { $env:MYSQL_PWD = $previousPassword } else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
}

$config = Get-PropertyMap $ConfigPath
$database = @{
    Host = Require-Value $config "database.host"; Port = Require-Value $config "database.port"
    Name = Require-Value $config "database.name"; User = Require-Value $config "database.user"
    Password = if ($config.ContainsKey("database.pass")) { [string]$config["database.pass"] } else { "" }
    MySql = (Get-Command mysql.exe -ErrorAction Stop).Source
}
$sql = @'
SELECT
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_claim_audit'),
  (SELECT version_no FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT revision FROM activity_runtime_config WHERE id=1),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.enabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.shadowMode')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.rewardEnabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_LENGTH(JSON_EXTRACT(config_json, '$.dailyTiers')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_LENGTH(JSON_EXTRACT(config_json, '$.weeklyTiers')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1));
'@
$values = ((Invoke-MySqlQuery $sql $database) -join "").Trim().Split("`t")
$issues = New-Object System.Collections.Generic.List[string]
if ($values.Count -ne 8) { $issues.Add("Unable to read Activity Phase 3 database state") }
else {
    if ([int]$values[0] -ne 1) { $issues.Add("activity_claim_audit table is missing") }
    if ([int]$values[1] -lt 3) { $issues.Add("published config version is $($values[1]), expected >= 3") }
    if ([int64]$values[2] -lt 3) { $issues.Add("runtime revision is $($values[2]), expected >= 3") }
    if ($values[3] -ne 'true') { $issues.Add("activity.enabled must stay true for shadow collection") }
    if ($values[4] -ne 'true') { $issues.Add("activity.shadowMode must remain true") }
    if ($values[5] -ne 'false') { $issues.Add("activity.rewardEnabled must remain false") }
    if ([int]$values[6] -ne 5 -or [int]$values[7] -ne 2) { $issues.Add("published tier counts are not daily=5 / weekly=2") }
}

$requiredFiles = @{
    "src/nro/models/activity/ActivityRewardService.java" = "claimWeekly"
    "src/nro/models/activity/ActivityClaimAuditService.java" = "uk_activity_claim_once"
    "src/nro/models/npc_list/BoMong.java" = "MENU_ACTIVITY_OVERVIEW"
    "src/nro/models/server/Manager.java" = "ActivityClaimAuditService.gI().ensureSchema"
}
foreach ($entry in $requiredFiles.GetEnumerator()) {
    $path = Join-Path $Root $entry.Key
    if (-not (Test-Path -LiteralPath $path) -or (Get-Content -LiteralPath $path -Raw -Encoding UTF8).IndexOf($entry.Value) -lt 0) {
        $issues.Add("Missing Phase 3 marker '$($entry.Value)' in $($entry.Key)")
    }
}
$jarEntries = @(jar tf $JarPath 2>&1)
if ($LASTEXITCODE -ne 0 -or -not ($jarEntries -contains "nro/models/activity/ActivityRewardService.class") -or -not ($jarEntries -contains "nro/models/activity/ActivityClaimAuditService.class")) {
    $issues.Add("20.jar does not contain Phase 3 Activity reward classes")
}

$summary = [ordered]@{
    phase = "activity-phase3"; claimAuditTablePresent = ($values.Count -eq 8 -and [int]$values[0] -eq 1)
    publishedConfigVersion = if ($values.Count -eq 8) { [int]$values[1] } else { $null }
    runtimeRevision = if ($values.Count -eq 8) { [int64]$values[2] } else { $null }
    rewardsEnabled = if ($values.Count -eq 8) { $values[5] } else { $null }
    issues = $issues.ToArray(); jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash
}
$summary | ConvertTo-Json -Depth 5
if ($FailOnIssue -and $issues.Count -gt 0) { exit 1 }
