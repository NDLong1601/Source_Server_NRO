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
        "GiftCode", "CountLeft", "GiftDetail", "ExpiryMode", "ValidDays", "StartDate", "EndDate"
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

try {
    $result = switch ($Action.ToLowerInvariant()) {
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

    Write-Result $result
    Write-AdminLog "Action=$Action OK"
}
catch {
    $message = "ERROR`t$($_.Exception.Message)"
    Write-Result $message
    Write-AdminLog "Action=$Action ERROR $($_.Exception.Message)"
    exit 1
}
