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
$backupPath = Join-Path $resolvedClientRoot 'GameAssembly.dll.fishing-equipment.original'

if (-not (Test-Path -LiteralPath $assemblyPath -PathType Leaf)) {
    throw "Khong tim thay GameAssembly.dll tai: $assemblyPath"
}

function New-BinaryPatch {
    param(
        [string]$Name,
        [long]$Offset,
        [byte[]]$Original,
        [byte[]]$Patched
    )
    [pscustomobject]@{
        Name = $Name
        Offset = $Offset
        Original = $Original
        Patched = $Patched
    }
}

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

    if ($Stream.Length -lt ($Offset + $Length)) {
        throw 'GameAssembly.dll ngan hon vi tri va du kien; khong dung phien ban client.'
    }
    [void]$Stream.Seek($Offset, [System.IO.SeekOrigin]::Begin)
    $buffer = New-Object byte[] $Length
    $read = $Stream.Read($buffer, 0, $buffer.Length)
    if ($read -ne $buffer.Length) {
        throw 'Khong doc du du lieu tai vi tri va.'
    }
    return $buffer
}

function Format-HexBytes {
    param([byte[]]$Bytes)

    (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

function Get-PeSectionTable {
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
        [void]$reader.ReadUInt16() # machine
        $sectionCount = $reader.ReadUInt16()
        $stream.Position = $peOffset + 20
        $optionalHeaderSize = $reader.ReadUInt16()
        $stream.Position = $peOffset + 24 + $optionalHeaderSize

        $sections = New-Object System.Collections.Generic.List[object]
        for ($index = 0; $index -lt $sectionCount; $index++) {
            $name = [Text.Encoding]::ASCII.GetString($reader.ReadBytes(8)).Trim([char]0)
            $virtualSize = [int64]$reader.ReadUInt32()
            $virtualAddress = [int64]$reader.ReadUInt32()
            $rawSize = [int64]$reader.ReadUInt32()
            $rawAddress = [int64]$reader.ReadUInt32()
            $stream.Position += 16 # relocation/line-number fields and characteristics
            $sections.Add([pscustomobject]@{
                Name = $name
                VirtualAddress = $virtualAddress
                VirtualSize = $virtualSize
                RawAddress = $rawAddress
                RawSize = $rawSize
            })
        }
        return $sections.ToArray()
    }
    finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

$script:peSections = Get-PeSectionTable -Path $assemblyPath

function Convert-FileOffsetToRva {
    param([long]$Offset)

    $section = @($script:peSections | Where-Object {
        $Offset -ge $_.RawAddress -and $Offset -lt ($_.RawAddress + [Math]::Max($_.RawSize, $_.VirtualSize))
    } | Select-Object -First 1)
    if ($section.Count -ne 1) {
        throw ("Khong the doi file offset 0x{0:X} sang RVA." -f $Offset)
    }
    return [int64]($section[0].VirtualAddress + ($Offset - $section[0].RawAddress))
}

function New-NearJump {
    param(
        [long]$Offset,
        [long]$Target
    )

    [byte[]]$result = New-Object byte[] 5
    $result[0] = 0xE9
    $displacement = (Convert-FileOffsetToRva $Target) - ((Convert-FileOffsetToRva $Offset) + $result.Length)
    if ($displacement -lt [int]::MinValue -or $displacement -gt [int]::MaxValue) {
        throw 'Dia chi nhay vuot qua pham vi rel32 cua client.'
    }
    [BitConverter]::GetBytes([int]$displacement).CopyTo($result, 1)
    return $result
}

function New-NearConditionalJump {
    param(
        [byte]$Condition,
        [long]$Offset,
        [long]$Target
    )

    [byte[]]$result = New-Object byte[] 6
    $result[0] = 0x0F
    $result[1] = $Condition
    $displacement = (Convert-FileOffsetToRva $Target) - ((Convert-FileOffsetToRva $Offset) + $result.Length)
    if ($displacement -lt [int]::MinValue -or $displacement -gt [int]::MaxValue) {
        throw 'Dia chi nhay vuot qua pham vi rel32 cua client.'
    }
    [BitConverter]::GetBytes([int]$displacement).CopyTo($result, 2)
    return $result
}

# Client XUNGLORDLOCAL IL2CPP, Unity 2022.3.26f1.
# Panel.paintEffectItem currently renders the existing glow for option 72.
# The hook below makes option 251 use the identical renderer. The server only
# assigns option 251 to the exact bait/tackle stack selected by the player.
$optionCheckOffset = 0x27B3AC
$optionEffectContinuation = 0x27B3B2
$skipEffectOffset = 0x27B659
$codeCaveOffset = 0x214B02

[byte[]]$codeCaveOriginal = New-Object byte[] 20
for ($index = 0; $index -lt $codeCaveOriginal.Length; $index++) {
    $codeCaveOriginal[$index] = 0xCC
}
[byte[]]$codeCavePatched = [byte[]](@(
    # cmp dword ptr [rdx+10h], 251 -- use imm32; the imm8 encoding would
    # sign-extend FB to -5 and never match option ID 251.
    0x81, 0x7A, 0x10, 0xFB, 0x00, 0x00, 0x00
) + (New-NearConditionalJump -Condition 0x84 -Offset ($codeCaveOffset + 7) -Target $optionEffectContinuation) + (New-NearJump -Offset ($codeCaveOffset + 13) -Target $skipEffectOffset) + @(
    0xCC, 0xCC
))

$patches = @(
    # Existing JNE remains conditional: option 72 follows the original path;
    # all other options are checked in the code cave for our marker ID 251.
    (New-BinaryPatch 'Accept fishing equipped marker option' $optionCheckOffset `
        ([byte[]]@(0x0F, 0x85, 0xA7, 0x02, 0x00, 0x00)) `
        (New-NearConditionalJump -Condition 0x85 -Offset $optionCheckOffset -Target $codeCaveOffset)),
    (New-BinaryPatch 'Fishing equipped marker payload' $codeCaveOffset $codeCaveOriginal $codeCavePatched)
)

$stream = [System.IO.File]::Open(
    $assemblyPath,
    [System.IO.FileMode]::Open,
    [System.IO.FileAccess]::Read,
    [System.IO.FileShare]::Read
)
try {
    $states = foreach ($patch in $patches) {
        [byte[]]$current = Read-BytesAt $stream $patch.Offset $patch.Original.Length
        $state = if (Test-BytesEqual $current $patch.Patched) {
            'Patched'
        } elseif (Test-BytesEqual $current $patch.Original) {
            'Original'
        } else {
            'Mismatch'
        }
        [pscustomobject]@{ Patch = $patch; State = $state; Current = $current }
    }
}
finally {
    $stream.Dispose()
}

$mismatch = @($states | Where-Object State -eq 'Mismatch')
if ($mismatch.Count -gt 0) {
    $details = $mismatch | ForEach-Object {
        "[$($_.Patch.Name)] offset 0x$($_.Patch.Offset.ToString('X')): $(Format-HexBytes $_.Current)"
    }
    throw "Khong va de tranh lam hong client vi chu ky binary khong khop:`n$($details -join "`n")"
}

$missing = @($states | Where-Object State -eq 'Original')
if ($Action -eq 'Verify') {
    if ($missing.Count -gt 0) {
        throw "Client chua co danh dau trang bi cau ca: $((($missing | ForEach-Object { $_.Patch.Name }) -join ', '))"
    }
    Write-Host 'OK: client hien thi hieu ung vien cho dung moi/phu kien cau ca dang dung.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi thay doi GameAssembly.dll.'
}

if ($Action -eq 'Restore') {
    if ($missing.Count -eq $patches.Count) {
        Write-Host 'Client chua co va danh dau trang bi cau ca; khong can khoi phuc.'
        exit 0
    }
    $patchesToWrite = @($states | Where-Object State -eq 'Patched')
} else {
    if ($missing.Count -eq 0) {
        Write-Host 'Client da co va danh dau trang bi cau ca, khong can thay doi.'
        Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
        exit 0
    }
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
    }
    # Write the code cave before switching the branch that executes it.
    $patchesToWrite = @($states | Where-Object State -eq 'Original' | Sort-Object {
        if ($_.Patch.Name -eq 'Fishing equipped marker payload') { 0 } else { 1 }
    })
}

$stream = [System.IO.File]::Open(
    $assemblyPath,
    [System.IO.FileMode]::Open,
    [System.IO.FileAccess]::ReadWrite,
    [System.IO.FileShare]::None
)
try {
    foreach ($state in $patchesToWrite) {
        $targetBytes = if ($Action -eq 'Restore') { $state.Patch.Original } else { $state.Patch.Patched }
        [void]$stream.Seek($state.Patch.Offset, [System.IO.SeekOrigin]::Begin)
        $stream.Write($targetBytes, 0, $targetBytes.Length)
    }
    $stream.Flush($true)
}
finally {
    $stream.Dispose()
}

if ($Action -eq 'Restore') {
    & $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot 2>$null
    if ($LASTEXITCODE -eq 0) {
        throw 'Khoi phuc khong dat: client van con nhan option danh dau cau ca.'
    }
    Write-Host 'Da go va danh dau trang bi cau ca; cac va client khac van duoc giu nguyen.'
} else {
    & $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot
    Write-Host 'Da va client: option 251 se tao hieu ung vien cho moi/phu kien cau ca dang su dung.'
    Write-Host "Ban sao truoc khi va: $backupPath"
}
