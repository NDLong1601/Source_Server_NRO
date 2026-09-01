param(
    [switch]$FailOnIssue
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$LogDir = Join-Path $Root 'logs'
$issues = New-Object System.Collections.Generic.List[string]

function Get-PropertyMap([string]$Path) {
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$') { $map[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $map
}

$port = 14445
try {
    $serverProperties = Get-PropertyMap (Join-Path $Root 'Config.properties')
    if ($serverProperties.ContainsKey('server.port')) { $port = [int]$serverProperties['server.port'] }
    $activityProperties = Get-PropertyMap (Join-Path $Root 'activity.properties')
    if ($activityProperties['activity.emergency.disable'] -ne 'false') { $issues.Add('activity.emergency.disable must be false while rewards are live.') }
} catch { $issues.Add("Cannot read properties: $($_.Exception.Message)") }

$overview = $null
try {
    if (-not (Test-Path -LiteralPath $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    $output = Join-Path $LogDir 'activity_live_overview.json'
    & (Get-Command powershell.exe -ErrorAction Stop).Source -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'admin_data.ps1') -Action getactivityoverview -Output $output
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output)) { throw 'Admin did not return an Activity overview.' }
    $overview = Get-Content -LiteralPath $output -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($overview.status -ne 'ok') { $issues.Add("Admin overview returned status '$($overview.status)'.") }
    if ($overview.revision -lt 5) { $issues.Add("Runtime revision $($overview.revision) is below the rewards-live minimum of 5.") }
    if (-not [bool]$overview.config.global.enabled) { $issues.Add('Activity Points are disabled.') }
    if ([bool]$overview.config.global.shadowMode) { $issues.Add('shadowMode=true; rewards are not live.') }
    if (-not [bool]$overview.config.global.rewardEnabled) { $issues.Add('rewardEnabled=false; rewards are disabled.') }
    if ([bool]$overview.emergencyDisabled) { $issues.Add('Emergency disable is enabled.') }
    if (@($overview.config.sources).Count -ne 14) { $issues.Add('Runtime does not contain exactly 14 activity sources.') }
    if (@($overview.config.dailyTiers).Count -ne 5 -or @($overview.config.weeklyTiers).Count -ne 2) { $issues.Add('Runtime does not contain exactly 5 daily and 2 weekly tiers.') }
} catch { $issues.Add("Live overview failed: $($_.Exception.Message)") }

try {
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | Where-Object {
        (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName -eq 'java'
    })
    if ($listeners.Count -eq 0) { $issues.Add("Java server is not listening on port $port.") }
} catch { $issues.Add("Cannot verify port ${port}: $($_.Exception.Message)") }

$jar = Join-Path $Root '20.jar'
try {
    $entries = @(jar tf $jar 2>&1)
    foreach ($entry in @('nro/models/activity/ActivityRewardService.class', 'nro/models/server/Manager.class')) {
        if ($entries -notcontains $entry) { $issues.Add("20.jar is missing $entry.") }
    }
} catch { $issues.Add("Cannot inspect 20.jar: $($_.Exception.Message)") }

if ($null -ne $overview) {
    $serverLog = Join-Path $LogDir 'server.log'
    $needle = "Loaded Activity Points config revision $($overview.revision)"
    if (-not (Test-Path -LiteralPath $serverLog) -or (Get-Content -LiteralPath $serverLog -Raw -Encoding UTF8).IndexOf($needle, [StringComparison]::Ordinal) -lt 0) {
        $issues.Add("server.log has not confirmed loading revision $($overview.revision).")
    }
}

$summary = [ordered]@{
    phase = 'activity-live'
    status = if ($issues.Count -eq 0) { 'rewards-live' } else { 'blocked' }
    revision = if ($null -ne $overview) { $overview.revision } else { $null }
    rewardsEnabled = if ($null -ne $overview) { [bool]$overview.config.global.rewardEnabled } else { $null }
    shadowMode = if ($null -ne $overview) { [bool]$overview.config.global.shadowMode } else { $null }
    issues = $issues.ToArray()
}
$summary | ConvertTo-Json -Depth 8
if ($FailOnIssue -and $issues.Count -gt 0) { exit 1 }
