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
$backupPath = Join-Path $resolvedClientRoot 'GameAssembly.dll.rename-input.original'

if (-not (Test-Path -LiteralPath $assemblyPath -PathType Leaf)) {
    throw "Khong tim thay GameAssembly.dll tai: $assemblyPath"
}

# XUNGLORDLOCAL (Unity 2022.3.26f1, IL2CPP metadata v29) keeps the legacy
# TField editor. It receives physical KeyCode values, so Windows Shift/Caps
# Lock and Ctrl+V never reach the Unicode text path. The injected handler:
#   - maps Shift XOR Caps Lock to the original Telex processor;
#   - reads Unity's system clipboard for Ctrl+V;
#   - sends each UTF-16 character through TField.processTelex, preserving
#     Vietnamese Telex behavior while also accepting Chinese characters.
# Only the verified TField.keyPressed prologue is hooked. The code is put in
# its own executable PE section so existing client patches remain untouched.
$hookOffset = 0x2F3690
[byte[]]$originalHook = [byte[]]@(0x48, 0x89, 0x5C, 0x24, 0x10, 0x56, 0x48, 0x83, 0xEC, 0x20)
$sectionName = '.rinp'

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

function Read-BytesAt {
    param(
        [System.IO.FileStream]$Stream,
        [long]$Offset,
        [int]$Length
    )

    if ($Offset -lt 0 -or $Stream.Length -lt ($Offset + $Length)) {
        throw 'GameAssembly.dll ngan hon vi tri patch da kiem tra; dung lai de tranh lam hong client.'
    }
    [void]$Stream.Seek($Offset, [System.IO.SeekOrigin]::Begin)
    $buffer = New-Object byte[] $Length
    if ($Stream.Read($buffer, 0, $buffer.Length) -ne $buffer.Length) {
        throw 'Khong doc du du lieu GameAssembly.dll tai vi tri patch.'
    }
    return $buffer
}

