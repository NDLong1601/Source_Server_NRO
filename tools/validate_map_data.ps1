[CmdletBinding()]
param(
    [string]$MapRoot,
    [switch]$FailOnWarnings,
    [ValidateRange(1, 500)]
    [int]$MaxDetails = 30
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($MapRoot)) {
    $MapRoot = Join-Path $PSScriptRoot "..\data\map"
}

$validationErrors = New-Object System.Collections.Generic.List[string]
$validationWarnings = New-Object System.Collections.Generic.List[string]
$tileValidCount = 0
$backgroundValidCount = 0

function Add-ValidationError {
    param([string]$Message)
    $script:validationErrors.Add($Message)
}

function Add-ValidationWarning {
    param([string]$Message)
    $script:validationWarnings.Add($Message)
}

function Get-NumericMapFiles {
    param(
        [string]$Directory,
        [string]$Kind
    )

    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        Add-ValidationError "${Kind}: thiếu thư mục $Directory"
        return @()
    }

    $result = New-Object System.Collections.Generic.List[object]
    foreach ($file in Get-ChildItem -LiteralPath $Directory -File) {
        $id = 0
        if (-not [int]::TryParse($file.Name, [ref]$id) -or $id -lt 0) {
            Add-ValidationWarning "${Kind}: bỏ qua file không có tên là ID số nguyên: $($file.Name)"
            continue
        }
        if ($id -gt 254) {
            Add-ValidationError "$Kind/${id}: map ID vượt giới hạn packet 0..254"
        }
        $result.Add([pscustomobject]@{
            Id = $id
            File = $file
        })
    }
    return @($result | Sort-Object Id)
}

function Test-TileMapFile {
    param([object]$Entry)

    try {
        $bytes = [System.IO.File]::ReadAllBytes($Entry.File.FullName)
        if ($bytes.Length -lt 2) {
            Add-ValidationError "tile/$($Entry.Id): file chỉ có $($bytes.Length) byte, thiếu width/height"
            return
        }

        $width = [int]$bytes[0]
        $height = [int]$bytes[1]
        if ($width -lt 1 -or $width -gt 127 -or $height -lt 1 -or $height -gt 127) {
            Add-ValidationError "tile/$($Entry.Id): kích thước $($width)x$($height) ngoài giới hạn runtime 1..127"
            return
        }

        $expectedLength = 2 + ($width * $height)
        if ($bytes.Length -lt $expectedLength) {
            Add-ValidationError "tile/$($Entry.Id): bị cắt, cần $expectedLength byte nhưng chỉ có $($bytes.Length)"
            return
        }
        if ($bytes.Length -gt $expectedLength) {
            $trailing = $bytes.Length - $expectedLength
            Add-ValidationWarning "tile/$($Entry.Id): có $trailing legacy trailing byte; runtime chấp nhận nhưng compiler mới phải ghi canonical"
        }

        for ($index = 2; $index -lt $expectedLength; $index++) {
            if ([int]$bytes[$index] -gt 127) {
                Add-ValidationError "tile/$($Entry.Id): tile ID $([int]$bytes[$index]) tại offset $index vượt giới hạn signed byte"
                return
            }
        }
        $script:tileValidCount++
    } catch {
        Add-ValidationError "tile/$($Entry.Id): không đọc được file - $($_.Exception.Message)"
    }
}

function Test-BackgroundMapFile {
    param([object]$Entry)

    try {
        $bytes = [System.IO.File]::ReadAllBytes($Entry.File.FullName)
        if ($bytes.Length -lt 2) {
            Add-ValidationError "background/$($Entry.Id): file chỉ có $($bytes.Length) byte, thiếu item count"
            return
        }

        $count = ([int]$bytes[0] -shl 8) -bor [int]$bytes[1]
        if ($count -gt 32767) {
            Add-ValidationError "background/$($Entry.Id): item count $count vượt giới hạn signed short"
            return
        }
        $expectedLength = 2 + ($count * 6)
        if ($bytes.Length -ne $expectedLength) {
            Add-ValidationError "background/$($Entry.Id): độ dài sai, count=$count cần $expectedLength byte nhưng có $($bytes.Length)"
            return
        }

        for ($record = 0; $record -lt $count; $record++) {
            $offset = 2 + ($record * 6)
            $templateId = ([int]$bytes[$offset] -shl 8) -bor [int]$bytes[$offset + 1]
            if ($templateId -gt 32767) {
                Add-ValidationError "background/$($Entry.Id): template ID $templateId ở record $record vượt giới hạn signed short"
                return
            }
        }
        $script:backgroundValidCount++
    } catch {
        Add-ValidationError "background/$($Entry.Id): không đọc được file - $($_.Exception.Message)"
    }
}

$resolvedMapRoot = [System.IO.Path]::GetFullPath($MapRoot)
$tileDirectory = Join-Path $resolvedMapRoot "tile_map_data"
$backgroundDirectory = Join-Path $resolvedMapRoot "item_bg_map_data"

$tileFiles = @(Get-NumericMapFiles -Directory $tileDirectory -Kind "tile")
$backgroundFiles = @(Get-NumericMapFiles -Directory $backgroundDirectory -Kind "background")

foreach ($entry in $tileFiles) {
    Test-TileMapFile -Entry $entry
}
foreach ($entry in $backgroundFiles) {
    Test-BackgroundMapFile -Entry $entry
}

$tileIds = @{}
foreach ($entry in $tileFiles) {
    $tileIds[$entry.Id] = $true
}
$backgroundIds = @{}
foreach ($entry in $backgroundFiles) {
    $backgroundIds[$entry.Id] = $true
}
foreach ($entry in $tileFiles) {
    if (-not $backgroundIds.ContainsKey($entry.Id)) {
        Add-ValidationWarning "map/$($entry.Id): có tile map nhưng không có item_bg_map_data"
    }
}
foreach ($entry in $backgroundFiles) {
    if (-not $tileIds.ContainsKey($entry.Id)) {
        Add-ValidationWarning "map/$($entry.Id): có item_bg_map_data mồ côi nhưng không có tile map"
    }
}

$details = @()
$details += @($validationErrors | ForEach-Object { "[ERROR] $_" })
$details += @($validationWarnings | ForEach-Object { "[WARN ] $_" })
$shownDetails = @($details | Select-Object -First $MaxDetails)
foreach ($detail in $shownDetails) {
    Write-Output $detail
}
if ($details.Count -gt $shownDetails.Count) {
    Write-Output "[INFO ] Đã ẩn $($details.Count - $shownDetails.Count) chi tiết; tăng -MaxDetails để xem thêm."
}

Write-Output ("[MAP  ] root={0}" -f $resolvedMapRoot)
Write-Output ("[TILE ] files={0}; valid={1}" -f $tileFiles.Count, $tileValidCount)
Write-Output ("[BG   ] files={0}; valid={1}" -f $backgroundFiles.Count, $backgroundValidCount)
Write-Output ("[TOTAL] errors={0}; warnings={1}" -f $validationErrors.Count, $validationWarnings.Count)

if ($validationErrors.Count -gt 0 -or ($FailOnWarnings -and $validationWarnings.Count -gt 0)) {
    exit 1
}
exit 0
