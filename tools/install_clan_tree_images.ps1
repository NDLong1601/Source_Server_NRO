param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,
    [string]$RepositoryRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
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

$sourceFiles = Get-ChildItem -LiteralPath $sourceRoot -File -Filter 'cay_lv_*.png'
if ($sourceFiles.Count -ne 20) {
    throw "Source must contain exactly 20 PNG files named cay_lv_01..20; found $($sourceFiles.Count)."
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupRoot = Join-Path $repoRoot "artifacts\clan_tree_assets_backup_$stamp"
$plans = New-Object System.Collections.Generic.List[object]
$temporaryFiles = New-Object System.Collections.Generic.List[string]

try {
    foreach ($level in 1..20) {
        $name = 'cay_lv_{0:D2}.png' -f $level
        $sourcePath = Join-Path $sourceRoot $name
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Missing source image $name."
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
        }
        finally {
            $sourceImage.Dispose()
        }

        foreach ($zoom in 1..4) {
            $targetDirectory = Join-Path $assetRoot "x$zoom"
            $targetPath = Join-Path $targetDirectory $name
            if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
                throw "Existing asset required for backup is missing: $targetPath"
            }
            $backupDirectory = Join-Path $backupRoot "x$zoom"
            New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
            Copy-Item -LiteralPath $targetPath -Destination (Join-Path $backupDirectory $name) -Force

            $width = $baseWidth * $zoom
            $height = $baseHeight * $zoom
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
