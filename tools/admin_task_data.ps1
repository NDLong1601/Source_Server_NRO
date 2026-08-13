function Get-TaskConfigCatalog {
    @(
        [pscustomobject]@{ Key="task.side.maxPerDay"; Category="Nhiem vu ngay"; Name="So luot nhiem vu moi ngay"; Default="10"; Kind="int"; Scope="runtime"; Description="So luot nhiem vu ngay reset sau nua dem." },
        [pscustomobject]@{ Key="task.side.goldRewards"; Category="Nhiem vu ngay"; Name="Vang thuong theo do kho"; Default="5000,20000,50000,200000,25"; Kind="int-list-5"; Scope="runtime"; Description="5 so tuong ung De, Binh thuong, Kho, Rat kho, Dia nguc." },
        [pscustomobject]@{ Key="task.side.itemRewards"; Category="Nhiem vu ngay"; Name="Item thuong theo do kho"; Default="708,707,706,705,704"; Kind="int-list-5"; Scope="runtime"; Description="5 item template id tuong ung De den Dia nguc." },
        [pscustomobject]@{ Key="task.side.hellTreeItemId"; Category="Nhiem vu ngay"; Name="Item dac biet dia nguc"; Default="822"; Kind="item-id"; Scope="runtime"; Description="Item dac biet co the nhan o nhiem vu Dia nguc khi dat nguong luot con lai." },
        [pscustomobject]@{ Key="task.side.hellTreeLeftThreshold"; Category="Nhiem vu ngay"; Name="Nguong luot con lai nhan item dac biet"; Default="15"; Kind="int"; Scope="runtime"; Description="Neu luot con lai nho hon gia tri nay thi xet thuong item dac biet." },
        [pscustomobject]@{ Key="task.clan.maxPerDay"; Category="Nhiem vu bang"; Name="So luot nhiem vu bang moi ngay"; Default="5"; Kind="int"; Scope="runtime"; Description="So luot nhiem vu bang reset sau nua dem." },
        [pscustomobject]@{ Key="task.clan.capsulePerLevel"; Category="Nhiem vu bang"; Name="Capsule bang moi cap do"; Default="10"; Kind="int"; Scope="runtime"; Description="Thuong capsule bang = (do kho + 1) nhan gia tri nay." }
    )
}

function Get-TaskConfigEntry {
    param([string]$Key)
    Get-TaskConfigCatalog | Where-Object { $_.Key -eq $Key } | Select-Object -First 1
}

function Assert-TaskConfigValue {
    param($Entry, [string]$Value)
    $Value = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Gia tri khong duoc de trong." }
    if ($Entry.Kind -eq "int") {
        if ($Value -notmatch '^\d+$') { throw "Gia tri phai la so nguyen khong am." }
        if ([decimal]$Value -gt 2147483647) { throw "Gia tri vuot gioi han int." }
    } elseif ($Entry.Kind -eq "item-id") {
        if ($Value -notmatch '^-?\d+$') { throw "Item id phai la so nguyen." }
        if ([int]$Value -lt -1 -or [int]$Value -gt 32767) { throw "Item id phai tu -1 den 32767." }
    } elseif ($Entry.Kind -eq "int-list-5") {
        $parts = @($Value -split ',')
        if ($parts.Count -ne 5) { throw "Danh sach phai co dung 5 so." }
        foreach ($part in $parts) {
            $number = $part.Trim()
            if ($number -notmatch '^\d+$') { throw "Danh sach chi gom so nguyen khong am, phan cach bang dau phay." }
            if ([decimal]$number -gt 2147483647) { throw "Moi gia tri khong duoc vuot gioi han int." }
        }
        $Value = ($parts | ForEach-Object { ([int]$_.Trim()).ToString() }) -join ','
    } else {
        throw "Kieu cau hinh nhiem vu khong hop le: $($Entry.Kind)"
    }
    $Value
}

function List-TaskConfig {
    $path = Join-Path $Root "task.properties"
    $map = Get-PropertyMap $path
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("key`tcategory`tname`tvalue`tdefault`tkind`tscope`tdescription")
    foreach ($entry in (Get-TaskConfigCatalog)) {
        $value = if ($map.ContainsKey($entry.Key)) { $map[$entry.Key] } else { $entry.Default }
        $rows.Add("$($entry.Key)`t$($entry.Category)`t$($entry.Name)`t$value`t$($entry.Default)`t$($entry.Kind)`t$($entry.Scope)`t$($entry.Description)")
    }
    $rows -join "`r`n"
}

function Save-TaskConfig {
    $entry = Get-TaskConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khoa cau hinh nhiem vu khong hop le: $ConfigKey" }
    $validated = Assert-TaskConfigValue $entry $ConfigValue
    Set-PropertyValue -Path (Join-Path $Root "task.properties") -Key $entry.Key -Value $validated
    "OK`tDa luu $($entry.Name). Runtime ap dung trong toi da 1 giay."
}

