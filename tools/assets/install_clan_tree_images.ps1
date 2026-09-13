<#
.SYNOPSIS
Installs a 20-level clan-tree sprite set at client zooms x1 through x4.

.DESCRIPTION
ScalePercentByLevel accepts exactly 20 comma-separated integer percentages,
ordered from tree level 1 to 20. The percentage is applied to the source
artwork's x1 canvas before every x1-x4 variant is generated.

.EXAMPLE
.\tools\assets\install_clan_tree_images.ps1 -SourceDirectory .\data\img_by_name\x4 `
  -SourcePrefix cay_lv_ -DestinationPrefix cay_lv_v4_ `
  -ScalePercentByLevel '261,244,200,188,184,175,186,198,200,211,197,207,215,221,231,210,222,226,234,239'
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,
    [string]$RepositoryRoot = '',
    [string]$SourcePrefix = 'cay_lv_',
    [string]$DestinationPrefix = 'cay_lv_',
    [string]$ScalePercentByLevel = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Assert-ImagePrefix {
    param([Parameter(Mandatory = $true)][string]$Prefix, [Parameter(Mandatory = $true)][string]$ParameterName)

    if ($Prefix -notmatch '^[a-z0-9_]{1,32}$') {
        throw "$ParameterName must use 1..32 lowercase letters, digits, or underscores."
    }
}

function Get-LevelScalePercents {
    param([string]$RawValue)

    if ([string]::IsNullOrWhiteSpace($RawValue)) {
        return @(1..20 | ForEach-Object { 100 })
    }
    $parts = $RawValue.Split(',')
    if ($parts.Count -ne 20) {
        throw 'ScalePercentByLevel must contain exactly 20 comma-separated percentages.'
    }
    $result = New-Object System.Collections.Generic.List[int]
    for ($index = 0; $index -lt $parts.Count; $index++) {
        $percent = 0
        if (-not [int]::TryParse($parts[$index].Trim(), [ref]$percent) -or $percent -lt 50 -or $percent -gt 600) {
            throw "ScalePercentByLevel level $($index + 1) must be an integer from 50 to 600."
        }
        $result.Add($percent)
    }
    return $result.ToArray()
}

Assert-ImagePrefix -Prefix $SourcePrefix -ParameterName 'SourcePrefix'
Assert-ImagePrefix -Prefix $DestinationPrefix -ParameterName 'DestinationPrefix'
$levelScalePercents = Get-LevelScalePercents -RawValue $ScalePercentByLevel

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Resize-ClanTreeImage {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height
    )

    $source = [System.Drawing.Image]::FromFile($SourcePath)
    try {
        $bitmap = New-Object System.Drawing.Bitmap($Width, $Height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $attributes = New-Object System.Drawing.Imaging.ImageAttributes
                try {
                    $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
                    $rectangle = New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)
                    $graphics.DrawImage($source, $rectangle, 0, 0, $source.Width, $source.Height,
                        [System.Drawing.GraphicsUnit]::Pixel, $attributes)
                }
                finally {
                    $attributes.Dispose()
                }
            }
            finally {
                $graphics.Dispose()
            }
            $bitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
        }
        finally {
            $bitmap.Dispose()
        }
    }
    finally {
        $source.Dispose()
    }
}

$sourceRoot = [System.IO.Path]::GetFullPath($SourceDirectory)
$repoRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
$assetRoot = Join-Path $repoRoot 'data\img_by_name'
if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Source directory not found: $sourceRoot"
}
if (-not (Test-Path -LiteralPath $assetRoot -PathType Container)) {
    throw "Repository has no data\img_by_name directory: $repoRoot"
}

