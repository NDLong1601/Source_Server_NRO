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
$backupPath = Join-Path $resolvedClientRoot 'GameAssembly.dll.skill-mastery.original'

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
    return [pscustomobject]@{
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
    return (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

# Client XUNGLORDLOCAL IL2CPP, intVERSION 240.
# Executable code cave at RVA 0x21A6EC, using 261/276 padding bytes in .text.
# The .text virtual size is extended to its existing raw size so Windows maps
# the full cave explicitly; this does not add or resize any file data.
# The payload reads the extended sub-command 62/b=0 and updates the actual
# level and dynamic stats. point remains 7 so effects keep using level 7.
[byte[]]$masteryPayload = [Convert]::FromBase64String(
    'TIt7GE2F/w+EUFETAEyJ+THS6P2kHwAPt9jpFUkTAGZBiVhiTInHTIn5MdLo46QfAA+3wIlHIMdHJAAAAABMifkx0ujMpB8AZolHUkyJ+THS6F6jHwCJR0RMifkx0uhRox8AiUcoTIn5MdLoRKMfAIlHOEyJ+THS6DejHwCJRzxMifkx0ugqox8AiUdA6T1QEwBIjU4cg34cB3wTg34gB3wNgX4gECcAAHcESI1OIDHS6UolBwBJjU8cQYN/HAd8FUGDfyAHfA5BgX8gECcAAHcESY1PIDHS6ZiIBABMi4AIDAAAQYN/HAd8FUGDfyAHfA5BgX8gECcAAHcETItAKOnkjAQA'
)
[byte[]]$emptyCodeCave = New-Object byte[] $masteryPayload.Length

$patches = @(
    # Panel.paintSkill: point >= 7 uses the mastery progress branch.
    (New-BinaryPatch 'Mastery progress bar' 0x28B953 `
        ([byte[]]@(0x8B, 0x47, 0x20, 0x39, 0x46, 0x1C, 0x0F, 0x84, 0xFB, 0x03, 0x00, 0x00)) `
        ([byte[]]@(0x83, 0x7E, 0x1C, 0x07, 0x0F, 0x8D, 0x49, 0x02, 0x00, 0x00, 0x90, 0x90))),

    # Controller.read_UpdateSkill: redirect to the extended packet payload.
    (New-BinaryPatch 'Read mastery packet' 0x34DC04 `
        ([byte[]]@(0x48, 0x8B, 0x4B, 0x18, 0x48, 0x85, 0xC9)) `
        ([byte[]]@(0xE9, 0xE3, 0xB6, 0xEC, 0xFF, 0x90, 0x90))),
    (New-BinaryPatch 'Apply level and stats' 0x34DCF3 `
        ([byte[]]@(0x66, 0x41, 0x89, 0x58, 0x62)) `
        ([byte[]]@(0xE9, 0x13, 0xB6, 0xEC, 0xFF))),

    # Skill list and detail popup read the synced actual level from powRequire.
    # point is unchanged to prevent indexing beyond the level-7 effect data.
    (New-BinaryPatch 'Actual level in skill list' 0x28B8E6 `
        ([byte[]]@(0x48, 0x8D, 0x4E, 0x1C, 0x33, 0xD2)) `
        ([byte[]]@(0xE9, 0x93, 0xDA, 0xF8, 0xFF, 0x90))),
    (New-BinaryPatch 'Actual level in skill detail' 0x261C5B `
        ([byte[]]@(0x49, 0x8D, 0x4F, 0x1C, 0x33, 0xD2)) `
        ([byte[]]@(0xE9, 0x42, 0x77, 0xFB, 0xFF, 0x90))),
    (New-BinaryPatch 'Mastery label in skill detail' 0x2620CE `
        ([byte[]]@(0x4C, 0x8B, 0x80, 0x08, 0x0C, 0x00, 0x00)) `
        ([byte[]]@(0xE9, 0xF6, 0x72, 0xFB, 0xFF, 0x90, 0x90))),

    (New-BinaryPatch 'Payload mastery' 0x219AEC $emptyCodeCave $masteryPayload),
    (New-BinaryPatch 'Map mastery payload' 0x230 `
        ([byte[]]@(0xEC, 0x96, 0x21, 0x00)) `
        ([byte[]]@(0x00, 0x98, 0x21, 0x00)))
)

if ($Action -eq 'Restore') {
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        throw "Khong co ban goc de khoi phuc: $backupPath"
    }
    $runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
    if ($null -ne $runningClient) {
        throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi khoi phuc.'
    }
    Copy-Item -LiteralPath $backupPath -Destination $assemblyPath -Force
    Write-Host "Da khoi phuc client tu: $backupPath"
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

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
        throw "Client chua du ban va mastery: $((($missing | ForEach-Object { $_.Patch.Name }) -join ', '))"
    }
    Write-Host 'OK: client da nhan cap that, chi so dong va thanh mastery cho skill cap 7 tro len.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if ($missing.Count -eq 0) {
    Write-Host 'Client da co day du ban va mastery, khong can thay doi.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client roi ap dung lai ban va.'
}

if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
}

$stream = [System.IO.File]::Open(
    $assemblyPath,
    [System.IO.FileMode]::Open,
    [System.IO.FileAccess]::ReadWrite,
    [System.IO.FileShare]::None
)
try {
    # Write the payload before enabling its jump hooks.
    foreach ($state in ($missing | Sort-Object { if ($_.Patch.Name -eq 'Payload mastery') { 0 } else { 1 } })) {
        [void]$stream.Seek($state.Patch.Offset, [System.IO.SeekOrigin]::Begin)
        $stream.Write($state.Patch.Patched, 0, $state.Patch.Patched.Length)
    }
    $stream.Flush($true)
}
finally {
    $stream.Dispose()
}

& $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot
Write-Host 'Da va client: cap 8+, chi so moi va thanh mastery se dong bo truc tiep tu server.'
Write-Host "Ban goc: $backupPath"
