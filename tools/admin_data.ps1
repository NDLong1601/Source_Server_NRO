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
    [string]$ExpRate = ""
)

$ErrorActionPreference = "Stop"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
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
    $args += "-e"
    $args += $Sql

    $output = & $mysql @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($output -join [Environment]::NewLine)
    }
    return ($output -join [Environment]::NewLine)
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
INSERT INTO item_template (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES ($itemId, $(SqlInt $Type), $(SqlInt $Gender), $(SqlString $Name), $(SqlString $Description), $(SqlInt $Level), $(SqlInt $IconId), $(SqlInt $Part -1), $(SqlInt $IsUpToUp), $(SqlInt $PowerRequire), $(SqlInt $Gold), $(SqlInt $Gem), $(SqlInt $Head -1), $(SqlInt $Body -1), $(SqlInt $Leg -1))
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), `gender`=VALUES(`gender`), `NAME`=VALUES(`NAME`), `description`=VALUES(`description`), `level`=VALUES(`level`), `icon_id`=VALUES(`icon_id`), `part`=VALUES(`part`), `is_up_to_up`=VALUES(`is_up_to_up`), `power_require`=VALUES(`power_require`), `gold`=VALUES(`gold`), `gem`=VALUES(`gem`), `head`=VALUES(`head`), `body`=VALUES(`body`), `leg`=VALUES(`leg`);
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
