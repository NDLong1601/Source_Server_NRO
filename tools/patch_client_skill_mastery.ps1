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
    throw "Không tìm thấy GameAssembly.dll tại: $assemblyPath"
}

# Panel.paintSkill, RVA 0x28CD53 (file offset 0x28B953), client XUNGLORDLOCAL IL2CPP.
# Gốc: if (skill.point == skillTemplate.maxPoint) -> "đã đạt cấp tối đa".
# Vá:  if (skill.point >= 7) -> nhánh vẽ thanh thông thạo.
$patchOffset = 0x28B953
[byte[]]$originalBytes = @(0x8B, 0x47, 0x20, 0x39, 0x46, 0x1C, 0x0F, 0x84, 0xFB, 0x03, 0x00, 0x00)
[byte[]]$patchedBytes  = @(0x83, 0x7E, 0x1C, 0x07, 0x0F, 0x8D, 0x49, 0x02, 0x00, 0x00, 0x90, 0x90)

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

function Read-PatchBytes {
    param([string]$Path)
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
    try {
        if ($stream.Length -lt ($patchOffset + $originalBytes.Length)) {
            throw 'GameAssembly.dll ngắn hơn vị trí vá dự kiến; không đúng phiên bản client.'
        }
        [void]$stream.Seek($patchOffset, [System.IO.SeekOrigin]::Begin)
        $buffer = New-Object byte[] $originalBytes.Length
        $read = $stream.Read($buffer, 0, $buffer.Length)
        if ($read -ne $buffer.Length) {
            throw 'Không đọc đủ dữ liệu tại vị trí vá.'
        }
        return $buffer
    }
    finally {
        $stream.Dispose()
    }
}

function Format-HexBytes {
    param([byte[]]$Bytes)
    return (($Bytes | ForEach-Object { $_.ToString('X2') }) -join ' ')
}

if ($Action -eq 'Restore') {
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        throw "Không có bản gốc để khôi phục: $backupPath"
    }
    Copy-Item -LiteralPath $backupPath -Destination $assemblyPath -Force
    Write-Host "Đã khôi phục client từ: $backupPath"
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

$currentBytes = Read-PatchBytes -Path $assemblyPath
$isOriginal = Test-BytesEqual -Left $currentBytes -Right $originalBytes
$isPatched = Test-BytesEqual -Left $currentBytes -Right $patchedBytes

if ($Action -eq 'Verify') {
    if (-not $isPatched) {
        throw "Client chưa được vá hoặc sai phiên bản. Bytes hiện tại: $(Format-HexBytes $currentBytes)"
    }
    Write-Host 'OK: client đã có bản vá thanh thông thạo cho skill cấp 7 trở lên.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if ($isPatched) {
    Write-Host 'Client đã được vá trước đó, không cần thay đổi.'
    Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
    exit 0
}

if (-not $isOriginal) {
    throw "Không áp dụng để tránh làm hỏng client: chữ ký binary không khớp. Bytes hiện tại: $(Format-HexBytes $currentBytes)"
}

$runningClient = Get-Process -Name 'XUNGLORDLOCAL' -ErrorAction SilentlyContinue
if ($null -ne $runningClient) {
    throw 'Client XUNGLORDLOCAL đang chạy. Hãy đóng client rồi áp dụng lại bản vá.'
}

if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    Copy-Item -LiteralPath $assemblyPath -Destination $backupPath
}

$stream = [System.IO.File]::Open($assemblyPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
try {
    [void]$stream.Seek($patchOffset, [System.IO.SeekOrigin]::Begin)
    $stream.Write($patchedBytes, 0, $patchedBytes.Length)
    $stream.Flush($true)
}
finally {
    $stream.Dispose()
}

$verifiedBytes = Read-PatchBytes -Path $assemblyPath
if (-not (Test-BytesEqual -Left $verifiedBytes -Right $patchedBytes)) {
    throw 'Ghi bản vá không thành công; hãy dùng -Action Restore để khôi phục.'
}

Write-Host 'Đã vá client: skill cấp 7 trở lên sẽ hiển thị thanh thông thạo.'
Write-Host "Bản gốc: $backupPath"
Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -LiteralPath $assemblyPath).Hash)"
