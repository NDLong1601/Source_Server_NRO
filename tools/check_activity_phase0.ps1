param(
    [switch]$CreateBackup,
    [switch]$FailOnDataIssue
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ConfigPath = Join-Path $Root "Config.properties"
$ActivityConfigPath = Join-Path $Root "activity.properties"
$JarPath = Join-Path $Root "20.jar"

function Get-PropertyMap {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Khong tim thay file cau hinh: $Path"
    }

    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match "^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$") {
            $map[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $map
}

function Get-RequiredConfigValue {
    param(
        [hashtable]$Map,
        [string]$Key
    )

    if (-not $Map.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Map[$Key])) {
        throw "Thieu cau hinh bat buoc: $Key"
    }
    return [string]$Map[$Key]
}

function Find-Executable {
    param([string]$Name)

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Khong tim thay $Name trong PATH."
    }
    return $command.Source
}

function Invoke-DatabaseCommand {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$Password
    )

    $hadPassword = Test-Path Env:MYSQL_PWD
    $previousPassword = if ($hadPassword) { $env:MYSQL_PWD } else { $null }
    $env:MYSQL_PWD = $Password
    try {
        $output = & $Executable @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            $details = ($output | Select-Object -Last 5) -join " | "
            throw "MySQL command failed with exit code $LASTEXITCODE. $details"
        }
        return @($output)
    } finally {
        if ($hadPassword) {
            $env:MYSQL_PWD = $previousPassword
        } else {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-MySqlQuery {
    param(
        [string]$Sql,
        [hashtable]$Database
    )

    $arguments = @(
        "--protocol=TCP",
        "--host=$($Database.Host)",
        "--port=$($Database.Port)",
        "--user=$($Database.User)",
        "--batch",
        "--skip-column-names",
        "--default-character-set=utf8mb4",
        "--execute=$Sql",
        $Database.Name
    )
    return Invoke-DatabaseCommand -Executable $Database.MySql -Arguments $arguments -Password $Database.Password
}

function Assert-ActivityDefaults {
    param([hashtable]$Map)

    $expected = [ordered]@{
        "activity.schema.version" = "1"
        "activity.enabled" = "false"
        "activity.shadow.mode" = "true"
        "activity.rewards.enabled" = "false"
        "activity.emergency.disable" = "false"
        "activity.timezone" = "Asia/Ho_Chi_Minh"
        "activity.daily.reset.hour" = "0"
        "activity.weekly.reset.day" = "MONDAY"
        "activity.daily.max" = "100"
        "activity.qualified.daily.points" = "80"
        "activity.config.poll.seconds" = "5"
        "activity.config.fail.closed" = "true"
    }

    $problems = New-Object System.Collections.Generic.List[string]
    foreach ($entry in $expected.GetEnumerator()) {
        $actual = if ($Map.ContainsKey($entry.Key)) { [string]$Map[$entry.Key] } else { "<missing>" }
        if ($actual -ne $entry.Value) {
            $problems.Add("$($entry.Key)=$actual (expected $($entry.Value))")
        }
    }
    return $problems.ToArray()
}

function Get-TopPowerIndexIssues {
    $files = @(
        "src/nro/models/managers/TopKhiGasHuyDiet.java",
        "src/nro/models/managers/TopConDuongRanDoc.java",
        "src/nro/models/managers/TopBanDoKhoBau.java",
        "src/nro/models/managers/MyClanTopBanDoKhoBau.java"
    )

    $issues = New-Object System.Collections.Generic.List[string]
    foreach ($relativePath in $files) {
        $path = Join-Path $Root $relativePath
        if (-not (Test-Path -LiteralPath $path)) {
            $issues.Add("Missing source file: $relativePath")
            continue
        }
        $source = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        if ($source -notmatch "player\.nPoint\.power\s*=\s*Long\.parseLong\(dataArray\.get\(1\)\.toString\(\)\)") {
            $issues.Add("Power index is not data_point[1]: $relativePath")
        }
        if ($source -match "player\.nPoint\.power\s*=\s*Long\.parseLong\(dataArray\.get\(11\)\.toString\(\)\)") {
            $issues.Add("Activity index 11 is still read as power: $relativePath")
        }
    }
    return $issues.ToArray()
}

$config = Get-PropertyMap $ConfigPath
$activityConfig = Get-PropertyMap $ActivityConfigPath
$mysqlPath = Find-Executable "mysql.exe"
$database = @{
    Host = Get-RequiredConfigValue $config "database.host"
    Port = Get-RequiredConfigValue $config "database.port"
    Name = Get-RequiredConfigValue $config "database.name"
    User = Get-RequiredConfigValue $config "database.user"
    Password = if ($config.ContainsKey("database.pass")) { [string]$config["database.pass"] } else { "" }
    MySql = $mysqlPath
}

$summarySql = @"
SELECT
  COUNT(*) AS total_players,
  SUM(CASE WHEN data_point IS NULL OR JSON_VALID(data_point) = 0 THEN 1 ELSE 0 END) AS invalid_json,
  SUM(CASE WHEN JSON_VALID(data_point) = 1 THEN 1 ELSE 0 END) AS valid_json
FROM player;
"@
$summaryParts = ((Invoke-MySqlQuery -Sql $summarySql -Database $database) -join "").Split("`t")
if ($summaryParts.Count -ne 3) {
    throw "Khong doc duoc tong hop data_point tu MySQL."
}
$totalPlayers = [int64]$summaryParts[0]
$invalidJson = [int64]$summaryParts[1]
$validJson = [int64]$summaryParts[2]

$shapeRows = @()
if ($validJson -gt 0) {
    $shapeSql = @"
SELECT
  JSON_LENGTH(data_point) AS json_length,
  COUNT(*) AS row_count
FROM player
WHERE JSON_VALID(data_point) = 1
GROUP BY JSON_LENGTH(data_point)
ORDER BY JSON_LENGTH(data_point);
"@
    $shapeRows = @(Invoke-MySqlQuery -Sql $shapeSql -Database $database)
}

$numericIndexRows = @()
if ($invalidJson -eq 0 -and $validJson -gt 0) {
    $numericIndexSql = @'
SELECT
  SUM(CASE WHEN LEFT(LTRIM(data_point), 1) = '[' AND JSON_LENGTH(data_point) = 15
                AND JSON_TYPE(JSON_EXTRACT(data_point, '$[1]')) IN ('INTEGER', 'DOUBLE')
           THEN 1 ELSE 0 END) AS numeric_power_index,
  SUM(CASE WHEN LEFT(LTRIM(data_point), 1) = '[' AND JSON_LENGTH(data_point) = 15
                AND JSON_TYPE(JSON_EXTRACT(data_point, '$[11]')) IN ('INTEGER', 'DOUBLE')
           THEN 1 ELSE 0 END) AS numeric_activity_index
FROM player;
'@
    $numericIndexRows = @(Invoke-MySqlQuery -Sql $numericIndexSql -Database $database)
}

$dataActivityColumn = ((Invoke-MySqlQuery -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'player' AND column_name = 'data_activity';" -Database $database) -join "").Trim()
$activityDefaultsIssues = @(Assert-ActivityDefaults $activityConfig)
$topIndexIssues = @(Get-TopPowerIndexIssues)
$jarHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash

$dataPointIssues = New-Object System.Collections.Generic.List[string]
if ($invalidJson -gt 0) {
    $dataPointIssues.Add("$invalidJson player rows have NULL or invalid data_point JSON.")
}
foreach ($row in $shapeRows) {
    $parts = $row.Split("`t")
    if ($parts.Count -eq 2 -and [int]$parts[0] -ne 15) {
        $dataPointIssues.Add("data_point length $($parts[0]) appears in $($parts[1]) player rows.")
    }
}
if ($numericIndexRows.Count -gt 0) {
    $numericParts = (($numericIndexRows -join "").Trim()).Split("`t")
    if ($numericParts.Count -eq 2) {
        if ([int64]$numericParts[0] -ne $totalPlayers) {
            $dataPointIssues.Add("data_point[1] is non-numeric or unavailable for $($totalPlayers - [int64]$numericParts[0]) player rows.")
        }
        if ([int64]$numericParts[1] -ne $totalPlayers) {
            $dataPointIssues.Add("data_point[11] is non-numeric or unavailable for $($totalPlayers - [int64]$numericParts[1]) player rows.")
        }
    }
}

$backupPath = ""
if ($CreateBackup) {
    $mysqldumpPath = Find-Executable "mysqldump.exe"
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupPath = Join-Path (Join-Path $Root "backups") ("activity_phase0_" + $stamp)
    if (Test-Path -LiteralPath $backupPath) {
        throw "Backup path already exists: $backupPath"
    }
    New-Item -ItemType Directory -Path $backupPath | Out-Null

    $dumpPath = Join-Path $backupPath ("$($database.Name)_baseline.sql")
    $dumpArguments = @(
        "--protocol=TCP",
        "--host=$($database.Host)",
        "--port=$($database.Port)",
        "--user=$($database.User)",
        "--single-transaction",
        "--routines",
        "--events",
        "--triggers",
        "--default-character-set=utf8mb4",
        "--result-file=$dumpPath",
        $database.Name
    )
    Invoke-DatabaseCommand -Executable $mysqldumpPath -Arguments $dumpArguments -Password $database.Password | Out-Null
    if (-not (Test-Path -LiteralPath $dumpPath) -or (Get-Item -LiteralPath $dumpPath).Length -le 0) {
        throw "Database backup was not created correctly: $dumpPath"
    }

    $jarBackupPath = Join-Path $backupPath "20.jar.baseline"
    Copy-Item -LiteralPath $JarPath -Destination $jarBackupPath
    Copy-Item -LiteralPath $ActivityConfigPath -Destination (Join-Path $backupPath "activity.properties.baseline")

    $manifest = [ordered]@{
        phase = "activity-phase0"
        createdAt = (Get-Date).ToString("o")
        database = [ordered]@{ host = $database.Host; port = $database.Port; name = $database.Name }
        jarSha256 = $jarHash
        playerCount = $totalPlayers
        invalidDataPointJson = $invalidJson
        dataActivityColumnPresent = ([int]$dataActivityColumn -gt 0)
        activityDefaultsIssues = @($activityDefaultsIssues)
        topPowerIndexIssues = @($topIndexIssues)
    }
    [System.IO.File]::WriteAllText(
        (Join-Path $backupPath "manifest.json"),
        ($manifest | ConvertTo-Json -Depth 6),
        [System.Text.UTF8Encoding]::new($false)
    )
}

$report = [ordered]@{
    phase = "activity-phase0"
    serverExpectedState = "stopped during source/build work"
    totalPlayers = $totalPlayers
    invalidDataPointJson = $invalidJson
    validDataPointJson = $validJson
    dataPointShapes = @($shapeRows)
    numericIndexCheck = @($numericIndexRows)
    dataActivityColumnPresent = ([int]$dataActivityColumn -gt 0)
    jarSha256 = $jarHash
    activityDefaultsIssues = @($activityDefaultsIssues)
    topPowerIndexIssues = @($topIndexIssues)
    dataPointIssues = @($dataPointIssues)
    backupPath = $backupPath
}

$report | ConvertTo-Json -Depth 6

if ($FailOnDataIssue -and ($dataPointIssues.Count -gt 0 -or $activityDefaultsIssues.Count -gt 0 -or $topIndexIssues.Count -gt 0)) {
    exit 2
}
