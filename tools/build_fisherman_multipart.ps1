param(
    [string]$PartsSource = "C:\Users\PC\Downloads\1.png",
    [string]$AvatarSource = "C:\Users\PC\Downloads\avatar.png",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$Root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $Root "output\fisherman_multipart"
}
if (-not (Test-Path -LiteralPath $PartsSource)) { throw "Không tìm thấy ảnh part Ngư dân: $PartsSource" }
if (-not (Test-Path -LiteralPath $AvatarSource)) { throw "Không tìm thấy ảnh avatar Ngư dân: $AvatarSource" }
if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}

function Export-ConnectedComponent {
    param(
        [byte[]]$SourceBytes,
        [int]$SourceWidth,
        [int]$SourceHeight,
        [int]$SourceStride,
        [int]$SeedX,
        [int]$SeedY,
        [string]$Destination
    )

    if ($SourceBytes[$SeedY * $SourceStride + $SeedX * 4 + 3] -le 8) {
        throw "Seed ($SeedX,$SeedY) không nằm trong vùng alpha cần tách."
    }

    $seen = [Collections.BitArray]::new($SourceWidth * $SourceHeight)
    $queue = [Collections.Generic.Queue[int]]::new()
    $pixels = [Collections.Generic.List[int]]::new()
    $seedIndex = $SeedY * $SourceWidth + $SeedX
    $seen[$seedIndex] = $true
    $queue.Enqueue($seedIndex)
    $minX = $SeedX; $maxX = $SeedX; $minY = $SeedY; $maxY = $SeedY

    while ($queue.Count -gt 0) {
        $pixel = $queue.Dequeue()
        $pixels.Add($pixel)
        $pixelX = $pixel % $SourceWidth
        $pixelY = [int][Math]::Floor($pixel / $SourceWidth)
        if ($pixelX -lt $minX) { $minX = $pixelX }
        if ($pixelX -gt $maxX) { $maxX = $pixelX }
        if ($pixelY -lt $minY) { $minY = $pixelY }
        if ($pixelY -gt $maxY) { $maxY = $pixelY }

        foreach ($neighbor in @(($pixel - 1), ($pixel + 1), ($pixel - $SourceWidth), ($pixel + $SourceWidth))) {
            if ($neighbor -lt 0 -or $neighbor -ge $SourceWidth * $SourceHeight -or $seen[$neighbor]) { continue }
            $neighborX = $neighbor % $SourceWidth
            $neighborY = [int][Math]::Floor($neighbor / $SourceWidth)
            if ([Math]::Abs($neighborX - $pixelX) + [Math]::Abs($neighborY - $pixelY) -ne 1) { continue }
            if ($SourceBytes[$neighborY * $SourceStride + $neighborX * 4 + 3] -gt 8) {
                $seen[$neighbor] = $true
                $queue.Enqueue($neighbor)
            }
        }
    }

    $width = $maxX - $minX + 1
    $height = $maxY - $minY + 1
    $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $rect = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
        $data = $bitmap.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $destStride = [Math]::Abs($data.Stride)
            $destBytes = New-Object byte[] ($destStride * $height)
            foreach ($pixel in $pixels) {
                $sourceX = $pixel % $SourceWidth
                $sourceY = [int][Math]::Floor($pixel / $SourceWidth)
                $sourceOffset = $sourceY * $SourceStride + $sourceX * 4
                $destOffset = ($sourceY - $minY) * $destStride + ($sourceX - $minX) * 4
                $destBytes[$destOffset] = $SourceBytes[$sourceOffset]
                $destBytes[$destOffset + 1] = $SourceBytes[$sourceOffset + 1]
                $destBytes[$destOffset + 2] = $SourceBytes[$sourceOffset + 2]
                $destBytes[$destOffset + 3] = $SourceBytes[$sourceOffset + 3]
            }
            [Runtime.InteropServices.Marshal]::Copy($destBytes, 0, $data.Scan0, $destBytes.Length)
        } finally {
            $bitmap.UnlockBits($data)
        }
        $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }

    [pscustomobject]@{ Path = $Destination; X = $minX; Y = $minY; Width = $width; Height = $height; Pixels = $pixels.Count }
}

function Resize-Image {
    param([string]$Source, [string]$Destination, [int]$Width, [int]$Height)
    $image = [System.Drawing.Image]::FromFile($Source)
    try {
        $bitmap = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.DrawImage($image, 0, 0, $Width, $Height)
            } finally {
                $graphics.Dispose()
            }
            $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $image.Dispose()
    }
}

$partsBitmap = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $PartsSource).Path)
try {
    $sourceWidth = $partsBitmap.Width
    $sourceHeight = $partsBitmap.Height
    $sourceRect = [System.Drawing.Rectangle]::new(0, 0, $sourceWidth, $sourceHeight)
    $sourceData = $partsBitmap.LockBits($sourceRect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $sourceStride = [Math]::Abs($sourceData.Stride)
        $sourceBytes = New-Object byte[] ($sourceStride * $sourceHeight)
        [Runtime.InteropServices.Marshal]::Copy($sourceData.Scan0, $sourceBytes, 0, $sourceBytes.Length)
    } finally {
        $partsBitmap.UnlockBits($sourceData)
    }

    $headSource = Join-Path $OutputDirectory "head_source.png"
    $bodySource = Join-Path $OutputDirectory "body_source.png"
    $legSource = Join-Path $OutputDirectory "leg_source.png"
    $headInfo = Export-ConnectedComponent $sourceBytes $sourceWidth $sourceHeight $sourceStride 200 300 $headSource
    $bodyInfo = Export-ConnectedComponent $sourceBytes $sourceWidth $sourceHeight $sourceStride 750 300 $bodySource
    $legInfo = Export-ConnectedComponent $sourceBytes $sourceWidth $sourceHeight $sourceStride 1200 350 $legSource
} finally {
    $partsBitmap.Dispose()
}

