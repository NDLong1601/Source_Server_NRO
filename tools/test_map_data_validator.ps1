[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$validator = Join-Path $PSScriptRoot "validate_map_data.ps1"
$powershellExe = Join-Path $PSHOME "powershell.exe"
if (-not (Test-Path -LiteralPath $powershellExe -PathType Leaf)) {
    $powershellExe = (Get-Command powershell.exe -ErrorAction Stop).Source
}

$systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\')
$fixtureRoot = Join-Path $systemTemp ("nro-map-validator-" + [Guid]::NewGuid().ToString("N"))
$resolvedFixtureRoot = [System.IO.Path]::GetFullPath($fixtureRoot)
$fixtureLeaf = Split-Path -Leaf $resolvedFixtureRoot
if (-not $resolvedFixtureRoot.StartsWith($systemTemp + "\", [System.StringComparison]::OrdinalIgnoreCase) -or
        -not $fixtureLeaf.StartsWith("nro-map-validator-", [System.StringComparison]::Ordinal)) {
    throw "Đường dẫn fixture tạm không an toàn: $resolvedFixtureRoot"
}

function Invoke-ValidatorCase {
    param(
        [string]$Name,
        [int]$ExpectedExitCode,
        [string]$ExpectedText = ""
    )

    $output = @(& $powershellExe -NoProfile -ExecutionPolicy Bypass -File $validator -MapRoot $resolvedFixtureRoot -MaxDetails 100 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne $ExpectedExitCode) {
        throw "${Name}: expected exit $ExpectedExitCode nhưng nhận $exitCode.`n$($output -join [Environment]::NewLine)"
    }
    if ($ExpectedText -and -not (($output -join "`n").Contains($ExpectedText))) {
        throw "${Name}: output không chứa '$ExpectedText'.`n$($output -join [Environment]::NewLine)"
    }
    Write-Output "[PASS ] $Name"
}

try {
    $tileDirectory = Join-Path $resolvedFixtureRoot "tile_map_data"
    $backgroundDirectory = Join-Path $resolvedFixtureRoot "item_bg_map_data"
    New-Item -ItemType Directory -Path $tileDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $backgroundDirectory -Force | Out-Null

    $tileFile = Join-Path $tileDirectory "1"
    $backgroundFile = Join-Path $backgroundDirectory "1"
    [System.IO.File]::WriteAllBytes($tileFile, [byte[]](2, 2, 1, 2, 3, 4))
    [System.IO.File]::WriteAllBytes($backgroundFile, [byte[]](0, 1, 0, 5, 0, 2, 0, 3))
    Invoke-ValidatorCase -Name "canonical tile/background" -ExpectedExitCode 0 -ExpectedText "errors=0"

    [System.IO.File]::WriteAllBytes($tileFile, [byte[]](2, 2, 1, 2, 3, 4, 99))
    Invoke-ValidatorCase -Name "legacy trailing byte được cảnh báo" -ExpectedExitCode 0 -ExpectedText "legacy trailing byte"

    [System.IO.File]::WriteAllBytes($tileFile, [byte[]](2, 2, 1, 2, 3))
    Invoke-ValidatorCase -Name "truncated tile bị từ chối" -ExpectedExitCode 1 -ExpectedText "bị cắt"

    Write-Output "[TOTAL] validator self-test passed=3"
} finally {
    if (Test-Path -LiteralPath $resolvedFixtureRoot) {
        $verifiedRoot = [System.IO.Path]::GetFullPath($resolvedFixtureRoot)
        if ($verifiedRoot.StartsWith($systemTemp + "\", [System.StringComparison]::OrdinalIgnoreCase) -and
                (Split-Path -Leaf $verifiedRoot).StartsWith("nro-map-validator-", [System.StringComparison]::Ordinal)) {
            Remove-Item -LiteralPath $verifiedRoot -Recurse -Force
        }
    }
}