function Format-HexBytes {
    param([byte[]]$Bytes)

    return (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

function Align-Up {
    param([long]$Value, [long]$Alignment)

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
    param([object]$PeInfo, [long]$Offset)

    $section = @($PeInfo.Sections | Where-Object {
        $Offset -ge $_.RawAddress -and $Offset -lt ($_.RawAddress + $_.RawSize)
    } | Select-Object -First 1)
    if ($section.Count -ne 1) {
        throw ("Khong the doi file offset 0x{0:X} sang RVA." -f $Offset)
    }
    return [long]($section[0].VirtualAddress + ($Offset - $section[0].RawAddress))
}

function New-NearJumpToRva {
    param([object]$PeInfo, [long]$Offset, [long]$TargetRva)

    $sourceRva = Convert-FileOffsetToRva -PeInfo $PeInfo -Offset $Offset
    $displacement = $TargetRva - ($sourceRva + 5)
    if ($displacement -lt [int]::MinValue -or $displacement -gt [int]::MaxValue) {
        throw 'Khoang cach nhay cua client vuot qua pham vi rel32.'
    }

    [byte[]]$jump = New-Object byte[] 10
    $jump[0] = 0xE9
    [BitConverter]::GetBytes([int]$displacement).CopyTo($jump, 1)
    for ($index = 5; $index -lt $jump.Length; $index++) {
        $jump[$index] = 0x90
    }
    return $jump
}

function New-SectionHeader {
    param([string]$Name, [long]$VirtualSize, [long]$VirtualAddress, [long]$RawSize, [long]$RawAddress)

    [byte[]]$header = New-Object byte[] 40
    [Text.Encoding]::ASCII.GetBytes($Name).CopyTo($header, 0)
    [BitConverter]::GetBytes([uint32]$VirtualSize).CopyTo($header, 8)
    [BitConverter]::GetBytes([uint32]$VirtualAddress).CopyTo($header, 12)
    [BitConverter]::GetBytes([uint32]$RawSize).CopyTo($header, 16)
    [BitConverter]::GetBytes([uint32]$RawAddress).CopyTo($header, 20)
    [BitConverter]::GetBytes([uint32]0x60000020).CopyTo($header, 36)
    return $header
}

function New-RenameInputPayload {
    param([long]$SectionRva)

    $bytes = New-Object System.Collections.Generic.List[byte]
    $labels = @{}
    $fixups = New-Object System.Collections.Generic.List[object]

    function Add-Bytes([int[]]$Values) {
        foreach ($value in $Values) {
            $bytes.Add([byte]$value)
        }
    }

    function Add-Label([string]$Name) {
        if ($labels.ContainsKey($Name)) {
            throw "Nhan payload trung: $Name"
        }
        $labels[$Name] = $bytes.Count
    }

    function Add-RelativeFixup([string]$Label, [long]$TargetRva = -1, [int]$TailBytes = 0) {
        $fixups.Add([pscustomobject]@{
            Offset = $bytes.Count
            Label = $Label
            TargetRva = $TargetRva
            TailBytes = $TailBytes
        })
        Add-Bytes @(0, 0, 0, 0)
    }

    function Add-Jump([string]$Label) {
        Add-Bytes @(0xE9)
        Add-RelativeFixup -Label $Label
    }

    function Add-Jcc([int[]]$Opcode, [string]$Label) {
        Add-Bytes $Opcode
        Add-RelativeFixup -Label $Label
    }

    function Add-CallRva([long]$TargetRva) {
        Add-Bytes @(0xE8)
        Add-RelativeFixup -TargetRva $TargetRva
    }

    # TField.keyPressed original entry: RCX=this, EDX=KeyCode.
    # Preserve rbx/rsi, reserve Windows x64 shadow space, then handle only a
    # focused field. Non-text navigation keeps the original implementation.
    Add-Label 'start'
    Add-Bytes @(0x53, 0x56, 0x48, 0x83, 0xEC, 0x38, 0x48, 0x89, 0xCB, 0x89, 0xD6)
    Add-Bytes @(0x80, 0x7B, 0x10, 0x00) # this.isFocus
    Add-Jcc @(0x0F, 0x84) 'original'
    # Preserve every numeric/password field. Server name forms use ANY = 1.
    Add-Bytes @(0x83, 0x7B, 0x5C, 0x01)
    Add-Jcc @(0x0F, 0x85) 'original'

    # Toggle our caps state when Caps Lock is pressed while the field has focus.
    Add-Bytes @(0x81, 0xFE, 0x2D, 0x01, 0x00, 0x00)
    Add-Jcc @(0x0F, 0x85) 'checkPaste'
    Add-Bytes @(0x80, 0x35)
    Add-RelativeFixup -Label 'capsState' -TailBytes 1
    Add-Bytes @(0x01)
    Add-Jump 'handled'

    # Ctrl+V uses Unity's existing GUIUtility clipboard API. Newline/tab is
    # intentionally skipped because character names only allow spaces.
    Add-Label 'checkPaste'
    Add-Bytes @(0x83, 0xFE, 0x76)
    Add-Jcc @(0x0F, 0x85) 'letters'
    Add-Bytes @(0xB9, 0x31, 0x01, 0x00, 0x00) # RightControl
    Add-CallRva 0xFA08B0 # UnityEngine.Input.GetKey
    Add-Bytes @(0x84, 0xC0)
    Add-Jcc @(0x0F, 0x85) 'paste'
    Add-Bytes @(0xB9, 0x32, 0x01, 0x00, 0x00) # LeftControl
    Add-CallRva 0xFA08B0
    Add-Bytes @(0x84, 0xC0)
    Add-Jcc @(0x0F, 0x84) 'letters'

    Add-Label 'paste'
    Add-Bytes @(0x33, 0xC9) # GUIUtility.get_systemCopyBuffer has no arguments
    Add-CallRva 0xF952D0
    Add-Bytes @(0x48, 0x85, 0xC0)
    Add-Jcc @(0x0F, 0x84) 'handled'
    Add-Bytes @(0x48, 0x89, 0x44, 0x24, 0x20) # clipboard string
    Add-Bytes @(0xC7, 0x44, 0x24, 0x28, 0x00, 0x00, 0x00, 0x00) # index

    Add-Label 'pasteLoop'
    Add-Bytes @(0x48, 0x8B, 0x44, 0x24, 0x20)
    Add-Bytes @(0x8B, 0x4C, 0x24, 0x28)
    Add-Bytes @(0x3B, 0x48, 0x10) # index < System.String.Length
    Add-Jcc @(0x0F, 0x8D) 'handled'
    Add-Bytes @(0x0F, 0xB7, 0x54, 0x48, 0x14) # UTF-16 char at 0x14 + index*2
    Add-Bytes @(0xFF, 0xC1, 0x89, 0x4C, 0x24, 0x28)
    Add-Bytes @(0x83, 0xFA, 0x0A)
    Add-Jcc @(0x0F, 0x84) 'pasteLoop'
    Add-Bytes @(0x83, 0xFA, 0x0D)
    Add-Jcc @(0x0F, 0x84) 'pasteLoop'
    Add-Bytes @(0x83, 0xFA, 0x09)
    Add-Jcc @(0x0F, 0x84) 'pasteLoop'
    Add-Bytes @(0x48, 0x89, 0xD9)
    Add-CallRva 0x2F5900 # TField.processTelex(char)
    Add-Jump 'pasteLoop'

    # Unity KeyCode letters are always a..z. Convert them to the desired case
    # from Shift XOR Caps, then use the client's Telex-aware insertion method.
    Add-Label 'letters'
    Add-Bytes @(0x83, 0xFE, 0x61)
    Add-Jcc @(0x0F, 0x8C) 'original'
    Add-Bytes @(0x83, 0xFE, 0x7A)
    Add-Jcc @(0x0F, 0x8F) 'original'
    Add-Bytes @(0xB9, 0x2F, 0x01, 0x00, 0x00) # RightShift
    Add-CallRva 0xFA08B0
    Add-Bytes @(0x44, 0x0F, 0xB6, 0xC0)
    Add-Bytes @(0xB9, 0x30, 0x01, 0x00, 0x00) # LeftShift
    Add-CallRva 0xFA08B0
    Add-Bytes @(0x44, 0x0A, 0xC0) # r8b |= left shift
    Add-Bytes @(0x44, 0x32, 0x05)
    Add-RelativeFixup -Label 'capsState'
    Add-Bytes @(0x89, 0xF2, 0x45, 0x84, 0xC0)
    Add-Jcc @(0x0F, 0x85) 'upper'
    Add-Bytes @(0x83, 0xC2, 0x20)
    Add-Label 'upper'
    Add-Bytes @(0x48, 0x89, 0xD9)
    Add-CallRva 0x2F5900
    Add-Jump 'handled'

    Add-Label 'original'
    Add-Bytes @(0x48, 0x83, 0xC4, 0x38, 0x5E, 0x5B)
    # Recreate the overwritten TField.keyPressed prologue before resuming its
    # body. The tail performs the matching stack cleanup on its own return.
    Add-Bytes @(0x48, 0x89, 0x5C, 0x24, 0x10, 0x56, 0x48, 0x83, 0xEC, 0x20)
    Add-Jump 'originalContinuation'

    Add-Label 'handled'
    Add-Bytes @(0x48, 0x83, 0xC4, 0x38, 0x5E, 0x5B, 0xB0, 0x01, 0xC3)
    Add-Label 'capsState'
    Add-Bytes @(0x00)

    # Resume after the exact 10-byte TField.keyPressed prologue replaced by the hook.
    $labels['originalContinuation'] = [long](0x2F4A90 + $originalHook.Length - $SectionRva)

    foreach ($fixup in $fixups) {
        $targetRva = if ($fixup.TargetRva -ge 0) {
            [long]$fixup.TargetRva
        } elseif ($labels.ContainsKey($fixup.Label)) {
            $SectionRva + [long]$labels[$fixup.Label]
        } else {
            throw "Khong tim thay nhan payload: $($fixup.Label)"
        }
        $nextInstructionRva = $SectionRva + [long]$fixup.Offset + 4 + [long]$fixup.TailBytes
        $displacement = $targetRva - $nextInstructionRva
        if ($displacement -lt [int]::MinValue -or $displacement -gt [int]::MaxValue) {
            throw "Nhanh payload vuot qua pham vi rel32: $($fixup.Label)"
        }
        $encoded = [BitConverter]::GetBytes([int]$displacement)
        for ($index = 0; $index -lt 4; $index++) {
            $bytes[$fixup.Offset + $index] = $encoded[$index]
        }
    }

    return $bytes.ToArray()
}

$peInfo = Get-PeInfo -Path $assemblyPath
if ($peInfo.ImageBase -ne 0x180000000) {
    throw 'Khong dung image base cua client XUNGLORDLOCAL da kiem tra.'
}

$existingPatchSection = @($peInfo.Sections | Where-Object Name -eq $sectionName)
$freshSection = $existingPatchSection.Count -eq 0
$patchSectionIndex = -1
for ($index = 0; $index -lt $peInfo.Sections.Length; $index++) {
    if ($peInfo.Sections[$index].Name -eq $sectionName) {
        $patchSectionIndex = $index
        break
    }
}

if ($freshSection) {
    # This client already has the independently-owned .pinfo section. Require
    # that known layout before adding our own section after it.
    $pinfo = @($peInfo.Sections | Where-Object Name -eq '.pinfo')
    if ($peInfo.SectionCount -ne 7 -or $pinfo.Count -ne 1 -or
        $peInfo.SizeOfImage -ne 0x1AFD000 -or (Get-Item -LiteralPath $assemblyPath).Length -ne 0x184A800) {
        throw 'Layout GameAssembly.dll da khac ban XUNGLORDLOCAL da kiem tra; dung lai de tranh lam hong client.'
    }
    if ($pinfo[0].VirtualAddress -ne 0x1AFC000 -or $pinfo[0].RawAddress -ne 0x184A600 -or $pinfo[0].RawSize -ne 0x200) {
        throw 'Section .pinfo hien tai khong dung layout da kiem tra.'
    }
    if (($peInfo.SectionTableOffset + (($peInfo.SectionCount + 1) * 40)) -gt $peInfo.SizeOfHeaders) {
        throw 'PE header khong con cho de them section patch an toan.'
    }

    $lastSection = $peInfo.Sections[$peInfo.Sections.Length - 1]
    $patchSectionRva = Align-Up ($lastSection.VirtualAddress + [Math]::Max($lastSection.VirtualSize, $lastSection.RawSize)) $peInfo.SectionAlignment
    $patchSectionRaw = Align-Up ((Get-Item -LiteralPath $assemblyPath).Length) $peInfo.FileAlignment
    $patchSectionIndex = $peInfo.SectionCount
    if ($patchSectionRva -ne 0x1AFD000 -or $patchSectionRaw -ne 0x184A800) {
        throw 'Vi tri section patch khong dung voi client XUNGLORDLOCAL da kiem tra.'
    }
} else {
    if ($existingPatchSection.Count -ne 1) {
        throw "Phat hien nhieu section $sectionName, khong the xac dinh patch an toan."
    }
    $patchSectionRva = $existingPatchSection[0].VirtualAddress
    $patchSectionRaw = $existingPatchSection[0].RawAddress
}

[byte[]]$payload = New-RenameInputPayload -SectionRva $patchSectionRva
$payloadVirtualSize = $payload.Length
$legacyPayload = $false

if ($freshSection) {
    $patchSectionRawSize = Align-Up $payload.Length $peInfo.FileAlignment
    $newSizeOfImage = Align-Up ($patchSectionRva + $payloadVirtualSize) $peInfo.SectionAlignment
    if ($patchSectionRawSize -ne 0x200 -or $newSizeOfImage -ne 0x1AFE000) {
        throw 'Kich thuoc payload Unicode input khong dung gioi han da kiem tra.'
    }
} else {
    $patchSectionRawSize = $existingPatchSection[0].RawSize
    $newSizeOfImage = $peInfo.SizeOfImage
    # Earlier revisions were never run: static checks found the original
    # fallback-prologue defect, then added the ANY-only guard. Permit only
    # those exact private-section layouts to be upgraded in place.
    $legacyPayload = $existingPatchSection[0].VirtualSize -in @(0x120, 0x12A)
    if ($patchSectionRva -ne 0x1AFD000 -or $patchSectionRaw -ne 0x184A800 -or
        $patchSectionRawSize -ne 0x200 -or (-not $legacyPayload -and $existingPatchSection[0].VirtualSize -ne $payloadVirtualSize) -or
        $existingPatchSection[0].Characteristics -ne 0x60000020) {
        throw "Section $sectionName khong khop payload Unicode input da kiem tra."
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
        $payloadState = if (Test-BytesEqual $currentPayload $payload) { 'Patched' } else { 'Mismatch' }
    }
}
finally {
    $stream.Dispose()
}

if ($hookState -eq 'Mismatch') {
    throw "Khong va de tranh lam hong client: hook TField.keyPressed tai 0x$($hookOffset.ToString('X')) khong khop.`nHien tai: $(Format-HexBytes $currentHook)"
}
if ($payloadState -eq 'Mismatch' -and -not $legacyPayload) {
    throw "Section $sectionName dang chua payload khac; khong the ghi de an toan."
}

if ($Action -eq 'Verify') {
    if ($freshSection -or $hookState -ne 'Patched' -or $payloadState -ne 'Patched') {
        throw 'Client chua co patch input Unicode/Shift/Caps Lock/Ctrl+V.'
    }
    Write-Host 'OK: input ten nhan vat ho tro Shift, Caps Lock va Ctrl+V Unicode.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi thay doi GameAssembly.dll.'
}

if ($Action -eq 'Restore') {
    if ($hookState -eq 'Original') {
        Write-Host 'Client chua dung patch input Unicode; khong can khoi phuc.'
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
    Write-Host 'Da tat patch input Unicode; section du phong duoc giu lai de co the ap dung lai an toan.'
    exit 0
}

if ($hookState -eq 'Patched' -and $payloadState -eq 'Patched') {
    Write-Host 'Client da co patch input Unicode/Shift/Caps Lock/Ctrl+V, khong can thay doi.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
}

$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
try {
    if ($freshSection -or $legacyPayload) {
        [void]$stream.Seek($patchSectionRaw, [System.IO.SeekOrigin]::Begin)
        $stream.Write($payload, 0, $payload.Length)
        [byte[]]$padding = New-Object byte[] ($patchSectionRawSize - $payload.Length)
        for ($index = 0; $index -lt $padding.Length; $index++) {
            $padding[$index] = 0xCC
        }
        $stream.Write($padding, 0, $padding.Length)

        [byte[]]$sectionHeader = New-SectionHeader -Name $sectionName -VirtualSize $payloadVirtualSize -VirtualAddress $patchSectionRva -RawSize $patchSectionRawSize -RawAddress $patchSectionRaw
        [void]$stream.Seek($peInfo.SectionTableOffset + ($patchSectionIndex * 40), [System.IO.SeekOrigin]::Begin)
        $stream.Write($sectionHeader, 0, $sectionHeader.Length)
        if ($freshSection) {
            [void]$stream.Seek($peInfo.PeOffset + 6, [System.IO.SeekOrigin]::Begin)
            $stream.Write([BitConverter]::GetBytes([UInt16]($peInfo.SectionCount + 1)), 0, 2)
            [void]$stream.Seek($peInfo.OptionalHeaderOffset + 56, [System.IO.SeekOrigin]::Begin)
            $stream.Write([BitConverter]::GetBytes([UInt32]$newSizeOfImage), 0, 4)
        }
    }

    # Enable the hook only after the entire payload and PE metadata are written.
    [void]$stream.Seek($hookOffset, [System.IO.SeekOrigin]::Begin)
    $stream.Write($patchedHook, 0, $patchedHook.Length)
    $stream.Flush($true)
}
finally {
    $stream.Dispose()
}

& $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot
Write-Host 'Da va client: ten nhan vat nhan Shift, Caps Lock va Ctrl+V Unicode.'
Write-Host "Ban sao truoc khi va: $backupPath"
