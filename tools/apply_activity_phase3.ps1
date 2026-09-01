param(
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
if (-not $Apply) {
    throw "This command changes the database. Re-run with -Apply after confirming the server is stopped."
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ConfigPath = Join-Path $Root "Config.properties"
$SqlPath = Join-Path $Root "sql\activity_phase3.sql"
$JarPath = Join-Path $Root "20.jar"
$ActivityPropertiesPath = Join-Path $Root "activity.properties"

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
function Invoke-MySqlProgram([string]$Executable, [string[]]$Arguments, [string]$Password) {
    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Password
    try {
        $output = & $Executable @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL command failed: $(($output | Select-Object -Last 5) -join ' | ')" }
        return @($output)
    } finally {
        if ($hadPassword) { $env:MYSQL_PWD = $previousPassword } else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
}

$activeServer = @(Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq "java.exe" -or $_.Name -eq "javaw.exe") -and $_.CommandLine -like "*20.jar*"
})
if ($activeServer.Count -gt 0) { throw "Stop the server before applying Activity Phase 3. Active PIDs: $($activeServer.ProcessId -join ', ')" }
foreach ($requiredPath in @($ConfigPath, $SqlPath, $JarPath, $ActivityPropertiesPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) { throw "Required file not found: $requiredPath" }
}

$config = Get-PropertyMap $ConfigPath
$database = @{
    Host = Require-Value $config "database.host"; Port = Require-Value $config "database.port"
    Name = Require-Value $config "database.name"; User = Require-Value $config "database.user"
    Password = if ($config.ContainsKey("database.pass")) { [string]$config["database.pass"] } else { "" }
}
$mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
$mysqldump = (Get-Command mysqldump.exe -ErrorAction Stop).Source
$backupPath = Join-Path (Join-Path $Root "backups") ("activity_phase3_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Path $backupPath | Out-Null

$dumpPath = Join-Path $backupPath ("$($database.Name)_before_activity_phase3.sql")
Invoke-MySqlProgram $mysqldump @("--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)", "--single-transaction", "--routines", "--events", "--triggers", "--default-character-set=utf8mb4", "--result-file=$dumpPath", $database.Name) $database.Password | Out-Null
if (-not (Test-Path -LiteralPath $dumpPath) -or (Get-Item -LiteralPath $dumpPath).Length -le 0) { throw "Database backup was not created correctly." }
Copy-Item -LiteralPath $JarPath -Destination (Join-Path $backupPath "20.jar.before_activity_phase3")
Copy-Item -LiteralPath $ActivityPropertiesPath -Destination (Join-Path $backupPath "activity.properties.before_activity_phase3")
Copy-Item -LiteralPath $SqlPath -Destination (Join-Path $backupPath "activity_phase3.sql")

$sql = [System.IO.File]::ReadAllText($SqlPath, [System.Text.Encoding]::UTF8)
Invoke-MySqlProgram $mysql @("--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)", "--default-character-set=utf8mb4", "--execute=$sql", $database.Name) $database.Password | Out-Null

$checkSql = "SELECT " +
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_claim_audit')," +
    "(SELECT JSON_VALID(config_json) FROM activity_config_version WHERE version_no=3)," +
    "(SELECT version_no FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT revision FROM activity_runtime_config WHERE id=1)," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.enabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.shadowMode')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.rewardEnabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_LENGTH(JSON_EXTRACT(config_json, '$.dailyTiers')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_LENGTH(JSON_EXTRACT(config_json, '$.weeklyTiers')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1));"
$values = ((Invoke-MySqlProgram $mysql @("--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)", "--batch", "--skip-column-names", "--default-character-set=utf8mb4", "--execute=$checkSql", $database.Name) $database.Password) -join "").Trim().Split("`t")
if ($values.Count -ne 9 -or [int]$values[0] -ne 1 -or [int]$values[1] -ne 1 -or [int]$values[2] -ne 3 `
        -or [int64]$values[3] -lt 3 -or $values[4] -ne 'true' -or $values[5] -ne 'true' -or $values[6] -ne 'false' `
        -or [int]$values[7] -ne 5 -or [int]$values[8] -ne 2) { throw "Activity Phase 3 schema or shadow-mode verification failed." }

$manifest = [ordered]@{
    phase = "activity-phase3"; appliedAt = (Get-Date).ToString("o")
    database = [ordered]@{ host = $database.Host; port = $database.Port; name = $database.Name }
    jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash
    claimAuditTablePresent = $true; publishedConfigVersion = 3; runtimeRevision = [int64]$values[3]
    featureEnabled = $true; shadowMode = $true; rewardsEnabled = $false
}
[System.IO.File]::WriteAllText((Join-Path $backupPath "manifest.json"), ($manifest | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
$manifest["backupPath"] = $backupPath
$manifest | ConvertTo-Json -Depth 5
