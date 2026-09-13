$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
. (Join-Path $repoRoot 'tools\admin\clan_economy_admin_backend.ps1')

function Assert-Equal {
    param([string]$Label, $Expected, $Actual)
    if ($Expected -ne $Actual) { throw "$Label expected=$Expected actual=$Actual" }
}

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('nro_admin_config_storage_' + [Guid]::NewGuid().ToString('N'))
$configRoot = Join-Path $fixtureRoot 'config\clan'
$dataRoot = Join-Path $fixtureRoot 'data'
try {
    New-Item -ItemType Directory -Path $configRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $configRoot 'clan_buff.properties'), "attack_percent=10`r`n", [System.Text.UTF8Encoding]::new($false))

    Save-ClanConfig -Key 'buff.attack_percent' -Value '25' -ConfigRoot $configRoot -DataRoot $dataRoot | Out-Null
    $saved = Get-ClanEconomyPropertyMap -Path (Join-Path $configRoot 'clan_buff.properties')
    Assert-Equal 'saved in central clan directory' '25' ([string]$saved['attack_percent'])
    Assert-Equal 'legacy data directory untouched' $false (Test-Path -LiteralPath (Join-Path $dataRoot 'clan_buff.properties'))

    $entry = Get-ClanConfigEntry -Key 'buff.attack_percent'
    $rejected = $false
    try { Get-ClanConfigPath -Entry $entry -ConfigRoot (Join-Path $fixtureRoot 'outside') | Out-Null } catch { $rejected = $true }
    Assert-Equal 'config root must exist' $true $rejected

    Write-Output 'AdminConfigStorageTest: PASS'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}
