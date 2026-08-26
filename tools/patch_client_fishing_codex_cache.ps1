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
$backupPath = Join-Path $resolvedClientRoot 'GameAssembly.dll.fishing-codex-cache.original'

if (-not (Test-Path -LiteralPath $assemblyPath -PathType Leaf)) {
    throw "Khong tim thay GameAssembly.dll tai: $assemblyPath"
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

function Format-HexBytes {
    param([byte[]]$Bytes)

    (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

# Panel.doRada (IL2CPP client build XUNGLORDLOCAL):
#   cmp RadarScr.list, 0
#   je request-radar-from-server
# With the original conditional jump, opening the fishing book fills the shared
# RadarScr.list and a later inventory click skips the normal radar request.
# Replacing JE with JMP preserves the target and always requests action 0,
# allowing the server to restore the character-card list after the fish book.
$patchOffset = 0x26E80A
$windowOffset = $patchOffset - 8
[byte[]]$originalWindow = @(0x48, 0x83, 0xB8, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x74, 0x33, 0x83, 0xB9, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x75, 0x0C, 0xE8)
[byte[]]$patchedWindow = @($originalWindow)
$patchedWindow[8] = 0xEB

$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
try {
    if ($stream.Length -lt ($windowOffset + $originalWindow.Length)) {
        throw 'GameAssembly.dll ngan hon vi tri can va; khong dung phien ban client.'
    }
    [void]$stream.Seek($windowOffset, [System.IO.SeekOrigin]::Begin)
    [byte[]]$currentWindow = New-Object byte[] $originalWindow.Length
    if ($stream.Read($currentWindow, 0, $currentWindow.Length) -ne $currentWindow.Length) {
        throw 'Khong doc du du lieu tai vi tri va.'
    }
} finally {
    $stream.Dispose()
}

$state = if (Test-BytesEqual $currentWindow $patchedWindow) {
    'Patched'
} elseif (Test-BytesEqual $currentWindow $originalWindow) {
    'Original'
} else {
    'Mismatch'
}

if ($state -eq 'Mismatch') {
    throw "Khong va de tranh lam hong client. Byte ky vong: $(Format-HexBytes $originalWindow); byte hien tai: $(Format-HexBytes $currentWindow)"
}

if ($Action -eq 'Verify') {
    if ($state -ne 'Patched') {
        throw 'Client chua co ban va tach cache So suu tam va So tay ca.'
    }
    Write-Host 'OK: So suu tam luon tai lai the nhan vat sau khi mo So tay ca.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if ($Action -eq 'Restore' -and $state -eq 'Original') {
    Write-Host 'Client chua co ban va cache so tay ca; khong can khoi phuc.'
    exit 0
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL dang chay. Hay dong client truoc khi thay doi GameAssembly.dll.'
}

if ($Action -eq 'Apply' -and $state -eq 'Patched') {
    Write-Host 'Client da co ban va cache So suu tam/So tay ca, khong can thay doi.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if ($Action -eq 'Apply' -and -not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
}

$targetByte = if ($Action -eq 'Restore') { 0x74 } else { 0xEB }
$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
try {
    [void]$stream.Seek($patchOffset, [System.IO.SeekOrigin]::Begin)
    $stream.WriteByte($targetByte)
    $stream.Flush($true)
} finally {
    $stream.Dispose()
}

if ($Action -eq 'Restore') {
    Write-Host 'Da khoi phuc dieu kien cache Radar goc cua client.'
} else {
    & $PSCommandPath -Action Verify -ClientRoot $resolvedClientRoot
    Write-Host 'Da va client: So suu tam se luon tai lai the nhan vat, khong bi So tay ca ghi de cache.'
    Write-Host "Ban sao truoc khi va: $backupPath"
}
