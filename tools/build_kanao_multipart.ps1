param(
    [string]$PartsSource = "C:\Users\PC\Downloads\Kanao.png",
    [string]$AvatarSource = "C:\Users\PC\Downloads\Avatar.png"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$Root = Split-Path -Parent $PSScriptRoot
$HeadIconId = 25015
$BodyIconId = 25016
$LegIconId = 25017
$AvatarIconId = 25014

if (-not (Test-Path -LiteralPath $PartsSource)) {
    throw "Không tìm thấy ảnh part Kanao: $PartsSource"
}
if (-not (Test-Path -LiteralPath $AvatarSource)) {
    throw "Không tìm thấy ảnh avatar Kanao: $AvatarSource"
}

function New-TransparentBitmap {
    param([int]$Width, [int]$Height)
    [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Save-ScaledCrop {
    param(
        [System.Drawing.Image]$Source,
        [System.Drawing.Rectangle]$Crop,
        [int]$TargetWidth,
        [int]$TargetHeight,
        [string]$Destination
    )

    $bitmap = New-TransparentBitmap $TargetWidth $TargetHeight
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

            $scale = [Math]::Min($TargetWidth / [double]$Crop.Width, $TargetHeight / [double]$Crop.Height)
            $drawWidth = [Math]::Max(1, [int][Math]::Round($Crop.Width * $scale))
            $drawHeight = [Math]::Max(1, [int][Math]::Round($Crop.Height * $scale))
            $drawX = [int][Math]::Floor(($TargetWidth - $drawWidth) / 2.0)
            $drawY = [int][Math]::Floor(($TargetHeight - $drawHeight) / 2.0)
            $destinationRect = [System.Drawing.Rectangle]::new($drawX, $drawY, $drawWidth, $drawHeight)
            $graphics.DrawImage($Source, $destinationRect, $Crop, [System.Drawing.GraphicsUnit]::Pixel)
        } finally {
            $graphics.Dispose()
        }

        $directory = Split-Path -Parent $Destination
        if (-not (Test-Path -LiteralPath $directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }
        $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Save-ContainedImage {
    param(
        [System.Drawing.Image]$Source,
        [int]$TargetWidth,
        [int]$TargetHeight,
        [string]$Destination
    )
    $crop = [System.Drawing.Rectangle]::new(0, 0, $Source.Width, $Source.Height)
    Save-ScaledCrop -Source $Source -Crop $crop -TargetWidth $TargetWidth -TargetHeight $TargetHeight -Destination $Destination
}

function Export-IconScales {
    param(
        [System.Drawing.Image]$Source,
        [System.Drawing.Rectangle]$Crop,
        [int]$IconId,
        [int]$Width4,
        [int]$Height4
    )
    for ($scale = 1; $scale -le 4; $scale++) {
        $targetWidth = [int]($Width4 * $scale / 4)
        $targetHeight = [int]($Height4 * $scale / 4)
        $destination = Join-Path $Root ("data\icon\x{0}\{1}.png" -f $scale, $IconId)
        Save-ScaledCrop -Source $Source -Crop $Crop -TargetWidth $targetWidth -TargetHeight $targetHeight -Destination $destination
    }
}

$partsImage = [System.Drawing.Image]::FromFile($PartsSource)
try {
    # Alpha bounds from the supplied 1448x1086 source, plus a four-pixel safety margin.
    $headCrop = [System.Drawing.Rectangle]::new(7, 113, 443, 512)
    $bodyCrop = [System.Drawing.Rectangle]::new(477, 262, 551, 640)
    $legCrop = [System.Drawing.Rectangle]::new(1053, 363, 378, 667)

    Export-IconScales -Source $partsImage -Crop $headCrop -IconId $HeadIconId -Width4 56 -Height4 64
    Export-IconScales -Source $partsImage -Crop $bodyCrop -IconId $BodyIconId -Width4 72 -Height4 84
    Export-IconScales -Source $partsImage -Crop $legCrop -IconId $LegIconId -Width4 48 -Height4 84
} finally {
    $partsImage.Dispose()
}

$avatarImage = [System.Drawing.Image]::FromFile($AvatarSource)
try {
    for ($scale = 1; $scale -le 4; $scale++) {
        $side = 45 * $scale
        $destination = Join-Path $Root ("data\icon\x{0}\{1}.png" -f $scale, $AvatarIconId)
        Save-ContainedImage -Source $avatarImage -TargetWidth $side -TargetHeight $side -Destination $destination
    }
} finally {
    $avatarImage.Dispose()
}

# Render the exact NRO standing-frame composition for visual QA.
$previewWidth = 180
$previewHeight = 290
$originX = 90
$groundY = 270
$preview = New-TransparentBitmap $previewWidth $previewHeight
try {
    $graphics = [System.Drawing.Graphics]::FromImage($preview)
    try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(255, 99, 170, 112))
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(90, 0, 0, 0))
        try { $graphics.FillEllipse($shadowBrush, $originX - 28, $groundY - 6, 56, 12) } finally { $shadowBrush.Dispose() }

        $head = [System.Drawing.Image]::FromFile((Join-Path $Root "data\icon\x4\$HeadIconId.png"))
        $body = [System.Drawing.Image]::FromFile((Join-Path $Root "data\icon\x4\$BodyIconId.png"))
        $leg = [System.Drawing.Image]::FromFile((Join-Path $Root "data\icon\x4\$LegIconId.png"))
        try {
            # Thứ tự hình nhìn thấy đã chuẩn hóa: Leg -> Body -> Head.
            $graphics.DrawImageUnscaled($leg, $originX + ((-8 + 2) * 4), $groundY + ((-10 - 11) * 4))
            $graphics.DrawImageUnscaled($body, $originX + ((-9 + 0) * 4), $groundY + ((-16 - 13) * 4))
            $graphics.DrawImageUnscaled($head, $originX + ((-13 + 4) * 4), $groundY + ((-34 - 7) * 4))
        } finally {
            $head.Dispose()
            $body.Dispose()
            $leg.Dispose()
        }

        $groundPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(170, 255, 255, 255), 1)
        try { $graphics.DrawLine($groundPen, 0, $groundY, $previewWidth, $groundY) } finally { $groundPen.Dispose() }
    } finally {
        $graphics.Dispose()
    }
    $previewDir = Join-Path $Root "output"
    if (-not (Test-Path -LiteralPath $previewDir)) {
        New-Item -ItemType Directory -Path $previewDir -Force | Out-Null
    }
    $preview.Save((Join-Path $previewDir "kanao_multipart_preview.png"), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $preview.Dispose()
}

[pscustomobject]@{
    HeadIconId = $HeadIconId
    BodyIconId = $BodyIconId
    LegIconId = $LegIconId
    AvatarIconId = $AvatarIconId
    HeadSize4 = "56x64"
    BodySize4 = "72x84"
    LegSize4 = "48x84"
    AvatarSize4 = "180x180"
    HeadDx = 4
    HeadDy = -7
    BodyDx = 0
    BodyDy = -13
    LegDx = 2
    LegDy = -11
    Preview = (Join-Path $Root "output\kanao_multipart_preview.png")
} | Format-List
