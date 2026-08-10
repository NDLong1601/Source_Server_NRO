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
        "IconSpec", "OptionId", "Param", "EventValue", "ExpRate", "ConfigKey", "ConfigValue",
        "GiftCode", "CountLeft", "GiftDetail", "ExpiryMode", "ValidDays", "StartDate", "EndDate",
        "OwnerId", "TemplateId", "Enabled", "UseTimeRange", "TimeStart", "TimeEnd", "UseInterval",
        "IntervalMinutes", "MapId", "MapIdsJson", "ZoneId", "SpawnX", "SpawnY", "Hp", "Damage", "Announce", "DropsJson", "SkillsJson"
    )) {
    Set-Variable -Name $paramName -Value (Decode-InputParam (Get-Variable -Name $paramName -ValueOnly))
}

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root "logs"
$ResultPath = if ([string]::IsNullOrWhiteSpace($Output)) { Join-Path $LogDir "admin_data_result.txt" } else { $Output }
$AdminLog = Join-Path $LogDir "admin_data.log"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

function Write-Result {
    param([string]$Text)
    [System.IO.File]::WriteAllText($ResultPath, $Text, [System.Text.Encoding]::UTF8)
}

function Write-AdminLog {
    param([string]$Message)
    $line = "[{0}] {1}{2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message, [Environment]::NewLine
    for ($i = 0; $i -lt 8; $i++) {
        try {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($line)
            $stream = [System.IO.File]::Open($AdminLog, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
            try {
                $stream.Write($bytes, 0, $bytes.Length)
            } finally {
                $stream.Dispose()
            }
            return
        } catch {
            if ($i -eq 7) {
                return
            }
            Start-Sleep -Milliseconds 100
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
    Invoke-MySql "SELECT i.id, i.tab_id, i.temp_id, COALESCE(t.`NAME`, '') AS item_name, i.is_new, i.is_sell, i.type_sell, i.cost, i.icon_spec, i.create_time FROM item_shop i LEFT JOIN item_template t ON t.id = i.temp_id WHERE i.tab_id=$(SqlInt $TabId) ORDER BY i.create_time DESC, i.id DESC;"
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
  COUNT(o.id) AS options_count
FROM item_shop i
JOIN item_template t ON t.id = i.temp_id
LEFT JOIN tab_shop ts ON ts.id = i.tab_id
LEFT JOIN shop s ON s.id = ts.shop_id
LEFT JOIN npc_template n ON n.id = s.npc_id
LEFT JOIN item_shop_option o ON o.item_shop_id = i.id
$whereSql
GROUP BY i.id, i.temp_id, t.`NAME`, t.`TYPE`, t.gender, i.tab_id, ts.`NAME`, s.id, n.`NAME`, i.type_sell, i.cost
ORDER BY t.gender, t.`TYPE`, t.id, i.id
LIMIT 500;
"@
}

function Save-ShopItem {
    $itemShopId = SqlInt $Id
    if ($itemShopId -gt 0) {
        Invoke-MySql "UPDATE item_shop SET tab_id=$(SqlInt $TabId), temp_id=$(SqlInt $TempId), is_new=$(SqlInt $IsNew), is_sell=$(SqlInt $IsSell), type_sell=$(SqlInt $TypeSell), cost=$(SqlInt $Cost), icon_spec=$(SqlInt $IconSpec) WHERE id=$itemShopId;" | Out-Null
        return "OK`tĐã cập nhật vật phẩm shop ID $itemShopId."
    }

    Invoke-MySql "INSERT INTO item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, create_time) VALUES ($(SqlInt $TabId), $(SqlInt $TempId), $(SqlInt $IsNew), $(SqlInt $IsSell), $(SqlInt $TypeSell), $(SqlInt $Cost), $(SqlInt $IconSpec), NOW());" | Out-Null
    "OK`tĐã thêm vật phẩm vào shop."
}

function Delete-ShopItem {
    Invoke-MySql "DELETE FROM item_shop WHERE id=$(SqlInt $Id);" | Out-Null
    "OK`tĐã xóa vật phẩm shop ID $Id."
}

function List-ShopOptions {
    Invoke-MySql "SELECT o.id, o.item_shop_id, o.option_id, COALESCE(t.`NAME`, '') AS option_name, o.param FROM item_shop_option o LEFT JOIN item_option_template t ON t.id = o.option_id WHERE o.item_shop_id=$(SqlInt $Id) ORDER BY o.id;"
}

function Save-ShopOption {
    $rowId = SqlInt $Id
    if ($rowId -gt 0) {
        Invoke-MySql "UPDATE item_shop_option SET option_id=$(SqlInt $OptionId), param=$(SqlInt $Param) WHERE id=$rowId;" | Out-Null
        return "OK`tĐã cập nhật option ID $rowId."
    }

    Invoke-MySql "INSERT INTO item_shop_option (item_shop_id, option_id, param) VALUES ($(SqlInt $TempId), $(SqlInt $OptionId), $(SqlInt $Param));" | Out-Null
    "OK`tĐã thêm option cho vật phẩm shop."
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
        $validated += [ordered]@{
            id = [int]$itemIdText
            quantity = [int64]$quantityText
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
        $optionsJson = ConvertTo-Json -InputObject $validatedOptions.ToArray() -Compress -Depth 6
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
        "--skip-add-locks", "--skip-disable-keys", "--default-character-set=utf8mb4",
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
        "deleteshopitem" { "Xóa item shop ID $Id" }
        "saveshopoption" { "Lưu option shop $(if ($Id) { "ID $Id" } else { "option $OptionId" }), param $Param" }
        "deleteshopoption" { "Xóa option shop ID $Id" }
        "savegiftcode" { "Lưu Giftcode $GiftCode, lượt $CountLeft, $(Get-JsonArrayCount $GiftDetail) phần quà" }
        "deletegiftcode" { "Xóa Giftcode ID $Id" }
        "savebossoverride" { "Lưu Boss $Name ($OwnerId): $(Get-JsonArrayCount $MapIdsJson) map, $(Get-JsonArrayCount $SkillsJson) skill, $(Get-JsonArrayCount $DropsJson) drop, chu kỳ $(if ($UseInterval -eq '1') { "$IntervalMinutes phút" } else { 'mặc định' })" }
        "deletebossoverride" { "Trả Boss $OwnerId về mặc định" }
        "saveadminboss" { "Lưu Boss tùy chỉnh $Name" }
        "deleteadminboss" { "Xóa Boss tùy chỉnh ID $OwnerId" }
        "saveadminmob" { "Lưu Mob template ${TemplateId}: $(Get-JsonArrayCount $DropsJson) drop, chu kỳ $(if ($UseInterval -eq '1') { "$IntervalMinutes phút" } else { 'mặc định' })" }
        "deleteadminmob" { "Trả Mob config $OwnerId về mặc định" }
        "savecombineconfig" { "Đổi Combine $ConfigKey = $ConfigValue" }
        "resetcombineconfig" { "Khôi phục cấu hình Combine mặc định" }
        "setevent" { "Đổi sự kiện server thành $EventValue" }
        "setexp" { "Đổi tỉ lệ TNSM thành $ExpRate" }
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
        "deleteshopitem" {
            $itemShop = SqlInt $Id
            $snapshots.Add((New-DbAuditSnapshot "item_shop" "id=$itemShop"))
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "item_shop_id=$itemShop"))
        }
        "saveshopoption" {
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "item_shop_id=$(SqlInt $TempId) AND option_id=$(SqlInt $OptionId) AND param=$(SqlInt $Param)" }
            $snapshots.Add((New-DbAuditSnapshot "item_shop_option" $where))
        }
        "deleteshopoption" { $snapshots.Add((New-DbAuditSnapshot "item_shop_option" "id=$(SqlInt $Id)")) }
        "savegiftcode" {
            $where = if ((SqlInt $Id) -gt 0) { "id=$(SqlInt $Id)" } else { "code=$(SqlString $GiftCode.Trim())" }
            $snapshots.Add((New-DbAuditSnapshot "giftcode" $where))
        }
        "deletegiftcode" { $snapshots.Add((New-DbAuditSnapshot "giftcode" "id=$(SqlInt $Id)")) }
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
        if ($relativePath -notin @("Config.properties", "combine.properties")) { throw "Snapshot chứa đường dẫn file không hợp lệ." }
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
    "saveitem", "saveshop", "savetab", "deletetab", "saveshopitem", "deleteshopitem",
    "saveshopoption", "deleteshopoption", "savegiftcode", "deletegiftcode",
    "savebossoverride", "deletebossoverride", "saveadminboss", "deleteadminboss",
    "saveadminmob", "deleteadminmob", "savecombineconfig", "resetcombineconfig", "setevent", "setexp"
)
$isAuditedMutation = $mutationActions -contains $actionLower
$auditContext = $null

try {
    if ($isAuditedMutation) {
        Ensure-AuditSchema
        $auditContext = Get-AuditContext $actionLower
    }
    $result = switch ($actionLower) {
        "status" {
            $config = Get-ConfigMap
            "key`tvalue`r`nserver.event`t$($config['server.event'])`r`nserver.expserver`t$($config['server.expserver'])"
        }
        "listitems" { List-Items }
        "listitemtypes" { List-ItemTypes }
        "getitem" { Invoke-MySql "SELECT id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg FROM item_template WHERE id=$(SqlInt $Id) LIMIT 1;" }
        "saveitem" { Save-Item }
        "listoptions" { Invoke-MySql "SELECT id, `NAME` FROM item_option_template ORDER BY id LIMIT 600;" }
        "listnpcs" { List-Npcs }
        "listshops" { List-Shops }
        "saveshop" { Save-Shop }
        "listtabs" { List-Tabs }
        "savetab" { Save-Tab }
        "deletetab" { Delete-Tab }
        "listequipmentshopitems" { List-EquipmentShopItems }
        "listshopitems" { List-ShopItems }
        "saveshopitem" { Save-ShopItem }
        "deleteshopitem" { Delete-ShopItem }
        "listshopoptions" { List-ShopOptions }
        "saveshopoption" { Save-ShopOption }
        "deleteshopoption" { Delete-ShopOption }
        "listgiftcodes" { List-GiftCodes }
        "listgiftitems" { List-GiftItems }
        "savegiftcode" { Save-GiftCode }
        "deletegiftcode" { Delete-GiftCode }
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
        "setevent" {
            if ([string]::IsNullOrWhiteSpace($EventValue)) { $EventValue = "none" }
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
