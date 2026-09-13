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

$catalog = @(Get-ClanConfigCatalog)
if ($catalog.Count -lt 100) {
    throw "Clan config catalog expected at least 100 entries, actual=$($catalog.Count)"
}
Assert-Equal "buff attack property" "attack_percent" (Get-ClanConfigEntry -Key "buff.attack_percent").Property
Assert-Equal "buff attack validation" "25" (Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "25")

$rejected = $false
try { Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "101" | Out-Null }
catch { $rejected = $true }
Assert-Equal "oversized buff rejected" $true $rejected

$rejected = $false
try { Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "10`nmalicious=true" | Out-Null }
catch { $rejected = $true }
Assert-Equal "multiline value rejected" $true $rejected

$dataRoot = Join-Path $repoRoot "data"
$maps = @{}
foreach ($entry in $catalog) {
    if (-not $maps.ContainsKey($entry.File)) {
        $maps[$entry.File] = Get-ClanEconomyPropertyMap -Path (Get-ClanConfigPath -Entry $entry -DataRoot $dataRoot)
    }
    $rawValue = Get-ClanConfigEffectiveValue -Entry $entry -Map $maps[$entry.File]
    $editableValue = if ($entry.Kind -eq "java-text") { ConvertFrom-ClanJavaPropertyText $rawValue } else { $rawValue }
    $validatedValue = Assert-ClanConfigValue -Entry $entry -Value $editableValue
    Assert-ClanConfigRelationships -Entry $entry -ValidatedValue $validatedValue -DataRoot $dataRoot
}

$tempData = Join-Path ([System.IO.Path]::GetTempPath()) ("nro_clan_config_test_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempData | Out-Null
try {
    [System.IO.File]::WriteAllText((Join-Path $tempData "clan_buff.properties"), "attack_percent=10`r`n", [System.Text.UTF8Encoding]::new($false))
    $saveResult = Save-ClanConfig -Key "buff.attack_percent" -Value "25" -DataRoot $tempData
    Assert-Equal "save result" $true ($saveResult.StartsWith("OK`t"))
    Assert-Equal "saved buff value" "25" ([string](Get-ClanEconomyPropertyMap -Path (Join-Path $tempData "clan_buff.properties"))["attack_percent"])
    Reset-ClanConfig -Key "buff.attack_percent" -DataRoot $tempData | Out-Null
    Assert-Equal "reset removes override" $false ((Get-ClanEconomyPropertyMap -Path (Join-Path $tempData "clan_buff.properties")).ContainsKey("attack_percent"))
} finally {
    Remove-Item -LiteralPath $tempData -Recurse -Force
}

Write-Output "ClanPhase5EAdminBackendTest: PASS"
