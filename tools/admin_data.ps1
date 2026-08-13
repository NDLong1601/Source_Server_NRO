param(
    [string]$Action = "status",
    [string]$Output = "",
    [string]$Id = "",
    [string]$Search = "",
    [string]$Type = "",
    [string]$Name = "",
    [string]$Description = "",
    [string]$Gender = "3",
    [string]$Level = "0",
    [string]$IconId = "0",
    [string]$Part = "-1",
    [string]$IsUpToUp = "0",
    [string]$PowerRequire = "0",
    [string]$Gold = "0",
    [string]$Gem = "0",
    [string]$Head = "-1",
    [string]$Body = "-1",
    [string]$Leg = "-1",
    [string]$NpcId = "",
    [string]$ShopId = "",
    [string]$TabId = "",
    [string]$TagName = "",
    [string]$TypeShop = "0",
    [string]$TempId = "",
    [string]$IsNew = "0",
    [string]$IsSell = "1",
    [string]$TypeSell = "0",
    [string]$Cost = "0",
    [string]$IconSpec = "0",
    [string]$OptionMode = "0",
    [string]$OptionId = "",
    [string]$Param = "0",
    [string]$EventValue = "",
    [string]$ExpRate = "",
    [string]$ConfigKey = "",
    [string]$ConfigValue = "",
    [string]$GiftCode = "",
    [string]$CountLeft = "0",
    [string]$GiftDetail = "",
    [string]$ExpiryMode = "days",
    [string]$ValidDays = "30",
    [string]$StartDate = "",
    [string]$EndDate = "",
    [string]$OwnerId = "",
    [string]$TemplateId = "",
    [string]$RadarRank = "0",
    [string]$RadarMax = "120",
    [string]$RadarType = "0",
    [string]$RadarMobId = "-1",
    [string]$RequireId = "-1",
    [string]$RequireLevel = "0",
    [string]$AuraId = "-1",
    [string]$OptionsJson = "[]",
    [string]$MilestonesJson = "[]",
    [string]$MobDropsJson = "[]",
    [string]$BossDropsJson = "[]",
    [string]$Enabled = "1",
    [string]$UseTimeRange = "0",
    [string]$TimeStart = "",
    [string]$TimeEnd = "",
    [string]$UseInterval = "0",
    [string]$IntervalMinutes = "1",
    [string]$MapId = "",
    [string]$MapIdsJson = "[]",
    [string]$ZoneId = "-1",
    [string]$SpawnX = "-1",
    [string]$SpawnY = "-1",
    [string]$Hp = "1000000",
    [string]$Damage = "10000",
    [string]$Announce = "1",
    [string]$DropsJson = "[]",
    [string]$SkillsJson = "[]",
    [string]$PointMultiplier = "1",
    [string]$DropMultiplier = "1",
    [string]$BossId = "",
    [string]$BossQuantity = "1",
    [string]$SourceType = "mob",
    [string]$SourceId = "-1",
    [string]$QuantityMin = "1",
    [string]$QuantityMax = "1",
    [string]$DropRate = "100",
    [string]$Points = "0",
    [string]$Notes = "",
    [string]$Notify = "",
    [string]$PayloadJson = "{}",
    [string]$Encoded = "0"
)

$ErrorActionPreference = "Stop"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

function Decode-InputParam {
    param([string]$Value)
    if ($Encoded -eq "1") {
        return [System.Uri]::UnescapeDataString($Value)
    }
    return $Value
}

foreach ($paramName in @(
        "Id", "Search", "Type", "Name", "Description", "Gender", "Level", "IconId", "Part",
        "IsUpToUp", "PowerRequire", "Gold", "Gem", "Head", "Body", "Leg", "NpcId", "ShopId",
        "TabId", "TagName", "TypeShop", "TempId", "IsNew", "IsSell", "TypeSell", "Cost",
        "IconSpec", "OptionMode", "OptionId", "Param", "EventValue", "ExpRate", "ConfigKey", "ConfigValue",
        "GiftCode", "CountLeft", "GiftDetail", "ExpiryMode", "ValidDays", "StartDate", "EndDate",
        "OwnerId", "TemplateId", "RadarRank", "RadarMax", "RadarType", "RadarMobId", "RequireId", "RequireLevel", "AuraId", "OptionsJson", "MilestonesJson", "MobDropsJson", "BossDropsJson", "Enabled", "UseTimeRange", "TimeStart", "TimeEnd", "UseInterval",
        "IntervalMinutes", "MapId", "MapIdsJson", "ZoneId", "SpawnX", "SpawnY", "Hp", "Damage", "Announce", "DropsJson", "SkillsJson",
        "PointMultiplier", "DropMultiplier", "BossId", "BossQuantity", "SourceType", "SourceId", "QuantityMin", "QuantityMax", "DropRate", "Points", "Notes", "Notify", "PayloadJson"
    )) {
    Set-Variable -Name $paramName -Value (Decode-InputParam (Get-Variable -Name $paramName -ValueOnly))
}

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root "logs"
$ResultPath = if ([string]::IsNullOrWhiteSpace($Output)) { Join-Path $LogDir "admin_data_result.txt" } else { $Output }
$AdminLog = Join-Path $LogDir "admin_data.log"
$AdminLogMutex = New-Object System.Threading.Mutex($false, "Global\NRO_ADMIN_DATA_LOG")

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

function Write-Result {
    param([string]$Text)
    [System.IO.File]::WriteAllText($ResultPath, $Text, [System.Text.Encoding]::UTF8)
}

function Write-AdminLog {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    $hasLogMutex = $false
    for ($i = 0; $i -lt 8; $i++) {
        try {
            $hasLogMutex = $AdminLogMutex.WaitOne(5000)
            if (-not $hasLogMutex) {
                throw "Không lấy được khóa ghi admin log."
            }

            $recentLines = @()
            if (Test-Path -LiteralPath $AdminLog) {
                $recentLines = @([System.IO.File]::ReadAllLines($AdminLog, [System.Text.Encoding]::UTF8))
            }
            $recentLines = @($recentLines + $line | Select-Object -Last 20)

            $stream = [System.IO.File]::Open($AdminLog, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
            try {
                $writer = New-Object System.IO.StreamWriter($stream, $Utf8NoBom)
                try {
                    foreach ($recentLine in $recentLines) {
                        $writer.WriteLine($recentLine)
                    }
                    $writer.Flush()
                } finally {
                    $writer.Dispose()
                }
            } finally {
                $stream.Dispose()
            }
            return
        } catch {
            if ($i -eq 7) {
                return
            }
            Start-Sleep -Milliseconds 100
        } finally {
            if ($hasLogMutex) {
                $AdminLogMutex.ReleaseMutex() | Out-Null
                $hasLogMutex = $false
            }
        }
    }
}

function Get-ConfigMap {
    $configPath = Join-Path $Root "Config.properties"
    $map = @{}
    if (Test-Path $configPath) {
        Get-Content -Path $configPath -Encoding UTF8 | ForEach-Object {
            if ($_ -match "^\s*([^#][^=]+?)\s*=\s*(.*)\s*$") {
                $map[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }
    return $map
}

function Set-ConfigValue {
    param(
        [string]$Key,
        [string]$NewValue
    )

    $configPath = Join-Path $Root "Config.properties"
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $configPath) {
        foreach ($line in (Get-Content -Path $configPath -Encoding UTF8)) {
            $lines.Add($line)
        }
    }

    $updated = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$([regex]::Escape($Key))\s*=") {
            $lines[$i] = "$Key=$NewValue"
            $updated = $true
            break
        }
    }

    if (-not $updated) {
        $insertAt = 0
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^\s*#SERVER\s*$") {
                $insertAt = $i + 1
                break
            }
        }
        $lines.Insert($insertAt, "$Key=$NewValue")
    }

    [System.IO.File]::WriteAllText($configPath, ($lines -join [Environment]::NewLine), [System.Text.Encoding]::UTF8)
}

function Get-CombineCatalog {
    @(
        [pscustomobject]@{ Key="equipment.upgrade.rates"; Category="Cường hóa trang bị"; Name="Tỉ lệ nâng cấp +0 đến +7"; Default="80,50,20,10,7,5,1,0.3"; Kind="rate-list"; Description="Danh sách phần trăm theo cấp hiện tại, bắt đầu từ +0." },
        [pscustomobject]@{ Key="equipment.upgrade.statPercent"; Category="Cường hóa trang bị"; Name="Chỉ số tăng mỗi cấp"; Default="10"; Kind="int"; Description="Phần trăm tăng chỉ số gốc khi nâng cấp thành công." },
        [pscustomobject]@{ Key="equipment.upgrade.failStatLossPercent"; Category="Cường hóa trang bị"; Name="Chỉ số giảm khi rớt cấp"; Default="11"; Kind="int"; Description="Phần trăm trừ chỉ số khi thất bại ở mốc bị rớt cấp." },
        [pscustomobject]@{ Key="equipment.socket.rates"; Category="Đục lỗ / sao pha lê"; Name="Tỉ lệ đục lỗ 0 đến 8 sao"; Default="50,20,10,5,1,0.7,0.5,0.1,0.1"; Kind="rate-list"; Description="Tỉ lệ thật và tỉ lệ hiển thị dùng chung danh sách này." },
        [pscustomobject]@{ Key="equipment.socket.enhanceRate"; Category="Đục lỗ / sao pha lê"; Name="Cường hóa lỗ sao 8-9"; Default="25"; Kind="rate"; Description="Tỉ lệ cường hóa lỗ sao pha lê bằng Hematite và dùi đục." },
        [pscustomobject]@{ Key="earring.level2.upgradeRate"; Category="Bông tai"; Name="Nâng bông tai cấp 2"; Default="50"; Kind="rate"; Description="Tỉ lệ Bông tai Porata cấp 1 lên cấp 2." },
        [pscustomobject]@{ Key="earring.level3.upgradeRate"; Category="Bông tai"; Name="Nâng bông tai cấp 3"; Default="50"; Kind="rate"; Description="Tỉ lệ Bông tai Porata cấp 2 lên cấp 3." },
        [pscustomobject]@{ Key="earring.level2.optionRate"; Category="Bông tai"; Name="Mở chỉ số bông tai cấp 2"; Default="45"; Kind="rate"; Description="Tỉ lệ random thành công một dòng option cấp 2." },
        [pscustomobject]@{ Key="earring.level2.options"; Category="Bông tai"; Name="Option có thể ra ở cấp 2"; Default="77,80,81,103,50,94,5"; Kind="option-list"; Description="Danh sách ID option, phân cách bằng dấu phẩy; mỗi option có xác suất như nhau." },
        [pscustomobject]@{ Key="earring.level2.paramMin"; Category="Bông tai"; Name="Param nhỏ nhất cấp 2"; Default="5"; Kind="int"; Description="Giá trị option nhỏ nhất, có tính cả hai đầu mút." },
        [pscustomobject]@{ Key="earring.level2.paramMax"; Category="Bông tai"; Name="Param lớn nhất cấp 2"; Default="15"; Kind="int"; Description="Giá trị option lớn nhất, có tính cả hai đầu mút." },
        [pscustomobject]@{ Key="earring.level3.optionRate"; Category="Bông tai"; Name="Mở chỉ số bông tai cấp 3"; Default="30"; Kind="rate"; Description="Tỉ lệ random thành công hai dòng option cấp 3." },
        [pscustomobject]@{ Key="earring.level3.options"; Category="Bông tai"; Name="Option có thể ra ở cấp 3"; Default="77,80,81,103,50,94,5"; Kind="option-list"; Description="Danh sách ID option cấp 3, phân cách bằng dấu phẩy." },
        [pscustomobject]@{ Key="earring.level3.paramMin"; Category="Bông tai"; Name="Param nhỏ nhất cấp 3"; Default="5"; Kind="int"; Description="Giá trị nhỏ nhất cho cả hai dòng." },
        [pscustomobject]@{ Key="earring.level3.paramMax"; Category="Bông tai"; Name="Param lớn nhất cấp 3"; Default="15"; Kind="int"; Description="Giá trị lớn nhất cho cả hai dòng." },
        [pscustomobject]@{ Key="earring.level3.allowDuplicate"; Category="Bông tai"; Name="Cho phép trùng option cấp 3"; Default="true"; Kind="bool"; Description="true: hai dòng có thể trùng; false: luôn chọn hai ID khác nhau nếu đủ option." },
        [pscustomobject]@{ Key="crystal.level2.upgradeRate"; Category="Sao pha lê"; Name="Nâng sao pha lê cấp 2"; Default="50"; Kind="rate"; Description="Tỉ lệ dùng Hematite nâng sao pha lê lên cấp 2." },
        [pscustomobject]@{ Key="crystal.level2.polishRate"; Category="Sao pha lê"; Name="Đánh bóng sao pha lê"; Default="100"; Kind="rate"; Description="Tỉ lệ đánh bóng sao pha lê cấp 2." },
        [pscustomobject]@{ Key="craft.mergeUpgradeStoneRate"; Category="Ghép / phân rã"; Name="Ghép đá nâng cấp"; Default="80"; Kind="rate"; Description="Tỉ lệ làm phép nhập mảnh đá vụn thành đá nâng cấp." },
        [pscustomobject]@{ Key="craft.recycleActiveEquipmentRate"; Category="Ghép / phân rã"; Name="Phân rã đồ kích hoạt"; Default="100"; Kind="rate"; Description="Tỉ lệ phân rã trang bị kích hoạt." },
        [pscustomobject]@{ Key="craft.rebuildActiveCapsuleRate"; Category="Ghép / phân rã"; Name="Tái tạo Capsule kích hoạt"; Default="100"; Kind="rate"; Description="Tỉ lệ tái tạo Capsule kích hoạt." },
        [pscustomobject]@{ Key="craft.hematiteRate"; Category="Ghép / phân rã"; Name="Tạo đá Hematite"; Default="100"; Kind="rate"; Description="Tỉ lệ ghép sao pha lê thành Hematite." },
        [pscustomobject]@{ Key="craft.anvilRate"; Category="Ghép / phân rã"; Name="Chế tạo dùi đục"; Default="100"; Kind="rate"; Description="Tỉ lệ chế tạo dùi đục." },
        [pscustomobject]@{ Key="craft.grindStoneRate"; Category="Ghép / phân rã"; Name="Chế tạo đá mài"; Default="100"; Kind="rate"; Description="Tỉ lệ chế tạo đá mài." },
        [pscustomobject]@{ Key="book.oldBookRate"; Category="Sách tuyệt kỹ"; Name="Chế tạo cuốn sách cũ"; Default="20"; Kind="rate"; Description="Tỉ lệ đóng trang và bìa thành cuốn sách cũ." },
        [pscustomobject]@{ Key="book.exchangeRate"; Category="Sách tuyệt kỹ"; Name="Đổi sách tuyệt kỹ"; Default="20"; Kind="rate"; Description="Tỉ lệ đổi cuốn sách cũ khi không dùng con dấu." },
        [pscustomobject]@{ Key="book.extraOptionRate"; Category="Sách tuyệt kỹ"; Name="Thêm dòng khi dùng con dấu"; Default="20"; Kind="rate"; Description="Tỉ lệ nhánh random thêm option của sách khi dùng con dấu." },
        [pscustomobject]@{ Key="book.upgradeRate"; Category="Sách tuyệt kỹ"; Name="Nâng cấp sách tuyệt kỹ"; Default="10"; Kind="rate"; Description="Tỉ lệ nâng Sách Tuyệt Kỹ 1 lên cấp kế tiếp." },
        [pscustomobject]@{ Key="angel.createBaseRate"; Category="Trang bị Thiên Sứ"; Name="Tỉ lệ chế tạo cơ bản"; Default="90"; Kind="rate"; Description="Tỉ lệ cơ bản trước phần cộng thêm của đá nâng cấp." },
        [pscustomobject]@{ Key="angel.baseStatBonusPercent"; Category="Trang bị Thiên Sứ"; Name="Phần trăm cộng chỉ số gốc"; Default="100"; Kind="int"; Description="Mức cộng vào chỉ số gốc của đồ Thiên Sứ khi chế tạo thành công." },
        [pscustomobject]@{ Key="angel.luckyBaseRate"; Category="Trang bị Thiên Sứ"; Name="May mắn cơ bản"; Default="5"; Kind="rate"; Description="Mốc may mắn cơ bản trước phần cộng của đá may mắn." },
        [pscustomobject]@{ Key="angel.bonusOptions"; Category="Trang bị Thiên Sứ"; Name="Danh sách option phụ"; Default="50,77,103,94,5"; Kind="option-list"; Description="Các option phụ có thể được chọn ngẫu nhiên, không trùng nhau." },
        [pscustomobject]@{ Key="angel.bonusParamMin"; Category="Trang bị Thiên Sứ"; Name="Param phụ nhỏ nhất"; Default="1"; Kind="int"; Description="Param nhỏ nhất của option phụ." },
        [pscustomobject]@{ Key="angel.bonusParamMax"; Category="Trang bị Thiên Sứ"; Name="Param phụ lớn nhất"; Default="3"; Kind="int"; Description="Param lớn nhất của option phụ." }
    )
}

function Get-PropertyMap {
    param([string]$Path)
    $map = @{}
    if (Test-Path $Path) {
        Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
            if ($_ -match "^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$") {
                $map[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }
    $map
}

function Set-PropertyValue {
    param([string]$Path, [string]$Key, [string]$Value, [switch]$Remove)
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $Path) {
        foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) { $lines.Add($line) }
    }
    $found = $false
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
        if ($lines[$i] -match "^\s*$([regex]::Escape($Key))\s*=") {
            if ($Remove) { $lines.RemoveAt($i) } else { $lines[$i] = "$Key=$Value" }
            $found = $true
        }
    }
    if (-not $Remove -and -not $found) { $lines.Add("$Key=$Value") }
    $tempPath = "$Path.tmp.$PID"
    [System.IO.File]::WriteAllText($tempPath, ($lines -join [Environment]::NewLine) + [Environment]::NewLine, $Utf8NoBom)
    Move-Item -LiteralPath $tempPath -Destination $Path -Force
}

function Get-CombineEntry {
    param([string]$Key)
    Get-CombineCatalog | Where-Object { $_.Key -eq $Key } | Select-Object -First 1
}

function Assert-CombineValue {
    param($Entry, [string]$Value)
    $Value = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Giá trị không được để trống." }
    if ($Entry.Kind -eq "rate" -and $Value -notmatch '^\d+(\.\d+)?$') { throw "Tỉ lệ phải là số từ 0 đến 100, dùng dấu chấm cho số lẻ." }
    if ($Entry.Kind -eq "rate" -and ([double]$Value -lt 0 -or [double]$Value -gt 100)) { throw "Tỉ lệ phải nằm trong khoảng 0 đến 100." }
    if ($Entry.Kind -eq "int" -and $Value -notmatch '^\d+$') { throw "Giá trị phải là số nguyên không âm." }
    if ($Entry.Kind -eq "bool" -and $Value -notmatch '^(true|false|0|1)$') { throw "Giá trị bật/tắt phải là true, false, 1 hoặc 0." }
    if ($Entry.Kind -eq "option-list") {
        $parts = $Value -split ','
        if ($parts.Count -eq 0 -or @($parts | Where-Object { $_.Trim() -notmatch '^\d+$' }).Count -gt 0) { throw "Danh sách option chỉ gồm ID nguyên không âm, phân cách bằng dấu phẩy." }
    }
    if ($Entry.Kind -eq "rate-list") {
        $parts = $Value -split ','
        if ($parts.Count -eq 0) { throw "Danh sách tỉ lệ không được trống." }
        foreach ($part in $parts) {
            $number = $part.Trim()
            if ($number -notmatch '^\d+(\.\d+)?$' -or [double]$number -lt 0 -or [double]$number -gt 100) { throw "Mỗi tỉ lệ phải từ 0 đến 100 và phân cách bằng dấu phẩy." }
        }
    }
    $Value
}

function List-CombineConfig {
    $path = Join-Path $Root "combine.properties"
    $map = Get-PropertyMap $path
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("key`tcategory`tname`tvalue`tdefault`tkind`tdescription")
    foreach ($entry in (Get-CombineCatalog)) {
        $value = if ($map.ContainsKey($entry.Key)) { $map[$entry.Key] } else { $entry.Default }
        $rows.Add("$($entry.Key)`t$($entry.Category)`t$($entry.Name)`t$value`t$($entry.Default)`t$($entry.Kind)`t$($entry.Description)")
    }
    $rows -join "`r`n"
}

function Save-CombineConfig {
    $entry = Get-CombineEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa combine không hợp lệ: $ConfigKey" }
    $validated = Assert-CombineValue $entry $ConfigValue
    Set-PropertyValue -Path (Join-Path $Root "combine.properties") -Key $entry.Key -Value $validated
    "OK`tĐã lưu $($entry.Name). Server tự áp dụng trong tối đa 1 giây."
}

function Reset-CombineConfig {
    $entry = Get-CombineEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa combine không hợp lệ: $ConfigKey" }
    Set-PropertyValue -Path (Join-Path $Root "combine.properties") -Key $entry.Key -Value "" -Remove
    "OK`tĐã đưa $($entry.Name) về mặc định $($entry.Default)."
}

function Get-PlayerConfigCatalog {
    @(
        [pscustomobject]@{ Key="player.start.gold"; Category="Khởi tạo - Tài sản"; Name="Vàng ban đầu"; Default="2000"; Kind="long"; Scope="Nhân vật mới"; Description="Số vàng của nhân vật khi vừa được tạo." },
        [pscustomobject]@{ Key="player.start.gem"; Category="Khởi tạo - Tài sản"; Name="Ngọc xanh ban đầu"; Default="10000"; Kind="int"; Scope="Nhân vật mới"; Description="Số ngọc xanh của nhân vật mới." },
        [pscustomobject]@{ Key="player.start.testGem"; Category="Khởi tạo - Tài sản"; Name="Ngọc xanh server test"; Default="1000000000"; Kind="int"; Scope="Nhân vật mới"; Description="Số ngọc xanh khởi tạo khi server.test=true." },
        [pscustomobject]@{ Key="player.start.ruby"; Category="Khởi tạo - Tài sản"; Name="Hồng ngọc ban đầu"; Default="0"; Kind="int"; Scope="Nhân vật mới"; Description="Số hồng ngọc của nhân vật mới." },
        [pscustomobject]@{ Key="player.start.coupon"; Category="Khởi tạo - Tài sản"; Name="Coupon ban đầu"; Default="0"; Kind="int"; Scope="Nhân vật mới"; Description="Số coupon của nhân vật mới." },
        [pscustomobject]@{ Key="player.start.bagSlots"; Category="Khởi tạo - Sức chứa"; Name="Ô hành trang ban đầu"; Default="30"; Kind="slot"; Scope="Nhân vật mới"; Description="Độ dài items_bag khi tạo nhân vật." },
        [pscustomobject]@{ Key="player.start.boxSlots"; Category="Khởi tạo - Sức chứa"; Name="Ô rương ban đầu"; Default="30"; Kind="slot"; Scope="Nhân vật mới"; Description="Độ dài items_box khi tạo nhân vật." },
        [pscustomobject]@{ Key="player.start.stamina"; Category="Khởi tạo - Chỉ số"; Name="Thể lực ban đầu"; Default="1000"; Kind="stamina"; Scope="Nhân vật mới"; Description="Thể lực hiện tại và tối đa lúc tạo nhân vật." },
        [pscustomobject]@{ Key="player.start.limitPower"; Category="Khởi tạo - Chỉ số"; Name="Cấp giới hạn ban đầu"; Default="0"; Kind="limit-level"; Scope="Nhân vật mới"; Description="Cấp mở giới hạn sức mạnh ban đầu, từ 0 đến 9." },
        [pscustomobject]@{ Key="player.start.power"; Category="Khởi tạo - Chỉ số"; Name="Sức mạnh ban đầu"; Default="2000"; Kind="long"; Scope="Nhân vật mới"; Description="Sức mạnh của nhân vật khi vừa tạo." },
        [pscustomobject]@{ Key="player.start.potential"; Category="Khởi tạo - Chỉ số"; Name="Tiềm năng ban đầu"; Default="2000"; Kind="long"; Scope="Nhân vật mới"; Description="Tiềm năng của nhân vật khi vừa tạo." },
        [pscustomobject]@{ Key="player.start.criticalDragon"; Category="Khởi tạo - Chỉ số"; Name="Chí mạng rồng ban đầu"; Default="0"; Kind="critical"; Scope="Nhân vật mới"; Description="Chỉ số chí mạng rồng ban đầu." },

        [pscustomobject]@{ Key="player.start.earth.hp"; Category="Chỉ số gốc - Trái Đất"; Name="HP gốc"; Default="200"; Kind="positive-int"; Scope="Nhân vật mới"; Description="HP gốc ban đầu của Trái Đất." },
        [pscustomobject]@{ Key="player.start.earth.mp"; Category="Chỉ số gốc - Trái Đất"; Name="KI gốc"; Default="100"; Kind="positive-int"; Scope="Nhân vật mới"; Description="KI gốc ban đầu của Trái Đất." },
        [pscustomobject]@{ Key="player.start.earth.damage"; Category="Chỉ số gốc - Trái Đất"; Name="Sức đánh gốc"; Default="10"; Kind="positive-int"; Scope="Nhân vật mới"; Description="Sức đánh gốc ban đầu của Trái Đất." },
        [pscustomobject]@{ Key="player.start.earth.defense"; Category="Chỉ số gốc - Trái Đất"; Name="Giáp gốc"; Default="0"; Kind="int"; Scope="Nhân vật mới"; Description="Giáp gốc ban đầu của Trái Đất." },
        [pscustomobject]@{ Key="player.start.earth.critical"; Category="Chỉ số gốc - Trái Đất"; Name="Chí mạng gốc"; Default="0"; Kind="critical"; Scope="Nhân vật mới"; Description="Chí mạng gốc ban đầu của Trái Đất." },

        [pscustomobject]@{ Key="player.start.namek.hp"; Category="Chỉ số gốc - Namek"; Name="HP gốc"; Default="100"; Kind="positive-int"; Scope="Nhân vật mới"; Description="HP gốc ban đầu của Namek." },
        [pscustomobject]@{ Key="player.start.namek.mp"; Category="Chỉ số gốc - Namek"; Name="KI gốc"; Default="200"; Kind="positive-int"; Scope="Nhân vật mới"; Description="KI gốc ban đầu của Namek." },
        [pscustomobject]@{ Key="player.start.namek.damage"; Category="Chỉ số gốc - Namek"; Name="Sức đánh gốc"; Default="10"; Kind="positive-int"; Scope="Nhân vật mới"; Description="Sức đánh gốc ban đầu của Namek." },
        [pscustomobject]@{ Key="player.start.namek.defense"; Category="Chỉ số gốc - Namek"; Name="Giáp gốc"; Default="0"; Kind="int"; Scope="Nhân vật mới"; Description="Giáp gốc ban đầu của Namek." },
        [pscustomobject]@{ Key="player.start.namek.critical"; Category="Chỉ số gốc - Namek"; Name="Chí mạng gốc"; Default="0"; Kind="critical"; Scope="Nhân vật mới"; Description="Chí mạng gốc ban đầu của Namek." },

        [pscustomobject]@{ Key="player.start.saiyan.hp"; Category="Chỉ số gốc - Xayda"; Name="HP gốc"; Default="100"; Kind="positive-int"; Scope="Nhân vật mới"; Description="HP gốc ban đầu của Xayda." },
        [pscustomobject]@{ Key="player.start.saiyan.mp"; Category="Chỉ số gốc - Xayda"; Name="KI gốc"; Default="100"; Kind="positive-int"; Scope="Nhân vật mới"; Description="KI gốc ban đầu của Xayda." },
        [pscustomobject]@{ Key="player.start.saiyan.damage"; Category="Chỉ số gốc - Xayda"; Name="Sức đánh gốc"; Default="15"; Kind="positive-int"; Scope="Nhân vật mới"; Description="Sức đánh gốc ban đầu của Xayda." },
        [pscustomobject]@{ Key="player.start.saiyan.defense"; Category="Chỉ số gốc - Xayda"; Name="Giáp gốc"; Default="0"; Kind="int"; Scope="Nhân vật mới"; Description="Giáp gốc ban đầu của Xayda." },
        [pscustomobject]@{ Key="player.start.saiyan.critical"; Category="Chỉ số gốc - Xayda"; Name="Chí mạng gốc"; Default="0"; Kind="critical"; Scope="Nhân vật mới"; Description="Chí mạng gốc ban đầu của Xayda." },

        [pscustomobject]@{ Key="player.max.gold"; Category="Giới hạn toàn server"; Name="Vàng tối đa"; Default="200000000000"; Kind="positive-long"; Scope="Runtime"; Description="Trần vàng dùng khi nhặt, giao dịch, mua bán và lưu player." },
        [pscustomobject]@{ Key="player.max.gem"; Category="Giới hạn toàn server"; Name="Ngọc xanh tối đa"; Default="2147483647"; Kind="positive-int"; Scope="Runtime"; Description="Trần ngọc xanh; không vượt giới hạn int." },
        [pscustomobject]@{ Key="player.max.ruby"; Category="Giới hạn toàn server"; Name="Hồng ngọc tối đa"; Default="2147483647"; Kind="positive-int"; Scope="Runtime"; Description="Trần hồng ngọc; không vượt giới hạn int." },
        [pscustomobject]@{ Key="player.max.coupon"; Category="Giới hạn toàn server"; Name="Coupon tối đa"; Default="2147483647"; Kind="positive-int"; Scope="Runtime"; Description="Trần coupon; không vượt giới hạn int." },
        [pscustomobject]@{ Key="player.max.bagSlots"; Category="Giới hạn toàn server"; Name="Ô hành trang tối đa"; Default="80"; Kind="slot"; Scope="Runtime"; Description="Trần hành trang; giao thức hiện dùng một byte nên tối đa an toàn là 127." },
        [pscustomobject]@{ Key="player.max.boxSlots"; Category="Giới hạn toàn server"; Name="Ô rương tối đa"; Default="100"; Kind="slot"; Scope="Runtime"; Description="Trần rương; giao thức hiện dùng một byte nên tối đa an toàn là 127." },

        [pscustomobject]@{ Key="player.limit.power"; Category="Giới hạn 10 cấp"; Name="Trần sức mạnh"; Default="17999999999,19999999999,24999999999,29999999999,39999999999,50010000000,60010000000,70010000000,80010000000,90010000000"; Kind="long-list"; Scope="Runtime"; Description="Đúng 10 số tương ứng limitPower 0 đến 9." },
        [pscustomobject]@{ Key="player.limit.hpMp"; Category="Giới hạn 10 cấp"; Name="Trần HP/KI gốc"; Default="220000,240000,300000,350000,400000,450000,500000,525000,550000,575000"; Kind="int-list"; Scope="Runtime"; Description="Đúng 10 số tương ứng limitPower 0 đến 9." },
        [pscustomobject]@{ Key="player.limit.damage"; Category="Giới hạn 10 cấp"; Name="Trần sức đánh gốc"; Default="11000,12000,15000,18000,20000,22000,24000,24500,25000,26000"; Kind="int-list"; Scope="Runtime"; Description="Đúng 10 số tương ứng limitPower 0 đến 9." },
        [pscustomobject]@{ Key="player.limit.defense"; Category="Giới hạn 10 cấp"; Name="Trần giáp gốc"; Default="550,600,700,800,1000,1200,1400,1500,1600,1800"; Kind="int-list"; Scope="Runtime"; Description="Đúng 10 số tương ứng limitPower 0 đến 9." },
        [pscustomobject]@{ Key="player.limit.critical"; Category="Giới hạn 10 cấp"; Name="Trần chí mạng gốc"; Default="1,2,3,4,5,6,7,8,9,10"; Kind="critical-list"; Scope="Runtime"; Description="Đúng 10 số từ 0 đến 127 tương ứng limitPower 0 đến 9." }
    )
}

