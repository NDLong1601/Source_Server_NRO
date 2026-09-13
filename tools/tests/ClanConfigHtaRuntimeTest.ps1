param(
    [ValidateSet("", "listclanconfig", "saveclanconfig", "resetclanconfig")][string]$Action = "",
    [string]$TestRoot = "",
    [string]$ConfigKey = "",
    [string]$ConfigValue = ""
)
$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$logsRoot = Join-Path $repoRoot "logs"
. (Join-Path $repoRoot "tools\admin\clan_economy_admin_backend.ps1")
$utf8 = New-Object System.Text.UTF8Encoding($false)

if ($Action) {
    # The test bridge can only write its marked, isolated fixture, never production data.
    $fixtureRoot = [IO.Path]::GetFullPath($TestRoot)
    if ([IO.Path]::GetDirectoryName($fixtureRoot) -ne $logsRoot -or
        [IO.Path]::GetFileName($fixtureRoot) -notmatch '^clan_config_hta_test_[a-f0-9]{32}$' -or
        -not (Test-Path -LiteralPath (Join-Path $fixtureRoot ".clan-ui-fixture"))) {
        throw "Not a clan UI test fixture."
    }
    $fixtureConfig = Join-Path $fixtureRoot "config\clan"
    $fixtureData = Join-Path $fixtureRoot "data"
    try {
        $key = [Uri]::UnescapeDataString($ConfigKey)
        $value = [Uri]::UnescapeDataString($ConfigValue)
        $result = switch ($Action) {
            "listclanconfig" { List-ClanConfig -ConfigRoot $fixtureConfig }
            "saveclanconfig" { Save-ClanConfig -Key $key -Value $value -ConfigRoot $fixtureConfig -DataRoot $fixtureData }
            "resetclanconfig" { Reset-ClanConfig -Key $key -ConfigRoot $fixtureConfig -DataRoot $fixtureData }
        }
    } catch { $result = "ERROR" + [char]9 + $_.Exception.Message }
    [IO.File]::WriteAllText((Join-Path $fixtureRoot "bridge.txt"), [string]$result, $utf8)
    exit
}

$fixtureRoot = Join-Path $logsRoot ("clan_config_hta_test_" + [Guid]::NewGuid().ToString("N"))
$fixtureConfig = Join-Path $fixtureRoot "config\clan"
$fixtureData = Join-Path $fixtureRoot "data"
New-Item -ItemType Directory -Path $fixtureConfig -Force | Out-Null
New-Item -ItemType Directory -Path $fixtureData -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $fixtureRoot ".clan-ui-fixture"), "Isolated clan UI regression fixture", $utf8)
Get-ChildItem -LiteralPath (Join-Path $repoRoot "config\clan") -Filter "clan_*.properties" |
    Copy-Item -Destination $fixtureConfig
$config = @{
    fixture = $fixtureRoot
    runner = $PSCommandPath
    report = (Join-Path $fixtureRoot "result.txt")
} | ConvertTo-Json -Compress

# Use the actual HTA shell, CSS, view and JScript; omit unrelated tabs/app startup.
$html = [IO.File]::ReadAllText((Join-Path $repoRoot "admin_data_menu.hta"))
$html = [regex]::Replace($html, '<script\b[\s\S]*?</script>', '')
$base = ([Uri]($repoRoot + "\")).AbsoluteUri
$html = $html.Replace('<head>', '<head><base href="' + $base + '">')
$html = $html.Replace('applicationname="NRO Admin Data"', 'applicationname="Clan Config Regression Test"').Replace('singleinstance="yes"', 'singleinstance="no"')
$view = [IO.File]::ReadAllText((Join-Path $repoRoot "admin_data_menu\views\clan-economy.html")).Replace('class="panel"', 'class="panel active"')
$html = $html.Replace('<div id="tabViews"></div>', '<div id="tabViews">' + $view + '</div>')
$scripts = ""
foreach ($relative in @("core/runtime.js", "core/utils.js", "core/ui.js", "core/registry.js", "components/config.js", "tabs/clan-economy.js")) {
    $scripts += '<script language="JScript" charset="utf-8" src="admin_data_menu/js/' + $relative + '"></script>'
}
$scripts += '<script language="JScript">var clanHtaTest = ' + $config + ';</script>'
$scripts += '<script language="JScript" charset="utf-8" src="tools/tests/ClanConfigHtaRuntimeTest.js"></script>'
$html = $html.Replace('</body>', $scripts + '</body>')
$hostPath = Join-Path $fixtureRoot "host.hta"
[IO.File]::WriteAllText($hostPath, $html, $utf8)
$testProcess = Start-Process -FilePath "$env:WINDIR\System32\mshta.exe" -ArgumentList ('"' + $hostPath + '"') -WindowStyle Hidden -PassThru
$deadline = [DateTime]::UtcNow.AddSeconds(120)
while (-not $testProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
    $testProcess.WaitForExit(1000) | Out-Null
}
if (-not $testProcess.HasExited) {
    Stop-Process -Id $testProcess.Id
    throw "HTA test timed out. Fixture retained: $fixtureRoot"
}
$reportPath = Join-Path $fixtureRoot "result.txt"
if (-not (Test-Path -LiteralPath $reportPath)) { throw "HTA did not produce a report: $fixtureRoot" }
$report = [IO.File]::ReadAllText($reportPath)
Write-Output $report
Write-Output "Fixture and visual preview: $fixtureRoot"
if (-not $report.StartsWith("PASS")) { throw "HTA runtime regression failed." }
