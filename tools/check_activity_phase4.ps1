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
    [string]$Map[$Key]
}
function Invoke-MySqlQuery([string]$Sql, [hashtable]$Database) {
    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Database.Password
    try {
        $output = & $Database.MySql "--protocol=TCP" "--host=$($Database.Host)" "--port=$($Database.Port)" "--user=$($Database.User)" "--batch" "--skip-column-names" "--default-character-set=utf8mb4" "--execute=$Sql" $Database.Name 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL query failed: $(($output | Select-Object -Last 3) -join ' | ')" }
        @($output)
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
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_admin_audit'),
  (SELECT revision FROM activity_runtime_config WHERE id=1),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(v.config_json, '$.global.shadowMode')) FROM activity_runtime_config r JOIN activity_config_version v ON v.id=r.published_config_id WHERE r.id=1),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(v.config_json, '$.global.rewardEnabled')) FROM activity_runtime_config r JOIN activity_config_version v ON v.id=r.published_config_id WHERE r.id=1),
  (SELECT JSON_LENGTH(JSON_EXTRACT(v.config_json, '$.sources')) FROM activity_runtime_config r JOIN activity_config_version v ON v.id=r.published_config_id WHERE r.id=1),
  (SELECT COUNT(*) FROM activity_admin_audit WHERE action_name IN ('saveactivitydraft','publishactivityconfig'));
'@
$values = ((Invoke-MySqlQuery $sql $database) -join "").Trim().Split("`t")
$issues = New-Object System.Collections.Generic.List[string]
if ($values.Count -ne 6) { $issues.Add("Unable to read Activity Phase 4 database state") }
else {
    if ([int]$values[0] -ne 1) { $issues.Add("activity_admin_audit table is missing") }
    if ([int64]$values[1] -lt 4) { $issues.Add("runtime revision is $($values[1]), expected >= 4") }
    if ($values[2] -ne 'true') { $issues.Add("shadowMode must remain true") }
    if ($values[3] -ne 'false') { $issues.Add("rewardEnabled must remain false") }
    if ([int]$values[4] -ne 14) { $issues.Add("published config must include 14 stable sources") }
    if ([int]$values[5] -lt 2) { $issues.Add("Phase 4 config audit entries are missing") }
}
foreach ($requiredPath in @(
    "tools/activity_admin_backend.ps1",
    "sql/activity_phase4.sql",
    "src/nro/models/activity/ActivitySourceRule.java")) {
    if (-not (Test-Path -LiteralPath (Join-Path $Root $requiredPath))) { $issues.Add("Missing $requiredPath") }
}
$jarEntries = @(jar tf $JarPath 2>&1)
if ($LASTEXITCODE -ne 0 -or -not ($jarEntries -contains "nro/models/activity/ActivitySourceRule.class")) {
    $issues.Add("20.jar does not contain ActivitySourceRule.class")
}
$summary = [ordered]@{
    phase = "activity-phase4"; adminAuditTablePresent = ($values.Count -eq 6 -and [int]$values[0] -eq 1)
    runtimeRevision = if ($values.Count -eq 6) { [int64]$values[1] } else { $null }
    sourceCount = if ($values.Count -eq 6) { [int]$values[4] } else { $null }
    rewardsEnabled = if ($values.Count -eq 6) { $values[3] } else { $null }
    issues = $issues.ToArray(); jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash
}
$summary | ConvertTo-Json -Depth 5
if ($FailOnIssue -and $issues.Count -gt 0) { exit 1 }
