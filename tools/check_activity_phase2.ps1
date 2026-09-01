param(
    [switch]$FailOnIssue
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ConfigPath = Join-Path $Root "Config.properties"
$JarPath = Join-Path $Root "20.jar"

function Get-PropertyMap {
    param([string]$Path)

    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match "^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$") {
            $map[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $map
}

function Require-Value {
    param([hashtable]$Map, [string]$Key)

    if (-not $Map.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Map[$Key])) {
        throw "Missing required configuration: $Key"
    }
    return [string]$Map[$Key]
}

function Invoke-MySqlQuery {
    param([string]$Sql, [hashtable]$Database)

    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Database.Password
    try {
        $output = & $Database.MySql "--protocol=TCP" "--host=$($Database.Host)" "--port=$($Database.Port)" "--user=$($Database.User)" `
            "--batch" "--skip-column-names" "--default-character-set=utf8mb4" "--execute=$Sql" $Database.Name 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL query failed: $(($output | Select-Object -Last 3) -join ' | ')"
        }
        return @($output)
    } finally {
        if ($hadPassword) { $env:MYSQL_PWD = $previousPassword }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
}

$config = Get-PropertyMap $ConfigPath
$mysql = Get-Command mysql.exe -ErrorAction Stop
$database = @{
    Host = Require-Value $config "database.host"
    Port = Require-Value $config "database.port"
    Name = Require-Value $config "database.name"
    User = Require-Value $config "database.user"
    Password = if ($config.ContainsKey("database.pass")) { [string]$config["database.pass"] } else { "" }
    MySql = $mysql.Source
}

$configSql = @'
SELECT
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_source_metric'),
  (SELECT version_no FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT revision FROM activity_runtime_config WHERE id=1),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.enabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.shadowMode')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1)),
  (SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.rewardEnabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1));
'@
$databaseValues = ((Invoke-MySqlQuery -Sql $configSql -Database $database) -join "").Trim().Split("`t")
if ($databaseValues.Count -ne 6) {
    throw "Unable to read the Activity Phase 2 database state."
}

$requiredHooks = [ordered]@{
    "src/nro/models/services/PlayerService.java" = "ActivityType.DAILY_LOGIN"
    "src/nro/models/npc/MagicTree.java" = "ActivityType.PEA_HARVEST"
    "src/nro/models/services/TaskService.java" = "SIDE_TASK_SPECIAL"
    "src/nro/models/npc_list/GiuMaDauBo.java" = "ActivityType.CLAN_CHECKIN"
    "src/nro/models/fishing/FishingService.java" = "ActivityType.FISH_CATCH"
    "src/nro/models/matches/ThachDau.java" = "awardPvpWin"
    "src/nro/models/boss/Boss.java" = "ActivityType.BOSS_KILL"
    "src/nro/models/map/phoban/BanDoKhoBau.java" = "dungeon:BDKB:"
    "src/nro/models/map/phoban/DestronGas.java" = "dungeon:KGHD:"
    "src/nro/models/map/phoban/SnakeWay.java" = "dungeon:CDRD:"
    "src/nro/models/server/Manager.java" = "ActivityMetricsService.gI().start"
}

$hookIssues = New-Object System.Collections.Generic.List[string]
foreach ($entry in $requiredHooks.GetEnumerator()) {
    $path = Join-Path $Root $entry.Key
    if (-not (Test-Path -LiteralPath $path)) {
        $hookIssues.Add("Missing hook file: $($entry.Key)")
    } elseif ((Get-Content -LiteralPath $path -Raw -Encoding UTF8).IndexOf($entry.Value) -lt 0) {
        $hookIssues.Add("Missing Activity hook marker '$($entry.Value)' in $($entry.Key)")
    }
}

$jarEntries = @(jar tf $JarPath 2>&1)
if ($LASTEXITCODE -ne 0 -or -not ($jarEntries -contains "nro/models/activity/ActivityMetricsService.class")) {
    $hookIssues.Add("20.jar does not contain ActivityMetricsService.class")
}

$databaseIssues = New-Object System.Collections.Generic.List[string]
if ([int]$databaseValues[0] -ne 1) { $databaseIssues.Add("activity_source_metric table is missing") }
if ([int]$databaseValues[1] -ne 2) { $databaseIssues.Add("published config version is $($databaseValues[1]), expected 2") }
if ([int64]$databaseValues[2] -lt 2) { $databaseIssues.Add("runtime revision is $($databaseValues[2]), expected >= 2") }
if ($databaseValues[3] -ne 'true') { $databaseIssues.Add("activity.enabled is $($databaseValues[3]), expected true for shadow collection") }
if ($databaseValues[4] -ne 'true') { $databaseIssues.Add("activity.shadowMode is $($databaseValues[4]), expected true") }
if ($databaseValues[5] -ne 'false') { $databaseIssues.Add("activity.rewardEnabled is $($databaseValues[5]), expected false") }

$summary = [ordered]@{
    phase = "activity-phase2"
    metricTablePresent = ([int]$databaseValues[0] -eq 1)
    publishedConfigVersion = [int]$databaseValues[1]
    runtimeRevision = [int64]$databaseValues[2]
    featureEnabled = $databaseValues[3]
    shadowMode = $databaseValues[4]
    rewardsEnabled = $databaseValues[5]
    hookIssues = $hookIssues.ToArray()
    databaseIssues = $databaseIssues.ToArray()
    jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash
}
$summary | ConvertTo-Json -Depth 5

if ($FailOnIssue -and ($hookIssues.Count -gt 0 -or $databaseIssues.Count -gt 0)) {
    exit 1
}
