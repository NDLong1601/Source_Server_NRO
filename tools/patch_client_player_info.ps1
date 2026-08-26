[CmdletBinding()]
param(
    [ValidateSet('Apply', 'Verify', 'Restore')]
    [string]$Action = 'Apply',

    [string]$ClientRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ClientRoot)) {
    $workspaceRoot = Split-Path -Parent $PSScriptRoot
    $projectRoot = Split-Path -Parent $workspaceRoot
    $ClientRoot = Join-Path $projectRoot 'Local Mod'
}

$resolvedClientRoot = (Resolve-Path -LiteralPath $ClientRoot).Path
$assemblyPath = Join-Path $resolvedClientRoot 'GameAssembly.dll'
$backupPath = Join-Path $resolvedClientRoot 'GameAssembly.dll.player-info.original'

if (-not (Test-Path -LiteralPath $assemblyPath -PathType Leaf)) {
    throw "Khong tim thay GameAssembly.dll tai: $assemblyPath"
}

# Client XUNGLORDLOCAL, Unity 2022.3.26f1. The hook is at the final stage of
# Panel.SetTypePlayerInfo, immediately before String.Concat(contenInfo). The
# payload removes inactive option label/value pairs before the text is split
# into display lines, so there are no blank rows or misleading 0% entries.
$hookOffset = 0x25B170
[byte[]]$originalHook = [byte[]]@(0x33, 0xD2, 0x48, 0x8B, 0xCB)
[byte[]]$payload = [Convert]::FromBase64String(
    'Mcnoaahy/kiFwA+E/wAAADHJg7hEBwAAAHUOSImLkAAAAEiJi5gAAACDuEgHAAAAdQ5IiYugAAAASImLqAAAAIO4TAcAAAB1DkiJi7AAAABIiYu4AAAAg7hQBwAAAHUOSImLwAAAAEiJi8gAAACDuFQHAAAAdQ5IiYvQAAAASImL2AAAAIO4WAcAAAB1DkiJi+AAAABIiYvoAAAAg7hcBwAAAHUOSImL8AAAAEiJi/gAAACAuGAHAAAAdQ5IiYsAAQAASImLCAEAAIC4YQcAAAB1DkiJixABAABIiYsYAQAAgLhiBwAAAHUOSImLIAEAAEiJiygBAACAuGMHAAAAdQ5IiYswAQAASImLOAEAADHSSInZ6OcY7/7pXAR2/g=='
)
$payloadVirtualSize = $payload.Length

function Test-BytesEqual {
    param([byte[]]$Left, [byte[]]$Right)

    if ($Left.Length -ne $Right.Length) {
        return $false
    }
    for ($index = 0; $index -lt $Left.Length; $index++) {
        if ($Left[$index] -ne $Right[$index]) {
            return $false
        }
    }
    return $true
}

function Test-BytesPrefix {
    param(
        [byte[]]$Source,
        [byte[]]$Expected,
        [int]$Length
    )

    if ($Length -lt 0 -or $Source.Length -lt $Length -or $Expected.Length -lt $Length) {
        return $false
    }
    for ($index = 0; $index -lt $Length; $index++) {
        if ($Source[$index] -ne $Expected[$index]) {
            return $false
        }
    }
    return $true
}

function Read-BytesAt {
    param(
        [System.IO.FileStream]$Stream,
        [long]$Offset,
        [int]$Length
    )

    if ($Offset -lt 0 -or $Stream.Length -lt ($Offset + $Length)) {
        throw 'GameAssembly.dll ngan hon vi tri can kiem tra; khong dung phien ban client.'
    }
    [void]$Stream.Seek($Offset, [System.IO.SeekOrigin]::Begin)
    $buffer = New-Object byte[] $Length
    $read = $Stream.Read($buffer, 0, $buffer.Length)
    if ($read -ne $buffer.Length) {
        throw 'Khong doc du du lieu tu GameAssembly.dll.'
    }
    return $buffer
}

