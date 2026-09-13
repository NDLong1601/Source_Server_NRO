$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backend = Join-Path $repoRoot "tools\admin\clan_economy_admin_backend.ps1"
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

$configRoot = Join-Path $repoRoot "config\clan"
$dataRoot = Join-Path $repoRoot "data"
$config = Get-ClanEconomyPropertyMap -Path (Join-Path $configRoot "clan_economy.properties")
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
Assert-Equal "tree maximum exposed" "20" (Get-ClanConfigEntry -Key "tree.max_tree_level").Maximum
Assert-Equal "resource prefix exposed" "resource-prefix" (Get-ClanConfigEntry -Key "appearance.resource_prefix").Kind
Assert-Equal "appearance version bound" "255" (Get-ClanConfigEntry -Key "appearance.appearance_version").Maximum
Assert-Equal "resource level bound" "20" (Get-ClanConfigEntry -Key "appearance.max_resource_level").Maximum
Assert-Equal "appearance tier bound" "4" (Get-ClanConfigEntry -Key "appearance.tier_count").Maximum
Assert-Equal "short tree schedule" "1,1,1,2" (Get-ClanConfigEntry -Key "tree.upgrade_days_to_levels_2_5").Default
Assert-Equal "safe resource prefix" "cay_lv_v4_" (Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "appearance.resource_prefix") -Value "cay_lv_v4_")
foreach ($invalidPrefix in @("../tree_", "C:\tree_", "x&calc", "TREE_", ("x" * 33))) {
    $rejected = $false
    try { Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "appearance.resource_prefix") -Value $invalidPrefix | Out-Null }
    catch { $rejected = $true }
    Assert-Equal "unsafe prefix rejected" $true $rejected
}
$listed = (List-ClanConfig -ConfigRoot $configRoot) -split "`r?`n" | ConvertFrom-Csv -Delimiter "`t"
$listedAttack = $listed | Where-Object { $_.key -eq "buff.attack_percent" }
Assert-Equal "minimum metadata" "0" $listedAttack.minimum
Assert-Equal "maximum metadata" "100" $listedAttack.maximum
Assert-Equal "list length metadata" "19" ($listed | Where-Object { $_.key -eq "tree.upgrade_days_to_levels_2_20" }).list_count
Assert-Equal "buff attack validation" "25" (Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "25")

$rejected = $false
try { Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "101" | Out-Null }
catch { $rejected = $true }
Assert-Equal "oversized buff rejected" $true $rejected

$rejected = $false
try { Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "buff.attack_percent") -Value "10`nmalicious=true" | Out-Null }
catch { $rejected = $true }
Assert-Equal "multiline value rejected" $true $rejected

$maps = @{}
foreach ($entry in $catalog) {
    if (-not $maps.ContainsKey($entry.File)) {
        $maps[$entry.File] = Get-ClanEconomyPropertyMap -Path (Get-ClanConfigPath -Entry $entry -ConfigRoot $configRoot)
    }
    $rawValue = Get-ClanConfigEffectiveValue -Entry $entry -Map $maps[$entry.File]
    $editableValue = if ($entry.Kind -eq "java-text") { ConvertFrom-ClanJavaPropertyText $rawValue } else { $rawValue }
    $validatedValue = Assert-ClanConfigValue -Entry $entry -Value $editableValue
    Assert-ClanConfigRelationships -Entry $entry -ValidatedValue $validatedValue -ConfigRoot $configRoot -DataRoot $dataRoot
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nro_clan_config_test_" + [Guid]::NewGuid().ToString("N"))
$tempConfig = Join-Path $tempRoot "config\clan"
$tempData = Join-Path $tempRoot "data"
New-Item -ItemType Directory -Path $tempConfig -Force | Out-Null
New-Item -ItemType Directory -Path $tempData -Force | Out-Null
try {
    [System.IO.File]::WriteAllText((Join-Path $tempConfig "clan_buff.properties"), "attack_percent=10`r`n", [System.Text.UTF8Encoding]::new($false))
    $saveResult = Save-ClanConfig -Key "buff.attack_percent" -Value "25" -ConfigRoot $tempConfig -DataRoot $tempData
    Assert-Equal "save result" $true ($saveResult.StartsWith("OK`t"))
    Assert-Equal "saved buff value" "25" ([string](Get-ClanEconomyPropertyMap -Path (Join-Path $tempConfig "clan_buff.properties"))["attack_percent"])
    Reset-ClanConfig -Key "buff.attack_percent" -ConfigRoot $tempConfig -DataRoot $tempData | Out-Null
    Assert-Equal "reset removes override" $false ((Get-ClanEconomyPropertyMap -Path (Join-Path $tempConfig "clan_buff.properties")).ContainsKey("attack_percent"))
    Save-ClanConfig -Key "tree.max_tree_level" -Value "5" -ConfigRoot $tempConfig -DataRoot $tempData | Out-Null
    Save-ClanConfig -Key "tree.upgrade_days_to_levels_2_5" -Value "1,2,3,4" -ConfigRoot $tempConfig -DataRoot $tempData | Out-Null
    Assert-Equal "short schedule saved" "1,2,3,4" (Get-ClanEconomyPropertyMap -Path (Join-Path $tempConfig "clan_tree.properties"))["upgrade_days_to_levels_2_5"]
    $rejected = $false
    try { Save-ClanConfig -Key "appearance.resource_prefix" -Value "missing_" -ConfigRoot $tempConfig -DataRoot $tempData | Out-Null }
    catch { $rejected = $true }
    Assert-Equal "missing tree assets rejected" $true $rejected
    Assert-Equal "rejected prefix not written" $false (Test-Path -LiteralPath (Join-Path $tempConfig "clan_appearance.properties"))
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}

Write-Output "ClanPhase5EAdminBackendTest: PASS"
