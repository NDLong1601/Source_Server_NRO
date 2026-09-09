$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backend = Join-Path $repoRoot "tools\clan_economy_admin_backend.ps1"
if (-not (Test-Path -LiteralPath $backend)) {
    throw "Missing phase 5E admin backend: $backend"
}
. $backend

function Assert-Equal {
    param([string]$Label, $Expected, $Actual)
    if ($Expected -ne $Actual) {
        throw "$Label expected=$Expected actual=$Actual"
    }
}

$config = Get-ClanEconomyPropertyMap -Path (Join-Path $repoRoot "data\clan_economy.properties")
Assert-Equal "properties parser" "90" ([string]$config["lookback_max_days"])

Assert-Equal "valid lookback" 14 (Assert-ClanEconomyLookbackDays -Value "14" -Maximum 90)

$rejected = $false
try { Assert-ClanEconomyLookbackDays -Value "14;DROP TABLE clan" -Maximum 90 | Out-Null }
catch { $rejected = $true }
Assert-Equal "SQL-shaped lookback rejected" $true $rejected

$rejected = $false
try { Assert-ClanEconomyLookbackDays -Value "91" -Maximum 90 | Out-Null }
catch { $rejected = $true }
Assert-Equal "oversized lookback rejected" $true $rejected

Assert-Equal "no flow" "NO_FLOW" (Get-ClanEconomyBalanceAssessment -Inflow 0 -Outflow 0 -MinimumPercent 70 -MaximumPercent 110).Status
Assert-Equal "surplus risk" "SURPLUS_RISK" (Get-ClanEconomyBalanceAssessment -Inflow 100 -Outflow 60 -MinimumPercent 70 -MaximumPercent 110).Status
Assert-Equal "balanced" "BALANCED" (Get-ClanEconomyBalanceAssessment -Inflow 100 -Outflow 80 -MinimumPercent 70 -MaximumPercent 110).Status
Assert-Equal "deficit risk" "DEFICIT_RISK" (Get-ClanEconomyBalanceAssessment -Inflow 100 -Outflow 120 -MinimumPercent 70 -MaximumPercent 110).Status
Assert-Equal "sink without source" "DEFICIT_RISK" (Get-ClanEconomyBalanceAssessment -Inflow 0 -Outflow 1 -MinimumPercent 70 -MaximumPercent 110).Status

Write-Output "ClanPhase5EAdminBackendTest: PASS"
