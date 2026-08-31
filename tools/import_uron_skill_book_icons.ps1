[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,
    [string]$Root
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
}
$resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
$resolvedSourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path
$iconRoot = Join-Path $resolvedRoot 'data\icon'
$origRoot = Join-Path $iconRoot 'orig'
$scales = 1..4

# The folders are sorted by name. Their stable order is checked before each
# import against the supplied source directory.
$groups = @(
    [pscustomobject]@{ Name = 'Kaioken'; FirstItemId = 300; FirstIconId = 26456 },
    [pscustomobject]@{ Name = 'EnergyBall'; FirstItemId = 307; FirstIconId = 26463 },
    [pscustomobject]@{ Name = 'GiantApe'; FirstItemId = 314; FirstIconId = 26470 },
    [pscustomobject]@{ Name = 'Chocolate'; FirstItemId = 474; FirstIconId = 26505 },
    [pscustomobject]@{ Name = 'SuicideBomb'; FirstItemId = 321; FirstIconId = 26477 },
    # Keep the source-folder/icon-ID positions: this folder depicts an egg,
    # and the following one depicts teleportation. Only item attribution changes.
    [pscustomobject]@{ Name = 'Egg'; FirstItemId = 335; FirstIconId = 26519 },
    [pscustomobject]@{ Name = 'Teleport'; FirstItemId = 488; FirstIconId = 26491 },
    [pscustomobject]@{ Name = 'Whistle'; FirstItemId = 509; FirstIconId = 26540 },
    [pscustomobject]@{ Name = 'EnergyShield'; FirstItemId = 434; FirstIconId = 26498 },
    [pscustomobject]@{ Name = 'LazeMakankosappo'; FirstItemId = 328; FirstIconId = 26484 },
    [pscustomobject]@{ Name = 'Combo'; FirstItemId = 481; FirstIconId = 26512 },
    [pscustomobject]@{ Name = 'Hypnosis'; FirstItemId = 495; FirstIconId = 26526 },
    [pscustomobject]@{ Name = 'Bind'; FirstItemId = 502; FirstIconId = 26533 }
)

function Save-BookIcon {
    param(
        [System.Drawing.Image]$Source,
        [string]$Destination,
        [int]$Side
    )

    $bitmap = [System.Drawing.Bitmap]::new(
        $Side,
        $Side,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

        # Fit (do not stretch) the 60x64 source on the square book canvas.
        $ratio = [Math]::Min($Side / [double]$Source.Width, $Side / [double]$Source.Height)
        $width = [Math]::Max(1, [int][Math]::Round($Source.Width * $ratio))
        $height = [Math]::Max(1, [int][Math]::Round($Source.Height * $ratio))
        $left = [int][Math]::Floor(($Side - $width) / 2)
        $top = [int][Math]::Floor(($Side - $height) / 2)
        $graphics.DrawImage($Source, [System.Drawing.Rectangle]::new($left, $top, $width, $height))
        $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

foreach ($scale in $scales) {
    $scalePath = Join-Path $iconRoot "x$scale"
    if (-not (Test-Path -LiteralPath $scalePath -PathType Container)) {
        throw "Missing destination icon directory: $scalePath"
    }
}

$sourceFolders = @(Get-ChildItem -LiteralPath $resolvedSourceRoot -Directory | Sort-Object Name)
if ($sourceFolders.Count -ne $groups.Count) {
    throw "Expected $($groups.Count) source folders but found $($sourceFolders.Count)."
}

$jobs = New-Object System.Collections.Generic.List[object]
for ($groupIndex = 0; $groupIndex -lt $groups.Count; $groupIndex++) {
    $group = $groups[$groupIndex]
    $sourceFiles = @(Get-ChildItem -LiteralPath $sourceFolders[$groupIndex].FullName -File -Filter '*.png' | Sort-Object Name)
    if ($sourceFiles.Count -ne 7) {
        throw "Expected seven PNG files in source folder index $groupIndex but found $($sourceFiles.Count)."
    }

    for ($level = 1; $level -le 7; $level++) {
        $sourcePath = $sourceFiles[$level - 1].FullName
        $source = [System.Drawing.Image]::FromFile($sourcePath)
        try {
            if ($source.Width -ne 60 -or $source.Height -ne 64) {
                throw "Unexpected source size for $sourcePath; expected 60x64."
            }
        }
        finally {
            $source.Dispose()
        }

        $jobs.Add([pscustomobject]@{
                Group = $group
                Level = $level
                ItemId = $group.FirstItemId + $level - 1
                IconId = $group.FirstIconId + $level - 1
                SourcePath = $sourcePath
            })
    }
}

if ($jobs.Count -ne 91 -or (@($jobs.IconId | Sort-Object -Unique).Count -ne 91)) {
    throw 'The Uron mapping must contain exactly 91 unique icons.'
}

New-Item -ItemType Directory -Path $origRoot -Force | Out-Null
foreach ($job in $jobs) {
    Copy-Item -LiteralPath $job.SourcePath -Destination (Join-Path $origRoot "$($job.IconId).png") -Force
    $source = [System.Drawing.Image]::FromFile($job.SourcePath)
    try {
        foreach ($scale in $scales) {
            Save-BookIcon $source (Join-Path $iconRoot "x$scale\$($job.IconId).png") (24 * $scale)
        }
    }
    finally {
        $source.Dispose()
    }
    Write-Output ("{0} level {1}: item {2} -> icon {3}" -f $job.Group.Name, $job.Level, $job.ItemId, $job.IconId)
}

Write-Output 'Imported 91 Uron book icons at x1, x2, x3 and x4.'
