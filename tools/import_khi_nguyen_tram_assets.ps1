[CmdletBinding()]
param(
    [string]$SourceRoot = 'C:\Users\PC\Downloads\Skill mới\Khí Nguyên Trảm'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$iconId = 14658
$bookIconIds = 14659..14665

function Require-Source([string]$Name) {
    $path = Join-Path $SourceRoot $Name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Không tìm thấy asset nguồn: $path"
    }
    return $path
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-Canvas([int]$Width, [int]$Height) {
    $canvas = New-Object System.Drawing.Bitmap($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    return @{ Bitmap = $canvas; Graphics = $graphics }
}

function Write-ScaledImage([System.Drawing.Image]$Source, [string]$Path, [int]$Width, [int]$Height) {
    $canvas = New-Canvas $Width $Height
    try {
        $canvas.Graphics.DrawImage($Source, (New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)))
        Save-Png $canvas.Bitmap $Path
    }
    finally {
        $canvas.Graphics.Dispose()
        $canvas.Bitmap.Dispose()
    }
}

function Write-BookIcon([System.Drawing.Image]$Source, [string]$Path, [int]$Side) {
    $canvas = New-Canvas $Side $Side
    try {
        $ratio = [Math]::Min($Side / [double]$Source.Width, $Side / [double]$Source.Height)
        $width = [Math]::Max(1, [int][Math]::Round($Source.Width * $ratio))
        $height = [Math]::Max(1, [int][Math]::Round($Source.Height * $ratio))
        $left = [int][Math]::Floor(($Side - $width) / 2)
        $top = [int][Math]::Floor(($Side - $height) / 2)
        $canvas.Graphics.DrawImage($Source, (New-Object System.Drawing.Rectangle($left, $top, $width, $height)))
        Save-Png $canvas.Bitmap $Path
    }
    finally {
        $canvas.Graphics.Dispose()
        $canvas.Bitmap.Dispose()
    }
}

function Write-Int16BigEndian([System.IO.BinaryWriter]$Writer, [int]$Value) {
    $Writer.Write([byte](($Value -shr 8) -band 0xFF))
    $Writer.Write([byte]($Value -band 0xFF))
}

function Write-EffectData([string]$Path) {
    # DataEffect format: image regions, frames, frame sequence, then two frame delays.
    # Coordinates are in x1 pixels; the client scales them for x2-x4 payloads.
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    $writer = New-Object System.IO.BinaryWriter($stream)
    try {
        # ImageInfo coordinates are bytes in the legacy client format.  The source
        # sheet is therefore normalized to a 240x80 x1 atlas (six 40px frames).
        $frameEdges = 0..6 | ForEach-Object { [int][Math]::Round($_ * 240 / 6.0) }
        $writer.Write([byte]6)
        for ($i = 0; $i -lt 6; $i++) {
            $writer.Write([byte]$i)
            $writer.Write([byte]$frameEdges[$i])
            $writer.Write([byte]0)
            $writer.Write([byte]($frameEdges[$i + 1] - $frameEdges[$i]))
            $writer.Write([byte]80)
        }

        Write-Int16BigEndian $writer 6
        for ($i = 0; $i -lt 6; $i++) {
            $writer.Write([byte]1)
            Write-Int16BigEndian $writer -20
            Write-Int16BigEndian $writer -40
            $writer.Write([byte]$i)
        }

        Write-Int16BigEndian $writer 12
        for ($i = 0; $i -lt 6; $i++) {
            Write-Int16BigEndian $writer $i
            Write-Int16BigEndian $writer $i
        }
        $writer.Write([byte]50)
        $writer.Write([byte]50)
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

$skillIconPath = Require-Source 'Hình ảnh chiêu thức trong tab skill.png'
$effectPath = Require-Source 'khí nguyên trảm.png'

$skillIcon = [System.Drawing.Image]::FromFile($skillIconPath)
try {
    foreach ($zoom in 1..4) {
        $side = 20 * $zoom
        Write-ScaledImage $skillIcon (Join-Path $repoRoot "data\icon\x$zoom\$iconId.png") $side $side
    }
}
finally {
    $skillIcon.Dispose()
}

for ($level = 1; $level -le 7; $level++) {
    $book = [System.Drawing.Image]::FromFile((Require-Source "Skill_Book_Level_$level.png"))
    try {
        foreach ($zoom in 1..4) {
            Write-BookIcon $book (Join-Path $repoRoot "data\icon\x$zoom\$($bookIconIds[$level - 1]).png") (24 * $zoom)
        }
    }
    finally {
        $book.Dispose()
    }
}

$effect = [System.Drawing.Image]::FromFile($effectPath)
try {
    foreach ($zoom in 1..4) {
        $width = 240 * $zoom
        $height = 80 * $zoom
        Write-ScaledImage $effect (Join-Path $repoRoot "data\effect\x$zoom\ImgEffect_27.png") $width $height
    }
}
finally {
    $effect.Dispose()
}

Write-EffectData (Join-Path $repoRoot 'data\effdata\DataEffect_27')
Write-Host "Đã import Khí Nguyên Trảm: skill icon $iconId, book icons $($bookIconIds -join ', '), EffectData_27."