# Avatar được đặt vào canvas vuông trước khi Admin sinh x1-x4 để không bị kéo méo.
$avatarImage = [System.Drawing.Image]::FromFile($AvatarSource)
try {
    $avatarCrop = [System.Drawing.Rectangle]::new(70, 0, 1174, 1143)
    $avatarSide = 1174
    $avatarSquare = [System.Drawing.Bitmap]::new($avatarSide, $avatarSide, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($avatarSquare)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.DrawImage($avatarImage, [System.Drawing.Rectangle]::new(0, 15, 1174, 1143), $avatarCrop, [System.Drawing.GraphicsUnit]::Pixel)
        } finally {
            $graphics.Dispose()
        }
        $avatarSquare.Save((Join-Path $OutputDirectory "avatar_source.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $avatarSquare.Dispose()
    }
} finally {
    $avatarImage.Dispose()
}

$headPreview = Join-Path $OutputDirectory "head_x4_preview.png"
$bodyPreview = Join-Path $OutputDirectory "body_x4_preview.png"
$legPreview = Join-Path $OutputDirectory "leg_x4_preview.png"
Resize-Image $headSource $headPreview 56 64
Resize-Image $bodySource $bodyPreview 76 84
Resize-Image $legSource $legPreview 48 80

$previewPath = Join-Path $OutputDirectory "fisherman_preview.png"
$preview = [System.Drawing.Bitmap]::new(260, 310, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    $graphics = [System.Drawing.Graphics]::FromImage($preview)
    try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(255, 105, 185, 210))
        $originX = 130; $groundY = 280
        $shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(95, 0, 0, 0))
        $groundPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 120, 82, 35), 3)
        try {
            $graphics.FillEllipse($shadowBrush, $originX - 26, $groundY - 7, 52, 14)
            $graphics.DrawLine($groundPen, 0, $groundY, 260, $groundY)
        } finally {
            $shadowBrush.Dispose(); $groundPen.Dispose()
        }

        $head = [System.Drawing.Image]::FromFile($headPreview)
        $body = [System.Drawing.Image]::FromFile($bodyPreview)
        $leg = [System.Drawing.Image]::FromFile($legPreview)
        try {
            # Ánh xạ vòng render slot để thứ tự nhìn thấy là Leg -> Body -> Head.
            # Client vẫn vẽ slot cố định Head -> Leg -> Body, không cần sửa client.
            $graphics.DrawImageUnscaled($leg, $originX - 6 * 4, $groundY - 20 * 4)
            $graphics.DrawImageUnscaled($body, $originX - 10 * 4, $groundY - 29 * 4)
            $graphics.DrawImageUnscaled($head, $originX - 7 * 4, $groundY - 41 * 4)
        } finally {
            $head.Dispose(); $body.Dispose(); $leg.Dispose()
        }

        $font = [System.Drawing.Font]::new("Arial", 13, [System.Drawing.FontStyle]::Bold)
        $yellow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::Yellow)
        $black = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::Black)
        try {
            $label = "Ngư dân"
            $size = $graphics.MeasureString($label, $font)
            $labelX = [single]($originX - $size.Width / 2)
            $labelY = [single]($groundY - 40 * 4 - $size.Height)
            $graphics.DrawString($label, $font, $black, $labelX + 1, $labelY + 1)
            $graphics.DrawString($label, $font, $yellow, $labelX, $labelY)
        } finally {
            $font.Dispose(); $yellow.Dispose(); $black.Dispose()
        }
    } finally {
        $graphics.Dispose()
    }
    $preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $preview.Dispose()
}

[pscustomobject]@{
    RenderOrder = "Leg -> Body -> Head"
    HeadSlotSource = $legSource; HeadSlotSize4 = "48x80"; HeadSlotDx = 7; HeadSlotDy = 14
    BodySlotSource = $headSource; BodySlotSize4 = "56x64"; BodySlotDx = 2; BodySlotDy = -25
    LegSlotSource = $bodySource; LegSlotSize4 = "76x84"; LegSlotDx = -2; LegSlotDy = -19
    HeadCrop = "$($headInfo.X),$($headInfo.Y),$($headInfo.Width),$($headInfo.Height)"
    BodyCrop = "$($bodyInfo.X),$($bodyInfo.Y),$($bodyInfo.Width),$($bodyInfo.Height)"
    LegCrop = "$($legInfo.X),$($legInfo.Y),$($legInfo.Width),$($legInfo.Height)"
    AvatarSource = (Join-Path $OutputDirectory "avatar_source.png"); AvatarSize4 = "180x180"
    Preview = $previewPath
} | Format-List
