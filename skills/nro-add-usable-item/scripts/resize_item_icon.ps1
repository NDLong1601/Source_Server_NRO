[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$Root,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 32766)]
    [int]$IconId,

    [ValidateRange(0, 32766)]
    [int]$ReferenceId = 15002,

    [ValidateSet('x1', 'x2', 'x3', 'x4')]
    [string]$SourceScale = 'x4'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
$iconRoot = Join-Path $resolvedRoot 'data\icon'
if (-not (Test-Path -LiteralPath $iconRoot -PathType Container)) {
    throw "Icon directory not found: $iconRoot"
}

$scales = @('x1', 'x2', 'x3', 'x4')
$dimensions = [ordered]@{}
foreach ($scale in $scales) {
    $referencePath = Join-Path $iconRoot "$scale\$ReferenceId.png"
    if (-not (Test-Path -LiteralPath $referencePath -PathType Leaf)) {
        throw "Reference icon not found: $referencePath"
    }

    $reference = [System.Drawing.Image]::FromFile($referencePath)
    try {
        $dimensions[$scale] = [pscustomobject]@{
            Width = $reference.Width
            Height = $reference.Height
        }
    }
    finally {
        $reference.Dispose()
    }
}

$sourcePath = Join-Path $iconRoot "$SourceScale\$IconId.png"
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
    throw "Source icon not found: $sourcePath"
}

$sourceBytes = [System.IO.File]::ReadAllBytes($sourcePath)
$sourceStream = New-Object System.IO.MemoryStream(, $sourceBytes)
$source = [System.Drawing.Image]::FromStream($sourceStream)

try {
    foreach ($scale in $scales) {
        $size = $dimensions[$scale]
        $outputPath = Join-Path $iconRoot "$scale\$IconId.png"
        $tempPath = Join-Path $iconRoot "$scale\$IconId.resize.tmp.png"

        if (-not $PSCmdlet.ShouldProcess($outputPath, "Resize to $($size.Width)x$($size.Height)")) {
            continue
        }

        $bitmap = New-Object System.Drawing.Bitmap(
            $size.Width,
            $size.Height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        )

        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

                $attributes = New-Object System.Drawing.Imaging.ImageAttributes
                try {
                    $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
                    $destination = New-Object System.Drawing.Rectangle(
                        0,
                        0,
                        $size.Width,
                        $size.Height
                    )
                    $graphics.DrawImage(
                        $source,
                        $destination,
                        0,
                        0,
                        $source.Width,
                        $source.Height,
                        [System.Drawing.GraphicsUnit]::Pixel,
                        $attributes
                    )
                }
                finally {
                    $attributes.Dispose()
                }
            }
            finally {
                $graphics.Dispose()
            }

            $bitmap.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
        }
        finally {
            $bitmap.Dispose()
        }

        Move-Item -LiteralPath $tempPath -Destination $outputPath -Force
        Write-Output "$scale`t$($size.Width)x$($size.Height)`t$outputPath"
    }
}
finally {
    $source.Dispose()
    $sourceStream.Dispose()
}