function Get-PlayerConfigEntry {
    param([string]$Key)
    Get-PlayerConfigCatalog | Where-Object { $_.Key -eq $Key } | Select-Object -First 1
}

function Assert-PlayerConfigValue {
    param($Entry, [string]$Value)
    $Value = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Giá trị không được để trống." }
    if ($Entry.Kind -in @("int", "positive-int", "slot", "stamina", "critical", "limit-level")) {
        if ($Value -notmatch '^\d+$' -or [decimal]$Value -gt 2147483647) { throw "Giá trị phải là số nguyên từ 0 đến 2.147.483.647." }
        $number = [long]$Value
        if ($Entry.Kind -eq "positive-int" -and $number -lt 1) { throw "Giá trị phải lớn hơn 0." }
        if ($Entry.Kind -eq "slot" -and ($number -lt 1 -or $number -gt 127)) { throw "Số ô phải từ 1 đến 127." }
        if ($Entry.Kind -eq "stamina" -and ($number -lt 1 -or $number -gt 32767)) { throw "Thể lực phải từ 1 đến 32.767." }
        if ($Entry.Kind -eq "critical" -and $number -gt 127) { throw "Chí mạng không được vượt 127." }
        if ($Entry.Kind -eq "limit-level" -and $number -gt 9) { throw "Cấp giới hạn phải từ 0 đến 9." }
    } elseif ($Entry.Kind -in @("long", "positive-long")) {
        $parsed = 0L
        if (-not [long]::TryParse($Value, [ref]$parsed) -or $parsed -lt 0) { throw "Giá trị phải là số nguyên 64-bit không âm." }
        if ($Entry.Kind -eq "positive-long" -and $parsed -lt 1) { throw "Giá trị phải lớn hơn 0." }
    } elseif ($Entry.Kind -in @("int-list", "long-list", "critical-list")) {
        $parts = @($Value -split ',')
        if ($parts.Count -ne 10) { throw "Danh sách giới hạn phải có đúng 10 giá trị cho cấp 0 đến 9." }
        $last = [decimal]-1
        foreach ($part in $parts) {
            $numberText = $part.Trim()
            if ($numberText -notmatch '^\d+$') { throw "Danh sách chỉ gồm số nguyên không âm, phân cách bằng dấu phẩy." }
            $number = [decimal]$numberText
            if ($Entry.Kind -ne "long-list" -and $number -gt 2147483647) { throw "Mỗi giá trị phải nằm trong giới hạn int." }
            if ($Entry.Kind -eq "critical-list" -and $number -gt 127) { throw "Mỗi giới hạn chí mạng phải từ 0 đến 127." }
            if ($number -lt $last) { throw "Giới hạn cấp sau không được nhỏ hơn cấp trước." }
            $last = $number
        }
        $Value = ($parts | ForEach-Object { $_.Trim() }) -join ','
    } else {
        throw "Kiểu cấu hình Player không được hỗ trợ: $($Entry.Kind)"
    }
    $Value
}

function List-PlayerConfig {
    $path = Join-Path $Root "player.properties"
    $map = Get-PropertyMap $path
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("key`tcategory`tname`tvalue`tdefault`tkind`tscope`tdescription")
    foreach ($entry in (Get-PlayerConfigCatalog)) {
        $value = if ($map.ContainsKey($entry.Key)) { $map[$entry.Key] } else { $entry.Default }
        $rows.Add("$($entry.Key)`t$($entry.Category)`t$($entry.Name)`t$value`t$($entry.Default)`t$($entry.Kind)`t$($entry.Scope)`t$($entry.Description)")
    }
    $rows -join "`r`n"
}

function Save-PlayerConfig {
    $entry = Get-PlayerConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa cấu hình Player không hợp lệ: $ConfigKey" }
    $validated = Assert-PlayerConfigValue $entry $ConfigValue
    Set-PropertyValue -Path (Join-Path $Root "player.properties") -Key $entry.Key -Value $validated
    "OK`tĐã lưu $($entry.Name). Giới hạn runtime áp dụng trong tối đa 1 giây; giá trị khởi tạo chỉ áp dụng cho player mới."
}

function Reset-PlayerConfig {
    $entry = Get-PlayerConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa cấu hình Player không hợp lệ: $ConfigKey" }
    Set-PropertyValue -Path (Join-Path $Root "player.properties") -Key $entry.Key -Value "" -Remove
    "OK`tĐã đưa $($entry.Name) về mặc định $($entry.Default)."
}

function Get-PetConfigCatalog {
    $rows = New-Object System.Collections.Generic.List[object]
    $rows.Add([pscustomobject]@{ Key="pet.start.stamina"; Category="Khởi tạo - Chung"; Name="Thể lực ban đầu"; Default="1000"; Kind="stamina"; Scope="Đệ tử mới"; Description="Thể lực hiện tại và tối đa khi tạo hoặc thay đệ tử." })
    $rows.Add([pscustomobject]@{ Key="pet.start.limitPower"; Category="Khởi tạo - Chung"; Name="Cấp giới hạn ban đầu"; Default="0"; Kind="limit-level"; Scope="Đệ tử mới"; Description="Áp dụng nếu nơi tạo đệ tử không truyền cấp giới hạn cũ." })

    $types = @(
        @{ Key="normal"; Name="Đệ tử thường"; Power="2000"; HpMin="800"; HpMax="2100"; MpMin="800"; MpMax="2100"; DamageMin="20"; DamageMax="45" },
        @{ Key="mabu"; Name="Mabư"; Power="1500000"; HpMin="800"; HpMax="2100"; MpMin="800"; MpMax="2100"; DamageMin="50"; DamageMax="120" },
        @{ Key="uub"; Name="Uub"; Power="40000000000"; HpMin="400000"; HpMax="400000"; MpMin="400000"; MpMax="400000"; DamageMin="20000"; DamageMax="20000" },
        @{ Key="kidbeer"; Name="Kid Beer"; Power="40000000000"; HpMin="400000"; HpMax="400000"; MpMin="400000"; MpMax="400000"; DamageMin="20000"; DamageMax="20000" },
        @{ Key="jiren"; Name="Kid Jiren"; Power="40000000000"; HpMin="400000"; HpMax="400000"; MpMin="400000"; MpMax="400000"; DamageMin="20000"; DamageMax="20000" }
    )
    foreach ($type in $types) {
        $category = "Khởi tạo - $($type.Name)"
        $prefix = "pet.start.$($type.Key)"
        $rows.Add([pscustomobject]@{ Key="$prefix.power"; Category=$category; Name="Sức mạnh ban đầu"; Default=$type.Power; Kind="long"; Scope="Đệ tử mới"; Description="Sức mạnh khi tạo loại $($type.Name)." })
        foreach ($stat in @(
            @{ Suffix="hpMin"; Name="HP gốc nhỏ nhất"; Default=$type.HpMin }, @{ Suffix="hpMax"; Name="HP gốc lớn nhất"; Default=$type.HpMax },
            @{ Suffix="mpMin"; Name="KI gốc nhỏ nhất"; Default=$type.MpMin }, @{ Suffix="mpMax"; Name="KI gốc lớn nhất"; Default=$type.MpMax },
            @{ Suffix="damageMin"; Name="Sức đánh nhỏ nhất"; Default=$type.DamageMin }, @{ Suffix="damageMax"; Name="Sức đánh lớn nhất"; Default=$type.DamageMax },
            @{ Suffix="defenseMin"; Name="Giáp nhỏ nhất"; Default="9" }, @{ Suffix="defenseMax"; Name="Giáp lớn nhất"; Default="50" },
            @{ Suffix="criticalMin"; Name="Chí mạng nhỏ nhất"; Default="0" }, @{ Suffix="criticalMax"; Name="Chí mạng lớn nhất"; Default="2" }
        )) {
            $rows.Add([pscustomobject]@{ Key="$prefix.$($stat.Suffix)"; Category=$category; Name=$stat.Name; Default=$stat.Default; Kind="int"; Scope="Đệ tử mới"; Description="Khoảng random chỉ số khi tạo loại $($type.Name); giá trị Max không được nhỏ hơn Min." })
        }
    }

    foreach ($entry in @(
        @{ Key="pet.skill.autoUnlock"; Category="Kỹ năng"; Name="Tự mở kỹ năng khi đủ sức mạnh"; Default="true"; Kind="bool"; Scope="Runtime"; Description="Nếu tắt, hệ thống không tự mở skill 2-5 theo sức mạnh." },
        @{ Key="pet.skill.unlockPower"; Category="Kỹ năng"; Name="Mốc mở skill 2-5"; Default="150000000,1500000000,20000000000,40000000000"; Kind="long-list-4"; Scope="Runtime"; Description="Đúng 4 số tăng dần tương ứng skill 2, 3, 4 và 5." },
        @{ Key="pet.skill.slot2Pool"; Category="Kỹ năng"; Name="Danh sách kỹ năng ô 2"; Default="1,3,5"; Kind="skill-pool"; Scope="Lần mở/đổi skill mới"; Description="Chọn bằng checklist; mọi kỹ năng được chọn có tỉ lệ xuất hiện cân bằng." },
        @{ Key="pet.skill.slot3Pool"; Category="Kỹ năng"; Name="Danh sách kỹ năng ô 3"; Default="6,8,9"; Kind="skill-pool"; Scope="Lần mở/đổi skill mới"; Description="Chọn bằng checklist; mọi kỹ năng được chọn có tỉ lệ xuất hiện cân bằng." },
        @{ Key="pet.skill.slot4Pool"; Category="Kỹ năng"; Name="Danh sách kỹ năng ô 4"; Default="13,12,19"; Kind="skill-pool"; Scope="Lần mở/đổi skill mới"; Description="Chọn bằng checklist; mọi kỹ năng được chọn có tỉ lệ xuất hiện cân bằng." },
        @{ Key="pet.skill.slot5Pool"; Category="Kỹ năng"; Name="Danh sách kỹ năng ô 5"; Default="24,26,25"; Kind="skill-pool"; Scope="Lần mở/đổi skill mới"; Description="Chọn bằng checklist; mọi tuyệt kỹ được chọn có tỉ lệ xuất hiện cân bằng." },
        @{ Key="pet.skill.maxLevel"; Category="Kỹ năng"; Name="Cấp skill tối đa"; Default="7"; Kind="skill-level"; Scope="Runtime"; Description="Giới hạn nâng skill đệ tử, từ 1 đến 7." },
        @{ Key="pet.skill.attackCooldownMs"; Category="Kỹ năng"; Name="Hồi chiêu chưởng (ms)"; Default="1000"; Kind="milliseconds"; Scope="Skill mới/đăng nhập lại"; Description="Cooldown ép cho Kamejoko, Masenko và Antomic." },
        @{ Key="pet.skill.fifthAllowedTypes"; Category="Kỹ năng"; Name="Loại được mở skill 5"; Default="2,3,4"; Kind="type-list"; Scope="Runtime"; Description="typePet: 0 thường, 1 Mabư, 2 Uub, 3 Kid Beer, 4 Jiren." },

        @{ Key="pet.ai.attackRange"; Category="AI chiến đấu"; Name="Phạm vi tìm mục tiêu"; Default="300"; Kind="positive-int"; Scope="Runtime"; Description="Khoảng cách tối đa tìm mob hoặc người chơi." },
        @{ Key="pet.ai.meleeRange"; Category="AI chiến đấu"; Name="Khoảng cách đánh cận chiến"; Default="50"; Kind="positive-int"; Scope="Runtime"; Description="Trong khoảng này đệ tử ưu tiên skill 1." },
        @{ Key="pet.ai.followIdleDistance"; Category="AI chiến đấu"; Name="Khoảng cách đi theo khi Follow"; Default="60"; Kind="positive-int"; Scope="Runtime"; Description="Khoảng cách bám sư phụ ở trạng thái đi theo." },
        @{ Key="pet.ai.followCombatDistance"; Category="AI chiến đấu"; Name="Khoảng cách đi theo khi chiến đấu"; Default="500"; Kind="positive-int"; Scope="Runtime"; Description="Khoảng cách quay về sư phụ và giới hạn truy đuổi player." },
        @{ Key="pet.ai.reviveDelayMs"; Category="AI chiến đấu"; Name="Thời gian hồi sinh (ms)"; Default="120000"; Kind="milliseconds"; Scope="Runtime"; Description="Thời gian đệ tử nằm chết trước khi tự hồi sinh." },
        @{ Key="pet.ai.peaCooldownMs"; Category="AI chiến đấu"; Name="Thời gian xin đậu (ms)"; Default="10000"; Kind="milliseconds"; Scope="Runtime"; Description="Khoảng cách giữa hai lần xin hoặc dùng đậu." },
        @{ Key="pet.ai.regenThresholdPercent"; Category="AI chiến đấu"; Name="Ngưỡng dùng Tái Tạo Năng Lượng (%)"; Default="20"; Kind="percent"; Scope="Runtime"; Description="Dùng skill khi HP hoặc KI xuống bằng hay dưới ngưỡng." },
        @{ Key="pet.ai.regenChargeMin"; Category="AI chiến đấu"; Name="Số nhịp vận công nhỏ nhất"; Default="3"; Kind="positive-int"; Scope="Runtime"; Description="Khoảng nhịp duy trì Tái Tạo Năng Lượng." },
        @{ Key="pet.ai.regenChargeMax"; Category="AI chiến đấu"; Name="Số nhịp vận công lớn nhất"; Default="5"; Kind="positive-int"; Scope="Runtime"; Description="Không được nhỏ hơn số nhịp nhỏ nhất." },
        @{ Key="pet.ai.autoStatIntervalMs"; Category="AI tăng trưởng"; Name="Chu kỳ tự cộng chỉ số (ms)"; Default="0"; Kind="milliseconds"; Scope="Runtime"; Description="0 giữ hành vi cũ: thử cộng ở mỗi vòng update." },
        @{ Key="pet.ai.autoStatAttempts"; Category="AI tăng trưởng"; Name="Số lần thử cộng mỗi chu kỳ"; Default="20"; Kind="int"; Scope="Runtime"; Description="Đặt 0 để tắt tự phân phối tiềm năng." },
        @{ Key="pet.ai.playerAttackAllowedTypes"; Category="AI chiến đấu"; Name="Loại được đánh người chơi"; Default="2,4"; Kind="type-list"; Scope="Runtime"; Description="Danh sách typePet được phép chọn player làm mục tiêu." },
        @{ Key="pet.ai.hakaiEnabled"; Category="AI Hakai"; Name="Bật Hakai"; Default="true"; Kind="bool"; Scope="Runtime"; Description="Cho phép xử tử tức thời mục tiêu thỏa điều kiện." },
        @{ Key="pet.ai.hakaiRatePercent"; Category="AI Hakai"; Name="Tỉ lệ Hakai (%)"; Default="20"; Kind="percent"; Scope="Runtime"; Description="Tỉ lệ kiểm tra khi đệ tử bảo vệ gặp player địch." },
        @{ Key="pet.ai.hakaiMaxTargetHp"; Category="AI Hakai"; Name="HP mục tiêu tối đa"; Default="1000000000"; Kind="positive-int"; Scope="Runtime"; Description="Mục tiêu phải có HP hiện tại nhỏ hơn giá trị này." },
        @{ Key="pet.ai.hakaiAllowedTypes"; Category="AI Hakai"; Name="Loại được dùng Hakai"; Default="2,4"; Kind="type-list"; Scope="Runtime"; Description="Danh sách typePet được phép Hakai." },

        @{ Key="pet.equipment.minPower"; Category="Trang bị & đổi tên"; Name="Sức mạnh tối thiểu để mặc đồ"; Default="1500000"; Kind="long"; Scope="Runtime"; Description="Áp dụng cho trang bị thường và sách tuyệt kỹ." },
        @{ Key="pet.equipment.vipTypes"; Category="Trang bị & đổi tên"; Name="Loại được dùng đồ VIP"; Default="2,3,4"; Kind="type-list"; Scope="Runtime"; Description="Các loại được mặc item type 11 và 25." },
        @{ Key="pet.rename.itemId"; Category="Trang bị & đổi tên"; Name="Item đổi tên"; Default="400"; Kind="item-id"; Scope="Runtime"; Description="Template ID vật phẩm bị trừ khi dùng lệnh đặt tên." },
        @{ Key="pet.rename.maxLength"; Category="Trang bị & đổi tên"; Name="Độ dài tên tối đa"; Default="10"; Kind="positive-int"; Scope="Runtime"; Description="Số ký tự tối đa của tên đệ tử do người chơi đặt." },

        @{ Key="pet.mabuEgg.durationMs"; Category="Thu nhận & giới hạn"; Name="Thời gian nở trứng Mabư (ms)"; Default="864000000"; Kind="long"; Scope="Trứng mới"; Description="Mặc định 10 ngày; trứng đã tạo giữ thời gian cũ." },
        @{ Key="pet.exchange.optionId"; Category="Thu nhận & giới hạn"; Name="Option Kilis yêu cầu"; Default="250"; Kind="option-id"; Scope="Runtime"; Description="Option được kiểm tra trên vật phẩm đổi đệ đặc biệt." },
        @{ Key="pet.exchange.optionParam"; Category="Thu nhận & giới hạn"; Name="Số Kilis yêu cầu"; Default="3000"; Kind="int"; Scope="Runtime"; Description="Param tối thiểu của option Kilis." },
        @{ Key="pet.exchange.requiredType"; Category="Thu nhận & giới hạn"; Name="Loại đệ tử dùng để đổi"; Default="1"; Kind="pet-type"; Scope="Runtime"; Description="Mặc định yêu cầu Mabư typePet 1." },
        @{ Key="pet.exchange.requiredPower"; Category="Thu nhận & giới hạn"; Name="Sức mạnh yêu cầu để đổi"; Default="40000000000"; Kind="long"; Scope="Runtime"; Description="Sức mạnh tối thiểu của đệ tử hiện tại." },
        @{ Key="pet.exchange.typeWeights"; Category="Thu nhận & giới hạn"; Name="Trọng số Uub/Kid Beer/Jiren"; Default="1,1,1"; Kind="weights-3"; Scope="Lần đổi mới"; Description="Ba trọng số nhận type 2, 3 và 4." },
        @{ Key="pet.openPower.cost"; Category="Thu nhận & giới hạn"; Name="Giá mở giới hạn nhanh"; Default="50000000"; Kind="long"; Scope="Runtime"; Description="Số vàng trừ khi mở giới hạn cho đệ tử." },
        @{ Key="pet.openPower.kaioStartLevel"; Category="Thu nhận & giới hạn"; Name="Cấp bắt đầu mở tại Kaio"; Default="5"; Kind="limit-level-positive"; Scope="Runtime"; Description="Cấp thấp hơn mở tại Quốc Vương, từ cấp này mở tại Tổ Sư Kaio." },

        @{ Key="pet.fusion.durationMs"; Category="Hợp thể - Chung"; Name="Thời gian Lưỡng Long (ms)"; Default="600000"; Kind="positive-int"; Scope="Runtime/lần hợp thể mới"; Description="Thời lượng hợp thể có giới hạn thời gian." },
        @{ Key="pet.fusion.unfusionCooldownMs"; Category="Hợp thể - Chung"; Name="Chờ sau khi tách (ms)"; Default="5000"; Kind="milliseconds"; Scope="Runtime"; Description="Thời gian phải chờ trước khi hợp thể lại." },
        @{ Key="pet.fusion.contributionPercent"; Category="Hợp thể - Chung"; Name="Tỉ lệ cộng chỉ số đệ vào sư phụ (%)"; Default="100"; Kind="wide-percent"; Scope="Runtime"; Description="Áp dụng đồng thời cho HP, KI và sức đánh." }
    )) { $rows.Add([pscustomobject]$entry) }

    foreach ($type in $types) {
        $category = "Hợp thể - $($type.Name)"
        foreach ($stat in @(@{ Suffix="hp"; Name="HP" }, @{ Suffix="mp"; Name="KI" }, @{ Suffix="damage"; Name="Sức đánh" })) {
            $default = if ($type.Key -in @("uub", "kidbeer", "jiren")) { "20" } else { "0" }
            $rows.Add([pscustomobject]@{ Key="pet.fusion.$($type.Key).$($stat.Suffix)BonusPercent"; Category=$category; Name="Thưởng $($stat.Name) khi Porata (%)"; Default=$default; Kind="wide-percent"; Scope="Runtime"; Description="Tăng chỉ số của đệ trước khi cộng vào sư phụ." })
        }
    }
    $rows.ToArray()
}

function Get-PetConfigEntry {
    param([string]$Key)
    Get-PetConfigCatalog | Where-Object { $_.Key -eq $Key } | Select-Object -First 1
}

function Assert-PetConfigValue {
    param($Entry, [string]$Value)
    $Value = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Giá trị không được để trống." }
    if ($Entry.Kind -eq "bool") {
        if ($Value -notmatch '^(true|false|0|1)$') { throw "Giá trị phải là true, false, 1 hoặc 0." }
        return $Value.ToLowerInvariant()
    }
    if ($Entry.Kind -in @("int", "positive-int", "milliseconds", "stamina", "limit-level", "limit-level-positive", "skill-level", "percent", "wide-percent", "item-id", "option-id", "pet-type")) {
        if ($Value -notmatch '^\d+$' -or [decimal]$Value -gt 2147483647) { throw "Giá trị phải là số nguyên không âm trong giới hạn int." }
        $number = [long]$Value
        if ($Entry.Kind -eq "positive-int" -and $number -lt 1) { throw "Giá trị phải lớn hơn 0." }
        if ($Entry.Kind -eq "stamina" -and ($number -lt 1 -or $number -gt 32767)) { throw "Thể lực phải từ 1 đến 32.767." }
        if ($Entry.Kind -eq "limit-level" -and $number -gt 9) { throw "Cấp giới hạn phải từ 0 đến 9." }
        if ($Entry.Kind -eq "limit-level-positive" -and ($number -lt 1 -or $number -gt 9)) { throw "Cấp phải từ 1 đến 9." }
        if ($Entry.Kind -eq "skill-level" -and ($number -lt 1 -or $number -gt 7)) { throw "Cấp skill phải từ 1 đến 7." }
        if ($Entry.Kind -eq "percent" -and $number -gt 100) { throw "Tỉ lệ phải từ 0 đến 100." }
        if ($Entry.Kind -eq "wide-percent" -and $number -gt 10000) { throw "Tỉ lệ không được vượt 10.000%." }
        if ($Entry.Kind -in @("item-id", "option-id") -and $number -gt 32767) { throw "ID phải từ 0 đến 32.767." }
        if ($Entry.Kind -eq "pet-type" -and $number -gt 4) { throw "typePet phải từ 0 đến 4." }
    } elseif ($Entry.Kind -eq "long") {
        $parsed = 0L
        if (-not [long]::TryParse($Value, [ref]$parsed) -or $parsed -lt 0) { throw "Giá trị phải là số nguyên 64-bit không âm." }
    } elseif ($Entry.Kind -in @("weights-3", "long-list-4", "type-list", "skill-pool")) {
        $parts = @($Value -split ',')
        $expected = if ($Entry.Kind -eq "long-list-4") { 4 } elseif ($Entry.Kind -eq "weights-3") { 3 } else { 0 }
        if ($expected -gt 0 -and $parts.Count -ne $expected) { throw "Danh sách phải có đúng $expected giá trị." }
        if ($Entry.Kind -eq "type-list" -and $parts.Count -lt 1) { throw "Chọn ít nhất một loại đệ tử." }
        if ($Entry.Kind -eq "skill-pool" -and $parts.Count -lt 1) { throw "Chọn ít nhất một kỹ năng." }
        $numbers = New-Object System.Collections.Generic.List[decimal]
        foreach ($part in $parts) {
            $numberText = $part.Trim()
            if ($numberText -notmatch '^\d+$') { throw "Danh sách chỉ gồm số nguyên không âm, phân cách bằng dấu phẩy." }
            $number = [decimal]$numberText
            if ($Entry.Kind -eq "type-list" -and $number -gt 4) { throw "typePet chỉ từ 0 đến 4." }
            if ($Entry.Kind -eq "skill-pool" -and ($number -gt 26 -or $number -in @(15,16))) { throw "Skill template ID không hợp lệ." }
            $numbers.Add($number)
        }
        if ($Entry.Kind -eq "weights-3" -and ($numbers | Measure-Object -Sum).Sum -le 0) { throw "Tổng trọng số phải lớn hơn 0." }
        if ($Entry.Kind -eq "long-list-4") {
            for ($i = 1; $i -lt $numbers.Count; $i++) { if ($numbers[$i] -lt $numbers[$i - 1]) { throw "Mốc sau không được nhỏ hơn mốc trước." } }
        }
        if ($Entry.Kind -eq "type-list" -and @($numbers | Select-Object -Unique).Count -ne $numbers.Count) { throw "Danh sách typePet không được trùng." }
        if ($Entry.Kind -eq "skill-pool" -and @($numbers | Select-Object -Unique).Count -ne $numbers.Count) { throw "Danh sách kỹ năng không được trùng." }
        $Value = ($parts | ForEach-Object { $_.Trim() }) -join ','
    } else { throw "Kiểu cấu hình Đệ tử không được hỗ trợ: $($Entry.Kind)" }

    if ($Entry.Key -match '^(.*)(Min|Max)$') {
        $baseKey = $matches[1]
        $isMin = $matches[2] -eq "Min"
        $otherKey = $baseKey + $(if ($isMin) { "Max" } else { "Min" })
        $otherEntry = Get-PetConfigEntry $otherKey
        if ($null -ne $otherEntry) {
            $map = Get-PropertyMap (Join-Path $Root "pet.properties")
            $otherValue = if ($map.ContainsKey($otherKey)) { [decimal]$map[$otherKey] } else { [decimal]$otherEntry.Default }
            if ($isMin -and [decimal]$Value -gt $otherValue) { throw "Giá trị nhỏ nhất không được lớn hơn giá trị lớn nhất ($otherValue)." }
            if (-not $isMin -and [decimal]$Value -lt $otherValue) { throw "Giá trị lớn nhất không được nhỏ hơn giá trị nhỏ nhất ($otherValue)." }
        }
    }
    $Value
}

function List-PetConfig {
    $path = Join-Path $Root "pet.properties"
    $map = Get-PropertyMap $path
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("key`tcategory`tname`tvalue`tdefault`tkind`tscope`tdescription")
    foreach ($entry in (Get-PetConfigCatalog)) {
        $value = if ($map.ContainsKey($entry.Key)) { $map[$entry.Key] } else { $entry.Default }
        $rows.Add("$($entry.Key)`t$($entry.Category)`t$($entry.Name)`t$value`t$($entry.Default)`t$($entry.Kind)`t$($entry.Scope)`t$($entry.Description)")
    }
    $rows -join "`r`n"
}

function Save-PetConfig {
    $entry = Get-PetConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa cấu hình Đệ tử không hợp lệ: $ConfigKey" }
    $validated = Assert-PetConfigValue $entry $ConfigValue
    Set-PropertyValue -Path (Join-Path $Root "pet.properties") -Key $entry.Key -Value $validated
    "OK`tĐã lưu $($entry.Name). Runtime áp dụng trong tối đa 1 giây; giá trị khởi tạo chỉ áp dụng cho đệ tử mới."
}

function Reset-PetConfig {
    $entry = Get-PetConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khóa cấu hình Đệ tử không hợp lệ: $ConfigKey" }
    Set-PropertyValue -Path (Join-Path $Root "pet.properties") -Key $entry.Key -Value "" -Remove
    "OK`tĐã đưa $($entry.Name) về mặc định $($entry.Default)."
}

. (Join-Path $PSScriptRoot "admin_task_data.ps1")

