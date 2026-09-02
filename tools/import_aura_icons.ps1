[CmdletBinding()]
param(
    [string]$SourceRoot,
    [string]$Root,
    [int[]]$ItemIds
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($SourceRoot)) {
    $SourceRoot = Join-Path $Root 'output\aura-icon-sources'
}

$resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
$resolvedSourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path
$iconRoot = Join-Path $resolvedRoot 'data\icon'
$origRoot = Join-Path $iconRoot 'orig'

$assets = @(
    @{ ItemId = 2154; File = '2154-hac-giap-ma-ton.png' },
    @{ ItemId = 2155; File = '2155-thanh-long-ho-the.png' },
    @{ ItemId = 2156; File = '2156-cuong-vien-kim-cang.png' },
    @{ ItemId = 2157; File = '2157-loi-than-kim-cang.png' },
    @{ ItemId = 2158; File = '2158-bach-diem-vo-tuong.png' },
    @{ ItemId = 2159; File = '2159-lam-diem-cuong-phong.png' },
    @{ ItemId = 2160; File = '2160-hoang-diem-thien-uy.png' },
    @{ ItemId = 2161; File = '2161-bach-quang-bao-khi.png' },
    @{ ItemId = 2162; File = '2162-huyet-an-ma-viem.png' },
    @{ ItemId = 2163; File = '2163-u-linh-tu-khi.png' },
    @{ ItemId = 2164; File = '2164-bang-tam-huyet-diem.png' },
    @{ ItemId = 2165; File = '2165-xich-diem-sat-khi.png' },
    @{ ItemId = 2166; File = '2166-tu-diem-cuc-quang.png' },
    @{ ItemId = 2167; File = '2167-han-bang-thanh-khi.png' },
    @{ ItemId = 2168; File = '2168-ma-dang-tu-diem.png' },
    @{ ItemId = 2169; File = '2169-loi-cau-tu-dien.png' },
    @{ ItemId = 2170; File = '2170-thien-thanh-loi-nguc.png' },
    @{ ItemId = 2171; File = '2171-tu-hoa-u-minh.png' },
    @{ ItemId = 2172; File = '2172-loi-hoa-lam-tinh.png' },
    @{ ItemId = 2173; File = '2173-moc-linh-luc-viem.png' },
    @{ ItemId = 2174; File = '2174-nhat-viem-kim-hoa.png' },
    @{ ItemId = 2175; File = '2175-huyet-nguc-tu-viem.png' },
    @{ ItemId = 2176; File = '2176-ma-diem-huyet-tam.png' },
    @{ ItemId = 2177; File = '2177-u-lam-quy-hoa.png' },
    @{ ItemId = 2178; File = '2178-sinh-menh-luc-hoa.png' },
    @{ ItemId = 2179; File = '2179-liet-duong-cuong-viem.png' },
    @{ ItemId = 2180; File = '2180-han-lang-khieu-nguyet.png' },
    @{ ItemId = 2181; File = '2181-lam-thien-bao-khi.png' },
    @{ ItemId = 2182; File = '2182-tu-loi-bao-khi.png' },
    @{ ItemId = 2183; File = '2183-thai-duong-bao-khi.png' },
    @{ ItemId = 2184; File = '2184-pha-le-tu-gioi.png' },
    @{ ItemId = 2185; File = '2185-loi-viem-ho-phach.png' },
    @{ ItemId = 2186; File = '2186-bach-kim-than-khi.png' },
    @{ ItemId = 2187; File = '2187-hong-lien-loi-khi.png' },
    @{ ItemId = 2188; File = '2188-huyet-anh-loi-khi.png' },
    @{ ItemId = 2189; File = '2189-xich-hong-than-khi.png' },
    @{ ItemId = 2190; File = '2190-hoang-kim-thanh-khi.png' },
    @{ ItemId = 2191; File = '2191-hac-bach-vo-cuc.png' },
    @{ ItemId = 2192; File = '2192-ngu-luan-nhat-quang.png' },
    @{ ItemId = 2193; File = '2193-cuc-quang-bang-tinh.png' },
    @{ ItemId = 2194; File = '2194-kim-diem-tinh-thach.png' },
    @{ ItemId = 2195; File = '2195-thanh-quang-bach-kim.png' },
    @{ ItemId = 2196; File = '2196-ngoc-bich-thien-quang.png' },
    @{ ItemId = 2197; File = '2197-tham-lam-tinh-vuc.png' },
    @{ ItemId = 2198; File = '2198-huyet-tinh-diet-quang.png' },
    @{ ItemId = 2199; File = '2199-huyet-lang-pha-gioi.png' },
    @{ ItemId = 2200; File = '2200-tu-loi-thien-no.png' },
    @{ ItemId = 2201; File = '2201-xich-loi-thien-phat.png' },
    @{ ItemId = 2202; File = '2202-hong-lien-hon-chau.png' },
    @{ ItemId = 2203; File = '2203-luc-trong-ma-an.png' },
    @{ ItemId = 2204; File = '2204-tinh-van-luan-hoi.png' },
    @{ ItemId = 2205; File = '2205-tu-tinh-phap-luan.png' },
    @{ ItemId = 2206; File = '2206-luc-nguyen-sinh-khi.png' },
    @{ ItemId = 2207; File = '2207-tu-hong-mong-diem.png' },
    @{ ItemId = 2208; File = '2208-huyet-hong-me-diem.png' },
    @{ ItemId = 2209; File = '2209-hoa-nguc-cam-viem.png' },
    @{ ItemId = 2210; File = '2210-huyet-diem-tu-la.png' },
    @{ ItemId = 2211; File = '2211-hoang-loi-vuong-khi.png' },
    @{ ItemId = 2212; File = '2212-tham-lam-chien-khi.png' },
    @{ ItemId = 2213; File = '2213-thien-thanh-linh-khi.png' },
    @{ ItemId = 2214; File = '2214-bich-hai-linh-khi.png' },
    @{ ItemId = 2215; File = '2215-luc-quang-thien-menh.png' },
    @{ ItemId = 2216; File = '2216-hoang-luc-long-khi.png' },
    @{ ItemId = 2217; File = '2217-ngoc-lam-long-khi.png' }
)