function Format-HexBytes {
    param([byte[]]$Bytes)

    return (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

function Align-Up {
    param(
        [long]$Value,
        [long]$Alignment
    )

    if ($Alignment -le 0) {
        throw 'PE co alignment khong hop le.'
    }
    return [long](([Math]::Floor(($Value + $Alignment - 1) / $Alignment)) * $Alignment)
}

function Get-PeInfo {
    param([string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    $reader = [System.IO.BinaryReader]::new($stream)
    try {
        $stream.Position = 0x3C
        $peOffset = $reader.ReadInt32()
        $stream.Position = $peOffset
        if ($reader.ReadUInt32() -ne 0x00004550) {
            throw 'GameAssembly.dll khong phai PE hop le.'
        }

        [void]$reader.ReadUInt16()
        $sectionCount = $reader.ReadUInt16()
        $optionalHeaderOffset = $peOffset + 24
        $stream.Position = $peOffset + 20
        $optionalHeaderSize = $reader.ReadUInt16()
        if ($optionalHeaderSize -lt 0x70) {
            throw 'PE optional header khong hop le.'
        }

        $stream.Position = $optionalHeaderOffset
        if ($reader.ReadUInt16() -ne 0x20B) {
            throw 'Client khong phai PE32+ da duoc ho tro.'
        }

        $stream.Position = $optionalHeaderOffset + 24
        $imageBase = $reader.ReadUInt64()
        $stream.Position = $optionalHeaderOffset + 32
        $sectionAlignment = $reader.ReadUInt32()
        $fileAlignment = $reader.ReadUInt32()
        $stream.Position = $optionalHeaderOffset + 56
        $sizeOfImage = $reader.ReadUInt32()
        $stream.Position = $optionalHeaderOffset + 60
        $sizeOfHeaders = $reader.ReadUInt32()

        $sectionTableOffset = $optionalHeaderOffset + $optionalHeaderSize
        $sections = New-Object System.Collections.Generic.List[object]
        for ($index = 0; $index -lt $sectionCount; $index++) {
            $stream.Position = $sectionTableOffset + ($index * 40)
            $name = [Text.Encoding]::ASCII.GetString($reader.ReadBytes(8)).Trim([char]0)
            $virtualSize = $reader.ReadUInt32()
            $virtualAddress = $reader.ReadUInt32()
            $rawSize = $reader.ReadUInt32()
            $rawAddress = $reader.ReadUInt32()
            [void]$reader.ReadUInt32()
            [void]$reader.ReadUInt32()
            [void]$reader.ReadUInt16()
            [void]$reader.ReadUInt16()
            $characteristics = $reader.ReadUInt32()
            $sections.Add([pscustomobject]@{
                Name = $name
                VirtualSize = [long]$virtualSize
                VirtualAddress = [long]$virtualAddress
                RawSize = [long]$rawSize
                RawAddress = [long]$rawAddress
                Characteristics = [long]$characteristics
            })
        }

        return [pscustomobject]@{
            PeOffset = $peOffset
            OptionalHeaderOffset = $optionalHeaderOffset
            OptionalHeaderSize = $optionalHeaderSize
            SectionTableOffset = $sectionTableOffset
            SectionCount = $sectionCount
            ImageBase = [UInt64]$imageBase
            SectionAlignment = [long]$sectionAlignment
            FileAlignment = [long]$fileAlignment
            SizeOfImage = [long]$sizeOfImage
            SizeOfHeaders = [long]$sizeOfHeaders
            Sections = $sections.ToArray()
        }
    }
    finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Convert-FileOffsetToRva {
    param(
        [object]$PeInfo,
        [long]$Offset
    )

    $section = @($PeInfo.Sections | Where-Object {
        $Offset -ge $_.RawAddress -and $Offset -lt ($_.RawAddress + $_.RawSize)
    } | Select-Object -First 1)
    if ($section.Count -ne 1) {
        throw ("Khong the doi file offset 0x{0:X} sang RVA." -f $Offset)
    }
    return [long]($section[0].VirtualAddress + ($Offset - $section[0].RawAddress))
}

function New-NearJumpToRva {
    param(
        [object]$PeInfo,
        [long]$Offset,
        [long]$TargetRva
    )

    $sourceRva = Convert-FileOffsetToRva -PeInfo $PeInfo -Offset $Offset
    $displacement = $TargetRva - ($sourceRva + 5)
    if ($displacement -lt [int]::MinValue -or $displacement -gt [int]::MaxValue) {
        throw 'Khoang cach nhay cua client vuot qua pham vi rel32.'
    }

    [byte[]]$jump = New-Object byte[] 5
    $jump[0] = 0xE9
    [BitConverter]::GetBytes([int]$displacement).CopyTo($jump, 1)
    return $jump
}

function New-SectionHeader {
    param(
        [long]$VirtualSize,
        [long]$VirtualAddress,
        [long]$RawSize,
        [long]$RawAddress
    )

    [byte[]]$header = New-Object byte[] 40
    [Text.Encoding]::ASCII.GetBytes('.pinfo').CopyTo($header, 0)
    [BitConverter]::GetBytes([uint32]$VirtualSize).CopyTo($header, 8)
    [BitConverter]::GetBytes([uint32]$VirtualAddress).CopyTo($header, 12)
    [BitConverter]::GetBytes([uint32]$RawSize).CopyTo($header, 16)
    [BitConverter]::GetBytes([uint32]$RawAddress).CopyTo($header, 20)
    [BitConverter]::GetBytes([uint32]0x60000020).CopyTo($header, 36)
    return $header
}

$peInfo = Get-PeInfo -Path $assemblyPath
if ($peInfo.ImageBase -ne 0x180000000) {
    throw 'Khong dung image base cua client XUNGLORDLOCAL da kiem tra.'
}

$hookSourceRva = Convert-FileOffsetToRva -PeInfo $peInfo -Offset $hookOffset
$existingPatchSection = @($peInfo.Sections | Where-Object Name -eq '.pinfo')
$freshSection = $existingPatchSection.Count -eq 0

if ($freshSection) {
    if ($peInfo.SectionCount -ne 6 -or $peInfo.SizeOfImage -ne 0x1AFC000) {
        throw 'Layout PE client da khac ban XUNGLORDLOCAL da kiem tra; dung lai de tranh lam hong client.'
    }
    if (($peInfo.SectionTableOffset + (($peInfo.SectionCount + 1) * 40)) -gt $peInfo.SizeOfHeaders) {
        throw 'PE header khong con cho de them section patch an toan.'
    }

    $lastSection = $peInfo.Sections[$peInfo.Sections.Length - 1]
    $patchSectionRva = Align-Up ($lastSection.VirtualAddress + [Math]::Max($lastSection.VirtualSize, $lastSection.RawSize)) $peInfo.SectionAlignment
    $patchSectionRaw = Align-Up ((Get-Item -LiteralPath $assemblyPath).Length) $peInfo.FileAlignment
    $patchSectionRawSize = Align-Up $payload.Length $peInfo.FileAlignment
    $newSizeOfImage = Align-Up ($patchSectionRva + $payloadVirtualSize) $peInfo.SectionAlignment

    if ($patchSectionRva -ne 0x1AFC000 -or $patchSectionRaw -ne 0x184A600 -or $patchSectionRawSize -ne 0x200 -or $newSizeOfImage -ne 0x1AFD000) {
        throw 'Vi tri section patch khong dung voi client XUNGLORDLOCAL da kiem tra.'
    }
} else {
    if ($existingPatchSection.Count -ne 1) {
        throw 'Phat hien nhieu section .pinfo, khong the xac dinh patch an toan.'
    }
    $section = $existingPatchSection[0]
    $patchSectionRva = $section.VirtualAddress
    $patchSectionRaw = $section.RawAddress
    $patchSectionRawSize = $section.RawSize
    $newSizeOfImage = $peInfo.SizeOfImage
    if ($section.VirtualSize -ne $payloadVirtualSize -or $patchSectionRva -ne 0x1AFC000 -or $patchSectionRaw -ne 0x184A600 -or $patchSectionRawSize -ne 0x200 -or $section.Characteristics -ne 0x60000020) {
        throw 'Section .pinfo khong khop patch hien thi thong tin player da kiem tra.'
    }
}

[byte[]]$patchedHook = New-NearJumpToRva -PeInfo $peInfo -Offset $hookOffset -TargetRva $patchSectionRva

$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
try {
    [byte[]]$currentHook = Read-BytesAt -Stream $stream -Offset $hookOffset -Length $originalHook.Length
    $hookState = if (Test-BytesEqual $currentHook $patchedHook) {
        'Patched'
    } elseif (Test-BytesEqual $currentHook $originalHook) {
        'Original'
    } else {
        'Mismatch'
    }

    $payloadState = 'Missing'
    if (-not $freshSection) {
        [byte[]]$currentPayload = Read-BytesAt -Stream $stream -Offset $patchSectionRaw -Length $payload.Length
        $payloadState = if (Test-BytesEqual $currentPayload $payload) {
            'Patched'
        } elseif ((Test-BytesPrefix -Source $currentPayload -Expected $payload -Length 0x114) -and $currentPayload[0x114] -eq 0xE9) {
            # patch_client_player_info_options.ps1 redirects only the final
            # String.Concat call and leaves the validated base payload intact.
            'Extended'
        } else {
            'Mismatch'
        }
    }
}
finally {
    $stream.Dispose()
}

if ($hookState -eq 'Mismatch') {
    throw "Khong va de tranh lam hong client: hook Panel.SetTypePlayerInfo tai 0x$($hookOffset.ToString('X')) khong khop.`nHien tai: $(Format-HexBytes $currentHook)"
}
if ($payloadState -eq 'Mismatch') {
    throw 'Section .pinfo dang chua payload khac; khong the ghi de an toan.'
}

if ($Action -eq 'Verify') {
    if ($freshSection -or $hookState -ne 'Patched' -or ($payloadState -ne 'Patched' -and $payloadState -ne 'Extended')) {
        throw 'Client chua co ban va hien thi thong tin player dong.'
    }
    Write-Host 'OK: Thong tin player lay chi so thuc va tu an cac option khong co.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi thay doi GameAssembly.dll.'
}

if ($Action -eq 'Restore') {
    if ($hookState -eq 'Original') {
        Write-Host 'Client chua dung patch thong tin player; khong can khoi phuc.'
        exit 0
    }

    $stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    try {
        [void]$stream.Seek($hookOffset, [System.IO.SeekOrigin]::Begin)
        $stream.Write($originalHook, 0, $originalHook.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
    Write-Host 'Da tat patch thong tin player; section du phong duoc giu lai de co the ap dung lai an toan.'
    exit 0
}

if ($hookState -eq 'Patched' -and ($payloadState -eq 'Patched' -or $payloadState -eq 'Extended')) {
    Write-Host 'Client da co patch thong tin player, khong can thay doi.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
}

$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
try {
    if ($freshSection) {
        [void]$stream.Seek($patchSectionRaw, [System.IO.SeekOrigin]::Begin)
        $stream.Write($payload, 0, $payload.Length)
        [byte[]]$padding = New-Object byte[] ($patchSectionRawSize - $payload.Length)
        for ($index = 0; $index -lt $padding.Length; $index++) {
            $padding[$index] = 0xCC
        }
        $stream.Write($padding, 0, $padding.Length)

        [byte[]]$sectionHeader = New-SectionHeader -VirtualSize $payloadVirtualSize -VirtualAddress $patchSectionRva -RawSize $patchSectionRawSize -RawAddress $patchSectionRaw
        [void]$stream.Seek($peInfo.SectionTableOffset + ($peInfo.SectionCount * 40), [System.IO.SeekOrigin]::Begin)
        $stream.Write($sectionHeader, 0, $sectionHeader.Length)

        [void]$stream.Seek($peInfo.PeOffset + 6, [System.IO.SeekOrigin]::Begin)
        $stream.Write([BitConverter]::GetBytes([UInt16]($peInfo.SectionCount + 1)), 0, 2)
        [void]$stream.Seek($peInfo.OptionalHeaderOffset + 56, [System.IO.SeekOrigin]::Begin)
        $stream.Write([BitConverter]::GetBytes([UInt32]$newSizeOfImage), 0, 4)
    }

    # The code section and payload are fully written before enabling the hook.
    [void]$stream.Seek($hookOffset, [System.IO.SeekOrigin]::Begin)
    $stream.Write($patchedHook, 0, $patchedHook.Length)
    $stream.Flush($true)
}
finally {
    $stream.Dispose()
}

& $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot
Write-Host 'Da va client: thong tin player chi giu cac option dang co va tu cap nhat theo trang bi/hieu ung.'
Write-Host "Ban sao truoc khi va: $backupPath"