$sourceFiles = @(foreach ($level in 1..20) {
    Join-Path $sourceRoot ($SourcePrefix + ('{0:D2}.png' -f $level))
})
$missingSourceFiles = @($sourceFiles | Where-Object {
    -not (Test-Path -LiteralPath $_ -PathType Leaf)
})
if ($missingSourceFiles.Count -gt 0) {
    throw "Source is missing required files: $($missingSourceFiles -join ', ')"
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupRoot = Join-Path $repoRoot "artifacts\clan_tree_assets_backup_$stamp"
$plans = New-Object System.Collections.Generic.List[object]
$temporaryFiles = New-Object System.Collections.Generic.List[string]

try {
    foreach ($level in 1..20) {
        $sourceName = $SourcePrefix + ('{0:D2}.png' -f $level)
        $name = $DestinationPrefix + ('{0:D2}.png' -f $level)
        $sourcePath = Join-Path $sourceRoot $sourceName
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Missing source image $sourceName."
        }

        $sourceImage = [System.Drawing.Bitmap]::FromFile($sourcePath)
        try {
            $dimensionsInvalid = $sourceImage.Width -lt 4 -or $sourceImage.Height -lt 4 `
                    -or $sourceImage.Width -gt 4096 -or $sourceImage.Height -gt 4096
            if ($dimensionsInvalid) {
                throw "$name dimensions must be within 4..4096px."
            }
            $transparentCorner = $sourceImage.GetPixel(0, 0).A -eq 0 `
                    -or $sourceImage.GetPixel($sourceImage.Width - 1, 0).A -eq 0 `
                    -or $sourceImage.GetPixel(0, $sourceImage.Height - 1).A -eq 0 `
                    -or $sourceImage.GetPixel($sourceImage.Width - 1, $sourceImage.Height - 1).A -eq 0
            if (-not $transparentCorner) {
                throw "$name has no transparent corner; verify background removal."
            }
            $baseWidth = [Math]::Max(1,
                [int][Math]::Round($sourceImage.Width / 4.0, [MidpointRounding]::AwayFromZero))
            $baseHeight = [Math]::Max(1,
                [int][Math]::Round($sourceImage.Height / 4.0, [MidpointRounding]::AwayFromZero))
            $scalePercent = $levelScalePercents[$level - 1]
            $scaledBaseWidth = [Math]::Max(1,
                [int][Math]::Round($baseWidth * $scalePercent / 100.0, [MidpointRounding]::AwayFromZero))
            $scaledBaseHeight = [Math]::Max(1,
                [int][Math]::Round($baseHeight * $scalePercent / 100.0, [MidpointRounding]::AwayFromZero))
        }
        finally {
            $sourceImage.Dispose()
        }

        foreach ($zoom in 1..4) {
            $targetDirectory = Join-Path $assetRoot "x$zoom"
            $targetPath = Join-Path $targetDirectory $name
            if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
                throw "Destination asset directory is missing: $targetDirectory"
            }
            if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
                $backupDirectory = Join-Path $backupRoot "x$zoom"
                New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
                Copy-Item -LiteralPath $targetPath -Destination (Join-Path $backupDirectory $name) -Force
            }

            $width = $scaledBaseWidth * $zoom
            $height = $scaledBaseHeight * $zoom
            $temporaryPath = "$targetPath.clan-tree-$PID-$stamp.tmp.png"
            Resize-ClanTreeImage -SourcePath $sourcePath -DestinationPath $temporaryPath `
                -Width $width -Height $height
            $temporaryFiles.Add($temporaryPath)

            $verification = [System.Drawing.Bitmap]::FromFile($temporaryPath)
            try {
                if ($verification.Width -ne $width -or $verification.Height -ne $height) {
                    throw "Generated $name x$zoom has invalid dimensions."
                }
                if ($verification.GetPixel(0, 0).A -ne 0) {
                    throw "Generated $name x$zoom lost its transparent background."
                }
            }
            finally {
                $verification.Dispose()
            }

            $plans.Add([pscustomobject]@{
                Level = $level
                Zoom = $zoom
                ScalePercent = $scalePercent
                Width = $width
                Height = $height
                TemporaryPath = $temporaryPath
                TargetPath = $targetPath
            })
        }
    }

    foreach ($plan in $plans) {
        Move-Item -LiteralPath $plan.TemporaryPath -Destination $plan.TargetPath -Force
        $temporaryFiles.Remove($plan.TemporaryPath) | Out-Null
    }
}
finally {
    foreach ($temporaryPath in $temporaryFiles) {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

Write-Output "CLAN_TREE_IMAGES_OK files=$($plans.Count) backup=$backupRoot"