function Save-AuraIcon {
    param([System.Drawing.Image]$Source, [string]$Destination, [int]$Side)

    $bitmap = [System.Drawing.Bitmap]::new($Side, $Side, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $fit = [Math]::Min($Side / [double]$Source.Width, $Side / [double]$Source.Height) * 0.94
        $width = [Math]::Max(1, [int][Math]::Round($Source.Width * $fit))
        $height = [Math]::Max(1, [int][Math]::Round($Source.Height * $fit))
        $left = [int][Math]::Round(($Side - $width) / 2.0)
        $top = [int][Math]::Round(($Side - $height) / 2.0)
        $graphics.DrawImage($Source, [System.Drawing.Rectangle]::new($left, $top, $width, $height))
        $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

if (-not ('AuraIconBackdrop' -as [type])) {
    $drawingReferences = @([AppDomain]::CurrentDomain.GetAssemblies() |
        Where-Object { $_.Location -and $_.GetName().Name -match '^(System\.Drawing|System\.Private\.Windows)' } |
        ForEach-Object { $_.Location } |
        Sort-Object -Unique)
    $drawingReferences += [System.Collections.Generic.Queue[int]].Assembly.Location
    Add-Type -ReferencedAssemblies ($drawingReferences | Sort-Object -Unique) -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;

public static class AuraIconBackdrop
{
    private static bool IsWhiteBackdrop(Color color)
    {
        int minimum = Math.Min(color.R, Math.Min(color.G, color.B));
        int maximum = Math.Max(color.R, Math.Max(color.G, color.B));
        return color.A > 0 && minimum >= 215 && maximum - minimum <= 24;
    }

    private static void AddSeed(Bitmap clean, int x, int y, int width, bool[] seen, int[] queue, ref int queueTail)
    {
        int position = y * width + x;
        if (!seen[position] && IsWhiteBackdrop(clean.GetPixel(x, y)))
        {
            seen[position] = true;
            queue[queueTail++] = position;
        }
    }

    public static Bitmap Remove(Image source)
    {
        Bitmap clean = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
        using (Graphics canvas = Graphics.FromImage(clean))
        {
            canvas.CompositingMode = System.Drawing.Drawing2D.CompositingMode.SourceCopy;
            canvas.DrawImageUnscaled(source, 0, 0);
        }

        int width = clean.Width;
        int height = clean.Height;
        bool[] seen = new bool[width * height];
        int[] queue = new int[width * height];
        int queueHead = 0;
        int queueTail = 0;

        for (int x = 0; x < width; x++)
        {
            AddSeed(clean, x, 0, width, seen, queue, ref queueTail);
            AddSeed(clean, x, height - 1, width, seen, queue, ref queueTail);
        }
        for (int y = 1; y < height - 1; y++)
        {
            AddSeed(clean, 0, y, width, seen, queue, ref queueTail);
            AddSeed(clean, width - 1, y, width, seen, queue, ref queueTail);
        }

        while (queueHead < queueTail)
        {
            int position = queue[queueHead++];
            int x = position % width;
            int y = position / width;
            clean.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));

            for (int deltaY = -1; deltaY <= 1; deltaY++)
            {
                for (int deltaX = -1; deltaX <= 1; deltaX++)
                {
                    if (deltaX == 0 && deltaY == 0) continue;
                    int nextX = x + deltaX;
                    int nextY = y + deltaY;
                    if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) continue;
                    int nextPosition = nextY * width + nextX;
                    if (!seen[nextPosition] && IsWhiteBackdrop(clean.GetPixel(nextX, nextY)))
                    {
                        seen[nextPosition] = true;
                        queue[queueTail++] = nextPosition;
                    }
                }
            }
        }
        return clean;
    }
}
'@
}

