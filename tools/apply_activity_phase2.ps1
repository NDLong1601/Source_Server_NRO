param(
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

if (-not $Apply) {
    throw "This command changes the database and enables Activity Points shadow mode. Re-run with -Apply after confirming the server is stopped."
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ConfigPath = Join-Path $Root "Config.properties"
$SqlPath = Join-Path $Root "sql\activity_phase2.sql"
$JarPath = Join-Path $Root "20.jar"
$ActivityPropertiesPath = Join-Path $Root "activity.properties"

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

function Find-Executable {
    param([string]$Name)

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Cannot find $Name in PATH."
    }
    return $command.Source
}

function Invoke-MySqlProgram {
    param([string]$Executable, [string[]]$Arguments, [string]$Password)

    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Password
    try {
        $output = & $Executable @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL command failed with exit code ${LASTEXITCODE}: $(($output | Select-Object -Last 5) -join ' | ')"
        }
        return @($output)
    } finally {
        if ($hadPassword) { $env:MYSQL_PWD = $previousPassword }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
}

$serverProcesses = @()
try {
    $serverProcesses = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        ($_.Name -eq "java.exe" -or $_.Name -eq "javaw.exe") -and $_.CommandLine -like "*20.jar*"
    })
} catch {
    throw "Cannot determine whether the server is stopped: $($_.Exception.Message)"
}
if ($serverProcesses.Count -gt 0) {
    throw "Stop the server before applying Activity Phase 2. Active PIDs: $($serverProcesses.ProcessId -join ', ')"
}

foreach ($requiredPath in @($ConfigPath, $SqlPath, $JarPath, $ActivityPropertiesPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required file not found: $requiredPath"
    }
}

$config = Get-PropertyMap $ConfigPath
$database = @{
    Host = Require-Value $config "database.host"
    Port = Require-Value $config "database.port"
    Name = Require-Value $config "database.name"
    User = Require-Value $config "database.user"
    Password = if ($config.ContainsKey("database.pass")) { [string]$config["database.pass"] } else { "" }
}
$mysql = Find-Executable "mysql.exe"
$mysqldump = Find-Executable "mysqldump.exe"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupPath = Join-Path (Join-Path $Root "backups") ("activity_phase2_" + $stamp)
if (Test-Path -LiteralPath $backupPath) {
    throw "Backup path already exists: $backupPath"
}
New-Item -ItemType Directory -Path $backupPath | Out-Null

$dumpPath = Join-Path $backupPath ("$($database.Name)_before_activity_phase2.sql")
$dumpArgs = @(
    "--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)",
    "--single-transaction", "--routines", "--events", "--triggers", "--default-character-set=utf8mb4",
    "--result-file=$dumpPath", $database.Name
)
Invoke-MySqlProgram -Executable $mysqldump -Arguments $dumpArgs -Password $database.Password | Out-Null
if (-not (Test-Path -LiteralPath $dumpPath) -or (Get-Item -LiteralPath $dumpPath).Length -le 0) {
    throw "Database backup was not created correctly: $dumpPath"
}

Copy-Item -LiteralPath $JarPath -Destination (Join-Path $backupPath "20.jar.before_activity_phase2")
Copy-Item -LiteralPath $ActivityPropertiesPath -Destination (Join-Path $backupPath "activity.properties.before_activity_phase2")
Copy-Item -LiteralPath $SqlPath -Destination (Join-Path $backupPath "activity_phase2.sql")

$sql = [System.IO.File]::ReadAllText($SqlPath, [System.Text.Encoding]::UTF8)
$applyArgs = @(
    "--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)",
    "--default-character-set=utf8mb4", "--execute=$sql", $database.Name
)
Invoke-MySqlProgram -Executable $mysql -Arguments $applyArgs -Password $database.Password | Out-Null

$checkSql = "SELECT " +
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_source_metric')," +
    "(SELECT JSON_VALID(config_json) FROM activity_config_version WHERE version_no=2)," +
    "(SELECT version_no FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT revision FROM activity_runtime_config WHERE id=1)," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.enabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.shadowMode')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1))," +
    "(SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.global.rewardEnabled')) FROM activity_config_version WHERE id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1));"
$checkArgs = @(
    "--protocol=TCP", "--host=$($database.Host)", "--port=$($database.Port)", "--user=$($database.User)",
    "--batch", "--skip-column-names", "--default-character-set=utf8mb4", "--execute=$checkSql", $database.Name
)
$check = ((Invoke-MySqlProgram -Executable $mysql -Arguments $checkArgs -Password $database.Password) -join "").Trim().Split("`t")
if ($check.Count -ne 7 -or [int]$check[0] -ne 1 -or [int]$check[1] -ne 1 -or [int]$check[2] -ne 2 `
        -or [int64]$check[3] -lt 2 -or $check[4] -ne 'true' -or $check[5] -ne 'true' -or $check[6] -ne 'false') {
    throw "Activity Phase 2 schema or shadow-mode verification failed."
}

$manifest = [ordered]@{
    phase = "activity-phase2"
    appliedAt = (Get-Date).ToString("o")
    database = [ordered]@{ host = $database.Host; port = $database.Port; name = $database.Name }
    jarSha256BeforeBuild = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash
    metricTablePresent = $true
    publishedConfigVersion = 2
    runtimeRevision = [int64]$check[3]
    featureEnabled = $true
    shadowMode = $true
    rewardsEnabled = $false
}
[System.IO.File]::WriteAllText(
    (Join-Path $backupPath "manifest.json"),
    ($manifest | ConvertTo-Json -Depth 5),
    [System.Text.UTF8Encoding]::new($false)
)

$result = [ordered]@{}
foreach ($entry in $manifest.GetEnumerator()) {
    $result[$entry.Key] = $entry.Value
}
$result["backupPath"] = $backupPath
$result | ConvertTo-Json -Depth 5
