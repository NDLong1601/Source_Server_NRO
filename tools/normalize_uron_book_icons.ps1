[CmdletBinding()]
param(
    [string]$Root,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [int]$ReferenceId = 26512,
    [string]$SourceRevision = 'HEAD',
    [switch]$Apply
)

# Extends the resize_item_icon workflow: normalize the visible book, not the
# PNG canvas. Rebuild from orig/ or a pinned Git source, never from resized PNGs.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if ([string]::IsNullOrWhiteSpace($Root)) { $Root=Split-Path -Parent $PSScriptRoot }
$Root = (Resolve-Path -LiteralPath $Root).Path
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ($OutputDirectory.StartsWith((Join-Path $Root 'data'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The staging/backup directory must be outside live data/.'
}
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new output directory; previous backups must remain untouched.' }
$revision = (& git -C $Root rev-parse $SourceRevision).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the original Git source revision.' }

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
public static class BookIconLayout {
    public static Rectangle Bounds(Bitmap bmp, int alpha) {
        int x0=bmp.Width, y0=bmp.Height, x1=-1, y1=-1;
        for(int y=0;y<bmp.Height;y++) for(int x=0;x<bmp.Width;x++) {
            if(bmp.GetPixel(x,y).A < alpha) continue;
            x0=Math.Min(x0,x); y0=Math.Min(y0,y); x1=Math.Max(x1,x); y1=Math.Max(y1,y);
        }
        if(x1<0) throw new InvalidDataException("Empty book icon");
        return Rectangle.FromLTRB(x0,y0,x1+1,y1+1);
    }
    public static uint Crc(byte[] bytes) {
        uint crc=0xffffffff;
        foreach(byte b in bytes) {
            crc^=b;
            for(int bit=0;bit<8;bit++) crc=(crc>>1)^((crc&1)!=0?0xedb88320u:0u);
        }
        return crc^0xffffffff;
    }
    public static int Version(byte[] bytes) { return (int)(Crc(bytes)%126)+1; }
    static void BigEndian(Stream output, uint value) {
        output.WriteByte((byte)(value>>24)); output.WriteByte((byte)(value>>16));
        output.WriteByte((byte)(value>>8)); output.WriteByte((byte)value);
    }
    // PNG metadata changes only when needed to avoid a collision in the
    // existing server's one-byte CRC version. Pixel data remains identical.
    public static byte[] DistinctVersion(byte[] png, int oldVersion) {
        if(Version(png)!=oldVersion) return png;
        for(int nonce=1;nonce<=1000;nonce++) {
            byte[] text=Encoding.ASCII.GetBytes("BookLayout\0lien-hoan-visible-v1-"+nonce);
            byte[] chunk=new byte[4+text.Length];
            Encoding.ASCII.GetBytes("tEXt").CopyTo(chunk,0); text.CopyTo(chunk,4);
            using(var output=new MemoryStream()) {
                output.Write(png,0,png.Length-12); // before IEND
                BigEndian(output,(uint)text.Length); output.Write(chunk,0,chunk.Length);
                BigEndian(output,Crc(chunk)); output.Write(png,png.Length-12,12);
                byte[] result=output.ToArray(); if(Version(result)!=oldVersion) return result;
            }
        }
        throw new InvalidDataException("Cannot produce a distinct icon version");
    }
    public static byte[] Render(byte[] sourceBytes, int side, int visibleWidth, int visibleHeight) {
        using(var stream=new MemoryStream(sourceBytes)) using(var source=new Bitmap(stream)) {
            Rectangle core=Bounds(source,128);
            double factor=Math.Min((double)visibleWidth/core.Width,(double)visibleHeight/core.Height);
            for(int attempt=0;attempt<8;attempt++) {
            float left=(float)((side-core.Width*factor)/2-core.X*factor);
            float top=(float)((side-core.Height*factor)/2-core.Y*factor);
            using(var result=new Bitmap(side,side,PixelFormat.Format32bppArgb)) {
                using(var g=Graphics.FromImage(result)) using(var attrs=new ImageAttributes()) {
                    g.Clear(Color.Transparent);
                    g.CompositingMode=CompositingMode.SourceCopy;
                    g.CompositingQuality=CompositingQuality.HighQuality;
                    g.InterpolationMode=InterpolationMode.HighQualityBicubic;
                    g.SmoothingMode=SmoothingMode.HighQuality;
                    g.PixelOffsetMode=PixelOffsetMode.HighQuality;
                    // Draw the entire source with the transform anchored to its
                    // solid bounds, preserving the soft alpha halo and aspect ratio.
                    var points=new PointF[] { new PointF(left,top),
                        new PointF(left+(float)(source.Width*factor),top),
                        new PointF(left,top+(float)(source.Height*factor)) };
                    g.DrawImage(source,points,new RectangleF(0,0,source.Width,source.Height),GraphicsUnit.Pixel,attrs);
                }
                Rectangle actual=Bounds(result,128);
                if(actual.Width>visibleWidth+1 || actual.Height>visibleHeight+1) {
                    factor*=Math.Min((double)visibleWidth/actual.Width,(double)visibleHeight/actual.Height);
                    continue;
                }
                using(var output=new MemoryStream()) { result.Save(output,ImageFormat.Png); return output.ToArray(); }
            }
            }
            throw new InvalidDataException("Cannot fit the visible book bounds");
        }
    }
}
'@

function Get-GitImage([int]$IconId) {
    $start = [Diagnostics.ProcessStartInfo]::new('git', "-C `"$Root`" show ${revision}:data/icon/x4/$IconId.png")
    $start.UseShellExecute=$false
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $process=[Diagnostics.Process]::Start($start)
    $memory=[IO.MemoryStream]::new()
    try {
        $process.StandardOutput.BaseStream.CopyTo($memory)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "No unscaled source for icon $IconId" }
        return ,$memory.ToArray()
    } finally { $memory.Dispose(); $process.Dispose() }
}

$rows = @(& mysql --protocol=tcp -h 127.0.0.1 -P 3307 -u root --default-character-set=utf8mb4 -N -B team2026 -e 'SELECT s.tab_id,t.id,t.icon_id,t.NAME FROM item_shop s JOIN item_template t ON t.id=s.temp_id WHERE s.tab_id IN (10,11,12) AND s.is_sell=1 ORDER BY s.tab_id,s.create_time DESC,s.id ASC;')
if ($LASTEXITCODE -ne 0) { throw 'Cannot query the live shop icon mapping.' }
$books = @($rows | ForEach-Object {
    $fields=$_ -split "`t",4
    [pscustomobject]@{TabId=[int]$fields[0]; ItemId=[int]$fields[1]; IconId=[int]$fields[2]; Name=$fields[3]}
})
if ($books.Count -ne 174 -or @($books.IconId | Sort-Object -Unique).Count -ne 174) {
    throw 'The expected shop mapping has changed; review its scope before normalizing.'
}
$reference=[Drawing.Bitmap]::new((Join-Path $Root "data/icon/orig/$ReferenceId.png"))
try { $referenceBounds=[BookIconLayout]::Bounds($reference,128) }
finally { $reference.Dispose() }
if ($referenceBounds.Width -ne 50 -or $referenceBounds.Height -ne 52) {
    throw 'The original Lien hoan reference changed; review the visible bounds again.'
}
# Measured from the approved Lien hoan x1 reference before normalization.
# Keep the standard fixed instead of re-measuring an already resampled image.
$target=[Drawing.Size]::new(18,19)

New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
foreach ($sub in @('sources','before','after')) {
    New-Item -ItemType Directory -Path (Join-Path $OutputDirectory $sub) | Out-Null
}
foreach ($scale in 1..4) {
    foreach ($sub in @('before','after')) {
        New-Item -ItemType Directory -Path (Join-Path $OutputDirectory "$sub/x$scale") | Out-Null
    }
}
$report=[Collections.Generic.List[object]]::new()
foreach ($book in $books) {
    $id=$book.IconId
    $orig=Join-Path $Root "data/icon/orig/$id.png"
    $provenance=if (Test-Path -LiteralPath $orig) { 'orig' } else { "git:$revision" }
    [byte[]]$source=if ($provenance -eq 'orig') { [IO.File]::ReadAllBytes($orig) } else { Get-GitImage $id }
    [IO.File]::WriteAllBytes((Join-Path $OutputDirectory "sources/$id.png"),$source)
    foreach ($scale in 1..4) {
        $live=Join-Path $Root "data/icon/x$scale/$id.png"
        $before=Join-Path $OutputDirectory "before/x$scale/$id.png"
        $after=Join-Path $OutputDirectory "after/x$scale/$id.png"
        Copy-Item -LiteralPath $live -Destination $before
        $oldVersion=[BookIconLayout]::Version([IO.File]::ReadAllBytes($before))
        $bytes=[BookIconLayout]::Render($source,24*$scale,$target.Width*$scale,$target.Height*$scale)
        $bytes=[BookIconLayout]::DistinctVersion($bytes,$oldVersion)
        [IO.File]::WriteAllBytes($after,$bytes)
        $bmp=[Drawing.Bitmap]::new($after)
        try {
            $bounds=[BookIconLayout]::Bounds($bmp,128)
            if ($bmp.Width -ne 24*$scale -or $bmp.Height -ne 24*$scale -or
                $bounds.Width -gt 18*$scale+1 -or $bounds.Height -gt 19*$scale+1 -or
                [Math]::Max($bounds.Width,$bounds.Height) -lt 18*$scale-1 -or
                $bmp.GetPixel(0,0).A -ne 0 -or $bmp.GetPixel($bmp.Width-1,0).A -ne 0 -or
                $bmp.GetPixel(0,$bmp.Height-1).A -ne 0 -or $bmp.GetPixel($bmp.Width-1,$bmp.Height-1).A -ne 0) {
                throw "Visible bounds/alpha failed for icon $id zoom $scale : $bounds"
            }
            $report.Add([pscustomobject]@{TabId=$book.TabId;ItemId=$book.ItemId;IconId=$id;Zoom=$scale;
                Name=$book.Name;Source=$provenance;VisibleX=$bounds.X;VisibleY=$bounds.Y;
                VisibleWidth=$bounds.Width;VisibleHeight=$bounds.Height;
                OldVersion=$oldVersion;NewVersion=[BookIconLayout]::Version($bytes)})
        } finally { $bmp.Dispose() }
    }
}
$report | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'report.csv') -Encoding UTF8 -NoTypeInformation

# Compare book groups on the shop background at zoom 2. Each pair is before / after.
$samples=@(645,651,658,665,672,679,686,1090,1097,26456,26463,26470,26477,26484,26491,26498,26505,26512,26519,26526,26533,26540,14659,14643,14651)
$sheet=[Drawing.Bitmap]::new(720,560)
$graphics=[Drawing.Graphics]::FromImage($sheet)
$font=[Drawing.Font]::new('Arial',10)
$brush=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(32,32,32))
try {
    $graphics.Clear([Drawing.Color]::FromArgb(230,221,204))
    for($index=0;$index -lt $samples.Count;$index++) {
        $id=$samples[$index];$x=($index%5)*144;$y=[int][Math]::Floor($index/5)*112
        $graphics.DrawString("$id before / after",$font,$brush,$x+3,$y+4)
        foreach($sub in @('before','after')) {
            $bmp=[Drawing.Bitmap]::new((Join-Path $OutputDirectory "$sub/x2/$id.png"))
            try { $offset=if($sub -eq 'before'){8}else{78};$graphics.DrawImageUnscaled($bmp,$x+$offset,$y+27) }
            finally { $bmp.Dispose() }
        }
    }
    $sheet.Save((Join-Path $OutputDirectory 'comparison.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose();$sheet.Dispose();$font.Dispose();$brush.Dispose() }

if ($Apply) {
    # Full validation precedes any live write. Keep all backups for rollback.
    foreach ($entry in $report) {
        $relative="x$($entry.Zoom)/$($entry.IconId).png"
        $live=Join-Path $Root "data/icon/$relative"
        $before=Join-Path $OutputDirectory "before/$relative"
        if ((Get-FileHash -LiteralPath $live).Hash -ne (Get-FileHash -LiteralPath $before).Hash) {
            throw "Live icon changed during staging: $relative"
        }
    }
    foreach ($entry in $report) {
        $relative="x$($entry.Zoom)/$($entry.IconId).png"
        Copy-Item -LiteralPath (Join-Path $OutputDirectory "after/$relative") -Destination (Join-Path $Root "data/icon/$relative") -Force
    }
}
"Validated $($books.Count) books / $($report.Count) images; applied=$($Apply.IsPresent); visible target=18x19 on 24x24. Output: $OutputDirectory"