function Reset-TaskConfig {
    $entry = Get-TaskConfigEntry $ConfigKey
    if ($null -eq $entry) { throw "Khoa cau hinh nhiem vu khong hop le: $ConfigKey" }
    Set-PropertyValue -Path (Join-Path $Root "task.properties") -Key $entry.Key -Value "" -Remove
    "OK`tDa dua $($entry.Name) ve mac dinh $($entry.Default)."
}

function Assert-TaskText {
    param([string]$Value, [string]$Label, [int]$MaxLength, [bool]$Required = $true)
    $text = if ($null -eq $Value) { "" } else { $Value.Trim() }
    if ($Required -and [string]::IsNullOrWhiteSpace($text)) { throw "$Label khong duoc de trong." }
    if ($text.Length -gt $MaxLength) { throw "$Label khong duoc vuot qua $MaxLength ky tu." }
    $text
}

function Assert-TaskIntRange {
    param([string]$Value, [string]$Label, [int]$Min, [int]$Max)
    if ($Value -notmatch '^-?\d+$') { throw "$Label phai la so nguyen." }
    $number = [int]$Value
    if ($number -lt $Min -or $number -gt $Max) { throw "$Label phai tu $Min den $Max." }
    $number
}

function Assert-TaskCountRange {
    param([string]$Value, [string]$Label)
    $range = $Value.Trim()
    if ($range -notmatch '^\d+\s*-\s*\d+$') { throw "$Label phai co dang min-max." }
    $parts = $range -split '-'
    $from = [int]$parts[0].Trim()
    $to = [int]$parts[1].Trim()
    if ($from -gt $to) { throw "$Label co min lon hon max." }
    "$from-$to"
}

function Get-TaskRewardType {
    param([string]$Type)
    $value = $Type.Trim().ToLowerInvariant()
    if ($value -in @("main", "side", "clan", "badges")) { return $value }
    throw "Loai phan thuong nhiem vu khong hop le."
}

function Get-TaskRewardKeyPrefix {
    param([string]$Type, [string]$Id)
    $rewardType = Get-TaskRewardType $Type
    $rewardId = Assert-TaskIntRange $Id "ID nhiem vu" 0 32767
    "task.reward.$rewardType.$rewardId"
}

function Convert-TaskRewardPayload {
    param([string]$PayloadJson)
    try { $payload = $PayloadJson | ConvertFrom-Json } catch { throw "Du lieu phan thuong khong hop le." }
    if ($null -eq $payload) { throw "Du lieu phan thuong khong hop le." }
    $enabled = if ([string]$payload.Enabled -eq "1" -or [string]$payload.Enabled -eq "true") { "1" } else { "0" }
    $potential = Assert-TaskIntRange ([string]$payload.Potential) "Tiem nang" 0 2147483647
    $gold = Assert-TaskIntRange ([string]$payload.Gold) "Vang" 0 2147483647
    $gem = Assert-TaskIntRange ([string]$payload.Gem) "Ngoc" 0 2147483647
    $itemIds = @()
    foreach ($rawId in @($payload.ItemIds)) {
        $itemId = Assert-TaskIntRange ([string]$rawId) "ID vat pham" 0 32767
        if ($itemIds -notcontains $itemId) { $itemIds += $itemId }
    }
    if ($itemIds.Count -gt 50) { throw "Chi duoc chon toi da 50 vat pham." }
    if ($itemIds.Count -gt 0) {
        $idSql = $itemIds -join ','
        $found = [int](Get-MySqlScalar "SELECT COUNT(*) FROM item_template WHERE id IN ($idSql);")
        if ($found -ne $itemIds.Count) { throw "Co vat pham khong ton tai trong item_template." }
    }
    [pscustomobject]@{ Enabled=$enabled; Potential=$potential; Gold=$gold; Gem=$gem; ItemIds=($itemIds -join ',') }
}

function Get-TaskReward {
    $prefix = Get-TaskRewardKeyPrefix $Type $Id
    $map = Get-PropertyMap (Join-Path $Root "task.properties")
    $enabled = if ($map.ContainsKey("$prefix.enabled")) { $map["$prefix.enabled"] } else { "0" }
    $potential = if ($map.ContainsKey("$prefix.potential")) { $map["$prefix.potential"] } else { "0" }
    $gold = if ($map.ContainsKey("$prefix.gold")) { $map["$prefix.gold"] } else { "0" }
    $gem = if ($map.ContainsKey("$prefix.gem")) { $map["$prefix.gem"] } else { "0" }
    $items = if ($map.ContainsKey("$prefix.items")) { $map["$prefix.items"] } else { "" }
    "id`tenabled`tpotential`tgold`tgem`titems`r`n$Id`t$enabled`t$potential`t$gold`t$gem`t$items"
}

