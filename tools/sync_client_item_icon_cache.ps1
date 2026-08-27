[CmdletBinding()]
param(
    [ValidateSet('Sync', 'Verify')]
    [string]$Action = 'Sync',

    [Parameter(Mandatory = $true)]
    [int[]]$IconIds,

    [string]$CacheRoot,

    [ValidateRange(1, 4)]
    [int]$ZoomLevel = 0
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi dong bo cache icon.'
}

$workspaceRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($CacheRoot)) {
    $CacheRoot = Join-Path $env:USERPROFILE 'AppData\LocalLow\XUNGLORDLOCAL\XUNGLORDLOCAL'
}
$resolvedCacheRoot = $CacheRoot

if ($ZoomLevel -eq 0) {
    $zoomFile = Join-Path $resolvedCacheRoot 'lastZoomlevel'
    if (Test-Path -LiteralPath $zoomFile -PathType Leaf) {
        [byte[]]$savedZoom = [System.IO.File]::ReadAllBytes($zoomFile)
        if ($savedZoom.Length -gt 0 -and $savedZoom[0] -ge 1 -and $savedZoom[0] -le 4) {
            $ZoomLevel = $savedZoom[0]
        }
    }
    if ($ZoomLevel -eq 0) {
        $ZoomLevel = 2
    }
}

$sourceRoot = Join-Path $workspaceRoot ("data\icon\x{0}" -f $ZoomLevel)
if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Khong tim thay thu muc icon server: $sourceRoot"
}

$uniqueIds = @($IconIds | Where-Object { $_ -ge 0 } | Sort-Object -Unique)
if ($uniqueIds.Count -eq 0) {
    throw 'Can cung cap it nhat mot IconId >= 0.'
}

$missingSource = New-Object System.Collections.Generic.List[int]
$missingCache = New-Object System.Collections.Generic.List[int]
$alreadyCached = 0

foreach ($iconId in $uniqueIds) {
    $sourcePath = Join-Path $sourceRoot ("{0}.png" -f $iconId)
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        $missingSource.Add($iconId)
        continue
    }

    $cachePath = Join-Path $resolvedCacheRoot ("{0}Small{1}" -f $ZoomLevel, $iconId)
    if (Test-Path -LiteralPath $cachePath -PathType Leaf) {
        $alreadyCached++
        continue
    }

    $missingCache.Add($iconId)
}

if ($Action -eq 'Sync' -and $missingCache.Count -gt 0) {
    if (-not (Test-Path -LiteralPath $resolvedCacheRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $resolvedCacheRoot | Out-Null
    }
    foreach ($iconId in $missingCache) {
        Copy-Item -LiteralPath (Join-Path $sourceRoot ("{0}.png" -f $iconId)) `
            -Destination (Join-Path $resolvedCacheRoot ("{0}Small{1}" -f $ZoomLevel, $iconId))
    }
}

$status = if ($missingSource.Count -eq 0) { 'OK' } else { 'WARNING' }
Write-Host "${status}: zoom=x$ZoomLevel, da co=$alreadyCached, da them=$(if ($Action -eq 'Sync') { $missingCache.Count } else { 0 }), thieu-cache=$($missingCache.Count), thieu-nguon=$($missingSource.Count)."
if ($missingSource.Count -gt 0) {
    Write-Host ("Khong co icon tren server: " + ($missingSource -join ', '))
}
if ($Action -eq 'Verify' -and $missingCache.Count -gt 0) {
    exit 1
}
