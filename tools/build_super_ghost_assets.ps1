param(
    [string]$ServerRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ClientRoot = 'C:\Users\PC\Music\PRJ_2Tab_550K',
    [string]$SourceDirectory = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# These are the user's exact eight PNGs. Only deterministic fitting/padding is
# performed; alpha, colours, outlines and poses are not regenerated or flattened.
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
public static class SuperGhostBounds {
    public static Rectangle Find(Bitmap image) {
        int left=image.Width, top=image.Height, right=-1, bottom=-1;
        for(int y=0;y<image.Height;y++)
            for(int x=0;x<image.Width;x++)
                if(image.GetPixel(x,y).A > 8) {
                    left=Math.Min(left,x); top=Math.Min(top,y);
                    right=Math.Max(right,x); bottom=Math.Max(bottom,y);
                }
        if(right<left) throw new Exception("Empty source frame");
        return Rectangle.FromLTRB(left,top,right+1,bottom+1);
    }
}
'@

$frames = @(
    @{ Name='Triệu hồi 1.png'; AnchorX=625; AnchorY=620; Size=264 },
    @{ Name='Triệu hồi 2.png'; AnchorX=622; AnchorY=554; Size=264 },
    @{ Name='Triệu hồi 3.png'; AnchorX=634; AnchorY=549; Size=264 },
    @{ Name='Hoàn chỉnh sau triệu hồi.png'; AnchorX=645; AnchorY=563; Size=264 },
    @{ Name='Bay tấn công 1.png'; AnchorX=973; AnchorY=614; Size=264 },
    @{ Name='Bay tấn công 2.png'; AnchorX=984; AnchorY=652; Size=264 },
    @{ Name='Bay tấn công 3.png'; AnchorX=1014; AnchorY=631; Size=264 },
    @{ Name='Vụ nổ.png'; AnchorX=606; AnchorY=676; Size=288 }
)
$outputDirectory = Join-Path $ClientRoot 'Assets\Resources\res\x4\super_ghost_kamikaze'
$archiveDirectory = Join-Path $ServerRoot 'assets\super_ghost_kamikaze_v2'
$previewDirectory = Join-Path $ServerRoot 'output\super_ghost_kamikaze'
if ([string]::IsNullOrWhiteSpace($SourceDirectory)) {
    $SourceDirectory = $archiveDirectory
}
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[IO.Directory]::CreateDirectory($archiveDirectory) | Out-Null
[IO.Directory]::CreateDirectory($previewDirectory) | Out-Null
$layout = @()
for($index=0;$index -lt $frames.Count;$index++) {
    $frame = $frames[$index]
    $sourcePath = Join-Path $SourceDirectory $frame.Name
    $archivePath = Join-Path $archiveDirectory $frame.Name
    if (-not [string]::Equals([IO.Path]::GetFullPath($sourcePath),
            [IO.Path]::GetFullPath($archivePath), [StringComparison]::OrdinalIgnoreCase)) {
        Copy-Item -LiteralPath $sourcePath -Destination $archivePath -Force
    }
    $source = [Drawing.Bitmap]::FromFile($sourcePath)
    $target = [Drawing.Bitmap]::new($frame.Size,$frame.Size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($target)
    try {
        $crop = [SuperGhostBounds]::Find($source)
        $scale = [Math]::Min(($frame.Size-16)/$crop.Width,($frame.Size-16)/$crop.Height)
        $width = [int][Math]::Round($crop.Width*$scale)
        $height = [int][Math]::Round($crop.Height*$scale)
        $left = [int][Math]::Floor(($frame.Size-$width)/2)
        $top = [int][Math]::Floor(($frame.Size-$height)/2)
        $graphics.Clear([Drawing.Color]::Transparent)
        $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source,[Drawing.Rectangle]::new($left,$top,$width,$height),
            $crop.X,$crop.Y,$crop.Width,$crop.Height,[Drawing.GraphicsUnit]::Pixel)
        $outputPath = Join-Path $outputDirectory ("frame_{0}.png" -f $index)
        $target.Save($outputPath,[Drawing.Imaging.ImageFormat]::Png)
        $px = [int][Math]::Round($left+($frame.AnchorX-$crop.X)*$width/$crop.Width)
        $py = [int][Math]::Round($top+($frame.AnchorY-$crop.Y)*$height/$crop.Height)
        $layout += [pscustomobject]@{Index=$index;Name=$frame.Name;Size=$frame.Size;PivotX=$px;PivotY=$py;Crop=$crop.ToString()}
    }
    finally { $graphics.Dispose(); $target.Dispose(); $source.Dispose() }
}
$preview = [Drawing.Bitmap]::new(1152,600)
$previewGraphics = [Drawing.Graphics]::FromImage($preview)
$light = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(230,237,246))
$dark = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(176,190,210))
$font = [Drawing.Font]::new('Arial',11)
try {
    for($y=0;$y -lt 600;$y+=20) {
        for($x=0;$x -lt 1152;$x+=20) {
            $brush = if((($x/20)+($y/20))%2 -eq 0){$light}else{$dark}
            $previewGraphics.FillRectangle($brush,$x,$y,20,20)
        }
    }
    foreach($row in $layout) {
        $frameImage=[Drawing.Bitmap]::FromFile((Join-Path $outputDirectory ("frame_{0}.png" -f $row.Index)))
        try {
            $x=($row.Index%4)*288; $y=[int][Math]::Floor($row.Index/4)*300
            $previewGraphics.DrawImage($frameImage,$x,$y,$row.Size,$row.Size)
            $previewGraphics.DrawString(("$($row.Index): $($row.Name)"),$font,[Drawing.Brushes]::Black,$x+8,$y+280)
        } finally { $frameImage.Dispose() }
    }
    $preview.Save((Join-Path $previewDirectory 'frames_preview.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $light.Dispose(); $dark.Dispose(); $previewGraphics.Dispose(); $preview.Dispose() }
$layout | Format-Table -AutoSize
Write-Output ('pivotX = { ' + (($layout | ForEach-Object {$_.PivotX}) -join ', ') + ' };')
Write-Output ('pivotY = { ' + (($layout | ForEach-Object {$_.PivotY}) -join ', ') + ' };')
Write-Output 'Built 8 exact-source alpha frames. Skill icon 26554 was intentionally left unchanged.'