function Save-TaskReward {
    $prefix = Get-TaskRewardKeyPrefix $Type $Id
    $reward = Convert-TaskRewardPayload $PayloadJson
    $path = Join-Path $Root "task.properties"
    Set-PropertyValue -Path $path -Key "$prefix.enabled" -Value $reward.Enabled
    Set-PropertyValue -Path $path -Key "$prefix.potential" -Value $reward.Potential
    Set-PropertyValue -Path $path -Key "$prefix.gold" -Value $reward.Gold
    Set-PropertyValue -Path $path -Key "$prefix.gem" -Value $reward.Gem
    Set-PropertyValue -Path $path -Key "$prefix.items" -Value $reward.ItemIds
    "OK`tDa luu phan thuong nhiem vu. Runtime ap dung trong toi da 1 giay."
}

function Get-TaskTemplateTable {
    $taskType = $Type.Trim().ToLowerInvariant()
    if ($taskType -eq "side") { return "side_task_template" }
    if ($taskType -eq "clan") { return "clan_task_template" }
    throw "Loai nhiem vu phai la side hoac clan."
}

function Get-TaskTemplateLabel {
    $taskType = $Type.Trim().ToLowerInvariant()
    if ($taskType -eq "side") { return "nhiem vu ngay" }
    if ($taskType -eq "clan") { return "nhiem vu bang" }
    return "nhiem vu"
}

function Convert-TaskRangePayload {
    try { $payload = $PayloadJson | ConvertFrom-Json } catch { throw "Du lieu so luong theo cap khong hop le." }
    @(
        Assert-TaskCountRange ([string]$payload.lv1) "Cap de",
        Assert-TaskCountRange ([string]$payload.lv2) "Cap binh thuong",
        Assert-TaskCountRange ([string]$payload.lv3) "Cap kho",
        Assert-TaskCountRange ([string]$payload.lv4) "Cap rat kho",
        Assert-TaskCountRange ([string]$payload.lv5) "Cap dia nguc"
    )
}