function Remove-WhiteBackdrop {
    param([System.Drawing.Image]$Source)
    return [AuraIconBackdrop]::Remove($Source)
}

New-Item -ItemType Directory -Force -Path $origRoot | Out-Null
foreach ($scale in 1..4) {
    New-Item -ItemType Directory -Force -Path (Join-Path $iconRoot "x$scale") | Out-Null
}

$selectedAssets = if ($ItemIds -and $ItemIds.Count -gt 0) {
    @($assets | Where-Object { $ItemIds -contains [int]$_.ItemId })
} else {
    $assets
}
if ($selectedAssets.Count -eq 0) {
    throw 'Không có item hào quang hợp lệ để import.'
}
if ($ItemIds -and $selectedAssets.Count -ne @($ItemIds | Sort-Object -Unique).Count) {
    throw 'Có ItemIds nằm ngoài dải hào quang 2154..2217.'
}

foreach ($asset in $selectedAssets) {
    $sourcePath = Join-Path $resolvedSourceRoot $asset.File
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Thiếu icon nguồn: $sourcePath"
    }
    $iconId = 26576 + ([int]$asset.ItemId - 2154)
    $source = [System.Drawing.Image]::FromFile($sourcePath)
    try {
        if ($source.Width -lt 128 -or $source.Height -lt 128) {
            throw "Icon nguồn $($asset.File) phải có cả hai chiều tối thiểu 128px."
        }
        $transparentSource = Remove-WhiteBackdrop -Source $source
        try {
            $transparentSource.Save((Join-Path $origRoot "$iconId.png"), [System.Drawing.Imaging.ImageFormat]::Png)
            foreach ($scale in 1..4) {
                Save-AuraIcon -Source $transparentSource -Destination (Join-Path $iconRoot "x$scale\$iconId.png") -Side (24 * $scale)
            }
        }
        finally {
            $transparentSource.Dispose()
        }
    }
    finally {
        $source.Dispose()
    }
    Write-Output ("item {0} -> icon {1} ({2})" -f $asset.ItemId, $iconId, $asset.File)
}

Write-Output ("Imported {0} dedicated aura icons with transparent padding at x1-x4." -f $selectedAssets.Count)