function SqlString {
    param([string]$Value)
    if ($null -eq $Value) {
        return "''"
    }
    return "'" + $Value.Replace("\", "\\").Replace("'", "''") + "'"
}

function SqlInt {
    param(
        [string]$Value,
        [int]$Default = 0
    )
    if ($Value -match "^-?\d+$") {
        return [int]$Value
    }
    return $Default
}

function Find-MySql {
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $known = @(
        "C:\xampp\mysql\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MariaDB 10.4\bin\mysql.exe"
    )
    foreach ($path in $known) {
        if (Test-Path $path) {
            return $path
        }
    }

    throw "Không tìm thấy mysql.exe. Hãy cài MySQL client hoặc thêm mysql.exe vào PATH."
}

function Invoke-MySql {
    param([string]$Sql)

    $config = Get-ConfigMap
    $mysql = Find-MySql
    $hostName = if ($config["database.host"]) { $config["database.host"] } else { "localhost" }
    $port = if ($config["database.port"]) { $config["database.port"] } else { "3306" }
    $dbName = if ($config["database.name"]) { $config["database.name"] } else { "team2026" }
    $user = if ($config["database.user"]) { $config["database.user"] } else { "root" }
    $pass = if ($config.ContainsKey("database.pass")) { $config["database.pass"] } else { "" }

    $args = @(
        "--batch",
        "--raw",
        "--default-character-set=utf8mb4",
        "-h", $hostName,
        "-P", $port,
        "-u", $user
    )
    if (-not [string]::IsNullOrEmpty($pass)) {
        $args += "--password=$pass"
    }
    $args += $dbName

    $tempSql = Join-Path $env:TEMP ("nro_admin_sql_{0}_{1}.sql" -f $PID, ([Guid]::NewGuid().ToString("N")))
    [System.IO.File]::WriteAllText($tempSql, $Sql, $Utf8NoBom)
    $sourcePath = $tempSql.Replace("\", "/")
    $args += "--execute=source $sourcePath"

    try {
        $output = & $mysql @args 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw ($output -join [Environment]::NewLine)
        }
        return ($output -join [Environment]::NewLine)
    }
    finally {
        if (Test-Path $tempSql) {
            Remove-Item -LiteralPath $tempSql -Force -ErrorAction SilentlyContinue
        }
    }
}

function Get-MySqlScalar {
    param([string]$Sql, [string]$Default = "0")
    $raw = Invoke-MySql $Sql
    $lines = @($raw -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { return $Default }
    [string]$lines[-1]
}

function Get-EventRuntimeState {
    $running = $false
    $restartPending = $false
    $startedAt = ""
    $pidPath = Join-Path $Root "logs\server.pid"
    if (Test-Path $pidPath) {
        $serverPid = (Get-Content -LiteralPath $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ([string]$serverPid -match '^\d+$') {
            $process = Get-Process -Id ([int]$serverPid) -ErrorAction SilentlyContinue
            if ($null -ne $process) {
                $running = $true
                try {
                    $startedAt = $process.StartTime.ToString("yyyy-MM-dd HH:mm:ss")
                    $configInfo = Get-Item (Join-Path $Root "Config.properties") -ErrorAction SilentlyContinue
                    if ($null -ne $configInfo) { $restartPending = $configInfo.LastWriteTime -gt $process.StartTime }
                    if (-not $restartPending) {
                        $eventTableCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('admin_event_config','admin_event_boss','admin_event_item');"
                        if ($eventTableCount -eq '3') {
                            $eventUpdatedText = Get-MySqlScalar "SELECT DATE_FORMAT(MAX(updated_at),'%Y-%m-%d %H:%i:%s') FROM (SELECT updated_at FROM admin_event_config UNION ALL SELECT updated_at FROM admin_event_boss UNION ALL SELECT updated_at FROM admin_event_item) event_updates;" ""
                            $eventUpdated = [datetime]::MinValue
                            if ([datetime]::TryParseExact($eventUpdatedText, 'yyyy-MM-dd HH:mm:ss', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeLocal, [ref]$eventUpdated)) {
                                $restartPending = $eventUpdated -gt $process.StartTime
                            }
                        }
                    }
                } catch { }
            }
        }
    }
    [pscustomobject]@{ Running=$running; RestartPending=$restartPending; StartedAt=$startedAt }
}

function Normalize-EventValue {
    param([string]$Value)
    $aliases = @{
        "tet"="lunar_new_year"; "international_womens_day"="womens_day"; "8_3"="womens_day";
        "noel"="christmas"; "hung_vuong"="hungvuong"; "trung_thu"="trungthu";
        "top_up"="topup"; "he"="summer"; "mua_he"="summer"
    }
    $allowed = @("lunar_new_year","womens_day","halloween","christmas","hungvuong","trungthu","summer","topup")
    $selected = New-Object System.Collections.Generic.List[string]
    foreach ($raw in @($Value -split ',')) {
        $code = ([string]$raw).Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($code)) { continue }
        if ($aliases.ContainsKey($code)) { $code = $aliases[$code] }
        if ($code -eq "none") { continue }
        if ($code -eq "all") { return "all" }
        if ($allowed -notcontains $code) { throw "Mã sự kiện không hợp lệ: $code" }
        if (-not $selected.Contains($code)) { $selected.Add($code) }
    }
    if ($selected.Count -eq 0) { return "none" }
    ($allowed | Where-Object { $selected.Contains($_) }) -join ','
}

function Assert-EventCode {
    param([string]$Value)
    $normalized = Normalize-EventValue $Value
    if ($normalized -eq "none" -or $normalized -eq "all" -or $normalized.Contains(',')) {
        throw "Hãy chọn đúng một sự kiện để cấu hình."
    }
    $normalized
}

function Ensure-EventConfigSchema {
    $needsDefaultSeed = (Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_event_config';") -ne '1'
    $sql = @"
CREATE TABLE IF NOT EXISTS admin_event_config (
  event_code VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  point_multiplier DECIMAL(7,3) NOT NULL DEFAULT 1.000,
  drop_multiplier DECIMAL(7,3) NOT NULL DEFAULT 1.000,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_event_boss (
  event_code VARCHAR(64) NOT NULL,
  boss_id INT NOT NULL,
  boss_name VARCHAR(100) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  notes VARCHAR(500) NOT NULL DEFAULT '',
  map_ids_json TEXT NOT NULL DEFAULT ('[]'),
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_code,boss_id),
  KEY idx_admin_event_boss_enabled (event_code,enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE admin_event_boss ADD COLUMN IF NOT EXISTS map_ids_json TEXT NOT NULL DEFAULT ('[]') AFTER notes;

CREATE TABLE IF NOT EXISTS admin_event_item (
  id INT NOT NULL AUTO_INCREMENT,
  event_code VARCHAR(64) NOT NULL,
  item_id INT NOT NULL,
  source_type VARCHAR(16) NOT NULL DEFAULT 'mob',
  source_id INT NOT NULL DEFAULT -1,
  quantity_min INT NOT NULL DEFAULT 1,
  quantity_max INT NOT NULL DEFAULT 1,
  drop_rate DECIMAL(7,4) NOT NULL DEFAULT 100.0000,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  options_json TEXT NOT NULL,
  notes VARCHAR(500) NOT NULL DEFAULT '',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_event_item_source (event_code,item_id,source_type,source_id),
  KEY idx_admin_event_item_event (event_code,enabled),
  KEY idx_admin_event_item_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_event_point_grant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_code VARCHAR(64) NOT NULL,
  player_id BIGINT NOT NULL,
  player_name VARCHAR(100) NOT NULL,
  points INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  result_message VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_admin_event_grant_status (status,id),
  KEY idx_admin_event_grant_player (player_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ADMIN_EVENT_DEFAULT_SEED
INSERT IGNORE INTO admin_event_config (event_code,display_name,description) VALUES
('lunar_new_year','Tết Nguyên Đán','NPC Đường Tăng và boss Lân con.'),
('womens_day','Quốc tế Phụ nữ 8/3','Buff chỉ số và hoạt động ngày 8/3.'),
('halloween','Halloween','Boss Bí ma, Ma trơi và Dơi.'),
('christmas','Giáng Sinh','Boss Ông già Noel và hộp quà Giáng Sinh.'),
('hungvuong','Giỗ Tổ Hùng Vương','Boss Thủy Tinh, Sơn Tinh, NPC Hùng Vương và Nồi bánh.'),
('trungthu','Trung Thu','Boss Khỉ đột, Nguyệt thần và Nhật thần.'),
('summer','Mùa Hè','NPC mùa hè và nguyên liệu rơi trên toàn bản đồ.'),
('topup','TopUp','Khung cấu hình cho sự kiện nạp thẻ.');

INSERT IGNORE INTO admin_event_boss (event_code,boss_id,boss_name,quantity,enabled,notes) VALUES
('lunar_new_year',-371,'Lân con',10,1,'Boss ID runtime được random từ ID gốc -371.'),
('halloween',-351,'Bí ma',10,1,''),
('halloween',-349,'Ma trơi',10,1,''),
('halloween',-350,'Dơi',10,1,''),
('christmas',-353,'Ông già Noel',30,1,''),
('hungvuong',-355,'Thủy Tinh',10,1,'Sơn Tinh xuất hiện cùng boss.'),
('trungthu',-344,'Khỉ đột',10,1,''),
('trungthu',-345,'Nguyệt thần',10,1,'Nhật thần xuất hiện cùng boss.');

INSERT IGNORE INTO admin_event_item (event_code,item_id,source_type,source_id,quantity_min,quantity_max,drop_rate,enabled,options_json,notes) VALUES
('halloween',585,'built_in',-1,1,1,0,1,'[]','Bí ngô đang rơi theo code boss Halloween.'),
('christmas',648,'built_in',-1,1,1,0,1,'[]','Hộp quà đang rơi theo code boss Noel.'),
('hungvuong',1546,'built_in',-1,1,1,0,1,'[]','Nguyên liệu đang rơi theo code gameplay.'),
('hungvuong',1220,'built_in',-1,1,1,0,1,'[]','Lễ vật đang rơi theo code boss.'),
('hungvuong',1221,'built_in',-1,1,1,0,1,'[]','Lễ vật đang rơi theo code boss.'),
('hungvuong',1222,'built_in',-1,1,1,0,1,'[]','Lễ vật đang rơi theo code boss.'),
('trungthu',1045,'built_in',-1,1,1,0,1,'[]','Vật phẩm đang rơi theo code boss.'),
('trungthu',890,'built_in',-1,1,1,0,1,'[]','Vật phẩm đang rơi theo code boss.'),
('trungthu',891,'built_in',-1,1,1,0,1,'[]','Vật phẩm đang rơi theo code boss.'),
('summer',1798,'built_in',-1,1,1,0,1,'[]','Nguyên liệu đang rơi theo code mùa hè.'),
('summer',1799,'built_in',-1,1,1,0,1,'[]','Nguyên liệu đang rơi theo code mùa hè.'),
('summer',1800,'built_in',-1,1,1,0,1,'[]','Nguyên liệu đang rơi theo code mùa hè.'),
('summer',1612,'built_in',-1,1,1,0,1,'[]','Nguyên liệu đang rơi theo code mùa hè.'),
('summer',1801,'built_in',-1,1,1,0,1,'[]','Vật phẩm đang rơi theo code mùa hè.'),
('summer',1802,'built_in',-1,1,1,0,1,'[]','Vật phẩm đang rơi theo code mùa hè.');
"@
    if (-not $needsDefaultSeed) {
        $seedIndex = $sql.IndexOf('-- ADMIN_EVENT_DEFAULT_SEED')
        if ($seedIndex -ge 0) { $sql = $sql.Substring(0, $seedIndex) }
    }
    Invoke-MySql $sql | Out-Null
}

function Assert-DecimalRange {
    param([string]$Value, [string]$Label, [double]$Minimum, [double]$Maximum)
    $number = 0D
    if (-not [double]::TryParse($Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$number) -or $number -lt $Minimum -or $number -gt $Maximum) {
        throw "$Label phải từ $Minimum đến $Maximum, dùng dấu chấm cho số lẻ."
    }
    $number.ToString("0.####", [Globalization.CultureInfo]::InvariantCulture)
}

function Get-EventProfile {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    Invoke-MySql "SELECT event_code,display_name,REPLACE(REPLACE(REPLACE(description,CHAR(13),' '),CHAR(10),' '),CHAR(9),' '),point_multiplier,drop_multiplier,DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS updated_at FROM admin_event_config WHERE event_code=$(SqlString $code) LIMIT 1;"
}

function List-EventBosses {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    Invoke-MySql "SELECT boss_id,boss_name,quantity,enabled,REPLACE(REPLACE(REPLACE(notes,CHAR(13),' '),CHAR(10),' '),CHAR(9),' '),COALESCE(NULLIF(map_ids_json,''),'[]') FROM admin_event_boss WHERE event_code=$(SqlString $code) ORDER BY enabled DESC,boss_name,boss_id;"
}

function List-EventItems {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    Invoke-MySql "SELECT e.id,e.item_id,COALESCE(i.NAME,CONCAT('Item ',e.item_id)) AS item_name,e.source_type,e.source_id,e.quantity_min,e.quantity_max,e.drop_rate,e.enabled,REPLACE(REPLACE(REPLACE(e.notes,CHAR(13),' '),CHAR(10),' '),CHAR(9),' '),COALESCE(e.options_json,'[]') FROM admin_event_item e LEFT JOIN item_template i ON i.id=e.item_id WHERE e.event_code=$(SqlString $code) ORDER BY e.enabled DESC,e.source_type,e.id;"
}

function Save-EventProfile {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    $drop = Assert-DecimalRange $DropMultiplier "Hệ số drop" 0 100
    if ($Description.Length -gt 500) { throw "Mô tả sự kiện tối đa 500 ký tự." }
    Invoke-MySql "UPDATE admin_event_config SET description=$(SqlString $Description),point_multiplier=1,drop_multiplier=$drop WHERE event_code=$(SqlString $code);" | Out-Null
    "OK`tĐã lưu cấu hình $code. Restart server để áp dụng."
}

function Save-EventBoss {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    $boss = SqlInt $BossId
    if ($boss -eq 0) { throw "Boss ID không được bằng 0." }
    $bossCatalogEntry = Get-ExistingBossCatalog | Where-Object { $_.Id -eq $boss } | Select-Object -First 1
    if ($null -eq $bossCatalogEntry) { throw "Boss ID $boss không tồn tại trong BossManager." }
    $quantity = Assert-PositiveInt $BossQuantity "Số lượng boss" 500
    $enabledValue = Assert-BoolValue $Enabled "Trạng thái boss"
    if ([string]::IsNullOrWhiteSpace($Name)) { $Name = [string]$bossCatalogEntry.Name }
    try { $parsedMapIds = $MapIdsJson | ConvertFrom-Json; $mapIds = @($parsedMapIds) } catch { throw "Danh sách map không phải JSON hợp lệ." }
    $validatedMapIds = New-Object System.Collections.Generic.List[int]
    foreach ($configuredMapId in $mapIds) {
        if ([string]$configuredMapId -notmatch '^\d+$') { throw "ID map phải là số nguyên không âm." }
        $mapNumber = [int]$configuredMapId
        if ((Get-MySqlScalar "SELECT COUNT(*) FROM map_template WHERE id=$mapNumber AND zones>0;") -ne '1') { throw "Map ID $mapNumber không tồn tại hoặc không có zone để boss xuất hiện." }
        if (-not $validatedMapIds.Contains($mapNumber)) { $validatedMapIds.Add($mapNumber) }
    }
    $mapIdsValue = ConvertTo-Json -InputObject $validatedMapIds.ToArray() -Compress
    if ([string]::IsNullOrWhiteSpace($mapIdsValue)) { $mapIdsValue = '[]' }
    Invoke-MySql "INSERT INTO admin_event_boss (event_code,boss_id,boss_name,quantity,enabled,notes,map_ids_json) VALUES ($(SqlString $code),$boss,$(SqlString $Name.Trim()),$quantity,$enabledValue,$(SqlString $Notes),$(SqlString $mapIdsValue)) ON DUPLICATE KEY UPDATE boss_name=VALUES(boss_name),quantity=VALUES(quantity),enabled=VALUES(enabled),notes=VALUES(notes),map_ids_json=VALUES(map_ids_json);" | Out-Null
    "OK`tĐã gắn Boss $boss vào $code. Restart server để áp dụng."
}

function Delete-EventBoss {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    $boss = SqlInt $BossId
    Invoke-MySql "DELETE FROM admin_event_boss WHERE event_code=$(SqlString $code) AND boss_id=$boss;" | Out-Null
    "OK`tĐã xóa cấu hình Boss $boss khỏi $code. Restart server để áp dụng."
}

function Save-EventItem {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    $itemId = Assert-PositiveInt $TemplateId "Item ID" 32767
    $source = $SourceType.Trim().ToLowerInvariant()
    if ($source -notin @('mob','boss','gameplay','reward','built_in')) { throw "Nguồn vật phẩm không hợp lệ." }
    $sourceValue = SqlInt $SourceId
    $min = Assert-PositiveInt $QuantityMin "Số lượng min" 30000
    $max = Assert-PositiveInt $QuantityMax "Số lượng max" 30000
    if ($min -gt $max) { throw "Số lượng min không được lớn hơn max." }
    $rate = Assert-DecimalRange $DropRate "Tỉ lệ drop" 0 100
    $enabledValue = Assert-BoolValue $Enabled "Trạng thái vật phẩm"
    if ((Get-MySqlScalar "SELECT COUNT(*) FROM item_template WHERE id=$itemId;") -ne '1') { throw "Không tìm thấy Item ID $itemId." }
    $optionsPayload = [ordered]@{ useDefaultOptions = $false; options = @() }
    if (-not [string]::IsNullOrWhiteSpace($OptionsJson)) {
        try { $rawOptions = $OptionsJson | ConvertFrom-Json } catch { throw "Option vật phẩm sự kiện không đúng JSON." }
        $useDefault = ([string]$rawOptions.useDefaultOptions -eq '1' -or [string]$rawOptions.useDefaultOptions -eq 'true')
        $rawList = if ($null -ne $rawOptions.options) { @($rawOptions.options) } elseif ($rawOptions -is [array]) { @($rawOptions) } else { @() }
        $seenOptions = @{}; $normalizedOptions = New-Object System.Collections.Generic.List[object]
        foreach ($rawOption in $rawList) {
            $optionIdText = [string]$rawOption.id; $paramText = [string]$rawOption.param
            if ($optionIdText -notmatch '^\d+$' -or $paramText -notmatch '^-?\d+$') { throw "Option sự kiện phải có ID và param là số nguyên." }
            if ($seenOptions.ContainsKey($optionIdText)) { throw "Option ID $optionIdText bị trùng." }
            $seenOptions[$optionIdText] = $true
            $normalizedOptions.Add([ordered]@{ id=[int]$optionIdText; param=[int]$paramText })
        }
        $optionsPayload = [ordered]@{ useDefaultOptions = $useDefault; options = $normalizedOptions.ToArray() }
    }
    $optionsSql = SqlString (ConvertTo-Json -InputObject $optionsPayload -Compress -Depth 6)
    $rowId = SqlInt $Id
    if ($rowId -gt 0) {
        Invoke-MySql "UPDATE admin_event_item SET item_id=$itemId,source_type=$(SqlString $source),source_id=$sourceValue,quantity_min=$min,quantity_max=$max,drop_rate=$rate,enabled=$enabledValue,options_json=$optionsSql,notes=$(SqlString $Notes) WHERE id=$rowId AND event_code=$(SqlString $code);" | Out-Null
    } else {
        Invoke-MySql "INSERT INTO admin_event_item (event_code,item_id,source_type,source_id,quantity_min,quantity_max,drop_rate,enabled,options_json,notes) VALUES ($(SqlString $code),$itemId,$(SqlString $source),$sourceValue,$min,$max,$rate,$enabledValue,$optionsSql,$(SqlString $Notes)) ON DUPLICATE KEY UPDATE quantity_min=VALUES(quantity_min),quantity_max=VALUES(quantity_max),drop_rate=VALUES(drop_rate),enabled=VALUES(enabled),options_json=VALUES(options_json),notes=VALUES(notes);" | Out-Null
    }
    "OK`tĐã gắn Item $itemId vào $code. Nguồn mob/boss có hiệu lực sau khi restart."
}

function Delete-EventItem {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    $rowId = Assert-PositiveInt $Id "Dòng vật phẩm" 2147483647
    Invoke-MySql "DELETE FROM admin_event_item WHERE id=$rowId AND event_code=$(SqlString $code);" | Out-Null
    "OK`tĐã xóa vật phẩm sự kiện ID $rowId. Restart server để áp dụng."
}

function Search-EventPlayers {
    Ensure-EventConfigSchema
    Ensure-PlayerOnlineSchema
    if ([string]::IsNullOrWhiteSpace($Search)) { throw "Nhập ID hoặc tên player cần tìm." }
    $safe = $Search.Trim().Replace("\", "\\").Replace("'", "''")
    $where = if ($Search.Trim() -match '^\d+$') {
        "p.id=$(SqlInt $Search.Trim()) OR p.name LIKE '%$safe%'"
    } else {
        "p.name LIKE '%$safe%'"
    }
    Invoke-MySql "SELECT p.id,p.name,COALESCE(a.username,''),p.event_point,CASE WHEN apo.last_seen >= DATE_SUB(NOW(), INTERVAL 20 SECOND) THEN 'ONLINE' WHEN a.last_time_login>a.last_time_logout THEN 'ONLINE?' ELSE 'OFFLINE' END AS online_state FROM player p LEFT JOIN account a ON a.id=p.account_id LEFT JOIN admin_player_online apo ON apo.player_id=p.id WHERE $where ORDER BY CASE WHEN p.name=$(SqlString $Search.Trim()) THEN 0 ELSE 1 END,p.id DESC LIMIT 20;"
}

function List-EventPointGrants {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    Invoke-MySql "SELECT id,player_id,player_name,points,status,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s'),COALESCE(DATE_FORMAT(processed_at,'%Y-%m-%d %H:%i:%s'),''),result_message FROM admin_event_point_grant WHERE event_code=$(SqlString $code) ORDER BY id DESC LIMIT 30;"
}

function Grant-EventPoint {
    Ensure-EventConfigSchema
    $code = Assert-EventCode $EventValue
    if ([string]::IsNullOrWhiteSpace($Search)) { throw "Nhập ID hoặc tên chính xác của player." }
    $pointValue = Assert-PositiveInt $Points "Số điểm cộng" 1000000000
    $target = $Search.Trim()
    $where = if ($target -match '^\d+$') { "p.id=$(SqlInt $target)" } else { "p.name=$(SqlString $target)" }
    $playerText = Invoke-MySql "SELECT p.id,p.name,p.event_point FROM player p WHERE $where LIMIT 2;"
    $playerLines = @($playerText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($playerLines.Count -lt 2) { throw "Không tìm thấy player '$target'." }
    if ($playerLines.Count -gt 2) { throw "Có nhiều player trùng thông tin; hãy dùng Player ID." }
    $player = $playerLines[1] -split "`t", 3
    $runtime = Get-EventRuntimeState
    if (-not $runtime.Running) {
        Invoke-MySql "START TRANSACTION; UPDATE player SET event_point=event_point+$pointValue WHERE id=$($player[0]); INSERT INTO admin_event_point_grant (event_code,player_id,player_name,points,status,result_message,processed_at) VALUES ($(SqlString $code),$($player[0]),$(SqlString $player[1]),$pointValue,'processed','Đã cộng trực tiếp khi server dừng',NOW()); COMMIT;" | Out-Null
        $result = "OK`tĐã cộng $pointValue điểm cho $($player[1]) (ID $($player[0])). Tổng mới: $([long]$player[2] + $pointValue)."
        Write-AuditEntry -ActionName "granteventpoint" -Summary "Cộng $pointValue điểm sự kiện cho $($player[1]) (ID $($player[0]))" -Status "success" -Reversible $false -Payload "" -ResultMessage $result
        return $result
    }
    $grantId = Get-MySqlScalar "INSERT INTO admin_event_point_grant (event_code,player_id,player_name,points) VALUES ($(SqlString $code),$($player[0]),$(SqlString $player[1]),$pointValue); SELECT LAST_INSERT_ID();"
    $statusText = "pending`t"
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        Start-Sleep -Milliseconds 350
        $statusText = Get-MySqlScalar "SELECT CONCAT(status,CHAR(9),result_message) FROM admin_event_point_grant WHERE id=$(SqlInt $grantId);" "pending`t"
        if (-not $statusText.StartsWith('pending')) { break }
    }
    if ($statusText.StartsWith('failed')) { throw "Không thể cộng điểm: $($statusText -replace '^failed\t','')" }
    $result = if ($statusText.StartsWith('processed')) {
        "OK`tĐã cộng $pointValue điểm cho $($player[1]) (ID $($player[0]))."
    } else {
        "OK`tĐã xếp hàng cộng $pointValue điểm cho $($player[1]) (ID $($player[0]))."
    }
    Write-AuditEntry -ActionName "granteventpoint" -Summary "Cộng $pointValue điểm sự kiện cho $($player[1]) (ID $($player[0]))" -Status "success" -Reversible $false -Payload "" -ResultMessage $result
    $result
}

function List-Events {
    Ensure-EventConfigSchema
    $config = Get-ConfigMap
    $configured = Normalize-EventValue ([string]$config["server.event"])
    $selected = @($configured -split ',')
    $runtime = Get-EventRuntimeState
    $eventTableExists = (Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='event';") -eq "1"
    $eventRowExists = $false
    if ($eventTableExists) {
        $eventRowExists = (Get-MySqlScalar "SELECT COUNT(*) FROM event WHERE name='international_womens_day';") -eq "1"
    }
    $profileRows = Invoke-MySql @"
SELECT c.event_code,c.point_multiplier,c.drop_multiplier,
       COALESCE((SELECT SUM(b.quantity) FROM admin_event_boss b WHERE b.event_code=c.event_code AND b.enabled=1),0) AS boss_count,
       COALESCE((SELECT COUNT(*) FROM admin_event_item i WHERE i.event_code=c.event_code),0) AS item_count
FROM admin_event_config c ORDER BY c.event_code;
"@
    $profiles = @{}
    foreach ($line in @($profileRows -split "`r?`n" | Select-Object -Skip 1)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $cells = $line -split "`t"
        $profiles[$cells[0]] = [pscustomobject]@{ Point=$cells[1]; Drop=$cells[2]; Bosses=$cells[3]; Items=$cells[4] }
    }
    $defs = @(
        [pscustomobject]@{Code="lunar_new_year"; Name="Tết Nguyên Đán"; Bosses=10; Components="NPC Đường Tăng map 0; 10 Lân con"; Health="warning"; Message="Boss Lân con chưa có phần thưởng trực tiếp."},
        [pscustomobject]@{Code="womens_day"; Name="Quốc tế Phụ nữ 8/3"; Bosses=0; Components="Buff chỉ số lưu trong bảng event"; Health=$(if ($eventTableExists -and $eventRowExists) {"warning"} else {"error"}); Message=$(if ($eventTableExists -and $eventRowExists) {"Dữ liệu hợp lệ; cơ chế áp buff cần được bổ sung theo thiết kế gameplay."} else {"Thiếu bảng hoặc dòng international_womens_day."})},
        [pscustomobject]@{Code="halloween"; Name="Halloween"; Bosses=30; Components="10 Bí ma; 10 Ma trơi; 10 Dơi"; Health="ready"; Message="Drop Bí ngô ID 585 hợp lệ."},
        [pscustomobject]@{Code="christmas"; Name="Giáng Sinh"; Bosses=30; Components="30 Ông già Noel"; Health="ready"; Message="Drop Hộp quà Giáng Sinh ID 648 hợp lệ."},
        [pscustomobject]@{Code="hungvuong"; Name="Giỗ Tổ Hùng Vương"; Bosses=20; Components="10 Thủy Tinh; 10 Sơn Tinh; NPC Hùng Vương và Nồi bánh"; Health="ready"; Message="Nguyên liệu rơi từ quái và boss khi event được bật."},
        [pscustomobject]@{Code="trungthu"; Name="Trung Thu"; Bosses=30; Components="10 Khỉ đột; 10 Nguyệt thần; 10 Nhật thần"; Health="ready"; Message="Drop dùng item 1045, 890 và 891."},
        [pscustomobject]@{Code="summer"; Name="Mùa Hè"; Bosses=0; Components="Chi Chi; Xe nước mía; drop nguyên liệu toàn map"; Health="ready"; Message="Drop và NPC được khóa theo trạng thái event."},
        [pscustomobject]@{Code="topup"; Name="TopUp"; Bosses=0; Components="Chưa có gameplay trong class TopUp"; Health="warning"; Message="Chỉ nên bật khi đã bổ sung cơ chế TopUp."}
    )
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("code`tname`tenabled`tstate`thealth`thealth_message`tboss_count`tcomponents`tpoint_multiplier`tdrop_multiplier`titem_count")
    foreach ($def in $defs) {
        $enabled = $configured -eq "all" -or $selected -contains $def.Code
        $state = "off"
        if ($enabled) {
            if ($runtime.RestartPending) { $state = "pending" }
            elseif ($runtime.Running) { $state = "active" }
            else { $state = "configured" }
        }
        $profile = $profiles[$def.Code]
        $bossCount = if ($null -ne $profile) { $profile.Bosses } else { $def.Bosses }
        $pointMultiplier = if ($null -ne $profile) { $profile.Point } else { "1.000" }
        $dropMultiplier = if ($null -ne $profile) { $profile.Drop } else { "1.000" }
        $itemCount = if ($null -ne $profile) { $profile.Items } else { "0" }
        $lines.Add("$($def.Code)`t$($def.Name)`t$(if ($enabled) {1} else {0})`t$state`t$($def.Health)`t$($def.Message)`t$bossCount`t$($def.Components)`t$pointMultiplier`t$dropMultiplier`t$itemCount")
    }
    $lines -join "`r`n"
}

function Get-EventStats {
    $runtime = Get-EventRuntimeState
    $dbStats = Invoke-MySql @"
SELECT COUNT(*) AS players,
       COALESCE(SUM(event_point<>0),0) AS event_players,COALESCE(SUM(event_point),0) AS total_event_point,COALESCE(MAX(event_point),0) AS max_event_point,
       COALESCE(SUM(point_sukien<>0 OR point_sukien1<>0 OR point_sukien2<>0),0) AS ranked_players,
       COALESCE(MAX(point_sukien),0) AS max_rank_1,COALESCE(MAX(point_sukien1),0) AS max_rank_2,COALESCE(MAX(point_sukien2),0) AS max_rank_3
FROM player;
"@
    $lines = @($dbStats -split "`r?`n")
    if ($lines.Count -lt 2) { throw "Không đọc được thống kê sự kiện." }
    "server_running`trestart_pending`tstarted_at`t$($lines[0])`r`n$(if ($runtime.Running) {1} else {0})`t$(if ($runtime.RestartPending) {1} else {0})`t$($runtime.StartedAt)`t$($lines[1])"
}

function Get-PlayerConfigCurrentValue {
    param([string]$Key)
    $entry = Get-PlayerConfigEntry $Key
    if ($null -eq $entry) { throw "Thiếu khai báo cấu hình Player: $Key" }
    $map = Get-PropertyMap (Join-Path $Root "player.properties")
    if ($map.ContainsKey($Key)) { return [string]$map[$Key] }
    [string]$entry.Default
}

function Convert-PlayerJsonArray {
    param([string]$Json, [string]$Label, [int]$MinimumCount = 0)
    try { $result = $Json | ConvertFrom-Json } catch { throw "$Label không phải JSON hợp lệ." }
    if ($result.Count -lt $MinimumCount) { throw "$Label thiếu dữ liệu: cần ít nhất $MinimumCount phần tử." }
    ,$result
}

function Get-PlayerUsedSlotCount {
    param([object[]]$Items)
    $used = 0
    foreach ($itemText in $Items) {
        try {
            $item = ([string]$itemText) | ConvertFrom-Json
            if ($item.Count -gt 0 -and [int]$item[0] -ne -1) { $used++ }
        } catch { throw "Dữ liệu item của player bị hỏng." }
    }
    $used
}

function Get-PlayerDataVersion {
    param($Record)
    $raw = @($Record.DataPoint, $Record.DataInventory, $Record.ItemsBag, $Record.ItemsBox,
        $Record.DataLocation, $Record.Ban, $Record.Active) -join "|"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($raw)
        (($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally { $sha.Dispose() }
}

function Get-PlayerRawRecord {
    param([int]$PlayerId)
    if ($PlayerId -le 0) { throw "Player ID không hợp lệ." }
    Ensure-PlayerOnlineSchema
    $text = Invoke-MySql @"
SELECT p.id,p.account_id,p.name,COALESCE(a.username,''),p.gender,p.head,p.clan_id,
       DATE_FORMAT(p.create_time,'%Y-%m-%d %H:%i:%s'),
       COALESCE(DATE_FORMAT(a.last_time_login,'%Y-%m-%d %H:%i:%s'),''),
       COALESCE(DATE_FORMAT(a.last_time_logout,'%Y-%m-%d %H:%i:%s'),''),
       COALESCE(a.ban,0),COALESCE(a.active,0),COALESCE(a.is_admin,0),
       p.data_point,p.data_inventory,p.items_bag,p.items_box,p.data_location,
       CASE
           WHEN apo.last_seen >= DATE_SUB(NOW(), INTERVAL 20 SECOND) THEN 'ONLINE'
           WHEN a.last_time_login>a.last_time_logout THEN 'ONLINE?'
           ELSE 'OFFLINE'
       END AS online_state
FROM player p LEFT JOIN account a ON a.id=p.account_id
LEFT JOIN admin_player_online apo ON apo.player_id=p.id
WHERE p.id=$PlayerId LIMIT 1;
"@
    $lines = @($text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { throw "Không tìm thấy player ID $PlayerId." }
    $parts = @($lines[1] -split "`t", 19)
    if ($parts.Count -lt 19) { throw "Không đọc được dữ liệu player ID $PlayerId." }
    [pscustomobject]@{
        Id=$parts[0]; AccountId=$parts[1]; Name=$parts[2]; Username=$parts[3]; Gender=$parts[4];
        Head=$parts[5]; ClanId=$parts[6]; CreatedAt=$parts[7]; LastLogin=$parts[8]; LastLogout=$parts[9];
        Ban=$parts[10]; Active=$parts[11]; IsAdmin=$parts[12]; DataPoint=$parts[13];
        DataInventory=$parts[14]; ItemsBag=$parts[15]; ItemsBox=$parts[16]; DataLocation=$parts[17];
        OnlineState=$parts[18]
    }
}

function Test-PlayerPossiblyOnline {
    param($Record)
    if ($Record.PSObject.Properties["OnlineState"] -and $Record.OnlineState -in @("ONLINE", "ONLINE?")) { return $true }
    if ([string]::IsNullOrWhiteSpace($Record.LastLogin) -or [string]::IsNullOrWhiteSpace($Record.LastLogout)) { return $false }
    ([datetime]::Parse($Record.LastLogin) -gt [datetime]::Parse($Record.LastLogout))
}

function Ensure-PlayerOnlineSchema {
    Invoke-MySql @"
CREATE TABLE IF NOT EXISTS admin_player_online (
  player_id BIGINT NOT NULL PRIMARY KEY,
  account_id INT NOT NULL DEFAULT 0,
  map_id INT NOT NULL DEFAULT -1,
  zone_id INT NOT NULL DEFAULT -1,
  last_seen DATETIME NOT NULL,
  KEY idx_admin_player_online_seen (last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"@ | Out-Null
}

function List-Players {
    Ensure-PlayerOnlineSchema
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match '^\d+$') {
            $where = "WHERE p.id=$(SqlInt $Search) OR p.account_id=$(SqlInt $Search) OR p.name LIKE '%$safe%' OR a.username LIKE '%$safe%'"
        } else {
            $where = "WHERE p.name LIKE '%$safe%' OR a.username LIKE '%$safe%'"
        }
    }
    Invoke-MySql @"
SELECT p.id,p.name,COALESCE(a.username,'') AS username,p.gender,
       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.data_point,'`$[1]')),'0') AS power,
       COALESCE(JSON_LENGTH(p.items_bag),0) AS bag_slots,
       COALESCE(JSON_LENGTH(p.items_box),0) AS box_slots,
       COALESCE(a.ban,0) AS ban,COALESCE(a.active,0) AS active,
       CASE
           WHEN apo.last_seen >= DATE_SUB(NOW(), INTERVAL 20 SECOND) THEN 'ONLINE'
           WHEN a.last_time_login>a.last_time_logout THEN 'ONLINE?'
           ELSE 'OFFLINE'
       END AS online_state,
       p.account_id
FROM player p LEFT JOIN account a ON a.id=p.account_id
LEFT JOIN admin_player_online apo ON apo.player_id=p.id
$where ORDER BY p.id DESC LIMIT 300;
"@
}

function Get-PlayerDetail {
    $record = Get-PlayerRawRecord (SqlInt $Id)
    $point = Convert-PlayerJsonArray $record.DataPoint "data_point" 14
    $inventory = Convert-PlayerJsonArray $record.DataInventory "data_inventory" 4
    $bag = Convert-PlayerJsonArray $record.ItemsBag "items_bag"
    $box = Convert-PlayerJsonArray $record.ItemsBox "items_box"
    $location = Convert-PlayerJsonArray $record.DataLocation "data_location" 3
    $level = [int]$point[0]
    $powerLimits = @((Get-PlayerConfigCurrentValue "player.limit.power") -split ',')
    $hpLimits = @((Get-PlayerConfigCurrentValue "player.limit.hpMp") -split ',')
    $damageLimits = @((Get-PlayerConfigCurrentValue "player.limit.damage") -split ',')
    $defenseLimits = @((Get-PlayerConfigCurrentValue "player.limit.defense") -split ',')
    $criticalLimits = @((Get-PlayerConfigCurrentValue "player.limit.critical") -split ',')
    $safeLevel = [Math]::Max(0, [Math]::Min(9, $level))
    $online = $record.OnlineState
    $version = Get-PlayerDataVersion $record
    $header = "id`taccountId`tname`tusername`tgender`thead`tclanId`tcreatedAt`tlastLogin`tlastLogout`tonline`tban`tactive`tisAdmin`tlimitPower`tpower`tpotential`tstamina`tmaxStamina`thpg`tmpg`tdamage`tdefense`tcritical`tcriticalDragon`thp`tmp`tgold`tgem`truby`tcoupon`tbagSlots`tbagUsed`tboxSlots`tboxUsed`tmapId`tx`ty`tversion`tpowerLimit`thpMpLimit`tdamageLimit`tdefenseLimit`tcriticalLimit`tmaxGold`tmaxGem`tmaxRuby`tmaxCoupon`tmaxBag`tmaxBox"
    $values = @(
        $record.Id,$record.AccountId,$record.Name,$record.Username,$record.Gender,$record.Head,$record.ClanId,
        $record.CreatedAt,$record.LastLogin,$record.LastLogout,$online,$record.Ban,$record.Active,$record.IsAdmin,
        $point[0],$point[1],$point[2],$point[3],$point[4],$point[5],$point[6],$point[7],$point[8],$point[9],$point[10],$point[12],$point[13],
        $inventory[0],$inventory[1],$inventory[2],$inventory[3],$bag.Count,(Get-PlayerUsedSlotCount $bag),$box.Count,(Get-PlayerUsedSlotCount $box),
        $location[0],$location[1],$location[2],$version,$powerLimits[$safeLevel],$hpLimits[$safeLevel],$damageLimits[$safeLevel],
        $defenseLimits[$safeLevel],$criticalLimits[$safeLevel],(Get-PlayerConfigCurrentValue "player.max.gold"),
        (Get-PlayerConfigCurrentValue "player.max.gem"),(Get-PlayerConfigCurrentValue "player.max.ruby"),
        (Get-PlayerConfigCurrentValue "player.max.coupon"),(Get-PlayerConfigCurrentValue "player.max.bagSlots"),
        (Get-PlayerConfigCurrentValue "player.max.boxSlots")
    ) -join "`t"
    "$header`r`n$values"
}

function Get-RequiredPayloadLong {
    param($Payload, [string]$Property, [string]$Label, [long]$Minimum, [long]$Maximum)
    $prop = $Payload.PSObject.Properties[$Property]
    if ($null -eq $prop) { throw "Thiếu trường $Label." }
    $text = [string]$prop.Value
    $number = 0L
    if (-not [long]::TryParse($text, [ref]$number) -or $number -lt $Minimum -or $number -gt $Maximum) {
        throw "$Label phải từ $Minimum đến $Maximum."
    }
    $number
}

function Resize-PlayerItems {
    param([object[]]$Items, [int]$Target, [string]$Label)
    if ($Target -lt $Items.Count) {
        for ($i = $Items.Count - 1; $i -ge $Target; $i--) {
            try { $item = ([string]$Items[$i]) | ConvertFrom-Json } catch { throw "$Label có item lỗi ở ô $($i + 1)." }
            if ($item.Count -eq 0 -or [int]$item[0] -ne -1) { throw "Không thể giảm ${Label}: ô $($i + 1) đang có vật phẩm." }
        }
        return @($Items[0..($Target - 1)])
    }
    $result = New-Object System.Collections.Generic.List[object]
    foreach ($item in $Items) { $result.Add($item) }
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    while ($result.Count -lt $Target) { $result.Add("[-1,0,`"[]`",$now]") }
    $result.ToArray()
}

function Save-PlayerCore {
    try { $payload = $PayloadJson | ConvertFrom-Json } catch { throw "Dữ liệu form Player không hợp lệ." }
    $playerId = SqlInt $Id
    $record = Get-PlayerRawRecord $playerId
    if ([string]$payload.version -ne (Get-PlayerDataVersion $record)) { throw "Dữ liệu player đã thay đổi. Hãy tải lại trước khi lưu." }
    $forceOffline = [string]$payload.forceOffline -eq "1"
    if ((Test-PlayerPossiblyOnline $record) -and -not $forceOffline) {
        throw "Player có dấu hiệu đang online. Hãy cho player logout hoặc dừng server rồi bật xác nhận sửa offline."
    }

    $point = Convert-PlayerJsonArray $record.DataPoint "data_point" 14
    $inventory = Convert-PlayerJsonArray $record.DataInventory "data_inventory" 4
    $bag = Convert-PlayerJsonArray $record.ItemsBag "items_bag"
    $box = Convert-PlayerJsonArray $record.ItemsBox "items_box"

    $newLevel = [int](Get-RequiredPayloadLong $payload "limitPower" "Cấp giới hạn" 0 9)
    $newPower = Get-RequiredPayloadLong $payload "power" "Sức mạnh" 0 ([long]::MaxValue)
    $newPotential = Get-RequiredPayloadLong $payload "potential" "Tiềm năng" 0 ([long]::MaxValue)
    $newStamina = [int](Get-RequiredPayloadLong $payload "stamina" "Thể lực" 0 32767)
    $newMaxStamina = [int](Get-RequiredPayloadLong $payload "maxStamina" "Thể lực tối đa" 1 32767)
    if ($newStamina -gt $newMaxStamina) { throw "Thể lực hiện tại không được lớn hơn thể lực tối đa." }
    $newHp = [int](Get-RequiredPayloadLong $payload "hpg" "HP gốc" 1 2147483647)
    $newMp = [int](Get-RequiredPayloadLong $payload "mpg" "KI gốc" 1 2147483647)
    $newDamage = [int](Get-RequiredPayloadLong $payload "damage" "Sức đánh gốc" 1 2147483647)
    $newDefense = [int](Get-RequiredPayloadLong $payload "defense" "Giáp gốc" 0 2147483647)
    $newCritical = [int](Get-RequiredPayloadLong $payload "critical" "Chí mạng gốc" 0 127)
    $newCriticalDragon = [int](Get-RequiredPayloadLong $payload "criticalDragon" "Chí mạng rồng" 0 127)

    $powerLimits = @((Get-PlayerConfigCurrentValue "player.limit.power") -split ',')
    $hpLimits = @((Get-PlayerConfigCurrentValue "player.limit.hpMp") -split ',')
    $damageLimits = @((Get-PlayerConfigCurrentValue "player.limit.damage") -split ',')
    $defenseLimits = @((Get-PlayerConfigCurrentValue "player.limit.defense") -split ',')
    $criticalLimits = @((Get-PlayerConfigCurrentValue "player.limit.critical") -split ',')
    $tierChanged = $newLevel -ne [int]$point[0]
    if (($tierChanged -or $newPower -ne [long]$point[1]) -and $newPower -gt [long]$powerLimits[$newLevel]) { throw "Sức mạnh vượt trần cấp $newLevel ($($powerLimits[$newLevel]))." }
    if (($tierChanged -or $newHp -ne [int]$point[5]) -and $newHp -gt [int]$hpLimits[$newLevel]) { throw "HP gốc vượt trần cấp $newLevel ($($hpLimits[$newLevel]))." }
    if (($tierChanged -or $newMp -ne [int]$point[6]) -and $newMp -gt [int]$hpLimits[$newLevel]) { throw "KI gốc vượt trần cấp $newLevel ($($hpLimits[$newLevel]))." }
    if (($tierChanged -or $newDamage -ne [int]$point[7]) -and $newDamage -gt [int]$damageLimits[$newLevel]) { throw "Sức đánh gốc vượt trần cấp $newLevel ($($damageLimits[$newLevel]))." }
    if (($tierChanged -or $newDefense -ne [int]$point[8]) -and $newDefense -gt [int]$defenseLimits[$newLevel]) { throw "Giáp gốc vượt trần cấp $newLevel ($($defenseLimits[$newLevel]))." }
    if (($tierChanged -or $newCritical -ne [int]$point[9]) -and $newCritical -gt [int]$criticalLimits[$newLevel]) { throw "Chí mạng gốc vượt trần cấp $newLevel ($($criticalLimits[$newLevel]))." }

    $newGold = Get-RequiredPayloadLong $payload "gold" "Vàng" 0 ([long]::MaxValue)
    $newGem = [int](Get-RequiredPayloadLong $payload "gem" "Ngọc xanh" 0 2147483647)
    $newRuby = [int](Get-RequiredPayloadLong $payload "ruby" "Hồng ngọc" 0 2147483647)
    $newCoupon = [int](Get-RequiredPayloadLong $payload "coupon" "Coupon" 0 2147483647)
    $maxGold = [long](Get-PlayerConfigCurrentValue "player.max.gold")
    $maxGem = [int](Get-PlayerConfigCurrentValue "player.max.gem")
    $maxRuby = [int](Get-PlayerConfigCurrentValue "player.max.ruby")
    $maxCoupon = [int](Get-PlayerConfigCurrentValue "player.max.coupon")
    if ($newGold -ne [long]$inventory[0] -and $newGold -gt $maxGold) { throw "Vàng vượt giới hạn toàn server $maxGold." }
    if ($newGem -ne [int]$inventory[1] -and $newGem -gt $maxGem) { throw "Ngọc xanh vượt giới hạn toàn server $maxGem." }
    if ($newRuby -ne [int]$inventory[2] -and $newRuby -gt $maxRuby) { throw "Hồng ngọc vượt giới hạn toàn server $maxRuby." }
    if ($newCoupon -ne [int]$inventory[3] -and $newCoupon -gt $maxCoupon) { throw "Coupon vượt giới hạn toàn server $maxCoupon." }

    $newBagSlots = [int](Get-RequiredPayloadLong $payload "bagSlots" "Ô hành trang" 1 127)
    $newBoxSlots = [int](Get-RequiredPayloadLong $payload "boxSlots" "Ô rương" 1 127)
    $maxBag = [int](Get-PlayerConfigCurrentValue "player.max.bagSlots")
    $maxBox = [int](Get-PlayerConfigCurrentValue "player.max.boxSlots")
    if ($newBagSlots -ne $bag.Count -and $newBagSlots -gt $maxBag) { throw "Ô hành trang vượt giới hạn toàn server $maxBag." }
    if ($newBoxSlots -ne $box.Count -and $newBoxSlots -gt $maxBox) { throw "Ô rương vượt giới hạn toàn server $maxBox." }
    $bag = Resize-PlayerItems $bag $newBagSlots "hành trang"
    $box = Resize-PlayerItems $box $newBoxSlots "rương"

    $point[0]=$newLevel; $point[1]=$newPower; $point[2]=$newPotential; $point[3]=$newStamina; $point[4]=$newMaxStamina
    $point[5]=$newHp; $point[6]=$newMp; $point[7]=$newDamage; $point[8]=$newDefense; $point[9]=$newCritical; $point[10]=$newCriticalDragon
    $inventory[0]=$newGold; $inventory[1]=$newGem; $inventory[2]=$newRuby; $inventory[3]=$newCoupon
    $pointJson = ConvertTo-Json -InputObject @($point) -Compress
    $inventoryJson = ConvertTo-Json -InputObject @($inventory) -Compress
    $bagJson = ConvertTo-Json -InputObject @($bag) -Compress
    $boxJson = ConvertTo-Json -InputObject @($box) -Compress
    $ban = [int](Get-RequiredPayloadLong $payload "ban" "Trạng thái khóa" 0 1)
    $active = [int](Get-RequiredPayloadLong $payload "active" "Trạng thái kích hoạt" 0 1)
    Invoke-MySql "START TRANSACTION; UPDATE player SET data_point=$(SqlString $pointJson),data_inventory=$(SqlString $inventoryJson),items_bag=$(SqlString $bagJson),items_box=$(SqlString $boxJson) WHERE id=$playerId; UPDATE account SET ban=$ban,active=$active WHERE id=$(SqlInt $record.AccountId); COMMIT;" | Out-Null
    "OK`tĐã cập nhật player $($record.Name) (ID $playerId)."
}

function Rescue-Player {
    try { $payload = $PayloadJson | ConvertFrom-Json } catch { throw "Dữ liệu cứu hộ Player không hợp lệ." }
    $record = Get-PlayerRawRecord (SqlInt $Id)
    if ([string]$payload.version -ne (Get-PlayerDataVersion $record)) { throw "Dữ liệu player đã thay đổi. Hãy tải lại trước khi cứu hộ." }
    $forceOffline = [string]$payload.forceOffline -eq "1"
    if ((Test-PlayerPossiblyOnline $record) -and -not $forceOffline) { throw "Player có dấu hiệu đang online; không thể cứu hộ bằng database." }
    $mapId = [int]$record.Gender + 21
    $location = "[$mapId,300,336]"
    Invoke-MySql "UPDATE player SET data_location=$(SqlString $location) WHERE id=$(SqlInt $record.Id);" | Out-Null
    "OK`tĐã đưa $($record.Name) về map $mapId, tọa độ 300/336."
}

function List-Items {
    $where = New-Object System.Collections.Generic.List[string]
    if ($Type -match "^-?\d+$") {
        $where.Add("`TYPE` = $(SqlInt $Type)")
    }
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match "^\d+$") {
            $where.Add("(`id` = $(SqlInt $Search) OR `NAME` LIKE '%$safeSearch%')")
        } else {
            $where.Add("`NAME` LIKE '%$safeSearch%'")
        }
    }
    $whereSql = if ($where.Count -gt 0) { "WHERE " + ($where -join " AND ") } else { "" }
    Invoke-MySql "SELECT id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg FROM item_template $whereSql ORDER BY id DESC LIMIT 300;"
}

function List-ItemCatalog {
    Invoke-MySql @"
SELECT id,
       REPLACE(REPLACE(REPLACE(COALESCE(`NAME`, ''), CHAR(9), ' '), CHAR(13), ' '), CHAR(10), ' ') AS item_name,
       REPLACE(REPLACE(REPLACE(COALESCE(description, ''), CHAR(9), ' '), CHAR(13), ' '), CHAR(10), ' ') AS description,
       `TYPE` AS item_type,
       gender,
       icon_id
FROM item_template
ORDER BY id
LIMIT 10000;
"@
}

function List-ItemTypes {
    Invoke-MySql "SELECT `TYPE`, COUNT(*) AS total FROM item_template GROUP BY `TYPE` ORDER BY `TYPE`;"
}

function Save-Item {
    $itemId = SqlInt $Id
    if ($itemId -lt 0) {
        throw "ID vật phẩm không hợp lệ."
    }
    $sql = @"
INSERT INTO item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES ($itemId, $(SqlInt $Type), $(SqlInt $Gender), $(SqlString $Name), $(SqlString $Description), $(SqlInt $Level), $(SqlInt $IconId), $(SqlInt $Part -1), $(SqlInt $IsUpToUp), $(SqlInt $PowerRequire), $(SqlInt $Gold), $(SqlInt $Gem), $(SqlInt $Head -1), $(SqlInt $Body -1), $(SqlInt $Leg -1))
ON DUPLICATE KEY UPDATE
TYPE=VALUES(TYPE), gender=VALUES(gender), NAME=VALUES(NAME), description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id), part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require), gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);
"@
    Invoke-MySql $sql | Out-Null
    "OK`tĐã lưu vật phẩm ID $itemId. Restart server để client/server nhận template mới."
}

function Assert-RadarOptionsJson {
    param([string]$Json)
    try {
        $rawOptions = @($Json | ConvertFrom-Json)
    } catch {
        throw "Danh sách option sổ sưu tầm không đúng JSON."
    }

    $validated = New-Object System.Collections.Generic.List[object]
    foreach ($raw in $rawOptions) {
        if ($null -eq $raw) { continue }
        $optionId = SqlInt ([string]$raw.id)
        $paramValue = SqlInt ([string]$raw.param)
        $activeValue = if ($null -ne $raw.activeCard) { SqlInt ([string]$raw.activeCard) } else { SqlInt ([string]$raw.active) }
        if ($optionId -lt 0 -or $optionId -gt 255) { throw "ID option radar phải từ 0 đến 255." }
        if ($paramValue -lt -32768 -or $paramValue -gt 32767) { throw "Param option radar phải nằm trong khoảng short (-32768..32767)." }
        if ($activeValue -lt 0 -or $activeValue -gt 2) { throw "Mốc kích hoạt activeCard chỉ nhận 0, 1 hoặc 2." }
        $validated.Add([pscustomobject]@{ id=$optionId; param=$paramValue; activeCard=$activeValue })
    }
    ConvertTo-Json -InputObject $validated.ToArray() -Compress -Depth 4
}

function Ensure-RadarSchema {
    Invoke-MySql "ALTER TABLE radar ADD COLUMN IF NOT EXISTS milestones TEXT NOT NULL DEFAULT ('[]') AFTER options;" | Out-Null
}

function Ensure-ItemDefaultOptionSchema {
    Invoke-MySql @"
CREATE TABLE IF NOT EXISTS item_default_option (
  item_template_id INT NOT NULL,
  option_id INT NOT NULL,
  param INT NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (item_template_id, option_id),
  KEY idx_item_default_option_option (option_id),
  CONSTRAINT fk_item_default_option_item FOREIGN KEY (item_template_id) REFERENCES item_template(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_default_option_template FOREIGN KEY (option_id) REFERENCES item_option_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE item_shop ADD COLUMN IF NOT EXISTS option_mode TINYINT NOT NULL DEFAULT 1 AFTER icon_spec;
"@ | Out-Null
    if ((Get-MySqlScalar "SELECT COUNT(*) FROM item_default_option;") -eq '0') {
        Invoke-MySql @"
INSERT IGNORE INTO item_default_option (item_template_id, option_id, param, sort_order)
SELECT s.temp_id, o.option_id, o.param, MIN(o.id)
FROM item_shop s
JOIN item_shop_option o ON o.item_shop_id=s.id
JOIN (
  SELECT temp_id
  FROM (
    SELECT s2.temp_id, s2.id,
           COALESCE(GROUP_CONCAT(CONCAT(o2.option_id, ':', o2.param)
                    ORDER BY o2.option_id, o2.param SEPARATOR ','), '') AS option_signature
    FROM item_shop s2
    LEFT JOIN item_shop_option o2 ON o2.item_shop_id=s2.id
    GROUP BY s2.temp_id, s2.id
  ) variants
  GROUP BY temp_id
  HAVING COUNT(DISTINCT option_signature)=1 AND MAX(option_signature)<>''
) unambiguous ON unambiguous.temp_id=s.temp_id
GROUP BY s.temp_id,o.option_id,o.param;
"@ | Out-Null
    }
}

function Assert-DefaultOptionPayload {
    param([string]$Json, [int]$ItemId)
    try {
        $parsedOptions = $Json | ConvertFrom-Json
        $rawOptions = if ($null -eq $parsedOptions) { @() } else { @($parsedOptions) }
    } catch { throw "Danh sách option mặc định không đúng định dạng JSON." }
    $seen = @{}
    $values = New-Object System.Collections.Generic.List[string]
    $order = 0
    foreach ($rawOption in $rawOptions) {
        $optionIdText = ([string]$rawOption.id).Trim()
        $paramText = ([string]$rawOption.param).Trim()
        if ($optionIdText -notmatch '^\d+$' -or [int64]$optionIdText -gt 255) { throw "Option ID không hợp lệ: $optionIdText" }
        if ($paramText -notmatch '^-?\d+$' -or [int64]$paramText -lt -32768 -or [int64]$paramText -gt 32767) { throw "Param option $optionIdText phải nằm trong khoảng -32768..32767." }
        if ($seen.ContainsKey($optionIdText)) { throw "Option ID $optionIdText bị chọn trùng." }
        $seen[$optionIdText] = $true
        $values.Add("($(SqlInt $ItemId),$([int]$optionIdText),$([int]$paramText),$order)")
        $order++
    }
    if ($seen.Count -gt 0) {
        $optionSql = ($seen.Keys -join ',')
        $found = [int](Get-MySqlScalar "SELECT COUNT(*) FROM item_option_template WHERE id IN ($optionSql);")
        if ($found -ne $seen.Count) { throw "Có option không tồn tại trong item_option_template." }
    }
    return $values
}

function List-ItemDefaultOptions {
    Ensure-ItemDefaultOptionSchema
    $where = if ($Id -match '^\d+$') { "d.item_template_id=$(SqlInt $Id)" } else { "1=1" }
    Invoke-MySql @"
SELECT d.item_template_id, COALESCE(t.`NAME`,''), d.option_id,
       COALESCE(o.`NAME`,''), d.param, d.sort_order
FROM item_default_option d
LEFT JOIN item_template t ON t.id=d.item_template_id
LEFT JOIN item_option_template o ON o.id=d.option_id
WHERE $where
ORDER BY d.item_template_id,d.sort_order,d.option_id;
"@
}

function List-ItemDefaultOptionItems {
    Ensure-ItemDefaultOptionSchema
    $where = "1=1"
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        if ($Search -match '^\d+$') { $where = "t.id=$(SqlInt $Search)" }
        else { $where = "t.`NAME` LIKE $(SqlString ('%' + $Search + '%'))" }
    }
    Invoke-MySql @"
SELECT t.id, COALESCE(t.`NAME`,''), COALESCE(t.description,''),
       COUNT(d.option_id), COALESCE((SELECT COUNT(*) FROM item_shop s WHERE s.temp_id=t.id),0)
FROM item_template t
LEFT JOIN item_default_option d ON d.item_template_id=t.id
WHERE $where
GROUP BY t.id,t.`NAME`,t.description
ORDER BY t.id
LIMIT 500;
"@
}

function Save-ItemDefaultOptions {
    Ensure-ItemDefaultOptionSchema
    $itemId = SqlInt $Id
    if ($itemId -lt 0) { throw "Chọn vật phẩm trước khi lưu option mặc định." }
    if ((Get-MySqlScalar "SELECT COUNT(*) FROM item_template WHERE id=$itemId;") -ne '1') { throw "Không tìm thấy vật phẩm ID $itemId." }
    $values = @(Assert-DefaultOptionPayload -Json $PayloadJson -ItemId $itemId)
    $sql = New-Object System.Text.StringBuilder
    [void]$sql.AppendLine("START TRANSACTION;")
    [void]$sql.AppendLine("DELETE FROM item_default_option WHERE item_template_id=$itemId;")
    if ($values.Count -gt 0) { [void]$sql.AppendLine("INSERT INTO item_default_option (item_template_id,option_id,param,sort_order) VALUES $($values -join ',');") }
    [void]$sql.AppendLine("COMMIT;")
    Invoke-MySql $sql.ToString() | Out-Null
    "OK`tĐã lưu $($values.Count) option mặc định cho vật phẩm ID $itemId. Restart server để áp dụng cache."
}

function Save-ItemDefaultOptionsBulk {
    Ensure-ItemDefaultOptionSchema
    $ids = @(Convert-UniqueIdPayload -Json $PayloadJson -Label "Danh sách vật phẩm")
    if ($ids.Count -eq 0) { throw "Chọn ít nhất một vật phẩm." }
    $validated = @(Assert-DefaultOptionPayload -Json $OptionsJson -ItemId 0)
    $optionParts = New-Object System.Collections.Generic.List[string]
    foreach ($v in $validated) { $optionParts.Add(($v -replace '^\(0,', '(')) }
    $idSql = $ids -join ','
    $sql = New-Object System.Text.StringBuilder
    [void]$sql.AppendLine("START TRANSACTION;")
    [void]$sql.AppendLine("DELETE FROM item_default_option WHERE item_template_id IN ($idSql);")
    foreach ($itemId in $ids) {
        if ($optionParts.Count -gt 0) {
            $rows = @($optionParts | ForEach-Object { $_ -replace '^\(', "($itemId," })
            [void]$sql.AppendLine("INSERT INTO item_default_option (item_template_id,option_id,param,sort_order) VALUES $($rows -join ',');")
        }
    }
    [void]$sql.AppendLine("COMMIT;")
    Invoke-MySql $sql.ToString() | Out-Null
    "OK`tĐã lưu option mặc định cho $($ids.Count) vật phẩm. Restart server để áp dụng cache."
}

function Assert-RadarMilestonesJson {
    param([string]$Json, [int]$DefaultMax)
    try {
        $rawMilestones = @($Json | ConvertFrom-Json)
    } catch {
        throw "Danh sách số lượng mốc không đúng JSON."
    }
    $safeMax = if ($DefaultMax -gt 0) { $DefaultMax } else { 1 }
    $values = New-Object System.Collections.Generic.List[int]
    $values.Add(1)
    for ($i = 1; $i -le 2; $i++) {
        $rawValue = if ($rawMilestones.Count -gt $i) { [string]$rawMilestones[$i] } else { [string]$safeMax }
        if ($rawValue -notmatch '^\d+$') { throw "Số lượng thẻ ở mốc $i phải là số nguyên dương." }
        $amount = [int]$rawValue
        if ($amount -lt 1 -or $amount -gt 127) { throw "Số lượng thẻ ở mốc $i phải từ 1 đến 127." }
        $values.Add($amount)
    }
    ConvertTo-Json -InputObject $values.ToArray() -Compress
}

function Get-RadarBodyJson {
    $headValue = SqlInt $Head -1
    $bodyValue = SqlInt $Body -1
    $legValue = SqlInt $Leg -1
    $bagValue = SqlInt $Part -1
    ConvertTo-Json -InputObject @([pscustomobject]@{ head=$headValue; body=$bodyValue; leg=$legValue; bag=$bagValue }) -Compress
}

function List-RadarCards {
    Ensure-RadarSchema
    $where = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match "^\d+$") {
            $where.Add("(r.id=$(SqlInt $Search) OR r.iconId=$(SqlInt $Search) OR r.name LIKE '%$safeSearch%')")
        } else {
            $where.Add("r.name LIKE '%$safeSearch%'")
        }
    }
    if ($RadarRank -match "^-?\d+$" -and (SqlInt $RadarRank -1) -ge 0) {
        $where.Add("r.rank=$(SqlInt $RadarRank)")
    }
    $whereSql = if ($where.Count -gt 0) { "WHERE " + ($where -join " AND ") } else { "" }
    Invoke-MySql @"
SELECT r.id,r.iconId,r.rank,r.max,r.type,r.mob_id,
       REPLACE(REPLACE(REPLACE(COALESCE(r.name,''),CHAR(9),' '),CHAR(13),' '),CHAR(10),' ') AS name,
       REPLACE(REPLACE(REPLACE(COALESCE(r.info,''),CHAR(9),' '),CHAR(13),' '),CHAR(10),' ') AS info,
       r.``require``,r.require_level,r.aura_id,
       COALESCE(JSON_LENGTH(r.options),0) AS option_count
FROM radar r
$whereSql
ORDER BY r.rank,r.id
LIMIT 500;
"@
}

function Get-RadarCard {
    Ensure-RadarSchema
    $radarId = SqlInt $Id
    Invoke-MySql @"
SELECT id,iconId,rank,max,type,mob_id,
       REPLACE(REPLACE(REPLACE(COALESCE(body,'[{""head"":-1,""body"":-1,""leg"":-1,""bag"":-1}]'),CHAR(9),' '),CHAR(13),''),CHAR(10),'') AS body_json,
       REPLACE(REPLACE(REPLACE(COALESCE(name,''),CHAR(9),' '),CHAR(13),' '),CHAR(10),' ') AS name,
       REPLACE(REPLACE(REPLACE(COALESCE(info,''),CHAR(9),' '),CHAR(13),' '),CHAR(10),' ') AS info,
       REPLACE(REPLACE(REPLACE(COALESCE(options,'[]'),CHAR(9),' '),CHAR(13),''),CHAR(10),'') AS options_json,
       ``require``,require_level,aura_id,
       REPLACE(REPLACE(REPLACE(COALESCE(milestones,'[]'),CHAR(9),' '),CHAR(13),''),CHAR(10),'') AS milestones_json
FROM radar
WHERE id=$radarId
LIMIT 1;
"@
}

function Save-RadarCard {
    Ensure-RadarSchema
    $radarId = SqlInt $Id
    if ($radarId -lt 0 -or $radarId -gt 32767) { throw "ID thẻ sưu tầm không hợp lệ." }
    $iconValue = SqlInt $IconId
    if ($iconValue -lt -1 -or $iconValue -gt 32767) { throw "Icon ID không hợp lệ." }
    $rankValue = SqlInt $RadarRank
    if ($rankValue -lt 0 -or $rankValue -gt 127) { throw "Rank phải từ 0 đến 127." }
    $maxValue = SqlInt $RadarMax
    if ($maxValue -lt 1 -or $maxValue -gt 127) { throw "Max thẻ phải từ 1 đến 127." }
    $typeValue = SqlInt $RadarType
    if ($typeValue -lt 0 -or $typeValue -gt 1) { throw "Type radar chỉ hỗ trợ 0=mob hoặc 1=nhân vật." }
    $mobValue = SqlInt $RadarMobId -1
    $requireValue = SqlInt $RequireId -1
    $requireLevelValue = SqlInt $RequireLevel
    if ($requireLevelValue -lt 0 -or $requireLevelValue -gt 2) { throw "Require level phải từ 0 đến 2." }
    $auraValue = SqlInt $AuraId -1
    $bodyJson = Get-RadarBodyJson
    $optionsValue = Assert-RadarOptionsJson $OptionsJson
    $milestonesValue = Assert-RadarMilestonesJson $MilestonesJson $maxValue
    $sql = @"
INSERT INTO radar (id, iconId, rank, max, type, mob_id, body, name, info, options, milestones, ``require``, require_level, aura_id)
VALUES ($radarId,$iconValue,$rankValue,$maxValue,$typeValue,$mobValue,$(SqlString $bodyJson),$(SqlString $Name),$(SqlString $Description),$(SqlString $optionsValue),$(SqlString $milestonesValue),$requireValue,$requireLevelValue,$auraValue)
ON DUPLICATE KEY UPDATE
iconId=VALUES(iconId), rank=VALUES(rank), max=VALUES(max), type=VALUES(type), mob_id=VALUES(mob_id), body=VALUES(body),
name=VALUES(name), info=VALUES(info), options=VALUES(options), milestones=VALUES(milestones), ``require``=VALUES(``require``), require_level=VALUES(require_level), aura_id=VALUES(aura_id);
"@
    Invoke-MySql $sql | Out-Null
    "OK`tĐã lưu thẻ sưu tầm ID $radarId. Restart server để áp dụng template radar mới."
}

function Delete-RadarCard {
    $radarId = SqlInt $Id
    if ($radarId -lt 0) { throw "ID thẻ sưu tầm không hợp lệ." }
    Invoke-MySql "DELETE FROM radar WHERE id=$radarId;" | Out-Null
    "OK`tĐã xóa thẻ sưu tầm ID $radarId. Restart server để áp dụng."
}

function List-RadarMobDrops {
    Ensure-SpawnSchema
    $radarId = SqlInt $Id
    if ($radarId -lt 0) { throw "ID thẻ sưu tầm không hợp lệ." }
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("\", "\\").Replace("'", "''")
        $where = if ($Search -match '^\d+$') { "WHERE t.id=$(SqlInt $Search) OR t.NAME LIKE '%$safe%'" } else { "WHERE t.NAME LIKE '%$safe%'" }
    }
    Invoke-MySql @"
SELECT COALESCE(c.id,0) AS config_id,t.id AS template_id,t.NAME,
       COALESCE(c.enabled,1) AS enabled,
       CASE WHEN d.id IS NULL THEN 0 ELSE 1 END AS has_drop,
       COALESCE(d.quantity_min,1) AS quantity_min,
       COALESCE(d.quantity_max,1) AS quantity_max,
       COALESCE(d.drop_rate,0) AS drop_rate
FROM mob_template t
LEFT JOIN admin_mob_config c ON c.mob_template_id=t.id
LEFT JOIN admin_spawn_drop d ON d.owner_type='mob' AND d.owner_id=c.id AND d.item_id=$radarId
$where
ORDER BY has_drop DESC,t.id
LIMIT 500;
"@
}

function Save-RadarMobDrops {
    Ensure-SpawnSchema
    $radarId = SqlInt $Id
    if ($radarId -lt 0 -or $radarId -gt 32767) { throw "ID thẻ sưu tầm không hợp lệ." }
    try {
        $rawDrops = @($MobDropsJson | ConvertFrom-Json)
    } catch {
        throw "Danh sách Mob rơi thẻ không đúng JSON."
    }
    $templateIds = New-Object System.Collections.Generic.List[int]
    $values = New-Object System.Collections.Generic.List[string]
    foreach ($drop in $rawDrops) {
        $templateIdText = if ($null -ne $drop.templateId) { [string]$drop.templateId } elseif ($null -ne $drop.mobId) { [string]$drop.mobId } else { "" }
        $templateId = SqlInt -Value $templateIdText -Default -1
        if ($templateId -lt 0) { throw "ID Mob không hợp lệ." }
        if (-not $templateIds.Contains($templateId)) { $templateIds.Add($templateId) }
        $quantityMin = Assert-PositiveInt ([string]$drop.quantityMin) "Số lượng nhỏ nhất" 2000000000
        $quantityMax = Assert-PositiveInt ([string]$drop.quantityMax) "Số lượng lớn nhất" 2000000000
        if ($quantityMin -gt $quantityMax) { throw "Số lượng nhỏ nhất không được lớn hơn số lượng lớn nhất." }
        $rateText = ([string]$drop.dropRate).Replace(',', '.')
        $rateNumber = 0.0
        if (-not [double]::TryParse($rateText, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$rateNumber) -or $rateNumber -lt 0 -or $rateNumber -gt 100) {
            throw "Tỉ lệ rơi phải từ 0 đến 100."
        }
        $rateSql = $rateNumber.ToString('0.####', [Globalization.CultureInfo]::InvariantCulture)
        $values.Add("('mob',(SELECT id FROM admin_mob_config WHERE mob_template_id=$templateId LIMIT 1),$radarId,$quantityMin,$quantityMax,$rateSql,'[]')")
    }
    $existingText = Invoke-MySql "SELECT DISTINCT c.mob_template_id FROM admin_mob_config c JOIN admin_spawn_drop d ON d.owner_type='mob' AND d.owner_id=c.id AND d.item_id=$radarId;"
    foreach ($line in @($existingText -split "`r?`n")) {
        if ($line -match '^\d+$' -and -not $templateIds.Contains([int]$line)) { $templateIds.Add([int]$line) }
    }
    if ($templateIds.Count -eq 0) {
        return "OK`tKhông có Mob nào cần cập nhật drop thẻ ID $radarId."
    }
    $idList = ($templateIds.ToArray() -join ',')
    $insertConfigs = "INSERT INTO admin_mob_config (mob_template_id,enabled,use_time_range,use_interval,interval_minutes) SELECT t.id,1,0,0,1 FROM mob_template t WHERE t.id IN ($idList) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(admin_mob_config.id);"
    $insertDrops = if ($values.Count -gt 0) { "INSERT INTO admin_spawn_drop (owner_type,owner_id,item_id,quantity_min,quantity_max,drop_rate,options_json) VALUES " + ($values -join ',') + ";" } else { "" }
    Invoke-MySql "START TRANSACTION; $insertConfigs DELETE d FROM admin_spawn_drop d JOIN admin_mob_config c ON c.id=d.owner_id WHERE d.owner_type='mob' AND d.item_id=$radarId AND c.mob_template_id IN ($idList); $insertDrops COMMIT;" | Out-Null
    "OK`tĐã lưu " + $rawDrops.Count + " Mob rơi thẻ sưu tầm ID $radarId. Restart server để áp dụng drop."
}

function List-RadarBossDrops {
    Ensure-SpawnSchema
    $radarId = SqlInt $Id
    if ($radarId -lt 0) { throw "ID thẻ sưu tầm không hợp lệ." }
    $dropByBoss = @{}
    $dropText = Invoke-MySql "SELECT owner_id,quantity_min,quantity_max,drop_rate FROM admin_spawn_drop WHERE owner_type='serverboss' AND item_id=$radarId ORDER BY id;"
    $dropLines = @($dropText -split "`r?`n")
    for ($i = 1; $i -lt $dropLines.Count; $i++) {
        $parts = $dropLines[$i] -split "`t", 4
        if ($parts.Count -ge 4) { $dropByBoss[$parts[0]] = $parts }
    }
    $searchLower = $Search.ToLowerInvariant()
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("boss_id`tboss_key`tname`tclass`tdefault_maps`thas_drop`tquantity_min`tquantity_max`tdrop_rate")
    foreach ($boss in (Get-ExistingBossCatalog)) {
        if (-not [string]::IsNullOrWhiteSpace($searchLower) -and
            ([string]$boss.Id -ne $Search) -and
            (-not $boss.Key.ToLowerInvariant().Contains($searchLower)) -and
            (-not $boss.Name.ToLowerInvariant().Contains($searchLower)) -and
            (-not $boss.Class.ToLowerInvariant().Contains($searchLower))) { continue }
        $drop = $dropByBoss[[string]$boss.Id]
        $hasDrop = if ($drop) { 1 } else { 0 }
        $quantityMin = if ($drop) { $drop[1] } else { 1 }
        $quantityMax = if ($drop) { $drop[2] } else { 1 }
        $dropRate = if ($drop) { $drop[3] } else { 0 }
        $rows.Add("$($boss.Id)`t$($boss.Key)`t$($boss.Name)`t$($boss.Class)`t$($boss.Maps)`t$hasDrop`t$quantityMin`t$quantityMax`t$dropRate")
    }
    $rows -join "`r`n"
}

function Save-RadarBossDrops {
    Ensure-SpawnSchema
    $radarId = SqlInt $Id
    if ($radarId -lt 0 -or $radarId -gt 32767) { throw "ID thẻ sưu tầm không hợp lệ." }
    try {
        $rawDrops = @($BossDropsJson | ConvertFrom-Json)
    } catch {
        throw "Danh sách Boss rơi thẻ không đúng JSON."
    }
    $validBossIds = @{}
    foreach ($boss in (Get-ExistingBossCatalog)) { $validBossIds[[string]$boss.Id] = $true }
    $bossIds = New-Object System.Collections.Generic.List[int]
    $values = New-Object System.Collections.Generic.List[string]
    foreach ($drop in $rawDrops) {
        $bossIdText = if ($null -ne $drop.bossId) { [string]$drop.bossId } elseif ($null -ne $drop.ownerId) { [string]$drop.ownerId } else { "" }
        $bossId = SqlInt -Value $bossIdText -Default 0
        if ($bossId -ge 0 -or -not $validBossIds.ContainsKey([string]$bossId)) { throw "ID Boss không hợp lệ hoặc chưa có trong BossesData." }
        if (-not $bossIds.Contains($bossId)) { $bossIds.Add($bossId) }
        $quantityMin = Assert-PositiveInt ([string]$drop.quantityMin) "Số lượng nhỏ nhất" 2000000000
        $quantityMax = Assert-PositiveInt ([string]$drop.quantityMax) "Số lượng lớn nhất" 2000000000
        if ($quantityMin -gt $quantityMax) { throw "Số lượng nhỏ nhất không được lớn hơn số lượng lớn nhất." }
        $rateText = ([string]$drop.dropRate).Replace(',', '.')
        $rateNumber = 0.0
        if (-not [double]::TryParse($rateText, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$rateNumber) -or $rateNumber -lt 0 -or $rateNumber -gt 100) {
            throw "Tỉ lệ rơi phải từ 0 đến 100."
        }
        $rateSql = $rateNumber.ToString('0.####', [Globalization.CultureInfo]::InvariantCulture)
        $values.Add("('serverboss',$bossId,$radarId,$quantityMin,$quantityMax,$rateSql,'[]')")
    }
    $existingText = Invoke-MySql "SELECT DISTINCT owner_id FROM admin_spawn_drop WHERE owner_type='serverboss' AND item_id=$radarId;"
    foreach ($line in @($existingText -split "`r?`n")) {
        if ($line -match '^-?\d+$' -and -not $bossIds.Contains([int]$line)) { $bossIds.Add([int]$line) }
    }
    if ($bossIds.Count -eq 0) {
        return "OK`tKhông có Boss nào cần cập nhật drop thẻ ID $radarId."
    }
    $idList = ($bossIds.ToArray() -join ',')
    $insertDrops = if ($values.Count -gt 0) { "INSERT INTO admin_spawn_drop (owner_type,owner_id,item_id,quantity_min,quantity_max,drop_rate,options_json) VALUES " + ($values -join ',') + ";" } else { "" }
    Invoke-MySql "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='serverboss' AND item_id=$radarId AND owner_id IN ($idList); $insertDrops COMMIT;" | Out-Null
    "OK`tĐã lưu " + $rawDrops.Count + " Boss rơi thẻ sưu tầm ID $radarId. Restart server để áp dụng drop."
}

function List-Shops {
    Invoke-MySql "SELECT s.id, s.npc_id, COALESCE(n.`NAME`, '') AS npc_name, s.tag_name, s.type_shop FROM shop s LEFT JOIN npc_template n ON n.id = s.npc_id ORDER BY s.npc_id, s.id;"
}

function List-Npcs {
    Invoke-MySql "SELECT id, `NAME`, head, body, leg, avatar FROM npc_template ORDER BY id;"
}

function Save-Shop {
    $shopIdNum = SqlInt $ShopId
    if ($shopIdNum -gt 0) {
        Invoke-MySql "UPDATE shop SET npc_id=$(SqlInt $NpcId), tag_name=$(SqlString $TagName), type_shop=$(SqlInt $TypeShop) WHERE id=$shopIdNum;" | Out-Null
        return "OK`tĐã cập nhật shop ID $shopIdNum."
    }

    Invoke-MySql "INSERT INTO shop (npc_id, tag_name, type_shop) VALUES ($(SqlInt $NpcId), $(SqlString $TagName), $(SqlInt $TypeShop));" | Out-Null
    "OK`tĐã thêm shop mới."
}

function List-Tabs {
    Invoke-MySql "SELECT id, shop_id, `NAME` FROM tab_shop WHERE shop_id=$(SqlInt $ShopId) ORDER BY id;"
}

function Save-Tab {
    $tabIdNum = SqlInt $TabId
    if ($tabIdNum -gt 0) {
        Invoke-MySql "UPDATE tab_shop SET shop_id=$(SqlInt $ShopId), `NAME`=$(SqlString $Name) WHERE id=$tabIdNum;" | Out-Null
        return "OK`tĐã cập nhật tab ID $tabIdNum."
    }

    Invoke-MySql "INSERT INTO tab_shop (shop_id, `NAME`) VALUES ($(SqlInt $ShopId), $(SqlString $Name));" | Out-Null
    "OK`tĐã thêm tab shop."
}

function Delete-Tab {
    $tabIdNum = SqlInt $TabId
    if ($tabIdNum -le 0) {
        throw "Chọn tab cần xóa trước."
    }

    $sql = @"
START TRANSACTION;
DELETE FROM item_shop_option WHERE item_shop_id IN (SELECT id FROM item_shop WHERE tab_id = $tabIdNum);
DELETE FROM item_shop WHERE tab_id = $tabIdNum;
DELETE FROM tab_shop WHERE id = $tabIdNum;
COMMIT;
"@
    Invoke-MySql $sql | Out-Null
    "OK`tĐã xóa tab ID $tabIdNum và toàn bộ item/options trong tab."
}

function List-ShopItems {
    Ensure-ItemDefaultOptionSchema
    Invoke-MySql @"
SELECT i.id, i.tab_id, i.temp_id,
       REPLACE(REPLACE(REPLACE(COALESCE(t.`NAME`, ''), CHAR(9), ' '), CHAR(13), ' '), CHAR(10), ' ') AS item_name,
       REPLACE(REPLACE(REPLACE(COALESCE(t.description, ''), CHAR(9), ' '), CHAR(13), ' '), CHAR(10), ' ') AS item_description,
       i.is_new, i.is_sell, i.type_sell, i.cost, i.icon_spec, i.option_mode, i.create_time
FROM item_shop i
LEFT JOIN item_template t ON t.id = i.temp_id
WHERE i.tab_id=$(SqlInt $TabId)
ORDER BY i.create_time DESC, i.id DESC;
"@
}

function List-EquipmentShopItems {
    $where = New-Object System.Collections.Generic.List[string]
    if ($Type -match "^-?\d+$") {
        $where.Add("t.`TYPE` = $(SqlInt $Type)")
    } else {
        $where.Add("t.`TYPE` IN (0,1,2,3,4,5,23,24,25,27,32)")
    }
    if ($Gender -match "^[0-3]$") {
        $where.Add("t.`gender` = $(SqlInt $Gender)")
    }
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match "^\d+$") {
            $where.Add("(i.`temp_id` = $(SqlInt $Search) OR i.`id` = $(SqlInt $Search) OR t.`NAME` LIKE '%$safeSearch%')")
        } else {
            $where.Add("t.`NAME` LIKE '%$safeSearch%'")
        }
    }
    $whereSql = "WHERE " + ($where -join " AND ")
    Invoke-MySql @"
SELECT
  i.id AS item_shop_id,
  i.temp_id,
  COALESCE(t.`NAME`, '') AS item_name,
  t.`TYPE`,
  t.gender,
  i.tab_id,
  COALESCE(ts.`NAME`, '') AS tab_name,
  COALESCE(s.id, 0) AS shop_id,
  COALESCE(n.`NAME`, '') AS npc_name,
  i.type_sell,
  i.cost,
       CASE WHEN i.option_mode IN (0,2)
       THEN (SELECT COUNT(*) FROM item_default_option d0 WHERE d0.item_template_id=i.temp_id) + CASE WHEN i.option_mode=2 THEN COUNT(o.id) ELSE 0 END
       ELSE COUNT(o.id) END AS options_count
FROM item_shop i
JOIN item_template t ON t.id = i.temp_id
LEFT JOIN tab_shop ts ON ts.id = i.tab_id
LEFT JOIN shop s ON s.id = ts.shop_id
LEFT JOIN npc_template n ON n.id = s.npc_id
LEFT JOIN item_shop_option o ON o.item_shop_id = i.id
$whereSql
 GROUP BY i.id, i.temp_id, t.`NAME`, t.`TYPE`, t.gender, i.tab_id, ts.`NAME`, s.id, n.`NAME`, i.type_sell, i.cost, i.option_mode
ORDER BY t.gender, t.`TYPE`, t.id, i.id
LIMIT 500;
"@
}

function Save-ShopItem {
    Ensure-ItemDefaultOptionSchema
    if ((SqlInt $OptionMode) -notin @(0,1,2)) { throw "Chế độ option shop chỉ nhận 0 (mặc định), 1 (tùy chỉnh) hoặc 2 (mặc định + bổ sung)." }
    $itemShopId = SqlInt $Id
    if ($itemShopId -gt 0) {
        Invoke-MySql "UPDATE item_shop SET tab_id=$(SqlInt $TabId), temp_id=$(SqlInt $TempId), is_new=$(SqlInt $IsNew), is_sell=$(SqlInt $IsSell), type_sell=$(SqlInt $TypeSell), cost=$(SqlInt $Cost), icon_spec=$(SqlInt $IconSpec), option_mode=$(SqlInt $OptionMode) WHERE id=$itemShopId;" | Out-Null
        return "OK`tĐã cập nhật vật phẩm shop ID $itemShopId."
    }

    Invoke-MySql "INSERT INTO item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time) VALUES ($(SqlInt $TabId), $(SqlInt $TempId), $(SqlInt $IsNew), $(SqlInt $IsSell), $(SqlInt $TypeSell), $(SqlInt $Cost), $(SqlInt $IconSpec), $(SqlInt $OptionMode), NOW());" | Out-Null
    "OK`tĐã thêm vật phẩm vào shop."
}

function Convert-UniqueIdPayload {
    param([string]$Json, [string]$Label)
    try { $parsed = $Json | ConvertFrom-Json } catch { $parsed = $Json }
    $rawIds = New-Object System.Collections.Generic.List[string]
    foreach ($rawValue in @($parsed)) {
        if ($null -eq $rawValue) { continue }
        if ($rawValue -is [string]) {
            $parts = ($rawValue -split '[,\s;]+')
            foreach ($part in $parts) {
                $token = [string]$part
                if (-not [string]::IsNullOrWhiteSpace($token)) { $rawIds.Add($token.Trim()) }
            }
            continue
        }
        $rawIds.Add(([string]$rawValue).Trim())
    }
    $ids = New-Object System.Collections.Generic.List[int]
    $seen = @{}
    foreach ($rawId in $rawIds) {
        $idText = ([string]$rawId).Trim()
        if ([string]::IsNullOrWhiteSpace($idText)) { continue }
        if ($idText -notmatch '^\d+$' -or [int64]$idText -gt 32767) { throw "$Label có ID không hợp lệ: $idText" }
        if (-not $seen.ContainsKey($idText)) {
            $seen[$idText] = $true
            $ids.Add([int]$idText)
        }
    }
    return $ids.ToArray()
}

function Save-ShopItems {
    Ensure-ItemDefaultOptionSchema
    $tabIdNum = SqlInt $TabId
    if ($tabIdNum -le 0) { throw "Chọn tab shop trước." }
    $ids = @(Convert-UniqueIdPayload -Json $PayloadJson -Label "Danh sách vật phẩm")
    if ($ids.Count -eq 0) { throw "Chọn ít nhất một vật phẩm để thêm vào shop." }
    $idSql = $ids -join ','
    $sql = @"
START TRANSACTION;
INSERT INTO item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT $tabIdNum, t.id, $(SqlInt $IsNew), $(SqlInt $IsSell), $(SqlInt $TypeSell), $(SqlInt $Cost), $(SqlInt $IconSpec), $(SqlInt $OptionMode), NOW()
FROM item_template t
WHERE t.id IN ($idSql)
  AND NOT EXISTS (SELECT 1 FROM item_shop current_item WHERE current_item.tab_id=$tabIdNum AND current_item.temp_id=t.id);
COMMIT;
"@
    Invoke-MySql $sql | Out-Null
    "OK`tĐã xử lý $($ids.Count) vật phẩm cho tab shop; vật phẩm đã tồn tại được giữ nguyên."
}

function Delete-ShopItem {
    $itemShopId = SqlInt $Id
    Invoke-MySql "START TRANSACTION; DELETE FROM item_shop_option WHERE item_shop_id=$itemShopId; DELETE FROM item_shop WHERE id=$itemShopId; COMMIT;" | Out-Null
    "OK`tĐã xóa vật phẩm shop ID $Id."
}

function List-ShopOptions {
    Ensure-ItemDefaultOptionSchema
    Invoke-MySql @"
SELECT o.id, o.item_shop_id, o.option_id, COALESCE(t.`NAME`, '') AS option_name, o.param
FROM item_shop_option o
LEFT JOIN item_option_template t ON t.id=o.option_id
JOIN item_shop s ON s.id=o.item_shop_id
    WHERE o.item_shop_id=$(SqlInt $Id) AND s.option_mode IN (1,2)
UNION ALL
SELECT 0, s.id, d.option_id, COALESCE(t.`NAME`, ''), d.param
FROM item_shop s
JOIN item_default_option d ON d.item_template_id=s.temp_id
LEFT JOIN item_option_template t ON t.id=d.option_id
    WHERE s.id=$(SqlInt $Id) AND s.option_mode IN (0,2)
ORDER BY option_id;
"@
}

function Save-ShopOption {
    Ensure-ItemDefaultOptionSchema
    $rowId = SqlInt $Id
    if ($rowId -gt 0) {
        Invoke-MySql "UPDATE item_shop_option SET option_id=$(SqlInt $OptionId), param=$(SqlInt $Param) WHERE id=$rowId; UPDATE item_shop s JOIN item_shop_option o ON o.item_shop_id=s.id SET s.option_mode=1 WHERE o.id=$rowId;" | Out-Null
        return "OK`tĐã cập nhật option ID $rowId."
    }

    Invoke-MySql "INSERT INTO item_shop_option (item_shop_id, option_id, param) VALUES ($(SqlInt $TempId), $(SqlInt $OptionId), $(SqlInt $Param)); UPDATE item_shop SET option_mode=1 WHERE id=$(SqlInt $TempId);" | Out-Null
    "OK`tĐã thêm option cho vật phẩm shop."
}

function Save-ShopOptions {
    Ensure-ItemDefaultOptionSchema
    $itemShopId = SqlInt $Id
    if ($itemShopId -le 0) { throw "Chọn vật phẩm shop trước." }
    $mode = SqlInt $OptionMode
    if ($mode -notin @(0,1,2)) { throw "Chế độ option shop không hợp lệ." }
    try { $rawOptions = @($PayloadJson | ConvertFrom-Json) } catch { throw "Danh sách option không đúng định dạng JSON." }
    $seen = @{}
    $values = New-Object System.Collections.Generic.List[string]
    foreach ($rawOption in $rawOptions) {
        $optionIdText = [string]$rawOption.id
        $paramText = [string]$rawOption.param
        if ($optionIdText -notmatch '^\d+$' -or [int64]$optionIdText -gt 2147483647) { throw "Option ID không hợp lệ: $optionIdText" }
        if ($paramText -notmatch '^-?\d+$' -or [int64]$paramText -lt -2147483648 -or [int64]$paramText -gt 2147483647) {
            throw "Param của option $optionIdText phải là số nguyên 32-bit."
        }
        if ($seen.ContainsKey($optionIdText)) { throw "Option ID $optionIdText bị chọn trùng." }
        $seen[$optionIdText] = $true
        $values.Add("($itemShopId,$([int]$optionIdText),$([int]$paramText))")
    }
    $sql = New-Object System.Text.StringBuilder
    [void]$sql.AppendLine("START TRANSACTION;")
    [void]$sql.AppendLine("DELETE FROM item_shop_option WHERE item_shop_id=$itemShopId;")
    if ($mode -eq 0 -and $values.Count -gt 0) { $mode = 2 }
    [void]$sql.AppendLine("UPDATE item_shop SET option_mode=$mode WHERE id=$itemShopId;")
    if ($values.Count -gt 0) {
        [void]$sql.AppendLine("INSERT INTO item_shop_option (item_shop_id, option_id, param) VALUES $($values -join ',');")
    }
    [void]$sql.AppendLine("COMMIT;")
    Invoke-MySql $sql.ToString() | Out-Null
    "OK`tĐã lưu $($values.Count) option cho vật phẩm shop ID $itemShopId."
}

function Delete-ShopOption {
    Invoke-MySql "DELETE FROM item_shop_option WHERE id=$(SqlInt $Id);" | Out-Null
    "OK`tĐã xóa option ID $Id."
}

function List-GiftCodes {
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        $where = "WHERE code LIKE '%$safeSearch%'"
    }
    Invoke-MySql @"
SELECT
  id,
  code,
  count_left,
  REPLACE(REPLACE(detail, CHAR(13), ''), CHAR(10), '') AS detail,
  DATE_FORMAT(datecreate, '%Y-%m-%d %H:%i:%s') AS datecreate,
  DATE_FORMAT(expired, '%Y-%m-%d %H:%i:%s') AS expired,
  CASE
    WHEN count_left = 0 THEN 'empty'
    WHEN NOW() < datecreate THEN 'waiting'
    WHEN NOW() > expired THEN 'expired'
    ELSE 'active'
  END AS status
FROM giftcode
$where
ORDER BY id DESC;
"@
}

function Ensure-GiftBoxSchema {
    Invoke-MySql @"
CREATE TABLE IF NOT EXISTS gift_box_config (
  box_template_id INT NOT NULL PRIMARY KEY,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  min_empty_slots INT NOT NULL DEFAULT 1,
  consume_quantity INT NOT NULL DEFAULT 1,
  draw_count INT NOT NULL DEFAULT 1,
  config_json TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@ | Out-Null
}

function New-DefaultGiftBoxJson {
    param([int]$BoxId, [int]$MinSlots, [int]$Stat, [int[]]$ItemIds, [int]$LuckOption, [int]$LuckParam)
    $rewards = @()
    foreach ($itemId in $ItemIds) {
        $rewards += [ordered]@{
            itemId=$itemId; weight=1; quantityMin=1; quantityMax=1; gender=3; initBaseOptions=$true; useDefaultOptions=$false;
            options=@(
                [ordered]@{id=50;paramMin=$Stat;paramMax=$Stat},
                [ordered]@{id=77;paramMin=$Stat;paramMax=$Stat},
                [ordered]@{id=103;paramMin=$Stat;paramMax=$Stat},
                [ordered]@{id=$LuckOption;paramMin=$LuckParam;paramMax=$LuckParam}
            );
            expiry=@(
                [ordered]@{mode='permanent';weight=1;daysMin=0;daysMax=0},
                [ordered]@{mode='days';weight=99;daysMin=3;daysMax=3}
            )
        }
    }
    return (ConvertTo-Json -InputObject ([ordered]@{boxTemplateId=$BoxId;enabled=$true;minEmptySlots=$MinSlots;consumeQuantity=1;drawCount=1;rewards=$rewards}) -Compress -Depth 12)
}

function Ensure-GiftBoxDefaults {
    Ensure-GiftBoxSchema
    $countText = Invoke-MySql "SELECT COUNT(*) FROM gift_box_config;"
    $countLines = $countText -split "`r?`n"
    if ($countLines.Count -gt 1 -and [int]$countLines[1] -gt 0) { return }
    $profiles = @(
        @{id=1776;slots=2;stat=10;items=@(1772,1773);luck=14;luckParam=10},
        @{id=1777;slots=2;stat=25;items=@(1761,1731,1732);luck=236;luckParam=10},
        @{id=1591;slots=2;stat=20;items=@(1588,1589,1595,1587,1593,1590);luck=210;luckParam=2},
        @{id=1594;slots=2;stat=20;items=@(1588,1589,1595,1587,1593,1590);luck=210;luckParam=2},
        @{id=1592;slots=2;stat=25;items=@(1588,1589,1595,1587,1593,1590);luck=210;luckParam=4},
        @{id=1840;slots=5;stat=27;items=@(1588,1589,1595,1587,1593,1590);luck=210;luckParam=4},
        @{id=1755;slots=2;stat=20;items=@(1741,1742,1743,1744,1745,1746);luck=210;luckParam=2},
        @{id=1756;slots=2;stat=20;items=@(1741,1742,1743,1744,1745,1746);luck=210;luckParam=2},
        @{id=1757;slots=2;stat=27;items=@(1741,1742,1743,1744,1745,1746);luck=210;luckParam=4}
    )
    foreach ($profile in $profiles) {
        $json = New-DefaultGiftBoxJson $profile.id $profile.slots $profile.stat $profile.items $profile.luck $profile.luckParam
        Invoke-MySql "INSERT IGNORE INTO gift_box_config (box_template_id,enabled,min_empty_slots,consume_quantity,draw_count,config_json) VALUES ($($profile.id),1,$($profile.slots),1,1,$(SqlString $json));" | Out-Null
    }
}

function List-GiftBoxes {
    Ensure-GiftBoxDefaults
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match '^\d+$') {
            $where = "WHERE g.box_template_id=$(SqlInt $Search) OR t.`NAME` LIKE '%$safeSearch%'"
        } else {
            $where = "WHERE t.`NAME` LIKE '%$safeSearch%'"
        }
    }
    Invoke-MySql @"
SELECT g.box_template_id, COALESCE(t.`NAME`, '') AS box_name, COALESCE(t.`TYPE`, -1) AS item_type,
       g.enabled, g.min_empty_slots, g.consume_quantity, g.draw_count,
       REPLACE(REPLACE(REPLACE(g.config_json, CHAR(9), ' '), CHAR(13), ' '), CHAR(10), '') AS config_json,
       DATE_FORMAT(g.updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at,
       COALESCE(t.icon_id, -1) AS icon_id
FROM gift_box_config g
LEFT JOIN item_template t ON t.id=g.box_template_id
$where
ORDER BY g.box_template_id;
"@
}

function Get-GiftBox {
    Ensure-GiftBoxDefaults
    Invoke-MySql @"
SELECT g.box_template_id, COALESCE(t.`NAME`, '') AS box_name, COALESCE(t.`TYPE`, -1) AS item_type,
       g.enabled, g.min_empty_slots, g.consume_quantity, g.draw_count,
       REPLACE(REPLACE(REPLACE(g.config_json, CHAR(9), ' '), CHAR(13), ' '), CHAR(10), '') AS config_json,
       DATE_FORMAT(g.updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at,
       COALESCE(t.icon_id, -1) AS icon_id
FROM gift_box_config g
LEFT JOIN item_template t ON t.id=g.box_template_id
WHERE g.box_template_id=$(SqlInt $Id)
LIMIT 1;
"@
}

function Parse-JsonInteger {
    param([object]$Value, [string]$Label, [int64]$Min = -2147483648, [int64]$Max = 2147483647)
    $text = [string]$Value
    if ($text -notmatch '^-?\d+$') { throw "$Label phải là số nguyên." }
    $number = [int64]$text
    if ($number -lt $Min -or $number -gt $Max) { throw "$Label nằm ngoài giới hạn cho phép." }
    return [int]$number
}

function Parse-JsonBoolean {
    param([object]$Value, [bool]$Default = $false)
    if ($null -eq $Value) { return $Default }
    return ([string]$Value -eq '1' -or [string]$Value -eq 'true')
}

function Convert-GiftBoxPayload {
    try { $raw = $PayloadJson | ConvertFrom-Json } catch { throw "Cấu hình hộp quà không đúng JSON." }
    if ($null -eq $raw) { throw "Cấu hình hộp quà bị trống." }
    $boxId = Parse-JsonInteger $Id "ID hộp" 0 32767
    $templateCount = Invoke-MySql "SELECT COUNT(*) FROM item_template WHERE id=$boxId;"
    $templateLines = $templateCount -split "`r?`n"
    if ($templateLines.Count -lt 2 -or [int]$templateLines[1] -eq 0) { throw "Không tìm thấy item template ID $boxId." }

    $enabled = if (Parse-JsonBoolean $raw.enabled $true) { 1 } else { 0 }
    $minSlots = Parse-JsonInteger $raw.minEmptySlots "Số ô trống tối thiểu" 1 127
    $consume = Parse-JsonInteger $raw.consumeQuantity "Số lượng hộp tiêu hao" 1 2000000000
    $drawCount = Parse-JsonInteger $raw.drawCount "Số lượt rút" 1 100
    $rawRewards = @($raw.rewards | Where-Object { $null -ne $_ })
    if ($rawRewards.Count -eq 0) { throw "Hộp quà phải có ít nhất một phần thưởng." }

    $normalizedRewards = New-Object System.Collections.Generic.List[object]
    $totalWeight = 0
    foreach ($rawReward in $rawRewards) {
        $itemId = Parse-JsonInteger $rawReward.itemId "ID phần thưởng" 0 32767
        $rewardTemplateCount = Invoke-MySql "SELECT COUNT(*) FROM item_template WHERE id=$itemId;"
        $rewardTemplateLines = $rewardTemplateCount -split "`r?`n"
        if ($rewardTemplateLines.Count -lt 2 -or [int]$rewardTemplateLines[1] -eq 0) {
            throw "Không tìm thấy item template phần thưởng ID $itemId."
        }
        $weight = Parse-JsonInteger $rawReward.weight "Trọng số phần thưởng" 0 2000000000
        $quantityMin = Parse-JsonInteger $rawReward.quantityMin "Số lượng min" 1 2000000000
        $quantityMax = Parse-JsonInteger $rawReward.quantityMax "Số lượng max" 1 2000000000
        if ($quantityMin -gt $quantityMax) { throw "Số lượng min không được lớn hơn max cho vật phẩm ID $itemId." }
        if ($weight -le 0) { continue }
        $gender = Parse-JsonInteger $rawReward.gender "Giới tính phần thưởng" -1 3
        $initBase = Parse-JsonBoolean $rawReward.initBaseOptions $false
        $useDefault = Parse-JsonBoolean $rawReward.useDefaultOptions $false
        $options = New-Object System.Collections.Generic.List[object]
        $seenOptions = @{}
        foreach ($rawOption in @($rawReward.options | Where-Object { $null -ne $_ })) {
            $optionId = Parse-JsonInteger $rawOption.id "ID option" 0 255
            if ($seenOptions.ContainsKey([string]$optionId)) { throw "Option $optionId bị trùng trong phần thưởng ID $itemId." }
            $seenOptions[[string]$optionId] = $true
            $paramMin = Parse-JsonInteger $rawOption.paramMin "Param min option $optionId" -32768 32767
            $paramMax = Parse-JsonInteger $rawOption.paramMax "Param max option $optionId" -32768 32767
            if ($paramMin -gt $paramMax) { throw "Param min không được lớn hơn max của option $optionId." }
            $options.Add([ordered]@{ id=$optionId; paramMin=$paramMin; paramMax=$paramMax })
        }
        $expiry = New-Object System.Collections.Generic.List[object]
        foreach ($rawExpiry in @($rawReward.expiry)) {
            $mode = ([string]$rawExpiry.mode).ToLowerInvariant()
            if ($mode -notin @('permanent','days')) { throw "HSD chỉ nhận permanent hoặc days." }
            $expiryWeight = Parse-JsonInteger $rawExpiry.weight "Trọng số HSD" 0 2000000000
            if ($expiryWeight -le 0) { continue }
            $daysMin = 0; $daysMax = 0
            if ($mode -eq 'days') {
                $daysMin = Parse-JsonInteger $rawExpiry.daysMin "Số ngày min" 1 3650
                $daysMax = Parse-JsonInteger $rawExpiry.daysMax "Số ngày max" 1 3650
                if ($daysMin -gt $daysMax) { throw "Số ngày min không được lớn hơn max." }
            }
            $expiry.Add([ordered]@{ mode=$mode; weight=$expiryWeight; daysMin=$daysMin; daysMax=$daysMax })
        }
        $normalizedRewards.Add([ordered]@{
            itemId=$itemId; weight=$weight; quantityMin=$quantityMin; quantityMax=$quantityMax; gender=$gender;
            initBaseOptions=$initBase; useDefaultOptions=$useDefault; options=$options.ToArray(); expiry=$expiry.ToArray()
        })
        $totalWeight += $weight
    }
    if ($totalWeight -le 0) { throw "Tổng trọng số phần thưởng phải lớn hơn 0." }
    $normalized = [ordered]@{
        boxTemplateId=$boxId; enabled=($enabled -eq 1); minEmptySlots=$minSlots; consumeQuantity=$consume; drawCount=$drawCount;
        rewards=$normalizedRewards.ToArray()
    }
    return (ConvertTo-Json -InputObject $normalized -Compress -Depth 12)
}

function Save-GiftBox {
    Ensure-GiftBoxSchema
    $boxId = Parse-JsonInteger $Id "ID hộp" 0 32767
    $json = Convert-GiftBoxPayload
    $raw = $json | ConvertFrom-Json
    $enabled = if (Parse-JsonBoolean $raw.enabled $true) { 1 } else { 0 }
    $sql = "INSERT INTO gift_box_config (box_template_id, enabled, min_empty_slots, consume_quantity, draw_count, config_json) VALUES ($boxId,$enabled,$(Parse-JsonInteger $raw.minEmptySlots 'Số ô trống' 1 127),$(Parse-JsonInteger $raw.consumeQuantity 'Số lượng tiêu hao' 1 2000000000),$(Parse-JsonInteger $raw.drawCount 'Số lượt rút' 1 100),$(SqlString $json)) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled), min_empty_slots=VALUES(min_empty_slots), consume_quantity=VALUES(consume_quantity), draw_count=VALUES(draw_count), config_json=VALUES(config_json);"
    Invoke-MySql $sql | Out-Null
    "OK`tĐã lưu cấu hình hộp ID $boxId. Restart server để áp dụng."
}

function Delete-GiftBox {
    Ensure-GiftBoxSchema
    $boxId = Parse-JsonInteger $Id "ID hộp" 0 32767
    Invoke-MySql "UPDATE gift_box_config SET enabled=0 WHERE box_template_id=$boxId;" | Out-Null
    "OK`tĐã khóa cấu hình hộp ID $boxId. Restart server để áp dụng."
}

function List-GiftItems {
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safeSearch = $Search.Replace("\", "\\").Replace("'", "''")
        if ($Search -match '^-?\d+$') {
            $where = "WHERE id = $(SqlInt $Search) OR item_name LIKE '%$safeSearch%'"
        } else {
            $where = "WHERE item_name LIKE '%$safeSearch%'"
        }
    }
    Invoke-MySql @"
SELECT id, item_name, item_type
FROM (
  SELECT -1 AS id, 'Vàng' AS item_name, -1 AS item_type
  UNION ALL SELECT -2, 'Ngọc', -1
  UNION ALL SELECT -3, 'Ngọc khóa', -1
  UNION ALL SELECT id, `NAME`, `TYPE` FROM item_template
) gift_items
$where
ORDER BY CASE WHEN id < 0 THEN 0 ELSE 1 END, id
LIMIT 2000;
"@
}

function Convert-GiftDetail {
    if ([string]::IsNullOrWhiteSpace($GiftDetail)) {
        throw "Giftcode phải có ít nhất một phần quà."
    }
    try {
        $rawItems = $GiftDetail | ConvertFrom-Json
    } catch {
        throw "Dữ liệu quà không đúng định dạng JSON."
    }
    if ($rawItems.Count -eq 0) {
        throw "Giftcode phải có ít nhất một phần quà."
    }

    $seenItems = @{}
    $validated = @()
    foreach ($rawItem in $rawItems) {
        $itemIdText = [string]$rawItem.id
        $quantityText = [string]$rawItem.quantity
        if ($itemIdText -notmatch '^-?\d+$' -or [int64]$itemIdText -lt -3 -or [int64]$itemIdText -gt 32767) {
            throw "ID phần quà không hợp lệ: $itemIdText"
        }
        if ($quantityText -notmatch '^\d+$' -or [long]$quantityText -le 0 -or [long]$quantityText -gt 2000000000) {
            throw "Số lượng quà phải từ 1 đến 2.000.000.000."
        }
        if ($seenItems.ContainsKey($itemIdText)) {
            throw "Vật phẩm ID $itemIdText bị chọn trùng."
        }
        $seenItems[$itemIdText] = $true

        $seenOptions = @{}
        $options = @()
        if ($null -ne $rawItem.options) {
            foreach ($rawOption in $rawItem.options) {
                $optionIdText = [string]$rawOption.id
                $paramText = [string]$rawOption.param
                if ($optionIdText -notmatch '^\d+$' -or [int64]$optionIdText -gt 2147483647) {
                    throw "Option ID không hợp lệ: $optionIdText"
                }
                if ($paramText -notmatch '^-?\d+$' -or [int64]$paramText -lt -2147483648 -or [int64]$paramText -gt 2147483647) {
                    throw "Param option $optionIdText phải nằm trong giới hạn số nguyên 32-bit."
                }
                if ($seenOptions.ContainsKey($optionIdText)) {
                    throw "Option ID $optionIdText bị trùng trong vật phẩm $itemIdText."
                }
                $seenOptions[$optionIdText] = $true
                $options += [ordered]@{ id = [int]$optionIdText; param = [int]$paramText }
            }
        }
        $useDefaultOptions = ([string]$rawItem.useDefaultOptions -eq '1' -or [string]$rawItem.useDefaultOptions -eq 'true')
        $validated += [ordered]@{
            id = [int]$itemIdText
            quantity = [int64]$quantityText
            useDefaultOptions = $useDefaultOptions
            options = $options
        }
    }
    return (ConvertTo-Json -InputObject @($validated) -Compress -Depth 8)
}

function Get-GiftDateRange {
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $style = [System.Globalization.DateTimeStyles]::None
    if ($ExpiryMode -eq "days") {
        if ($ValidDays -notmatch '^\d+$' -or [int]$ValidDays -lt 1 -or [int]$ValidDays -gt 3650) {
            throw "Số ngày sử dụng phải từ 1 đến 3650."
        }
        return [pscustomobject]@{
            StartSql = "NOW()"
            EndSql = "DATE_ADD(NOW(), INTERVAL $([int]$ValidDays) DAY)"
        }
    }
    if ($ExpiryMode -ne "range") {
        throw "Kiểu hạn sử dụng không hợp lệ."
    }
    $start = [DateTime]::MinValue
    $end = [DateTime]::MinValue
    if (-not [DateTime]::TryParseExact($StartDate, "yyyy-MM-dd HH:mm:ss", $culture, $style, [ref]$start)) {
        throw "Từ ngày phải theo định dạng YYYY-MM-DD HH:mm:ss."
    }
    if (-not [DateTime]::TryParseExact($EndDate, "yyyy-MM-dd HH:mm:ss", $culture, $style, [ref]$end)) {
        throw "Đến ngày phải theo định dạng YYYY-MM-DD HH:mm:ss."
    }
    if ($end -le $start) {
        throw "Đến ngày phải lớn hơn từ ngày."
    }
    return [pscustomobject]@{
        StartSql = SqlString $start.ToString("yyyy-MM-dd HH:mm:ss")
        EndSql = SqlString $end.ToString("yyyy-MM-dd HH:mm:ss")
    }
}

function Save-GiftCode {
    $giftId = SqlInt $Id
    $normalizedCode = $GiftCode.Trim()
    if ($normalizedCode -notmatch '^[A-Za-z0-9_-]{3,64}$') {
        throw "Giftcode chỉ gồm 3-64 ký tự chữ, số, dấu gạch ngang hoặc gạch dưới."
    }
    if ($CountLeft -notmatch '^-?\d+$' -or ([int64]$CountLeft -lt 0 -and [int64]$CountLeft -ne -1) -or [int64]$CountLeft -gt 2000000000) {
        throw "Số lượt còn lại phải là -1 (không giới hạn) hoặc từ 0 đến 2.000.000.000."
    }
    $duplicate = Invoke-MySql "SELECT COUNT(*) AS total FROM giftcode WHERE code=$(SqlString $normalizedCode) AND id<>$giftId;"
    $duplicateLines = $duplicate -split "`r?`n"
    if ($duplicateLines.Count -gt 1 -and [int]$duplicateLines[1] -gt 0) {
        throw "Giftcode '$normalizedCode' đã tồn tại."
    }

    $detailJson = Convert-GiftDetail
    $range = Get-GiftDateRange
    if ($giftId -gt 0) {
        Invoke-MySql "UPDATE giftcode SET code=$(SqlString $normalizedCode), count_left=$([int64]$CountLeft), detail=$(SqlString $detailJson), datecreate=$($range.StartSql), expired=$($range.EndSql) WHERE id=$giftId;" | Out-Null
        return "OK`tĐã cập nhật Giftcode $normalizedCode. Restart server để áp dụng dữ liệu quà và thời gian mới."
    }
    Invoke-MySql "INSERT INTO giftcode (code, count_left, detail, datecreate, expired) VALUES ($(SqlString $normalizedCode), $([int64]$CountLeft), $(SqlString $detailJson), $($range.StartSql), $($range.EndSql));" | Out-Null
    "OK`tĐã thêm Giftcode $normalizedCode. Restart server để nạp mã mới."
}

function Delete-GiftCode {
    $giftId = SqlInt $Id
    if ($giftId -le 0) {
        throw "Chọn Giftcode cần xóa trước."
    }
    Invoke-MySql "DELETE FROM giftcode WHERE id=$giftId;" | Out-Null
    "OK`tĐã xóa Giftcode ID $giftId. Restart server để xóa mã khỏi bộ nhớ chạy."
}

function Ensure-SpawnSchema {
    $sql = @"
CREATE TABLE IF NOT EXISTS admin_boss_config (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  use_time_range TINYINT(1) NOT NULL DEFAULT 0,
  time_start TIME NULL,
  time_end TIME NULL,
  use_interval TINYINT(1) NOT NULL DEFAULT 0,
  interval_minutes INT NOT NULL DEFAULT 1,
  map_id INT NOT NULL,
  zone_id INT NOT NULL DEFAULT -1,
  spawn_x INT NOT NULL DEFAULT -1,
  spawn_y INT NOT NULL DEFAULT -1,
  gender TINYINT NOT NULL DEFAULT 0,
  head SMALLINT NOT NULL DEFAULT 0,
  body SMALLINT NOT NULL DEFAULT 0,
  leg SMALLINT NOT NULL DEFAULT 0,
  hp INT NOT NULL DEFAULT 1000000,
  damage INT NOT NULL DEFAULT 10000,
  announce TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_boss_enabled (enabled),
  KEY idx_admin_boss_map (map_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_mob_config (
  id INT NOT NULL AUTO_INCREMENT,
  mob_template_id INT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  use_time_range TINYINT(1) NOT NULL DEFAULT 0,
  time_start TIME NULL,
  time_end TIME NULL,
  use_interval TINYINT(1) NOT NULL DEFAULT 0,
  interval_minutes INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_mob_template (mob_template_id),
  KEY idx_admin_mob_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_spawn_drop (
  id INT NOT NULL AUTO_INCREMENT,
  owner_type VARCHAR(16) NOT NULL,
  owner_id INT NOT NULL,
  item_id INT NOT NULL,
  quantity_min INT NOT NULL DEFAULT 1,
  quantity_max INT NOT NULL DEFAULT 1,
  drop_rate DECIMAL(7,4) NOT NULL DEFAULT 100,
  options_json TEXT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_admin_drop_owner (owner_type, owner_id),
  KEY idx_admin_drop_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_boss_override (
  boss_id INT NOT NULL,
  boss_key VARCHAR(80) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  skills_json TEXT NOT NULL,
  use_time_range TINYINT(1) NOT NULL DEFAULT 0,
  time_start TIME NULL,
  time_end TIME NULL,
  use_interval TINYINT(1) NOT NULL DEFAULT 0,
  interval_minutes INT NOT NULL DEFAULT 1,
  map_id INT NOT NULL DEFAULT -1,
  map_ids_json TEXT NOT NULL DEFAULT ('[]'),
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (boss_id),
  KEY idx_admin_boss_override_key (boss_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS use_time_range TINYINT(1) NOT NULL DEFAULT 0 AFTER skills_json;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS time_start TIME NULL AFTER use_time_range;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS time_end TIME NULL AFTER time_start;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS use_interval TINYINT(1) NOT NULL DEFAULT 0 AFTER time_end;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS interval_minutes INT NOT NULL DEFAULT 1 AFTER use_interval;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS map_id INT NOT NULL DEFAULT -1 AFTER interval_minutes;
ALTER TABLE admin_boss_override ADD COLUMN IF NOT EXISTS map_ids_json TEXT NOT NULL DEFAULT ('[]') AFTER map_id;
UPDATE admin_boss_override SET map_ids_json=CONCAT('[',map_id,']') WHERE map_id>=0 AND (map_ids_json IS NULL OR map_ids_json='' OR map_ids_json='[]');
ALTER TABLE admin_spawn_drop MODIFY COLUMN owner_type VARCHAR(16) NOT NULL;
UPDATE admin_spawn_drop SET owner_type='serverboss' WHERE owner_type='serverbo';
"@
    Invoke-MySql $sql | Out-Null
}

function Assert-BoolValue {
    param([string]$Value, [string]$Label)
    if ($Value -notmatch '^[01]$') { throw "$Label chỉ nhận giá trị bật hoặc tắt." }
    [int]$Value
}

function Assert-SpawnTime {
    param([string]$Value, [string]$Label)
    if ($Value -notmatch '^([01]\d|2[0-3]):[0-5]\d$') { throw "$Label phải có dạng HH:mm, ví dụ 08:30." }
    $Value
}

function Assert-PositiveInt {
    param([string]$Value, [string]$Label, [int]$Maximum = 2147483647)
    if ($Value -notmatch '^\d+$' -or [long]$Value -lt 1 -or [long]$Value -gt $Maximum) {
        throw "$Label phải là số nguyên từ 1 đến $Maximum."
    }
    [int]$Value
}

function Get-DropInsertSql {
    param([string]$OwnerType, [int]$ConfigId, [string]$DropPayload = $DropsJson)
    $drops = @()
    if (-not [string]::IsNullOrWhiteSpace($DropPayload)) {
        try { $parsedDrops = $DropPayload | ConvertFrom-Json; $drops = @($parsedDrops) } catch { throw "Danh sách vật phẩm rơi không phải JSON hợp lệ." }
    }
    $values = New-Object System.Collections.Generic.List[string]
    foreach ($drop in $drops) {
        if ([string]$drop.itemId -notmatch '^\d+$' -or [long]$drop.itemId -gt 32767) {
            throw "ID vật phẩm phải là số nguyên từ 0 đến 32767."
        }
        $itemId = [int]$drop.itemId
        $quantityMin = Assert-PositiveInt ([string]$drop.quantityMin) "Số lượng nhỏ nhất" 2000000000
        $quantityMax = Assert-PositiveInt ([string]$drop.quantityMax) "Số lượng lớn nhất" 2000000000
        if ($quantityMin -gt $quantityMax) { throw "Số lượng nhỏ nhất không được lớn hơn số lượng lớn nhất." }
        $rateText = ([string]$drop.dropRate).Replace(',', '.')
        $rateNumber = 0.0
        if (-not [double]::TryParse($rateText, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$rateNumber) -or $rateNumber -lt 0 -or $rateNumber -gt 100) {
            throw "Tỉ lệ rơi phải từ 0 đến 100."
        }
        $options = @()
        if ($null -ne $drop.options) { $options = @($drop.options) }
        $validatedOptions = New-Object System.Collections.Generic.List[object]
        foreach ($option in $options) {
            $optionIdText = [string]$option.id
            $minText = if ($null -ne $option.paramMin) { [string]$option.paramMin } else { [string]$option.param }
            $maxText = if ($null -ne $option.paramMax) { [string]$option.paramMax } else { $minText }
            if ($optionIdText -notmatch '^\d+$' -or $minText -notmatch '^-?\d+$' -or $maxText -notmatch '^-?\d+$') {
                throw "Option vật phẩm phải có ID nguyên không âm và chỉ số min/max là số nguyên."
            }
            $paramMin = [long]$minText; $paramMax = [long]$maxText
            if ($paramMin -lt -2000000000 -or $paramMax -gt 2000000000 -or $paramMin -gt $paramMax) {
                throw "Chỉ số option phải từ -2.000.000.000 đến 2.000.000.000 và min không lớn hơn max."
            }
            $validatedOptions.Add([pscustomobject]@{ id=[int]$optionIdText; paramMin=[int]$paramMin; paramMax=[int]$paramMax })
        }
        $useDefaultOptions = ([string]$drop.useDefaultOptions -eq '1' -or [string]$drop.useDefaultOptions -eq 'true')
        $optionsPayload = [ordered]@{ useDefaultOptions=$useDefaultOptions; options=$validatedOptions.ToArray() }
        $optionsJson = ConvertTo-Json -InputObject $optionsPayload -Compress -Depth 6
        if ([string]::IsNullOrWhiteSpace($optionsJson)) { $optionsJson = '[]' }
        $rateSql = $rateNumber.ToString('0.####', [Globalization.CultureInfo]::InvariantCulture)
        $values.Add("($(SqlString $OwnerType),$ConfigId,$itemId,$quantityMin,$quantityMax,$rateSql,$(SqlString $optionsJson))")
    }
    if ($values.Count -eq 0) { return "" }
    "INSERT INTO admin_spawn_drop (owner_type,owner_id,item_id,quantity_min,quantity_max,drop_rate,options_json) VALUES " + ($values -join ',') + ";"
}

function Get-SpawnDropsExpression {
    param([string]$OwnerType, [string]$OwnerIdExpression)
    @"
COALESCE((SELECT CONCAT('[', GROUP_CONCAT(CONCAT('{`"itemId`":',d.item_id,',`"quantityMin`":',d.quantity_min,',`"quantityMax`":',d.quantity_max,',`"dropRate`":',CAST(d.drop_rate AS CHAR),',`"options`":',d.options_json,'}') ORDER BY d.id SEPARATOR ','), ']') FROM admin_spawn_drop d WHERE d.owner_type='$OwnerType' AND d.owner_id=$OwnerIdExpression), '[]')
"@
}

function List-SpawnMaps {
    Invoke-MySql "SELECT id, NAME, zones FROM map_template ORDER BY id;"
}

function List-SpawnItems {
    Invoke-MySql "SELECT id, NAME FROM item_template ORDER BY id;"
}

function Get-JavaConstantMap {
    param([string]$Path, [string]$Pattern)
    $map = @{}
    $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
    foreach ($match in [regex]::Matches($content, $Pattern)) {
        $map[$match.Groups[1].Value] = [int]$match.Groups[2].Value
    }
    $map
}

function Get-BossDataBlock {
    param([string]$Source, [string]$DataKey)
    $marker = "public static final BossData $DataKey = new BossData("
    $start = $Source.IndexOf($marker, [System.StringComparison]::Ordinal)
    if ($start -lt 0) { return "" }
    $next = $Source.IndexOf("public static final BossData ", $start + $marker.Length, [System.StringComparison]::Ordinal)
    if ($next -lt 0) { $next = $Source.Length }
    $Source.Substring($start, $next - $start)
}

function Get-ExistingBossCatalog {
    $bossIdPath = Join-Path $Root "src\nro\models\boss\BossID.java"
    $skillPath = Join-Path $Root "src\nro\models\skill\Skill.java"
    $managerPath = Join-Path $Root "src\nro\models\boss\Boss_Manager\BossManager.java"
    $dataPath = Join-Path $Root "src\nro\models\boss\BossesData.java"
    $bossIds = Get-JavaConstantMap $bossIdPath 'public\s+static\s+final\s+int\s+([A-Za-z0-9_]+)\s*=\s*(-?\d+)\s*;'
    $skillIds = Get-JavaConstantMap $skillPath 'public\s+static\s+final\s+byte\s+([A-Za-z0-9_]+)\s*=\s*(\d+)\s*;'
    $managerSource = Get-Content -Raw -Encoding UTF8 -LiteralPath $managerPath
    $bossDataSource = Get-Content -Raw -Encoding UTF8 -LiteralPath $dataPath
    $bossFiles = @(Get-ChildItem -Path (Join-Path $Root "src\nro\models\boss") -Recurse -Filter "*.java")
    $catalog = New-Object System.Collections.Generic.List[object]
    $seen = @{}
    $casePattern = 'case\s+BossID\.([A-Za-z0-9_]+)\s*->\s*(?:\r?\n\s*)?new\s+([A-Za-z0-9_]+)\s*\('
    foreach ($match in [regex]::Matches($managerSource, $casePattern)) {
        $key = $match.Groups[1].Value
        $className = $match.Groups[2].Value
        if (-not $bossIds.ContainsKey($key) -or $seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $classFile = $bossFiles | Where-Object { $_.BaseName -eq $className } | Where-Object {
            (Get-Content -Raw -Encoding UTF8 -LiteralPath $_.FullName).Contains("BossID.$key")
        } | Select-Object -First 1
        $dataKeys = @()
        if ($classFile) {
            $classSource = Get-Content -Raw -Encoding UTF8 -LiteralPath $classFile.FullName
            $dataKeys = @([regex]::Matches($classSource, 'BossesData\.([A-Z0-9_]+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
        }
        $displayName = ($key.ToLowerInvariant() -replace '_', ' ')
        $displayName = (Get-Culture).TextInfo.ToTitleCase($displayName)
        $foundDataName = $false
        $maps = New-Object System.Collections.Generic.List[int]
        $skills = New-Object System.Collections.Generic.List[object]
        $skillSeen = @{}
        foreach ($dataKey in $dataKeys) {
            $block = Get-BossDataBlock $bossDataSource $dataKey
            if ([string]::IsNullOrWhiteSpace($block)) { continue }
            $nameMatch = [regex]::Match($block, 'new\s+BossData\s*\(\s*"([^"]+)"')
            if ($nameMatch.Success -and -not $foundDataName) {
                $displayName = $nameMatch.Groups[1].Value
                $foundDataName = $true
            }
            $mapMatch = [regex]::Match($block, 'new\s+int\[\]\s*\{([^}]*)\}\s*,?\s*//map join')
            if ($mapMatch.Success) {
                foreach ($mapText in ($mapMatch.Groups[1].Value -split ',')) {
                    $cleanMap = ($mapText -replace '_', '').Trim()
                    if ($cleanMap -match '^\d+$' -and -not $maps.Contains([int]$cleanMap)) { $maps.Add([int]$cleanMap) }
                }
            }
            foreach ($skillMatch in [regex]::Matches($block, '\{\s*Skill\.([A-Z0-9_]+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?\s*\}')) {
                $skillKey = $skillMatch.Groups[1].Value
                if (-not $skillIds.ContainsKey($skillKey)) { continue }
                $skillId = [int]$skillIds[$skillKey]
                $level = [int]$skillMatch.Groups[2].Value
                $cooldown = if ($skillMatch.Groups[3].Success) { [int]$skillMatch.Groups[3].Value } else { 1000 }
                $unique = "$skillId`:$level`:$cooldown"
                if (-not $skillSeen.ContainsKey($unique)) {
                    $skillSeen[$unique] = $true
                    $skills.Add([pscustomobject]@{ id=$skillId; level=$level; cooldown=$cooldown })
                }
            }
        }
        $catalog.Add([pscustomobject]@{
            Id=[int]$bossIds[$key]; Key=$key; Name=$displayName; Class=$className;
            Maps=($maps -join ','); Skills=$skills.ToArray()
        })
    }
    $catalog | Sort-Object Name, Id
}

function List-BossSkillCatalog {
    Invoke-MySql "SELECT id, MAX(NAME) AS NAME, MAX(max_point) AS max_point FROM skill_template GROUP BY id ORDER BY id;"
}

function List-ExistingBosses {
    Ensure-SpawnSchema
    $overrides = @{}
    $mapNames = @{}
    $mapText = Invoke-MySql "SELECT id,NAME FROM map_template;"
    $mapLines = @($mapText -split "`r?`n")
    for ($i = 1; $i -lt $mapLines.Count; $i++) {
        $mapParts = $mapLines[$i] -split "`t", 2
        if ($mapParts.Count -ge 2) { $mapNames[$mapParts[0]] = $mapParts[1] }
    }
    $dropsByBoss = @{}
    $dropText = Invoke-MySql "SELECT owner_id,item_id,quantity_min,quantity_max,drop_rate,options_json FROM admin_spawn_drop WHERE owner_type='serverboss' ORDER BY id;"
    $dropLines = @($dropText -split "`r?`n")
    for ($i = 1; $i -lt $dropLines.Count; $i++) {
        $dropParts = $dropLines[$i] -split "`t", 6
        if ($dropParts.Count -lt 6) { continue }
        if (-not $dropsByBoss.ContainsKey($dropParts[0])) { $dropsByBoss[$dropParts[0]] = New-Object System.Collections.ArrayList }
        try { $dropOptions = $dropParts[5] | ConvertFrom-Json } catch { $dropOptions = @() }
        [void]$dropsByBoss[$dropParts[0]].Add([pscustomobject]@{
            itemId=[int]$dropParts[1]; quantityMin=[int]$dropParts[2]; quantityMax=[int]$dropParts[3];
            dropRate=[double]::Parse($dropParts[4], [Globalization.CultureInfo]::InvariantCulture); options=@($dropOptions)
        })
    }
    $overrideText = Invoke-MySql "SELECT boss_id,boss_key,enabled,skills_json,use_time_range,COALESCE(TIME_FORMAT(time_start,'%H:%i'),''),COALESCE(TIME_FORMAT(time_end,'%H:%i'),''),use_interval,interval_minutes,map_id,COALESCE(NULLIF(map_ids_json,''),'[]') FROM admin_boss_override;"
    $lines = @($overrideText -split "`r?`n")
    for ($i = 1; $i -lt $lines.Count; $i++) {
        $parts = $lines[$i] -split "`t", 11
        if ($parts.Count -ge 11) { $overrides[$parts[0]] = $parts }
    }
    $searchLower = $Search.ToLowerInvariant()
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("boss_id`tboss_key`tname`tclass`tdefault_maps`tdefault_skills`tconfigured_skills`thas_override`tenabled`tuse_time_range`ttime_start`ttime_end`tuse_interval`tinterval_minutes`tmap_ids_json`tmap_names`tdrops_json")
    foreach ($boss in (Get-ExistingBossCatalog)) {
        if (-not [string]::IsNullOrWhiteSpace($searchLower) -and
            ([string]$boss.Id -ne $Search) -and
            (-not $boss.Key.ToLowerInvariant().Contains($searchLower)) -and
            (-not $boss.Name.ToLowerInvariant().Contains($searchLower)) -and
            (-not $boss.Class.ToLowerInvariant().Contains($searchLower))) { continue }
        $defaultJson = ConvertTo-Json -InputObject @($boss.Skills) -Compress -Depth 5
        $override = $overrides[[string]$boss.Id]
        $configuredJson = if ($override) { $override[3] } else { $defaultJson }
        $hasOverride = if ($override) { 1 } else { 0 }
        $enabledValue = if ($override) { $override[2] } else { 1 }
        $useRangeValue = if ($override) { $override[4] } else { 0 }
        $startValue = if ($override) { $override[5] } else { "" }
        $endValue = if ($override) { $override[6] } else { "" }
        $useIntervalValue = if ($override) { $override[7] } else { 0 }
        $minutesValue = if ($override) { $override[8] } else { 1 }
        $mapIdsJson = if ($override) { $override[10] } else { '[]' }
        try {
            $parsedConfiguredMapIds = $mapIdsJson | ConvertFrom-Json
            $configuredMapIds = @($parsedConfiguredMapIds | ForEach-Object { $_ })
        } catch { $configuredMapIds = @() }
        $configuredMapNames = New-Object System.Collections.Generic.List[string]
        foreach ($configuredMapId in $configuredMapIds) {
            $mapKey = [string]$configuredMapId
            $configuredMapNames.Add($(if ($mapNames.ContainsKey($mapKey)) { "$mapKey - $($mapNames[$mapKey])" } else { $mapKey }))
        }
        $mapName = $configuredMapNames -join ', '
        $bossDrops = $dropsByBoss[[string]$boss.Id]
        $dropJson = if ($bossDrops) { ConvertTo-Json -InputObject $bossDrops.ToArray() -Compress -Depth 6 } else { '[]' }
        $rows.Add("$($boss.Id)`t$($boss.Key)`t$($boss.Name)`t$($boss.Class)`t$($boss.Maps)`t$defaultJson`t$configuredJson`t$hasOverride`t$enabledValue`t$useRangeValue`t$startValue`t$endValue`t$useIntervalValue`t$minutesValue`t$mapIdsJson`t$mapName`t$dropJson")
    }
    $rows -join "`r`n"
}

function Save-BossOverride {
    Ensure-SpawnSchema
    $bossId = SqlInt $OwnerId
    if ($bossId -ge 0) { throw "ID Boss server phải là số âm hợp lệ." }
    if ([string]::IsNullOrWhiteSpace($Name)) { throw "Thiếu mã Boss server." }
    $enabledValue = Assert-BoolValue $Enabled "Trạng thái Boss"
    try { $parsedSkills = $SkillsJson | ConvertFrom-Json; $skills = @($parsedSkills) } catch { throw "Danh sách skill không phải JSON hợp lệ." }
    if ($skills.Count -eq 0) { throw "Boss phải có ít nhất một skill." }
    $validated = New-Object System.Collections.Generic.List[object]
    foreach ($skill in $skills) {
        $skillId = SqlInt -Value ([string]$skill.id) -Default -1
        $levelValue = SqlInt -Value ([string]$skill.level) -Default -1
        $cooldownValue = SqlInt -Value ([string]$skill.cooldown) -Default -1
        if ($skillId -lt 0) { throw "ID skill không hợp lệ." }
        if ($levelValue -lt 1 -or $levelValue -gt 7) { throw "Cấp skill phải từ 1 đến 7." }
        if ($cooldownValue -lt 50 -or $cooldownValue -gt 3600000) { throw "Hồi chiêu phải từ 50 đến 3.600.000 ms." }
        $validated.Add([pscustomobject]@{ id=$skillId; level=$levelValue; cooldown=$cooldownValue })
    }
    $skillsJson = ConvertTo-Json -InputObject $validated.ToArray() -Compress -Depth 5
    $rangeValue = Assert-BoolValue $UseTimeRange "Chế độ khoảng giờ"
    $intervalValue = Assert-BoolValue $UseInterval "Chế độ chu kỳ"
    $startSql = "NULL"; $endSql = "NULL"
    if ($rangeValue -eq 1) {
        $startSql = SqlString (Assert-SpawnTime $TimeStart "Giờ bắt đầu")
        $endSql = SqlString (Assert-SpawnTime $TimeEnd "Giờ kết thúc")
    }
    $interval = if ($intervalValue -eq 1) { Assert-PositiveInt $IntervalMinutes "Chu kỳ xuất hiện" 525600 } else { 1 }
    try { $parsedMapIds = $MapIdsJson | ConvertFrom-Json; $mapIds = @($parsedMapIds) } catch { throw "Danh sách map không phải JSON hợp lệ." }
    $validatedMapIds = New-Object System.Collections.Generic.List[int]
    foreach ($configuredMapId in $mapIds) {
        if ([string]$configuredMapId -notmatch '^\d+$') { throw "ID map phải là số nguyên không âm." }
        $mapNumber = [int]$configuredMapId
        if (-not $validatedMapIds.Contains($mapNumber)) { $validatedMapIds.Add($mapNumber) }
    }
    $mapIdsJson = ConvertTo-Json -InputObject $validatedMapIds.ToArray() -Compress
    if ([string]::IsNullOrWhiteSpace($mapIdsJson)) { $mapIdsJson = '[]' }
    $mapValue = if ($validatedMapIds.Count -gt 0) { $validatedMapIds[0] } else { -1 }
    $dropInsert = Get-DropInsertSql -OwnerType 'serverboss' -ConfigId $bossId -DropPayload $DropsJson
    Invoke-MySql "START TRANSACTION; INSERT INTO admin_boss_override (boss_id,boss_key,enabled,skills_json,use_time_range,time_start,time_end,use_interval,interval_minutes,map_id,map_ids_json) VALUES ($bossId,$(SqlString $Name),$enabledValue,$(SqlString $skillsJson),$rangeValue,$startSql,$endSql,$intervalValue,$interval,$mapValue,$(SqlString $mapIdsJson)) ON DUPLICATE KEY UPDATE boss_key=VALUES(boss_key),enabled=VALUES(enabled),skills_json=VALUES(skills_json),use_time_range=VALUES(use_time_range),time_start=VALUES(time_start),time_end=VALUES(time_end),use_interval=VALUES(use_interval),interval_minutes=VALUES(interval_minutes),map_id=VALUES(map_id),map_ids_json=VALUES(map_ids_json); DELETE FROM admin_spawn_drop WHERE owner_type='serverboss' AND owner_id=$bossId; $dropInsert COMMIT;" | Out-Null
    "OK`tĐã lưu cấu hình cho Boss $Name ($bossId). Restart server để áp dụng cho lần khởi tạo/respawn tiếp theo."
}

function Delete-BossOverride {
    Ensure-SpawnSchema
    $bossId = SqlInt $OwnerId
    if ($bossId -ge 0) { throw "Chọn Boss đã có cấu hình tùy chỉnh." }
    Invoke-MySql "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='serverboss' AND owner_id=$bossId; DELETE FROM admin_boss_override WHERE boss_id=$bossId; COMMIT;" | Out-Null
    "OK`tĐã trả Boss về toàn bộ cấu hình mặc định trong mã nguồn. Restart server để áp dụng."
}

function List-AdminBosses {
    Ensure-SpawnSchema
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("\", "\\").Replace("'", "''")
        $where = if ($Search -match '^\d+$') { "WHERE b.id=$(SqlInt $Search) OR b.name LIKE '%$safe%'" } else { "WHERE b.name LIKE '%$safe%'" }
    }
    $drops = Get-SpawnDropsExpression 'boss' 'b.id'
    Invoke-MySql "SELECT b.id,b.name,b.enabled,b.use_time_range,COALESCE(TIME_FORMAT(b.time_start,'%H:%i'),''),COALESCE(TIME_FORMAT(b.time_end,'%H:%i'),''),b.use_interval,b.interval_minutes,b.map_id,COALESCE(m.NAME,''),b.zone_id,b.spawn_x,b.spawn_y,b.gender,b.head,b.body,b.leg,b.hp,b.damage,b.announce,$drops AS drops_json FROM admin_boss_config b LEFT JOIN map_template m ON m.id=b.map_id $where ORDER BY b.id DESC;"
}

function Save-AdminBoss {
    Ensure-SpawnSchema
    if ([string]::IsNullOrWhiteSpace($Name)) { throw "Tên Boss không được để trống." }
    $enabledValue = Assert-BoolValue $Enabled "Trạng thái"
    $rangeValue = Assert-BoolValue $UseTimeRange "Chế độ khoảng giờ"
    $intervalValue = Assert-BoolValue $UseInterval "Chế độ chu kỳ"
    $announceValue = Assert-BoolValue $Announce "Thông báo toàn server"
    $startSql = "NULL"; $endSql = "NULL"
    if ($rangeValue -eq 1) {
        $startSql = SqlString (Assert-SpawnTime $TimeStart "Giờ bắt đầu")
        $endSql = SqlString (Assert-SpawnTime $TimeEnd "Giờ kết thúc")
    }
    $interval = if ($intervalValue -eq 1) { Assert-PositiveInt $IntervalMinutes "Chu kỳ xuất hiện" 525600 } else { 1 }
    $map = SqlInt $MapId -1
    if ($map -lt 0) { throw "Hãy chọn map xuất hiện cho Boss." }
    $hpValue = Assert-PositiveInt $Hp "HP" 2147483647
    $damageValue = Assert-PositiveInt $Damage "Sát thương" 2147483647
    $bossId = SqlInt $OwnerId
    $fields = "name=$(SqlString $Name),enabled=$enabledValue,use_time_range=$rangeValue,time_start=$startSql,time_end=$endSql,use_interval=$intervalValue,interval_minutes=$interval,map_id=$map,zone_id=$(SqlInt $ZoneId -1),spawn_x=$(SqlInt $SpawnX -1),spawn_y=$(SqlInt $SpawnY -1),gender=$(SqlInt $Gender),head=$(SqlInt $Head),body=$(SqlInt $Body),leg=$(SqlInt $Leg),hp=$hpValue,damage=$damageValue,announce=$announceValue"
    if ($bossId -gt 0) {
        Invoke-MySql "UPDATE admin_boss_config SET $fields WHERE id=$bossId;" | Out-Null
    } else {
        $insert = Invoke-MySql "INSERT INTO admin_boss_config SET $fields; SELECT LAST_INSERT_ID() AS id;"
        $lines = @($insert -split "`r?`n" | Where-Object { $_ -match '^\d+$' })
        if ($lines.Count -eq 0) { throw "Không lấy được ID Boss vừa tạo." }
        $bossId = [int]$lines[-1]
    }
    $dropInsert = Get-DropInsertSql 'boss' $bossId
    $dropSql = "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='boss' AND owner_id=$bossId; $dropInsert COMMIT;"
    Invoke-MySql $dropSql | Out-Null
    "OK`tĐã lưu Boss ID $bossId. Restart server để áp dụng cấu hình runtime."
}

function Delete-AdminBoss {
    Ensure-SpawnSchema
    $bossId = SqlInt $OwnerId
    if ($bossId -le 0) { throw "Chọn Boss cần xóa trước." }
    Invoke-MySql "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='boss' AND owner_id=$bossId; DELETE FROM admin_boss_config WHERE id=$bossId; COMMIT;" | Out-Null
    "OK`tĐã xóa Boss ID $bossId. Restart server để gỡ Boss khỏi runtime."
}

function List-AdminMobs {
    Ensure-SpawnSchema
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("\", "\\").Replace("'", "''")
        $where = if ($Search -match '^\d+$') { "WHERE t.id=$(SqlInt $Search) OR t.NAME LIKE '%$safe%'" } else { "WHERE t.NAME LIKE '%$safe%'" }
    }
    $drops = Get-SpawnDropsExpression 'mob' 'c.id'
    Invoke-MySql "SELECT COALESCE(c.id,0) AS config_id,t.id AS template_id,t.NAME,t.hp,t.percent_dame,COALESCE(c.enabled,1),COALESCE(c.use_time_range,0),COALESCE(TIME_FORMAT(c.time_start,'%H:%i'),''),COALESCE(TIME_FORMAT(c.time_end,'%H:%i'),''),COALESCE(c.use_interval,0),COALESCE(c.interval_minutes,1),CASE WHEN c.id IS NULL THEN '[]' ELSE $drops END AS drops_json FROM mob_template t LEFT JOIN admin_mob_config c ON c.mob_template_id=t.id $where ORDER BY t.id;"
}

function Save-AdminMob {
    Ensure-SpawnSchema
    $template = SqlInt $TemplateId -1
    if ($template -lt 0) { throw "Hãy chọn Mob cần cấu hình." }
    $enabledValue = Assert-BoolValue $Enabled "Trạng thái"
    $rangeValue = Assert-BoolValue $UseTimeRange "Chế độ khoảng giờ"
    $intervalValue = Assert-BoolValue $UseInterval "Chế độ chu kỳ"
    $startSql = "NULL"; $endSql = "NULL"
    if ($rangeValue -eq 1) {
        $startSql = SqlString (Assert-SpawnTime $TimeStart "Giờ bắt đầu")
        $endSql = SqlString (Assert-SpawnTime $TimeEnd "Giờ kết thúc")
    }
    $interval = if ($intervalValue -eq 1) { Assert-PositiveInt $IntervalMinutes "Chu kỳ xuất hiện" 525600 } else { 1 }
    Invoke-MySql "INSERT INTO admin_mob_config (mob_template_id,enabled,use_time_range,time_start,time_end,use_interval,interval_minutes) VALUES ($template,$enabledValue,$rangeValue,$startSql,$endSql,$intervalValue,$interval) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),enabled=VALUES(enabled),use_time_range=VALUES(use_time_range),time_start=VALUES(time_start),time_end=VALUES(time_end),use_interval=VALUES(use_interval),interval_minutes=VALUES(interval_minutes); SELECT LAST_INSERT_ID() AS id;" | Out-Null
    $idResult = Invoke-MySql "SELECT id FROM admin_mob_config WHERE mob_template_id=$template LIMIT 1;"
    $lines = @($idResult -split "`r?`n" | Where-Object { $_ -match '^\d+$' })
    if ($lines.Count -eq 0) { throw "Không lấy được ID cấu hình Mob." }
    $configId = [int]$lines[-1]
    $dropInsert = Get-DropInsertSql 'mob' $configId
    Invoke-MySql "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='mob' AND owner_id=$configId; $dropInsert COMMIT;" | Out-Null
    "OK`tĐã lưu cấu hình Mob template $template. Restart server để áp dụng runtime."
}

function Delete-AdminMob {
    Ensure-SpawnSchema
    $configId = SqlInt $OwnerId
    if ($configId -le 0) { throw "Mob này chưa có cấu hình tùy chỉnh để xóa." }
    Invoke-MySql "START TRANSACTION; DELETE FROM admin_spawn_drop WHERE owner_type='mob' AND owner_id=$configId; DELETE FROM admin_mob_config WHERE id=$configId; COMMIT;" | Out-Null
    "OK`tĐã gỡ cấu hình Mob. Mob trở về lịch và drop mặc định sau khi restart server."
}

function Ensure-AuditSchema {
    Invoke-MySql @"
CREATE TABLE IF NOT EXISTS admin_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  action_name VARCHAR(64) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reversible TINYINT(1) NOT NULL DEFAULT 0,
  rollback_payload LONGTEXT NULL,
  result_message TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  undone_at TIMESTAMP NULL DEFAULT NULL,
  undone_by_log_id BIGINT NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_admin_change_created (created_at),
  KEY idx_admin_change_undo (reversible,status,undone_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@ | Out-Null
}

function Find-MySqlDump {
    $mysql = Find-MySql
    $candidate = Join-Path (Split-Path -Parent $mysql) "mysqldump.exe"
    if (Test-Path $candidate) { return $candidate }
    $cmd = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Không tìm thấy mysqldump.exe để tạo điểm hoàn tác."
}

function Invoke-MySqlDump {
    param([string]$Table, [string]$Where)
    if ($Table -notmatch '^[A-Za-z0-9_]+$') { throw "Tên bảng snapshot không hợp lệ." }
    $config = Get-ConfigMap
    $dump = Find-MySqlDump
    $hostName = if ($config["database.host"]) { $config["database.host"] } else { "localhost" }
    $port = if ($config["database.port"]) { $config["database.port"] } else { "3306" }
    $dbName = if ($config["database.name"]) { $config["database.name"] } else { "team2026" }
    $user = if ($config["database.user"]) { $config["database.user"] } else { "root" }
    $pass = if ($config.ContainsKey("database.pass")) { $config["database.pass"] } else { "" }
    $args = @("--no-create-info", "--skip-triggers", "--compact", "--complete-insert", "--skip-comments",
        "--skip-add-locks", "--skip-disable-keys", "--single-transaction", "--skip-lock-tables",
        "--default-character-set=utf8mb4",
        "-h", $hostName, "-P", $port, "-u", $user)
    if (-not [string]::IsNullOrEmpty($pass)) { $args += "--password=$pass" }
    $args += "--where=$Where"
    $args += @($dbName, $Table)
    $output = & $dump @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    $sqlLines = @($output | Where-Object { $_ -notmatch '^mysqldump(?:\.exe)?: \[Warning\]' })
    ($sqlLines -join [Environment]::NewLine)
}

function New-DbAuditSnapshot {
    param([string]$Table, [string]$Where)
    [pscustomobject]@{ table=$Table; where=$Where; insertSql=(Invoke-MySqlDump -Table $Table -Where $Where) }
}

function Get-JsonArrayCount {
    param([string]$Json)
    try {
        $parsed = $Json | ConvertFrom-Json
        return @($parsed).Count
    } catch { return 0 }
}

function Get-AuditSummary {
    param([string]$ActionName)
    switch ($ActionName) {
        "saveitem" { "Lưu vật phẩm ID $Id - $Name (type $Type, gender $Gender)" }
        "saveshop" { "Lưu shop $(if ($ShopId) { "ID $ShopId" } else { $TagName }) cho NPC $NpcId, type $TypeShop" }
        "savetab" { "Lưu tab shop $(if ($TabId) { "ID $TabId" } else { $Name }) vào shop $ShopId" }
        "deletetab" { "Xóa tab shop ID $TabId" }
        "saveshopitem" { "Lưu item shop $(if ($Id) { "ID $Id" } else { "template $TempId" }), tab $TabId, giá $Cost loại $TypeSell" }
        "saveshopitems" { "Thêm $(Get-JsonArrayCount $PayloadJson) vật phẩm vào tab shop $TabId, giá $Cost loại $TypeSell" }
        "deleteshopitem" { "Xóa item shop ID $Id" }
        "saveshopoption" { "Lưu option shop $(if ($Id) { "ID $Id" } else { "option $OptionId" }), param $Param" }
        "saveshopoptions" { "Lưu $(Get-JsonArrayCount $PayloadJson) option cho item shop ID $Id" }
        "deleteshopoption" { "Xóa option shop ID $Id" }
        "saveitemdefaultoptions" { "Lưu $(Get-JsonArrayCount $PayloadJson) option mặc định cho vật phẩm ID $Id" }
        "saveitemdefaultoptionsbulk" { "Lưu option mặc định cho $(Get-JsonArrayCount $PayloadJson) vật phẩm" }
        "savegiftcode" { "Lưu Giftcode $GiftCode, lượt $CountLeft, $(Get-JsonArrayCount $GiftDetail) phần quà" }
        "deletegiftcode" { "Xóa Giftcode ID $Id" }
        "savegiftbox" { "Lưu cấu hình Hộp quà ID $Id" }
        "deletegiftbox" { "Khóa cấu hình Hộp quà ID $Id" }
        "saveradarcard" { "Lưu thẻ sưu tầm ID $Id - $Name, $(Get-JsonArrayCount $OptionsJson) option" }
        "deleteradarcard" { "Xóa thẻ sưu tầm ID $Id" }
        "saveradarmobdrops" { "Lưu Mob rơi thẻ sưu tầm ID $Id, $(Get-JsonArrayCount $MobDropsJson) Mob" }
        "saveradarbossdrops" { "Lưu Boss rơi thẻ sưu tầm ID $Id, $(Get-JsonArrayCount $BossDropsJson) Boss" }
        "savebossoverride" { "Lưu Boss $Name ($OwnerId): $(Get-JsonArrayCount $MapIdsJson) map, $(Get-JsonArrayCount $SkillsJson) skill, $(Get-JsonArrayCount $DropsJson) drop, chu kỳ $(if ($UseInterval -eq '1') { "$IntervalMinutes phút" } else { 'mặc định' })" }
        "deletebossoverride" { "Trả Boss $OwnerId về mặc định" }
        "saveadminboss" { "Lưu Boss tùy chỉnh $Name" }
        "deleteadminboss" { "Xóa Boss tùy chỉnh ID $OwnerId" }
        "saveadminmob" { "Lưu Mob template ${TemplateId}: $(Get-JsonArrayCount $DropsJson) drop, chu kỳ $(if ($UseInterval -eq '1') { "$IntervalMinutes phút" } else { 'mặc định' })" }
        "deleteadminmob" { "Trả Mob config $OwnerId về mặc định" }
        "saveeventconfig" { "Lưu cấu hình ${EventValue}: drop x$DropMultiplier" }
        "saveeventboss" { "Gắn Boss $BossId ($Name), số lượng $BossQuantity, $(Get-JsonArrayCount $MapIdsJson) map vào $EventValue" }
        "deleteeventboss" { "Xóa Boss $BossId khỏi $EventValue" }
        "saveeventitem" { "Gắn Item $TemplateId, nguồn $SourceType/$SourceId vào $EventValue" }
        "deleteeventitem" { "Xóa dòng vật phẩm sự kiện ID $Id khỏi $EventValue" }
        "savecombineconfig" { "Đổi Combine $ConfigKey = $ConfigValue" }
        "resetcombineconfig" { "Khôi phục cấu hình Combine mặc định" }
        "setevent" { "Đổi sự kiện server thành $EventValue" }
        "setexp" { "Đổi tỉ lệ TNSM thành $ExpRate" }
        "saveplayerconfig" { "Đổi cấu hình Player $ConfigKey = $ConfigValue" }
        "resetplayerconfig" { "Khôi phục cấu hình Player $ConfigKey về mặc định" }
        "savepetconfig" { "Đổi cấu hình Đệ tử $ConfigKey = $ConfigValue" }
        "resetpetconfig" { "Khôi phục cấu hình Đệ tử $ConfigKey về mặc định" }
        "savetaskmain" { "Luu nhiem vu chinh ID $Id - $Name" }
        "savetasksub" { "Luu buoc nhiem vu ID $Id thuoc nhiem vu $OwnerId" }
        "savetasktemplate" { "Luu template $(Get-TaskTemplateLabel) ID $Id - $Name" }
        "savebadgestask" { "Luu nhiem vu danh hieu ID $Id - $Name" }
        "savetaskreward" { "Luu phan thuong nhiem vu $Type ID $Id" }
        "savetaskconfig" { "Doi cau hinh nhiem vu $ConfigKey = $ConfigValue" }
        "resettaskconfig" { "Khoi phuc cau hinh nhiem vu $ConfigKey ve mac dinh" }
        "saveplayercore" { "Cập nhật chỉ số, tài sản và sức chứa Player ID $Id" }
        "rescueplayer" { "Cứu hộ Player ID $Id về map nhà" }
        default { $ActionName }
    }
}

function Get-AuditContext {
    param([string]$ActionName)
    $snapshots = New-Object System.Collections.Generic.List[object]
    $fileSnapshots = New-Object System.Collections.Generic.List[object]
    switch ($ActionName) {
        "saveitem" { $snapshots.Add((New-DbAuditSnapshot "item_template" "id=$(SqlInt $Id)")) }
        "saveshop" {
            $where = if ((SqlInt $ShopId) -gt 0) { "id=$(SqlInt $ShopId)" } else { "npc_id=$(SqlInt $NpcId) AND tag_name=$(SqlString $TagName) AND type_shop=$(SqlInt $TypeShop)" }
            $snapshots.Add((New-DbAuditSnapshot "shop" $where))
            $snapshots.Add((New-DbAuditSnapshot "tab_shop" "shop_id IN (SELECT id FROM shop WHERE $where)"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop" "tab_id IN (SELECT id FROM tab_shop WHERE shop_id IN (SELECT id FROM shop WHERE $where))"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id IN (SELECT id FROM item_shop WHERE tab_id IN (SELECT id FROM tab_shop WHERE shop_id IN (SELECT id FROM shop WHERE $where)))"))
        }
        "savetab" {
            $where = if ((SqlInt $TabId) -gt 0) { "id=$(SqlInt $TabId)" } else { "shop_id=$(SqlInt $ShopId) AND NAME=$(SqlString $Name)" }
            $snapshots.Add((New-DbAuditSnapshot "tab_shop" $where))
            $snapshots.Add((New-DbAuditSnapshot "item_shop" "tab_id IN (SELECT id FROM tab_shop WHERE $where)"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id IN (SELECT id FROM item_shop WHERE tab_id IN (SELECT id FROM tab_shop WHERE $where))"))
        }
        "deletetab" {
            $tab = SqlInt $TabId
            $snapshots.Add((New-DbAuditSnapshot "tab_shop" "id=$tab"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop" "tab_id=$tab"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id IN (SELECT id FROM item_shop WHERE tab_id=$tab)"))
        }
        "saveshopitem" {
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "tab_id=$(SqlInt $TabId) AND temp_id=$(SqlInt $TempId) AND type_sell=$(SqlInt $TypeSell) AND cost=$(SqlInt $Cost)" }
            $snapshots.Add((New-DbAuditSnapshot "item_shop" $where))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id IN (SELECT id FROM item_shop WHERE $where)"))
        }
        "saveshopitems" {
            $ids = @(Convert-UniqueIdPayload -Json $PayloadJson -Label "Danh sách vật phẩm")
            if ($ids.Count -eq 0) { throw "Chọn ít nhất một vật phẩm để thêm vào shop." }
            $where = "tab_id=$(SqlInt $TabId) AND temp_id IN ($($ids -join ','))"
            $snapshots.Add((New-DbAuditSnapshot "item_shop" $where))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id IN (SELECT id FROM item_shop WHERE $where)"))
        }
        "deleteshopitem" {
            $itemShop = SqlInt $Id
            $snapshots.Add((New-DbAuditSnapshot "item_shop" "id=$itemShop"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id=$itemShop"))
        }
        "saveshopoption" {
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "item_shop_id=$(SqlInt $TempId) AND option_id=$(SqlInt $OptionId) AND param=$(SqlInt $Param)" }
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" $where))
        }
        "saveshopoptions" { $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id=$(SqlInt $Id)")) }
        "deleteshopoption" { $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "id=$(SqlInt $Id)")) }
        "saveitemdefaultoptions" { $snapshots.Add((New-DbAuditSnapshot "item_default_option" "item_template_id=$(SqlInt $Id)")) }
        "saveitemdefaultoptionsbulk" {
            $ids = @(Convert-UniqueIdPayload -Json $PayloadJson -Label "Danh sách vật phẩm")
            if ($ids.Count -gt 0) { $snapshots.Add((New-DbAuditSnapshot "item_default_option" "item_template_id IN ($($ids -join ','))")) }
        }
        "savegiftcode" {
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "code=$(SqlString $GiftCode.Trim())" }
            $snapshots.Add((New-DbAuditSnapshot "giftcode" $where))
        }
        "deletegiftcode" { $snapshots.Add((New-DbAuditSnapshot "giftcode" "id=$(SqlInt $Id)")) }
        "savegiftbox" { Ensure-GiftBoxSchema; $snapshots.Add((New-DbAuditSnapshot "gift_box_config" "box_template_id=$(SqlInt $Id)")) }
        "deletegiftbox" { Ensure-GiftBoxSchema; $snapshots.Add((New-DbAuditSnapshot "gift_box_config" "box_template_id=$(SqlInt $Id)")) }
        { $_ -in @("saveradarcard", "deleteradarcard") } { $snapshots.Add((New-DbAuditSnapshot "radar" "id=$(SqlInt $Id)")) }
        "saveradarmobdrops" {
            Ensure-SpawnSchema
            $radarId = SqlInt $Id
            $snapshots.Add((New-DbAuditSnapshot "admin_mob_config" "id IN (SELECT owner_id FROM admin_spawn_drop WHERE owner_type='mob' AND item_id=$radarId)"))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='mob' AND item_id=$radarId"))
        }
        "saveradarbossdrops" {
            Ensure-SpawnSchema
            $radarId = SqlInt $Id
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='serverboss' AND item_id=$radarId"))
        }
        { $_ -in @("savebossoverride", "deletebossoverride") } {
            $bossId = SqlInt $OwnerId
            $snapshots.Add((New-DbAuditSnapshot "admin_boss_override" "boss_id=$bossId"))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='serverboss' AND owner_id=$bossId"))
        }
        "saveadminboss" {
            $where = if ((SqlInt $OwnerId) -gt 0) { "id=$(SqlInt $OwnerId)" } else { "name=$(SqlString $Name) AND map_id=$(SqlInt $MapId -1)" }
            $snapshots.Add((New-DbAuditSnapshot "admin_boss_config" $where))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='boss' AND owner_id IN (SELECT id FROM admin_boss_config WHERE $where)"))
        }
        "deleteadminboss" {
            $bossId = SqlInt $OwnerId
            $snapshots.Add((New-DbAuditSnapshot "admin_boss_config" "id=$bossId"))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='boss' AND owner_id=$bossId"))
        }
        "saveadminmob" {
            $where = if ((SqlInt $OwnerId) -gt 0) { "id=$(SqlInt $OwnerId)" } else { "mob_template_id=$(SqlInt $TemplateId)" }
            $snapshots.Add((New-DbAuditSnapshot "admin_mob_config" $where))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='mob' AND owner_id IN (SELECT id FROM admin_mob_config WHERE $where)"))
        }
        "deleteadminmob" {
            $configId = SqlInt $OwnerId
            $snapshots.Add((New-DbAuditSnapshot "admin_mob_config" "id=$configId"))
            $snapshots.Add((New-DbAuditSnapshot "admin_spawn_drop" "owner_type='mob' AND owner_id=$configId"))
        }
        "saveeventconfig" {
            $code = Assert-EventCode $EventValue
            $snapshots.Add((New-DbAuditSnapshot "admin_event_config" "event_code=$(SqlString $code)"))
        }
        { $_ -in @("saveeventboss", "deleteeventboss") } {
            $code = Assert-EventCode $EventValue
            $snapshots.Add((New-DbAuditSnapshot "admin_event_boss" "event_code=$(SqlString $code) AND boss_id=$(SqlInt $BossId)"))
        }
        "saveeventitem" {
            $code = Assert-EventCode $EventValue
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "event_code=$(SqlString $code) AND item_id=$(SqlInt $TemplateId) AND source_type=$(SqlString $SourceType.Trim().ToLowerInvariant()) AND source_id=$(SqlInt $SourceId)" }
            $snapshots.Add((New-DbAuditSnapshot "admin_event_item" $where))
        }
        "deleteeventitem" { $snapshots.Add((New-DbAuditSnapshot "admin_event_item" "id=$(SqlInt $Id)")) }
        { $_ -in @("savecombineconfig", "resetcombineconfig") } {
            $configPath = Join-Path $Root "combine.properties"
            if (Test-Path $configPath) {
                $fileSnapshots.Add([pscustomobject]@{ path="combine.properties"; contentBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($configPath)) })
            }
        }
        { $_ -in @("setevent", "setexp") } {
            $configPath = Join-Path $Root "Config.properties"
            if (Test-Path $configPath) {
                $fileSnapshots.Add([pscustomobject]@{ path="Config.properties"; contentBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($configPath)) })
            }
        }
        { $_ -in @("saveplayerconfig", "resetplayerconfig") } {
            $configPath = Join-Path $Root "player.properties"
            if (Test-Path $configPath) {
                $fileSnapshots.Add([pscustomobject]@{ path="player.properties"; contentBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($configPath)) })
            }
        }
        { $_ -in @("savepetconfig", "resetpetconfig") } {
            $configPath = Join-Path $Root "pet.properties"
            if (Test-Path $configPath) {
                $fileSnapshots.Add([pscustomobject]@{ path="pet.properties"; contentBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($configPath)) })
            }
        }
        "savetaskmain" { $snapshots.Add((New-DbAuditSnapshot "task_main_template" "id=$(SqlInt $Id)")) }
        "savetasksub" { $snapshots.Add((New-DbAuditSnapshot "task_sub_template" "ducvupro=$(SqlInt $Id)")) }
        "savetasktemplate" {
            $table = Get-TaskTemplateTable
            $snapshots.Add((New-DbAuditSnapshot $table "id=$(SqlInt $Id)"))
        }
        "savebadgestask" { $snapshots.Add((New-DbAuditSnapshot "task_badges_template" "id=$(SqlInt $Id)")) }
        { $_ -in @("savetaskconfig", "resettaskconfig", "savetaskreward") } {
            $configPath = Join-Path $Root "task.properties"
            if (Test-Path $configPath) {
                $fileSnapshots.Add([pscustomobject]@{ path="task.properties"; contentBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($configPath)) })
            }
        }
        "saveplayercore" {
            $playerId = SqlInt $Id
            $snapshots.Add((New-DbAuditSnapshot "player" "id=$playerId"))
            $snapshots.Add((New-DbAuditSnapshot "account" "id IN (SELECT account_id FROM player WHERE id=$playerId)"))
        }
        "rescueplayer" { $snapshots.Add((New-DbAuditSnapshot "player" "id=$(SqlInt $Id)")) }
        default { return $null }
    }
    [pscustomobject]@{ action=$ActionName; summary=(Get-AuditSummary $ActionName); snapshots=$snapshots.ToArray(); fileSnapshots=$fileSnapshots.ToArray() }
}

function Write-AuditEntry {
    param([string]$ActionName, [string]$Summary, [string]$Status, [bool]$Reversible, [string]$Payload, [string]$ResultMessage)
    try {
        Ensure-AuditSchema
        if ($Summary.Length -gt 500) { $Summary = $Summary.Substring(0, 497) + "..." }
        $cleanResult = ($ResultMessage -replace '[\r\n]+', ' ').Trim()
        Invoke-MySql "INSERT INTO admin_change_log (action_name,summary,status,reversible,rollback_payload,result_message) VALUES ($(SqlString $ActionName),$(SqlString $Summary),$(SqlString $Status),$(if ($Reversible) { 1 } else { 0 }),$(if ([string]::IsNullOrEmpty($Payload)) { 'NULL' } else { SqlString $Payload }),$(SqlString $cleanResult));" | Out-Null
    } catch {
        Write-AdminLog "Audit write failed for $ActionName`: $($_.Exception.Message)"
    }
}

function List-AuditEntries {
    Ensure-AuditSchema
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("\", "\\").Replace("'", "''")
        $where = "WHERE action_name LIKE '%$safe%' OR summary LIKE '%$safe%' OR result_message LIKE '%$safe%'"
    }
    Invoke-MySql @"
SELECT l.id,DATE_FORMAT(l.created_at,'%Y-%m-%d %H:%i:%s') AS created_at,l.action_name,l.summary,l.status,
       l.reversible,COALESCE(DATE_FORMAT(l.undone_at,'%Y-%m-%d %H:%i:%s'),'') AS undone_at,
       CASE WHEN l.id=(SELECT MAX(x.id) FROM admin_change_log x WHERE x.status='success' AND x.reversible=1 AND x.undone_at IS NULL) THEN 1 ELSE 0 END AS can_undo,
       REPLACE(REPLACE(REPLACE(COALESCE(l.result_message,''),CHAR(13),' '),CHAR(10),' '),CHAR(9),' ') AS result_message
FROM admin_change_log l $where ORDER BY l.id DESC LIMIT 500;
"@
}

function Undo-AuditEntry {
    Ensure-AuditSchema
    $auditId = SqlInt $Id
    if ($auditId -le 0) { throw "Chọn lịch sử cần hoàn tác." }
    $entryText = Invoke-MySql "SELECT id,action_name,summary,status,reversible,COALESCE(rollback_payload,'') FROM admin_change_log WHERE id=$auditId LIMIT 1;"
    $entryLines = @($entryText -split "`r?`n")
    if ($entryLines.Count -lt 2) { throw "Không tìm thấy lịch sử ID $auditId." }
    $entry = $entryLines[1] -split "`t", 6
    if ($entry[3] -ne 'success' -or $entry[4] -ne '1') { throw "Thao tác này không có dữ liệu hoàn tác." }
    $latestText = Invoke-MySql "SELECT COALESCE(MAX(id),0) FROM admin_change_log WHERE status='success' AND reversible=1 AND undone_at IS NULL;"
    $latestLines = @($latestText -split "`r?`n" | Where-Object { $_ -match '^\d+$' })
    if ($latestLines.Count -eq 0 -or [long]$latestLines[-1] -ne $auditId) {
        throw "Chỉ có thể hoàn tác thay đổi gần nhất chưa được hoàn tác."
    }
    try { $payload = $entry[5] | ConvertFrom-Json } catch { throw "Dữ liệu hoàn tác bị hỏng." }
    $snapshots = @($payload.snapshots)
    if ($snapshots.Count -gt 0) {
        $sql = New-Object System.Text.StringBuilder
        [void]$sql.AppendLine("START TRANSACTION;")
        for ($i = $snapshots.Count - 1; $i -ge 0; $i--) {
            if ([string]$snapshots[$i].table -notmatch '^[A-Za-z0-9_]+$') { throw "Snapshot chứa tên bảng không hợp lệ." }
            [void]$sql.AppendLine("DELETE FROM $($snapshots[$i].table) WHERE $($snapshots[$i].where);")
        }
        foreach ($snapshot in $snapshots) {
            if (-not [string]::IsNullOrWhiteSpace([string]$snapshot.insertSql)) { [void]$sql.AppendLine([string]$snapshot.insertSql) }
        }
        [void]$sql.AppendLine("COMMIT;")
        Invoke-MySql $sql.ToString() | Out-Null
    }
    foreach ($fileSnapshot in @($payload.fileSnapshots)) {
        $relativePath = [string]$fileSnapshot.path
        if ($relativePath -notin @("Config.properties", "combine.properties", "player.properties", "pet.properties", "task.properties")) { throw "Snapshot chứa đường dẫn file không hợp lệ." }
        [IO.File]::WriteAllBytes((Join-Path $Root $relativePath), [Convert]::FromBase64String([string]$fileSnapshot.contentBase64))
    }
    if (-not [string]::IsNullOrWhiteSpace([string]$payload.configBase64)) {
        [IO.File]::WriteAllBytes((Join-Path $Root "Config.properties"), [Convert]::FromBase64String([string]$payload.configBase64))
    }
    Invoke-MySql "UPDATE admin_change_log SET undone_at=NOW() WHERE id=$auditId; INSERT INTO admin_change_log (action_name,summary,status,reversible,result_message) VALUES ('undoaudit',$(SqlString "Hoàn tác #$auditId - $($entry[2])"),'success',0,$(SqlString "Đã hoàn tác thao tác $($entry[1])."));" | Out-Null
    "OK`tĐã hoàn tác lịch sử #$auditId. Restart server nếu thay đổi liên quan dữ liệu runtime."
}

function Record-ManualAuditEntry {
    $manualAction = if ([string]::IsNullOrWhiteSpace($Name)) { "manual" } else { $Name.Trim().ToLowerInvariant() }
    if ($manualAction -notmatch '^[a-z0-9_-]{2,64}$') { throw "Tên thao tác lịch sử không hợp lệ." }
    $summaryText = if ([string]::IsNullOrWhiteSpace($Description)) { $manualAction } else { $Description.Trim() }
    Write-AuditEntry -ActionName $manualAction -Summary $summaryText -Status "success" -Reversible $false -Payload "" -ResultMessage "Đã ghi nhận thao tác."
    "OK`tĐã ghi nhận thao tác vào lịch sử."
}

$actionLower = $Action.ToLowerInvariant()
$mutationActions = @(
    "saveitem", "saveshop", "savetab", "deletetab", "saveshopitem", "saveshopitems", "deleteshopitem",
    "saveshopoption", "saveshopoptions", "deleteshopoption", "saveitemdefaultoptions", "saveitemdefaultoptionsbulk", "savegiftcode", "deletegiftcode", "savegiftbox", "deletegiftbox",
    "saveradarcard", "deleteradarcard", "saveradarmobdrops", "saveradarbossdrops",
    "savebossoverride", "deletebossoverride", "saveadminboss", "deleteadminboss",
    "saveadminmob", "deleteadminmob", "savecombineconfig", "resetcombineconfig", "setevent", "setexp",
    "saveplayerconfig", "resetplayerconfig", "savepetconfig", "resetpetconfig", "saveplayercore", "rescueplayer",
    "saveeventconfig", "saveeventboss", "deleteeventboss", "saveeventitem", "deleteeventitem",
    "savetaskreward"
)
$isAuditedMutation = $mutationActions -contains $actionLower
$auditContext = $null

try {
    if ($isAuditedMutation) {
        Ensure-AuditSchema
        if ($actionLower -like '*eventconfig' -or $actionLower -like '*eventboss' -or $actionLower -like '*eventitem') {
            Ensure-EventConfigSchema
        }
        if ($actionLower -like '*itemdefaultoptions*') { Ensure-ItemDefaultOptionSchema }
        $auditContext = Get-AuditContext $actionLower
    }
    $result = switch ($actionLower) {
        "status" {
            $config = Get-ConfigMap
            $runtime = Get-EventRuntimeState
            "key`tvalue`r`nserver.event`t$($config['server.event'])`r`nserver.expserver`t$($config['server.expserver'])`r`nserver.running`t$(if ($runtime.Running) {1} else {0})`r`nserver.restart_pending`t$(if ($runtime.RestartPending) {1} else {0})`r`nserver.started_at`t$($runtime.StartedAt)"
        }
        "listevents" { List-Events }
        "eventstats" { Get-EventStats }
        "geteventconfig" { Get-EventProfile }
        "listeventbosses" { List-EventBosses }
        "listeventitems" { List-EventItems }
        "searcheventplayers" { Search-EventPlayers }
        "listeventpointgrants" { List-EventPointGrants }
        "granteventpoint" { Grant-EventPoint }
        "saveeventconfig" { Save-EventProfile }
        "saveeventboss" { Save-EventBoss }
        "deleteeventboss" { Delete-EventBoss }
        "saveeventitem" { Save-EventItem }
        "deleteeventitem" { Delete-EventItem }
        "listitems" { List-Items }
        "listitemcatalog" { List-ItemCatalog }
        "listitemtypes" { List-ItemTypes }
        "getitem" { Invoke-MySql "SELECT id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg FROM item_template WHERE id=$(SqlInt $Id) LIMIT 1;" }
        "saveitem" { Save-Item }
        "listoptions" { Invoke-MySql "SELECT id, `NAME` FROM item_option_template ORDER BY id LIMIT 600;" }
        "listitemdefaultoptions" { List-ItemDefaultOptions }
        "listitemdefaultoptionitems" { List-ItemDefaultOptionItems }
        "saveitemdefaultoptions" { Save-ItemDefaultOptions }
        "saveitemdefaultoptionsbulk" { Save-ItemDefaultOptionsBulk }
        "listnpcs" { List-Npcs }
        "listshops" { List-Shops }
        "saveshop" { Save-Shop }
        "listtabs" { List-Tabs }
        "savetab" { Save-Tab }
        "deletetab" { Delete-Tab }
        "listequipmentshopitems" { List-EquipmentShopItems }
        "listshopitems" { List-ShopItems }
        "saveshopitem" { Save-ShopItem }
        "saveshopitems" { Save-ShopItems }
        "deleteshopitem" { Delete-ShopItem }
        "listshopoptions" { List-ShopOptions }
        "saveshopoption" { Save-ShopOption }
        "saveshopoptions" { Save-ShopOptions }
        "deleteshopoption" { Delete-ShopOption }
        "listgiftcodes" { List-GiftCodes }
        "listgiftitems" { List-GiftItems }
        "savegiftcode" { Save-GiftCode }
        "deletegiftcode" { Delete-GiftCode }
        "listgiftboxes" { List-GiftBoxes }
        "getgiftbox" { Get-GiftBox }
        "savegiftbox" { Save-GiftBox }
        "deletegiftbox" { Delete-GiftBox }
        "listradarcards" { List-RadarCards }
        "getradarcard" { Get-RadarCard }
        "saveradarcard" { Save-RadarCard }
        "deleteradarcard" { Delete-RadarCard }
        "listradarmobdrops" { List-RadarMobDrops }
        "saveradarmobdrops" { Save-RadarMobDrops }
        "listradarbossdrops" { List-RadarBossDrops }
        "saveradarbossdrops" { Save-RadarBossDrops }
        "listspawnmaps" { List-SpawnMaps }
        "listspawnitems" { List-SpawnItems }
        "listbossskills" { List-BossSkillCatalog }
        "listexistingbosses" { List-ExistingBosses }
        "savebossoverride" { Save-BossOverride }
        "deletebossoverride" { Delete-BossOverride }
        "listadminbosses" { List-AdminBosses }
        "saveadminboss" { Save-AdminBoss }
        "deleteadminboss" { Delete-AdminBoss }
        "listadminmobs" { List-AdminMobs }
        "saveadminmob" { Save-AdminMob }
        "deleteadminmob" { Delete-AdminMob }
        "listaudit" { List-AuditEntries }
        "undoaudit" { Undo-AuditEntry }
        "recordaudit" { Record-ManualAuditEntry }
        "listcombineconfig" { List-CombineConfig }
        "savecombineconfig" { Save-CombineConfig }
        "resetcombineconfig" { Reset-CombineConfig }
        "listplayerconfig" { List-PlayerConfig }
        "saveplayerconfig" { Save-PlayerConfig }
        "resetplayerconfig" { Reset-PlayerConfig }
        "listpetconfig" { List-PetConfig }
        "savepetconfig" { Save-PetConfig }
        "resetpetconfig" { Reset-PetConfig }
        "listtaskconfig" { List-TaskConfig }
        "savetaskconfig" { Save-TaskConfig }
        "resettaskconfig" { Reset-TaskConfig }
        "gettaskreward" { Get-TaskReward }
        "savetaskreward" { Save-TaskReward }
        "listtaskmains" { List-TaskMains }
        "listtasksubs" { List-TaskSubs }
        "savetaskmain" { Save-TaskMain }
        "savetasksub" { Save-TaskSub }
        "listtasktemplates" { List-TaskTemplates }
        "savetasktemplate" { Save-TaskTemplate }
        "listbadgestasks" { List-BadgesTasks }
        "savebadgestask" { Save-BadgesTask }
        "listplayers" { List-Players }
        "getplayerdetail" { Get-PlayerDetail }
        "saveplayercore" { Save-PlayerCore }
        "rescueplayer" { Rescue-Player }
        "setevent" {
            $EventValue = Normalize-EventValue $EventValue
            Set-ConfigValue -Key "server.event" -NewValue $EventValue
            "OK`tĐã cập nhật server.event=$EventValue. Restart server để áp dụng."
        }
        "setexp" {
            if ($ExpRate -notmatch "^[1-9][0-9]*$") { $ExpRate = "1" }
            Set-ConfigValue -Key "server.expserver" -NewValue $ExpRate
            "OK`tĐã cập nhật server.expserver=$ExpRate. Restart server để áp dụng."
        }
        default { throw "Lệnh admin không hợp lệ: $Action" }
    }

    if ($isAuditedMutation -and $null -ne $auditContext) {
        $payload = ConvertTo-Json -InputObject $auditContext -Compress -Depth 10
        Write-AuditEntry -ActionName $actionLower -Summary $auditContext.summary -Status "success" -Reversible $true -Payload $payload -ResultMessage ([string]$result)
    }
    Write-Result $result
    Write-AdminLog "Action=$Action OK"
}
catch {
    $message = "ERROR`t$($_.Exception.Message)"
    if ($isAuditedMutation) {
        $failureSummary = if ($null -ne $auditContext) { $auditContext.summary } else { Get-AuditSummary $actionLower }
        Write-AuditEntry -ActionName $actionLower -Summary $failureSummary -Status "failed" -Reversible $false -Payload "" -ResultMessage $message
    }
    Write-Result $message
    Write-AdminLog "Action=$Action ERROR $($_.Exception.Message) | $($_.ScriptStackTrace -replace '[\r\n]+', ' ')"
    exit 1
}