function List-TaskMains {
    $where = ""
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $safe = $Search.Replace("", "\").Replace("'", "''")
        $where = "WHERE m.id LIKE '%$safe%' OR m.NAME LIKE '%$safe%' OR m.detail LIKE '%$safe%'"
    }
    Invoke-MySql @"
SELECT m.id,
       REPLACE(REPLACE(REPLACE(COALESCE(m.NAME,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS name,
       REPLACE(REPLACE(REPLACE(COALESCE(m.detail,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS detail,
       COUNT(s.ducvupro) AS sub_count
FROM task_main_template m
LEFT JOIN task_sub_template s ON s.task_main_id=m.id
$where
GROUP BY m.id,m.NAME,m.detail
ORDER BY m.id;
"@
}

function List-TaskSubs {
    $mainId = SqlInt $Id -1
    if ($mainId -lt 0) { throw "Chon nhiem vu chinh." }
    Invoke-MySql @"
SELECT ducvupro,task_main_id,
       REPLACE(REPLACE(REPLACE(COALESCE(NAME,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS name,
       max_count,
       REPLACE(REPLACE(REPLACE(COALESCE(notify,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS notify,
       npc_id,map
FROM task_sub_template
WHERE task_main_id=$mainId
ORDER BY ducvupro;
"@
}

function Save-TaskMain {
    $taskId = Assert-TaskIntRange $Id "ID nhiem vu chinh" 0 32767
    $taskName = Assert-TaskText $Name "Ten nhiem vu chinh" 255 $true
    $taskDetail = Assert-TaskText $Description "Chi tiet nhiem vu" 500 $true
    Invoke-MySql "INSERT INTO task_main_template (id,NAME,detail) VALUES ($taskId,$(SqlString $taskName),$(SqlString $taskDetail)) ON DUPLICATE KEY UPDATE NAME=VALUES(NAME),detail=VALUES(detail);" | Out-Null
    "OK`tDa luu nhiem vu chinh ID $taskId. Restart server de ap dung template."
}

function Save-TaskSub {
    $subId = Assert-TaskIntRange $Id "ID buoc nhiem vu" 1 2147483647
    $mainId = Assert-TaskIntRange $OwnerId "ID nhiem vu chinh" 0 32767
    $exists = Get-MySqlScalar "SELECT COUNT(*) FROM task_sub_template WHERE ducvupro=$subId LIMIT 1;"
    if ($exists -ne '1') { throw "Khong tim thay buoc nhiem vu ID $subId. Chuc nang nay chi sua buoc co san de tranh pha thu tu nhiem vu." }
    $mainExists = Get-MySqlScalar "SELECT COUNT(*) FROM task_main_template WHERE id=$mainId LIMIT 1;"
    if ($mainExists -ne '1') { throw "Khong tim thay nhiem vu chinh ID $mainId." }
    $subName = Assert-TaskText $Name "Ten buoc nhiem vu" 255 $true
    $maxCount = Assert-TaskIntRange $CountLeft "So luong can hoan thanh" 0 32767
    $notifyText = Assert-TaskText $Notify "Thong bao" 255 $false
    $npc = Assert-TaskIntRange $NpcId "NPC ID" -128 32767
    $map = Assert-TaskIntRange $MapId "Map ID" -32768 32767
    Invoke-MySql "UPDATE task_sub_template SET task_main_id=$mainId,NAME=$(SqlString $subName),max_count=$maxCount,notify=$(SqlString $notifyText),npc_id=$npc,map=$map WHERE ducvupro=$subId LIMIT 1;" | Out-Null
    "OK`tDa luu buoc nhiem vu ID $subId. Restart server de ap dung template."
}

function List-TaskTemplates {
    $table = Get-TaskTemplateTable
    Invoke-MySql @"
SELECT id,
       REPLACE(REPLACE(REPLACE(COALESCE(NAME,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS name,
       max_count_lv1,max_count_lv2,max_count_lv3,max_count_lv4,max_count_lv5,
       CASE WHEN id BETWEEN 0 AND 58 THEN 1 ELSE 0 END AS protected
FROM $table
ORDER BY id;
"@
}

function Save-TaskTemplate {
    $table = Get-TaskTemplateTable
    $label = Get-TaskTemplateLabel
    $templateId = Assert-TaskIntRange $Id "ID template" 0 32767
    $taskName = Assert-TaskText $Name "Ten template" 255 $true
    $ranges = @(Convert-TaskRangePayload)
    $lv1 = SqlString $ranges[0]
    $lv2 = SqlString $ranges[1]
    $lv3 = SqlString $ranges[2]
    $lv4 = SqlString $ranges[3]
    $lv5 = SqlString $ranges[4]
    Invoke-MySql @"
INSERT INTO $table (id,NAME,max_count_lv1,max_count_lv2,max_count_lv3,max_count_lv4,max_count_lv5)
VALUES ($templateId,$(SqlString $taskName),$lv1,$lv2,$lv3,$lv4,$lv5)
ON DUPLICATE KEY UPDATE NAME=VALUES(NAME),max_count_lv1=VALUES(max_count_lv1),max_count_lv2=VALUES(max_count_lv2),max_count_lv3=VALUES(max_count_lv3),max_count_lv4=VALUES(max_count_lv4),max_count_lv5=VALUES(max_count_lv5);
"@ | Out-Null
    "OK`tDa luu $label ID $templateId. Restart server de ap dung template."
}

function List-BadgesTasks {
    Invoke-MySql @"
SELECT t.id,
       REPLACE(REPLACE(REPLACE(COALESCE(t.NAME,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS name,
       t.maxCount,t.idBadgesReward,
       REPLACE(REPLACE(REPLACE(COALESCE(b.NAME,''),CHAR(9),' '),CHAR(13),'\\r'),CHAR(10),'\\n') AS badge_name,
       COALESCE(b.idItem,-1) AS badge_item_id,
       COALESCE(i.icon_id,-1) AS badge_icon_id
FROM task_badges_template t
LEFT JOIN data_badges b ON b.idEffect=t.idBadgesReward
LEFT JOIN item_template i ON i.id=b.idItem
ORDER BY t.id;
"@
}

function Save-BadgesTask {
    $taskId = Assert-TaskIntRange $Id "ID nhiem vu danh hieu" 1 32767
    $exists = Get-MySqlScalar "SELECT COUNT(*) FROM task_badges_template WHERE id=$taskId LIMIT 1;"
    if ($exists -ne '1') { throw "Khong tim thay nhiem vu danh hieu ID $taskId. Chuc nang nay chi sua nhiem vu co san vi tien trinh duoc cap nhat bang hang so trong code." }
    $taskName = Assert-TaskText $Name "Ten nhiem vu danh hieu" 255 $true
    $maxCount = Assert-TaskIntRange $CountLeft "So luong can hoan thanh" 0 2147483647
    $badgeId = Assert-TaskIntRange $RequireId "ID danh hieu thuong" -1 32767
    Invoke-MySql "UPDATE task_badges_template SET NAME=$(SqlString $taskName),maxCount=$maxCount,idBadgesReward=$badgeId WHERE id=$taskId LIMIT 1;" | Out-Null
    "OK`tDa luu nhiem vu danh hieu ID $taskId. Restart server de ap dung template; player cu giu tien trinh hien tai cho toi khi reset task badges."
}
