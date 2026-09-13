# Clan configuration editor and read-only economy reports. Dot-sourced by admin_data.ps1 and tests.

function Get-ClanEconomyPropertyMap {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or $trimmed.IndexOf("=") -lt 1) { continue }
        $separator = $trimmed.IndexOf("=")
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $result[$key] = $value
    }
    $result
}

function New-ClanConfigEntry {
    param(
        [string]$Key,
        [string]$File,
        [string]$Property,
        [string]$Category,
        [string]$Name,
        [string]$Default,
        [string]$Kind,
        [string]$Minimum,
        [string]$Maximum,
        [int]$ListCount,
        [string]$Scope,
        [string]$Description
    )
    [pscustomobject]@{
        Key = $Key; File = $File; Property = $Property; Category = $Category;
        Name = $Name; Default = $Default; Kind = $Kind; Minimum = $Minimum;
        Maximum = $Maximum; ListCount = $ListCount; Scope = $Scope;
        Description = $Description
    }
}

function Get-ClanConfigCatalog {
    $rows = New-Object System.Collections.Generic.List[object]
    $restart = "Restart server"

    $features = @(
        [pscustomobject]@{ Key="territory"; Name="Khu bang"; Default="true" },
        [pscustomobject]@{ Key="treasury"; Name="Kho bạc bang"; Default="true" },
        [pscustomobject]@{ Key="tree"; Name="Cây bang"; Default="true" },
        [pscustomobject]@{ Key="progression"; Name="Tiến trình bang"; Default="true" },
        [pscustomobject]@{ Key="shop"; Name="Cửa hàng bang"; Default="true" },
        [pscustomobject]@{ Key="gift"; Name="Quà thành viên"; Default="true" },
        [pscustomobject]@{ Key="item_storage"; Name="Kho vật phẩm bang"; Default="true" },
        [pscustomobject]@{ Key="buff"; Name="Buff toàn bang"; Default="true" },
        [pscustomobject]@{ Key="value"; Name="Clan Value"; Default="false" },
        [pscustomobject]@{ Key="ranking"; Name="Xếp hạng bang"; Default="false" },
        [pscustomobject]@{ Key="appearance"; Name="Ngoại hình Cây bang"; Default="false" },
        [pscustomobject]@{ Key="economy_metrics"; Name="Metric kinh tế bang"; Default="false" }
    )
    foreach ($feature in $features) {
        $rows.Add((New-ClanConfigEntry -Key ("features." + $feature.Key + ".enabled") -File "clan_features.properties" -Property ($feature.Key + ".enabled") -Category "Công tắc chức năng" -Name ("Bật " + $feature.Name) -Default $feature.Default -Kind "bool" -Minimum "" -Maximum "" -ListCount 0 -Scope $restart -Description ("Cho phép server đọc và hiển thị chức năng " + $feature.Name + ".")))
        $rows.Add((New-ClanConfigEntry -Key ("features." + $feature.Key + ".mutations_enabled") -File "clan_features.properties" -Property ($feature.Key + ".mutations_enabled") -Category "Công tắc chức năng" -Name ("Cho phép thay đổi " + $feature.Name) -Default $feature.Default -Kind "bool" -Minimum "" -Maximum "" -ListCount 0 -Scope $restart -Description "Tắt để giữ phần đọc/snapshot nhưng chặn các thao tác làm thay đổi tài sản hoặc trạng thái."))
    }

    $progression = @(
        @("base_exp", "EXP cơ sở để lên cấp bang", "100", "long", "0", "9223372036854775807", "Chi phí EXP được nhân theo cấp hiện tại."),
        @("base_capsule", "Capsule cơ sở để lên cấp bang", "100", "int", "0", "2147483647", "Chi phí Capsule bang được nhân theo cấp hiện tại."),
        @("gold_base", "Vàng cơ sở để lên cấp bang", "100000", "long", "0", "9223372036854775807", "Chi phí Vàng bang từ cấp 5 trở lên."),
        @("gem_per_ten_levels", "Ngọc mỗi mốc 10 cấp", "5", "int", "0", "1000000", "Số Ngọc bang nhân với bậc 10 tại các mốc cấp."),
        @("technical_max_level", "Cấp bang kỹ thuật tối đa", "1000", "int", "1", "1000", "Trần cấp bang phía server."),
        @("exp_tree_water", "EXP khi tưới Cây bang", "10", "int", "0", "1000000", "Clan EXP nhận cho một lần tưới hợp lệ."),
        @("exp_tree_fertilize", "EXP khi bón phân", "30", "int", "0", "1000000", "Clan EXP nhận cho một lần bón phân hợp lệ."),
        @("exp_weekly_contract", "EXP hợp đồng tuần", "250", "int", "0", "100000000", "Clan EXP nhận khi hoàn tất hợp đồng tuần." )
    )
    foreach ($item in $progression) {
        $rows.Add((New-ClanConfigEntry -Key ("progression." + $item[0]) -File "clan_progression.properties" -Property $item[0] -Category "Tiến trình & chi phí" -Name $item[1] -Default $item[2] -Kind $item[3] -Minimum $item[4] -Maximum $item[5] -ListCount 0 -Scope $restart -Description $item[6]))
    }
    $branches = @(
        @("attack", "Sức đánh"), @("hp", "HP"), @("ki", "KI"),
        @("luck", "May mắn"), @("power", "Tiềm năng / Sức mạnh"), @("mob_gold", "Vàng từ quái")
    )
    foreach ($branch in $branches) {
        $property = $branch[0] + "_max_rank"
        $rows.Add((New-ClanConfigEntry -Key ("progression." + $property) -File "clan_progression.properties" -Property $property -Category "Tiềm năng bang" -Name ("Bậc tối đa: " + $branch[1]) -Default "20" -Kind "int" -Minimum "0" -Maximum "100" -ListCount 0 -Scope $restart -Description "Số bậc tối đa thành viên bang có thể phân bổ cho nhánh này."))
    }

    $buffs = @(
        @("hp_regen_percent", "Hồi HP mỗi nhịp", "1", "Phần trăm HP tối đa được hồi khi buff đang hoạt động."),
        @("ki_regen_percent", "Hồi KI mỗi nhịp", "1", "Phần trăm KI tối đa được hồi khi buff đang hoạt động."),
        @("attack_percent", "Buff Sức đánh", "10", "Phần trăm Sức đánh cộng cho toàn bộ thành viên online."),
        @("luck_percent", "Buff May mắn", "10", "Phần trăm May mắn cộng cho toàn bộ thành viên online."),
        @("power_percent", "Buff Tiềm năng / Sức mạnh", "15", "Phần trăm Tiềm năng và Sức mạnh cộng cho toàn bộ thành viên."),
        @("mob_gold_percent", "Buff Vàng từ quái", "20", "Phần trăm Vàng rơi từ quái cộng cho toàn bộ thành viên.")
    )
    foreach ($buff in $buffs) {
        $rows.Add((New-ClanConfigEntry -Key ("buff." + $buff[0]) -File "clan_buff.properties" -Property $buff[0] -Category "Buff toàn bang" -Name $buff[1] -Default $buff[2] -Kind "percent" -Minimum "0" -Maximum "100" -ListCount 0 -Scope $restart -Description $buff[3]))
    }
    $durations = @(
        @("short_duration_days", "Thời hạn gói ngắn", "1"),
        @("medium_duration_days", "Thời hạn gói vừa", "3"),
        @("long_duration_days", "Thời hạn gói dài", "7")
    )
    foreach ($duration in $durations) {
        $rows.Add((New-ClanConfigEntry -Key ("buff." + $duration[0]) -File "clan_buff.properties" -Property $duration[0] -Category "Buff toàn bang" -Name $duration[1] -Default $duration[2] -Kind "positive-int" -Minimum "1" -Maximum "365" -ListCount 0 -Scope $restart -Description "Số ngày cộng thêm khi dùng vật phẩm buff tương ứng; thời gian được cộng dồn."))
    }
    $rows.Add((New-ClanConfigEntry -Key "buff.recovery_interval_seconds" -File "clan_buff.properties" -Property "recovery_interval_seconds" -Category "Buff toàn bang" -Name "Chu kỳ hồi HP/KI" -Default "5" -Kind "positive-int" -Minimum "1" -Maximum "60" -ListCount 0 -Scope $restart -Description "Số giây giữa hai nhịp hồi HP/KI khi buff tương ứng đang hoạt động."))

    $treeSettings = @(
        @("water_item_id", "ID vật phẩm tưới cây", "456", "item-id", "0", "32767", "Template ID vật phẩm dùng để tưới Cây bang."),
        @("fertilizer_item_id", "ID vật phẩm phân bón", "1094", "item-id", "0", "32767", "Template ID vật phẩm dùng để bón Cây bang."),
        @("daily_water_limit", "Giới hạn tưới mỗi ngày", "5", "int", "0", "127", "Số lần tưới tối đa của mỗi thành viên trong ngày."),
        @("daily_fertilizer_limit", "Giới hạn bón phân mỗi ngày", "2", "int", "0", "127", "Số lần bón phân tối đa của mỗi thành viên trong ngày."),
        @("action_cooldown_ms", "Hồi chiêu thao tác cây", "500", "milliseconds", "0", "3600000", "Khoảng chờ tối thiểu giữa hai thao tác Cây bang."),
        @("help_cooldown_minutes", "Hồi chiêu trợ giúp", "10", "int", "0", "43200", "Số phút chờ giữa hai lần trợ giúp Cây bang."),
        @("help_duration_minutes", "Thời hạn lời gọi trợ giúp", "120", "int", "0", "43200", "Số phút một lời gọi trợ giúp còn hiệu lực."),
        @("production_cap_hours", "Trần giờ tích lũy sản lượng", "24", "positive-int", "1", "8760", "Số giờ sản lượng tối đa có thể tích lũy."),
        @("hourly_gold_per_level", "Vàng mỗi giờ / cấp cây", "5000", "long", "0", "9223372036854775807", "Sản lượng Vàng cơ sở theo giờ và cấp Cây bang."),
        @("hourly_capsule_per_5_levels", "Capsule mỗi giờ / 5 cấp cây", "1", "int", "0", "1000000", "Sản lượng Capsule theo giờ cho mỗi 5 cấp Cây bang."),
        @("growth_base", "Tăng trưởng cơ sở", "100", "long", "1", "1000000000", "Nền công thức điểm tăng trưởng cần để lên cấp cây."),
        @("growth_exponent", "Số mũ tăng trưởng", "1.40", "decimal", "0.10", "10", "Số mũ làm chi phí tăng trưởng tăng theo cấp cây."),
        @("water_required_base", "Nước yêu cầu cơ sở", "20", "int", "0", "1000000", "Nền công thức lượng nước yêu cầu."),
        @("water_required_per_level", "Nước tăng thêm mỗi cấp", "10", "int", "0", "1000000", "Lượng nước cộng thêm theo từng cấp cây."),
        @("fertilizer_levels_per_unit", "Số cấp cho một đơn vị phân", "3", "positive-int", "1", "127", "Tỷ lệ cấp cây dùng để tính lượng phân cần."),
        @("clan_levels_per_tree_level", "Cấp bang cho một cấp cây", "2", "positive-int", "1", "127", "Tỷ lệ cấp bang tối thiểu để mở cấp Cây bang."),
        @("growth_per_water_base", "Tăng trưởng mỗi lần tưới", "10", "int", "0", "1000000", "Điểm tăng trưởng cơ sở từ một lần tưới."),
        @("growth_per_water_level_bonus_cap", "Trần cộng tăng trưởng theo cấp", "10", "int", "0", "1000000", "Trần điểm cộng thêm theo cấp Cây bang cho mỗi lần tưới."),
        @("fertilizer_growth_multiplier", "Hệ số tăng trưởng phân bón", "5", "int", "0", "1000000", "Hệ số nhân điểm tăng trưởng khi bón phân.")
    )
    foreach ($tree in $treeSettings) {
        $category = if ($tree[0] -like "hourly_*" -or $tree[0] -like "growth_*" -or $tree[0] -like "*_required_*" -or $tree[0] -eq "fertilizer_growth_multiplier") { "Cây bang - Sản lượng" } else { "Cây bang - Hoạt động" }
        $rows.Add((New-ClanConfigEntry -Key ("tree." + $tree[0]) -File "clan_tree.properties" -Property $tree[0] -Category $category -Name $tree[1] -Default $tree[2] -Kind $tree[3] -Minimum $tree[4] -Maximum $tree[5] -ListCount 0 -Scope $restart -Description $tree[6]))
    }
    $rows.Add((New-ClanConfigEntry -Key "tree.max_tree_level" -File "clan_tree.properties" -Property "max_tree_level" -Category "Cây bang - Nâng cấp" -Name "Cấp Cây bang tối đa" -Default "20" -Kind "int" -Minimum "1" -Maximum "20" -ListCount 0 -Scope $restart -Description "Trần cấp cây từ 1 đến 20. Server dùng lịch nâng cấp có cấp cuối khớp trần này; cấp 1 không có nâng cấp. Không làm thay đổi kích thước ảnh."))
    $upgradeDefaults = @(1,1,1,2,2,2,3,3,3,4,4,5,5,6,7,8,9,11,13)
    # ClanTreeConfig reads the schedule for exactly the configured maximum level.
    for ($maximumLevel = 20; $maximumLevel -ge 2; $maximumLevel--) {
        $property = "upgrade_days_to_levels_2_$maximumLevel"
        $rows.Add((New-ClanConfigEntry -Key ("tree." + $property) -File "clan_tree.properties" -Property $property -Category "Cây bang - Nâng cấp" -Name ("Lịch nâng cây khi trần là cấp " + $maximumLevel) -Default ($upgradeDefaults[0..($maximumLevel - 2)] -join ',') -Kind "int-list" -Minimum "0" -Maximum "3650" -ListCount ($maximumLevel - 1) -Scope $restart -Description ("Số ngày cho từng cấp đích từ 2 đến " + $maximumLevel + ". Chỉ được sử dụng khi Cấp Cây bang tối đa = " + $maximumLevel + "; 0 là nâng ngay (dành cho test).")))
    }

    $giftSettings = @(
        @("enabled", "Bật quà thành viên", "true", "bool", "", "", "Công tắc riêng của dịch vụ quà thành viên."),
        @("sent_per_day", "Số quà gửi tối đa / ngày", "0", "int", "0", "2147483647", "0 nghĩa là không giới hạn tổng; luật một người nhận mỗi ngày vẫn áp dụng."),
        @("received_per_day", "Số quà nhận tối đa / ngày", "0", "int", "0", "2147483647", "0 nghĩa là không giới hạn tổng số quà được nhận."),
        @("minimum_join_hours", "Giờ tham gia tối thiểu", "0", "int", "0", "2147483647", "Số giờ thành viên phải ở trong bang trước khi được tặng quà."),
        @("minimum_contribution", "Cống hiến tối thiểu", "0", "long", "0", "9223372036854775807", "Điểm cống hiến tối thiểu của người gửi."),
        @("bound_gem_chance_percent", "Tỉ lệ nhận Ngọc khóa", "15", "percent", "0", "100", "Tỉ lệ phần trăm nhánh quà Ngọc khóa."),
        @("gold_base", "Vàng quà cơ sở", "100000", "long", "1", "9223372036854775807", "Vàng cơ sở nhân theo cấp bang và bậc sức mạnh người nhận.")
    )
    foreach ($gift in $giftSettings) {
        $rows.Add((New-ClanConfigEntry -Key ("gift." + $gift[0]) -File "clan_gift.properties" -Property $gift[0] -Category "Quà thành viên" -Name $gift[1] -Default $gift[2] -Kind $gift[3] -Minimum $gift[4] -Maximum $gift[5] -ListCount 0 -Scope $restart -Description $gift[6]))
    }

    $rows.Add((New-ClanConfigEntry -Key "shop.enabled" -File "clan_shop.properties" -Property "enabled" -Category "Cửa hàng bang" -Name "Bật Cửa hàng bang" -Default "true" -Kind "bool" -Minimum "" -Maximum "" -ListCount 0 -Scope $restart -Description "Công tắc riêng của dịch vụ Cửa hàng bang."))
    for ($tier = 1; $tier -le 3; $tier++) {
        $defaultLevel = if ($tier -eq 1) { "3" } elseif ($tier -eq 2) { "10" } else { "15" }
        $rows.Add((New-ClanConfigEntry -Key ("shop.tier." + $tier + ".clanLevel") -File "clan_shop.properties" -Property ("tier." + $tier + ".clanLevel") -Category "Cửa hàng bang" -Name ("Cấp bang mở tầng " + $tier) -Default $defaultLevel -Kind "positive-int" -Minimum "1" -Maximum "1000" -ListCount 0 -Scope $restart -Description "Cấp bang tối thiểu để mở tầng hàng tương ứng."))
    }
    $shopNames = @{
        2252="Buff Vàng quái 1 ngày"; 2253="Buff Vàng quái 3 ngày"; 2254="Buff Vàng quái 7 ngày";
        2255="Buff Tiềm năng/Sức mạnh 1 ngày"; 2256="Buff Tiềm năng/Sức mạnh 3 ngày"; 2257="Buff Tiềm năng/Sức mạnh 7 ngày";
        2258="Buff hồi HP 1 ngày"; 2259="Buff hồi HP 3 ngày"; 2260="Buff hồi HP 7 ngày";
        2261="Buff hồi KI 1 ngày"; 2262="Buff hồi KI 3 ngày"; 2263="Buff hồi KI 7 ngày";
        2264="Buff May mắn 1 ngày"; 2265="Buff May mắn 3 ngày"; 2266="Buff May mắn 7 ngày";
        2267="Buff Sức đánh 1 ngày"; 2268="Buff Sức đánh 3 ngày"; 2269="Buff Sức đánh 7 ngày";
        2270="Rút ngắn nâng cây 12 giờ"; 2271="Rút ngắn nâng cây 24 giờ"; 2272="Vé đổi tên bang"
    }
    for ($itemId = 2252; $itemId -le 2272; $itemId++) {
        if ($itemId -ge 2272) { $defaultRow = "3,3,30,1500000,20,300,1" }
        elseif ($itemId -ge 2269) { $defaultRow = "3,5,20,1000000,15,300,1" }
        elseif ($itemId -ge 2261) { $defaultRow = "2,8,12,500000,8,100,2" }
        else { $defaultRow = "1,12,6,250000,4,0,3" }
        $itemCategory = if ($itemId -le 2269) { "Cửa hàng bang - Vật phẩm buff" } else { "Cửa hàng bang - Vật phẩm hỗ trợ" }
        $rows.Add((New-ClanConfigEntry -Key ("shop.item." + $itemId) -File "clan_shop.properties" -Property ("item." + $itemId) -Category $itemCategory -Name ($shopNames[$itemId] + " (#" + $itemId + ")") -Default $defaultRow -Kind "shop-item" -Minimum "" -Maximum "" -ListCount 7 -Scope $restart -Description "Theo thứ tự: tầng, trần tồn kho, phí nhập Capsule bang, phí nhập Vàng bang, giá Capsule cá nhân, cống hiến tối thiểu, giới hạn mua/ngày."))
    }

    $valueSettings = @(
        @("formula_version", "Phiên bản công thức", "1", "int", "1", "255", "Phiên bản ghi cùng Clan Value để đối chiếu dữ liệu."),
        @("clan_level_weight", "Trọng số cấp bang", "1000", "long", "0", "9223372036854775807", "Điểm Clan Value cho mỗi cấp bang."),
        @("spent_potential_weight", "Trọng số Tiềm năng đã dùng", "100", "long", "0", "9223372036854775807", "Điểm Clan Value cho mỗi bậc Tiềm năng đã phân bổ."),
        @("tree_level_weight", "Trọng số cấp Cây bang", "500", "long", "0", "9223372036854775807", "Điểm Clan Value cho mỗi cấp Cây bang."),
        @("weekly_activity_weight", "Trọng số hoạt động tuần", "1", "long", "0", "9223372036854775807", "Điểm Clan Value cho mỗi đơn vị tiến độ tuần hợp lệ."),
        @("clan_level_cap", "Trần cấp bang tính điểm", "1000", "int", "0", "2147483647", "Trần dữ liệu cấp bang đưa vào công thức."),
        @("spent_potential_cap", "Trần Tiềm năng tính điểm", "10000", "int", "0", "2147483647", "Trần tổng bậc Tiềm năng đã dùng đưa vào công thức."),
        @("tree_level_cap", "Trần cấp cây tính điểm", "1000", "int", "0", "2147483647", "Trần cấp Cây bang đưa vào công thức."),
        @("weekly_activity_cap", "Trần hoạt động tuần tính điểm", "1000", "int", "0", "2147483647", "Trần tiến độ hoạt động tuần đưa vào công thức."),
        @("achievement_score_cap", "Trần điểm thành tựu", "1000000", "long", "0", "9223372036854775807", "Trần điểm thành tựu đưa vào Clan Value.")
    )
    foreach ($value in $valueSettings) {
        $rows.Add((New-ClanConfigEntry -Key ("value." + $value[0]) -File "clan_value.properties" -Property $value[0] -Category "Clan Value" -Name $value[1] -Default $value[2] -Kind $value[3] -Minimum $value[4] -Maximum $value[5] -ListCount 0 -Scope $restart -Description $value[6]))
    }

    $rankingSettings = @(
        @("default_page_size", "Số dòng mặc định / trang", "20", "int", "1", "50", "Kích thước trang khi client không gửi giá trị hợp lệ."),
        @("max_page_size", "Số dòng tối đa / trang", "50", "int", "1", "50", "Giới hạn 50 để bảo vệ packet command 127."),
        @("max_page", "Trang tối đa", "1000", "int", "0", "65535", "Chỉ số trang lớn nhất server chấp nhận."),
        @("refresh_seconds", "Chu kỳ làm mới xếp hạng", "30", "positive-int", "1", "3600", "Số giây giữ cache bảng xếp hạng."),
        @("legacy_top_size", "Số dòng bảng xếp hạng cũ", "50", "int", "1", "50", "Số bang gửi cho client dùng giao diện xếp hạng cũ.")
    )
    foreach ($ranking in $rankingSettings) {
        $rows.Add((New-ClanConfigEntry -Key ("ranking." + $ranking[0]) -File "clan_ranking.properties" -Property $ranking[0] -Category "Xếp hạng bang" -Name $ranking[1] -Default $ranking[2] -Kind $ranking[3] -Minimum $ranking[4] -Maximum $ranking[5] -ListCount 0 -Scope $restart -Description $ranking[6]))
    }

    $appearanceSettings = @(
        @("appearance_version", "Phiên bản ngoại hình", "1", "int", "1", "255", "Phiên bản snapshot ngoại hình gửi tới client. Đổi phiên bản không tự tạo hoặc phóng to ảnh."),
        @("resource_prefix", "Bộ ảnh Cây bang (tiền tố)", "cay_lv_", "resource-prefix", "", "", "Tên bộ ảnh đã cài, ví dụ cay_lv_v4_. Chỉ nhận a-z, 0-9 và dấu gạch dưới; kiểm tra đủ PNG x1–x4 trước khi lưu. Đổi bộ ảnh cần client tải lại tài nguyên tương ứng."),
        @("max_resource_level", "Cấp ảnh Cây bang tối đa", "20", "int", "1", "20", "Cấp cây cao hơn dùng ảnh của cấp này. Chỉ lưu khi bộ ảnh hiện tại đủ tài nguyên x1–x4 đến cấp đã chọn."),
        @("tier_count", "Số mốc ngoại hình hoạt động", "4", "int", "1", "4", "Số mốc sử dụng, tính từ mốc 1. Các mốc còn lại vẫn giữ cấu hình nhưng không được kích hoạt.")
    )
    foreach ($appearance in $appearanceSettings) {
        $rows.Add((New-ClanConfigEntry -Key ("appearance." + $appearance[0]) -File "clan_appearance.properties" -Property $appearance[0] -Category "Ngoại hình - Bộ ảnh" -Name $appearance[1] -Default $appearance[2] -Kind $appearance[3] -Minimum $appearance[4] -Maximum $appearance[5] -ListCount 0 -Scope $restart -Description $appearance[6]))
    }
    $appearanceDefaults = @(
        @("Mầm xanh", "Khởi nguyên", "0", "1", "1", "0x39B96E", "0"),
        @("Cổ thụ", "Bền vững", "30000", "20", "10", "0xD7A83A", "1"),
        @("Linh thụ", "Phồn thịnh", "75000", "40", "15", "0x55A8FF", "2"),
        @("Thần mộc", "Huyền thoại", "120000", "60", "20", "0xC56CFF", "3")
    )
    for ($tierIndex = 0; $tierIndex -lt 4; $tierIndex++) {
        $tierLabel = "Mốc ngoại hình " + ($tierIndex + 1)
        $tierValues = $appearanceDefaults[$tierIndex]
        $appearanceEntries = @(
            @("name", "Tên mốc", $tierValues[0], "java-text", "", "", "Tên hiển thị của mốc ngoại hình."),
            @("title", "Danh hiệu", $tierValues[1], "java-text", "", "", "Danh hiệu hiển thị cùng ngoại hình Cây bang."),
            @("value", "Clan Value tối thiểu", $tierValues[2], "long", "0", "9223372036854775807", "Clan Value tối thiểu để mở mốc."),
            @("clan_level", "Cấp bang tối thiểu", $tierValues[3], "int", "0", "1000", "Cấp bang tối thiểu để mở mốc."),
            @("tree_level", "Cấp cây tối thiểu", $tierValues[4], "int", "0", "20", "Cấp Cây bang tối thiểu để mở mốc."),
            @("accent_rgb", "Màu nhấn RGB", $tierValues[5], "hex-color", "", "", "Màu nhấn 0xRRGGBB gửi tới client."),
            @("aura_style", "Kiểu hào quang", $tierValues[6], "int", "0", "15", "Mã kiểu hào quang từ 0 đến 15.")
        )
        foreach ($appearance in $appearanceEntries) {
            $property = "tier_" + $tierIndex + "_" + $appearance[0]
            $rows.Add((New-ClanConfigEntry -Key ("appearance." + $property) -File "clan_appearance.properties" -Property $property -Category $tierLabel -Name $appearance[1] -Default $appearance[2] -Kind $appearance[3] -Minimum $appearance[4] -Maximum $appearance[5] -ListCount 0 -Scope $restart -Description $appearance[6]))
        }
    }

    $economySettings = @(
        @("lookback_default_days", "Cửa sổ báo cáo mặc định", "14", "positive-int", "1", "365", "Số ngày dùng khi không truyền bộ lọc báo cáo."),
        @("lookback_max_days", "Cửa sổ báo cáo tối đa", "90", "positive-int", "7", "365", "Số ngày tối đa cho một lần tổng hợp báo cáo."),
        @("retention_days", "Số ngày giữ metric", "180", "positive-int", "7", "730", "Thời gian lưu dữ liệu metric kinh tế tổng hợp."),
        @("flush_interval_seconds", "Chu kỳ ghi metric", "15", "positive-int", "5", "300", "Số giây giữa hai lần gom và ghi metric vận hành."),
        @("gold_sink_ratio_min_percent", "Vàng: sink/source tối thiểu", "70", "wide-percent", "0", "1000", "Thấp hơn ngưỡng này được cảnh báo dư nguồn sinh."),
        @("gold_sink_ratio_max_percent", "Vàng: sink/source tối đa", "110", "wide-percent", "0", "1000", "Cao hơn ngưỡng này được cảnh báo sink quá nặng."),
        @("gem_sink_ratio_min_percent", "Ngọc: sink/source tối thiểu", "50", "wide-percent", "0", "1000", "Thấp hơn ngưỡng này được cảnh báo dư nguồn sinh."),
        @("gem_sink_ratio_max_percent", "Ngọc: sink/source tối đa", "150", "wide-percent", "0", "1000", "Cao hơn ngưỡng này được cảnh báo sink quá nặng."),
        @("capsule_sink_ratio_min_percent", "Capsule: sink/source tối thiểu", "70", "wide-percent", "0", "1000", "Thấp hơn ngưỡng này được cảnh báo dư nguồn sinh."),
        @("capsule_sink_ratio_max_percent", "Capsule: sink/source tối đa", "130", "wide-percent", "0", "1000", "Cao hơn ngưỡng này được cảnh báo sink quá nặng.")
    )
    foreach ($economy in $economySettings) {
        $rows.Add((New-ClanConfigEntry -Key ("economy." + $economy[0]) -File "clan_economy.properties" -Property $economy[0] -Category "Giám sát kinh tế" -Name $economy[1] -Default $economy[2] -Kind $economy[3] -Minimum $economy[4] -Maximum $economy[5] -ListCount 0 -Scope "Báo cáo ngay; metric sau restart" -Description $economy[6]))
    }

    $chatSettings = @(
        @("leader_color", "Màu tin nhắn Bang chủ", "0xFFD700", "Màu hiển thị tin nhắn của Bang chủ trong kênh chat bang."),
        @("deputy_color", "Màu tin nhắn Thuyền phó", "0x00E5FF", "Màu hiển thị tin nhắn của Thuyền phó / Phó bang trong kênh chat bang."),
        @("member_color", "Màu tin nhắn Thành viên", "0xFFFFFF", "Màu hiển thị tin nhắn của Thành viên thường trong kênh chat bang."),
        @("tree_color", "Màu thông báo Cây bang", "0x00E676", "Màu hiển thị thông báo liên quan đến Cây bang trong kênh chat bang."),
        @("notify_color", "Màu thông báo chung của bang", "0xFF5252", "Màu hiển thị các thông báo hệ thống và sự kiện bang trong kênh chat bang.")
    )
    foreach ($chat in $chatSettings) {
        $rows.Add((New-ClanConfigEntry -Key ("chat." + $chat[0]) -File "clan_chat.properties" -Property $chat[0] -Category "Màu chat bang" -Name $chat[1] -Default $chat[2] -Kind "hex-color" -Minimum "" -Maximum "" -ListCount 0 -Scope $restart -Description $chat[3]))
    }
    $rows.ToArray()
}

function Get-ClanConfigEntry {
    param([string]$Key)
    Get-ClanConfigCatalog | Where-Object { $_.Key -eq $Key } | Select-Object -First 1
}

function ConvertFrom-ClanJavaPropertyText {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    [regex]::Replace($Value, '\\u([0-9A-Fa-f]{4})', {
        param($match)
        [char][Convert]::ToInt32($match.Groups[1].Value, 16)
    })
}

function ConvertTo-ClanJavaPropertyText {
    param([string]$Value)
    $builder = New-Object System.Text.StringBuilder
    foreach ($character in $Value.ToCharArray()) {
        $code = [int][char]$character
        if ($character -eq '\') { [void]$builder.Append('\\') }
        elseif ($code -ge 32 -and $code -le 126) { [void]$builder.Append($character) }
        else { [void]$builder.Append(("\u{0:X4}" -f $code)) }
    }
    $builder.ToString()
}

function Assert-ClanConfigValue {
    param([object]$Entry, [string]$Value)
    if ($null -eq $Entry) { throw "Khóa cấu hình bang không hợp lệ." }
    $text = if ($null -eq $Value) { "" } else { $Value.Trim() }
    if ($text -match '[\x00-\x1F\x7F]') { throw "Giá trị cấu hình không được chứa ký tự điều khiển hoặc xuống dòng." }
    if ($Entry.Kind -eq "bool") {
        $normalized = $text.ToLowerInvariant()
        if ($normalized -notin @("true", "false", "1", "0")) { throw "$($Entry.Name) chỉ nhận true/false hoặc 1/0." }
        return $(if ($normalized -in @("true", "1")) { "true" } else { "false" })
    }
    if ($Entry.Kind -eq "java-text") {
        if ([string]::IsNullOrWhiteSpace($text) -or $text.Length -gt 64) { throw "$($Entry.Name) phải có từ 1 đến 64 ký tự." }
        return ConvertTo-ClanJavaPropertyText $text
    }
    if ($Entry.Kind -eq "hex-color") {
        if ($text -notmatch '^0x[0-9A-Fa-f]{6}$') { throw "$($Entry.Name) phải theo dạng 0xRRGGBB." }
        return ("0x" + $text.Substring(2).ToUpperInvariant())
    }
    if ($Entry.Kind -eq "resource-prefix") {
        if ($text -cnotmatch '^[a-z0-9_]{1,32}$') { throw "Tiền tố ảnh chỉ gồm 1–32 ký tự a-z, 0-9 hoặc gạch dưới; không nhận đường dẫn." }
        return $text
    }
    if ($Entry.Kind -eq "decimal") {
        $number = 0D
        if (-not [double]::TryParse($text, [Globalization.NumberStyles]::AllowDecimalPoint, [Globalization.CultureInfo]::InvariantCulture, [ref]$number)) { throw "$($Entry.Name) phải là số thập phân dùng dấu chấm." }
        if ($number -lt [double]$Entry.Minimum -or $number -gt [double]$Entry.Maximum) { throw "$($Entry.Name) phải từ $($Entry.Minimum) đến $($Entry.Maximum)." }
        return $number.ToString("0.######", [Globalization.CultureInfo]::InvariantCulture)
    }
    if ($Entry.Kind -eq "int-list") {
        $parts = @($text -split ',')
        if ($parts.Count -ne $Entry.ListCount) { throw "$($Entry.Name) phải có đúng $($Entry.ListCount) số, phân cách bằng dấu phẩy." }
        $normalizedParts = New-Object System.Collections.Generic.List[string]
        foreach ($part in $parts) {
            $item = $part.Trim()
            if ($item -notmatch '^\d+$') { throw "$($Entry.Name) chỉ nhận số nguyên không âm." }
            $number = [decimal]$item
            if ($number -lt [decimal]$Entry.Minimum -or $number -gt [decimal]$Entry.Maximum) { throw "Mỗi giá trị của $($Entry.Name) phải từ $($Entry.Minimum) đến $($Entry.Maximum)." }
            $normalizedParts.Add($number.ToString("0"))
        }
        return ($normalizedParts -join ',')
    }
    if ($Entry.Kind -eq "shop-item") {
        $parts = @($text -split ',')
        if ($parts.Count -ne 7) { throw "$($Entry.Name) phải có đúng 7 số theo định dạng Cửa hàng bang." }
        $limits = @(
            @(1, 3), @(1, 1000000), @(0, 2147483647), @(0, 9223372036854775807),
            @(1, 2147483647), @(0, 9223372036854775807), @(1, 2147483647)
        )
        $normalizedParts = New-Object System.Collections.Generic.List[string]
        for ($index = 0; $index -lt 7; $index++) {
            $item = $parts[$index].Trim()
            if ($item -notmatch '^\d+$') { throw "$($Entry.Name): thành phần $($index + 1) phải là số nguyên không âm." }
            $number = [decimal]$item
            if ($number -lt [decimal]$limits[$index][0] -or $number -gt [decimal]$limits[$index][1]) { throw "$($Entry.Name): thành phần $($index + 1) nằm ngoài giới hạn an toàn." }
            $normalizedParts.Add($number.ToString("0"))
        }
        return ($normalizedParts -join ',')
    }
    if ($text -notmatch '^\d+$') { throw "$($Entry.Name) phải là số nguyên không âm." }
    $integer = [decimal]$text
    if ($integer -lt [decimal]$Entry.Minimum -or $integer -gt [decimal]$Entry.Maximum) { throw "$($Entry.Name) phải từ $($Entry.Minimum) đến $($Entry.Maximum)." }
    $integer.ToString("0")
}

function Get-ClanConfigPath {
    param([object]$Entry, [string]$ConfigRoot)
    if ($null -eq $Entry -or $Entry.File -notmatch '^clan_[a-z_]+\.properties$') { throw "Tệp cấu hình bang không nằm trong whitelist." }
    if ([string]::IsNullOrWhiteSpace($ConfigRoot) -or -not (Test-Path -LiteralPath $ConfigRoot -PathType Container)) { throw "Thiếu thư mục cấu hình bang." }
    $rootPath = [System.IO.Path]::GetFullPath($ConfigRoot)
    $targetPath = [System.IO.Path]::GetFullPath((Join-Path $rootPath $Entry.File))
    $prefix = $rootPath.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $targetPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Tệp cấu hình bang nằm ngoài thư mục config được phép." }
    $targetPath
}

function Get-ClanConfigEffectiveValue {
    param([object]$Entry, [hashtable]$Map)
    if ($Map.ContainsKey($Entry.Property)) { return [string]$Map[$Entry.Property] }
    [string]$Entry.Default
}

function Assert-ClanConfigRelationships {
    param([object]$Entry, [string]$ValidatedValue, [string]$ConfigRoot, [string]$DataRoot)
    $path = Get-ClanConfigPath -Entry $Entry -ConfigRoot $ConfigRoot
    $map = Get-ClanEconomyPropertyMap -Path $path
    $map[$Entry.Property] = $ValidatedValue
    $catalog = @(Get-ClanConfigCatalog | Where-Object { $_.File -eq $Entry.File })
    $effective = @{}
    foreach ($candidate in $catalog) { $effective[$candidate.Property] = Get-ClanConfigEffectiveValue -Entry $candidate -Map $map }

    if ($Entry.File -eq "clan_buff.properties") {
        if ([int]$effective["short_duration_days"] -gt [int]$effective["medium_duration_days"] -or [int]$effective["medium_duration_days"] -gt [int]$effective["long_duration_days"]) { throw "Thời hạn buff phải theo thứ tự gói ngắn ≤ gói vừa ≤ gói dài." }
    }
    if ($Entry.File -eq "clan_shop.properties") {
        if ([int]$effective["tier.1.clanLevel"] -gt [int]$effective["tier.2.clanLevel"] -or [int]$effective["tier.2.clanLevel"] -gt [int]$effective["tier.3.clanLevel"]) { throw "Cấp mở tầng Cửa hàng bang phải tăng dần từ tầng 1 đến tầng 3." }
    }
    if ($Entry.File -eq "clan_ranking.properties") {
        if ([int]$effective["default_page_size"] -gt [int]$effective["max_page_size"] -or [int]$effective["legacy_top_size"] -gt [int]$effective["max_page_size"]) { throw "Số dòng mặc định và bảng cũ không được vượt số dòng tối đa mỗi trang." }
    }
    if ($Entry.File -eq "clan_economy.properties") {
        if ([int]$effective["lookback_default_days"] -gt [int]$effective["lookback_max_days"]) { throw "Cửa sổ báo cáo mặc định không được vượt cửa sổ tối đa." }
        if ([int]$effective["retention_days"] -lt [int]$effective["lookback_max_days"]) { throw "Số ngày giữ metric phải lớn hơn hoặc bằng cửa sổ báo cáo tối đa." }
        foreach ($currency in @("gold", "gem", "capsule")) {
            if ([int]$effective[$currency + "_sink_ratio_min_percent"] -gt [int]$effective[$currency + "_sink_ratio_max_percent"]) { throw "Ngưỡng sink/source tối thiểu của $currency không được vượt ngưỡng tối đa." }
        }
    }
    if ($Entry.File -eq "clan_appearance.properties") {
        if ($Entry.Property -in @("resource_prefix", "max_resource_level")) {
            $resourcePrefix = Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "appearance.resource_prefix") -Value $effective["resource_prefix"]
            $resourceMaximum = [int](Assert-ClanConfigValue -Entry (Get-ClanConfigEntry -Key "appearance.max_resource_level") -Value $effective["max_resource_level"])
            for ($zoom = 1; $zoom -le 4; $zoom++) {
                for ($level = 1; $level -le $resourceMaximum; $level++) {
                    $relative = "img_by_name/x$zoom/{0}{1:00}.png" -f $resourcePrefix, $level
                    if (-not (Test-Path -LiteralPath (Join-Path $DataRoot $relative) -PathType Leaf)) {
                        throw "Thiếu ảnh data/$relative. Cài đủ bộ ảnh x1–x4 trước khi đổi cấu hình."
                    }
                }
            }
        }
        foreach ($suffix in @("value", "clan_level", "tree_level")) {
            $previous = -1L
            for ($tier = 0; $tier -lt 4; $tier++) {
                $current = [long]$effective["tier_${tier}_$suffix"]
                if ($current -lt $previous) { throw "Các mốc ngoại hình phải tăng dần theo $suffix." }
                $previous = $current
            }
        }
    }
}

function Set-ClanConfigPropertyValue {
    param([string]$Path, [string]$Property, [string]$Value, [switch]$Remove)
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) { $lines.Add($line) }
    }
    $found = $false
    for ($index = $lines.Count - 1; $index -ge 0; $index--) {
        if ($lines[$index] -match ("^\s*" + [regex]::Escape($Property) + "\s*=")) {
            if ($Remove) { $lines.RemoveAt($index) } else { $lines[$index] = "$Property=$Value" }
            $found = $true
        }
    }
    if (-not $Remove -and -not $found) { $lines.Add("$Property=$Value") }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    $temporaryPath = "$Path.admin-$PID-$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        [System.IO.File]::WriteAllText($temporaryPath, ($lines -join [Environment]::NewLine) + [Environment]::NewLine, $encoding)
        Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath -Force }
    }
}

function List-ClanConfig {
    param([string]$ConfigRoot = (Join-Path $Root "config\clan"))
    $maps = @{}
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add("key`tcategory`tname`tvalue`tdefault`tkind`tscope`tfile`tproperty`tdescription`tminimum`tmaximum`tlist_count")
    foreach ($entry in (Get-ClanConfigCatalog)) {
        if (-not $maps.ContainsKey($entry.File)) {
            $maps[$entry.File] = Get-ClanEconomyPropertyMap -Path (Get-ClanConfigPath -Entry $entry -ConfigRoot $ConfigRoot)
        }
        $rawValue = Get-ClanConfigEffectiveValue -Entry $entry -Map $maps[$entry.File]
        $value = if ($entry.Kind -eq "java-text") { ConvertFrom-ClanJavaPropertyText $rawValue } else { $rawValue }
        $default = [string]$entry.Default
        $cells = @($entry.Key, $entry.Category, $entry.Name, $value, $default, $entry.Kind, $entry.Scope, $entry.File, $entry.Property, $entry.Description, $entry.Minimum, $entry.Maximum, $entry.ListCount)
        for ($index = 0; $index -lt $cells.Count; $index++) { $cells[$index] = ([string]$cells[$index]) -replace '[\x00-\x1F\x7F]', ' ' }
        $rows.Add(($cells -join "`t"))
    }
    $rows -join "`r`n"
}

function Save-ClanConfig {
    param([string]$Key = $ConfigKey, [string]$Value = $ConfigValue, [string]$ConfigRoot = (Join-Path $Root "config\clan"), [string]$DataRoot = (Join-Path $Root "data"))
    $entry = Get-ClanConfigEntry -Key $Key
    if ($null -eq $entry) { throw "Khóa cấu hình bang không hợp lệ: $Key" }
    $validated = Assert-ClanConfigValue -Entry $entry -Value $Value
    Assert-ClanConfigRelationships -Entry $entry -ValidatedValue $validated -ConfigRoot $ConfigRoot -DataRoot $DataRoot
    $path = Get-ClanConfigPath -Entry $entry -ConfigRoot $ConfigRoot
    Set-ClanConfigPropertyValue -Path $path -Property $entry.Property -Value $validated
    "OK`tĐã lưu $($entry.Name) trong $($entry.File). Restart server để áp dụng đầy đủ."
}

function Reset-ClanConfig {
    param([string]$Key = $ConfigKey, [string]$ConfigRoot = (Join-Path $Root "config\clan"), [string]$DataRoot = (Join-Path $Root "data"))
    $entry = Get-ClanConfigEntry -Key $Key
    if ($null -eq $entry) { throw "Khóa cấu hình bang không hợp lệ: $Key" }
    $validatedDefault = Assert-ClanConfigValue -Entry $entry -Value ([string]$entry.Default)
    Assert-ClanConfigRelationships -Entry $entry -ValidatedValue $validatedDefault -ConfigRoot $ConfigRoot -DataRoot $DataRoot
    $path = Get-ClanConfigPath -Entry $entry -ConfigRoot $ConfigRoot
    Set-ClanConfigPropertyValue -Path $path -Property $entry.Property -Value "" -Remove
    "OK`tĐã đưa $($entry.Name) về mặc định $($entry.Default). Restart server để áp dụng đầy đủ."
}

function Get-ClanEconomyConfigInteger {
    param(
        [hashtable]$Values,
        [string]$Key,
        [int]$Default,
        [int]$Minimum,
        [int]$Maximum
    )
    $parsed = 0
    if ($Values.ContainsKey($Key) -and [int]::TryParse([string]$Values[$Key], [ref]$parsed)) {
        return [Math]::Max($Minimum, [Math]::Min($Maximum, $parsed))
    }
    [Math]::Max($Minimum, [Math]::Min($Maximum, $Default))
}

function Assert-ClanEconomyLookbackDays {
    param([string]$Value, [int]$Maximum = 90)
    if ($Maximum -lt 1 -or $Maximum -gt 365) { $Maximum = 90 }
    if ($Value -notmatch '^[0-9]{1,3}$') {
        throw "Lookback must be an integer number of days."
    }
    $days = [int]$Value
    if ($days -lt 1 -or $days -gt $Maximum) {
        throw "Lookback must be between 1 and $Maximum days."
    }
    $days
}

function Get-ClanEconomyBalanceAssessment {
    param(
        [long]$Inflow,
        [long]$Outflow,
        [int]$MinimumPercent,
        [int]$MaximumPercent
    )
    $safeInflow = [Math]::Max(0L, $Inflow)
    $safeOutflow = [Math]::Max(0L, $Outflow)
    if ($safeInflow -eq 0L -and $safeOutflow -eq 0L) {
        return [pscustomobject]@{ Status = "NO_FLOW"; RatioPercent = 0; Message = "No transactions" }
    }
    if ($safeInflow -eq 0L) {
        return [pscustomobject]@{ Status = "DEFICIT_RISK"; RatioPercent = 9999; Message = "Outflow without inflow" }
    }
    $ratio = [Math]::Min(9999, [int][Math]::Round(($safeOutflow * 100.0) / $safeInflow))
    if ($ratio -lt $MinimumPercent) {
        return [pscustomobject]@{ Status = "SURPLUS_RISK"; RatioPercent = $ratio; Message = "Inflow exceeds sink threshold" }
    }
    if ($ratio -gt $MaximumPercent) {
        return [pscustomobject]@{ Status = "DEFICIT_RISK"; RatioPercent = $ratio; Message = "Sink exceeds inflow threshold" }
    }
    [pscustomobject]@{ Status = "BALANCED"; RatioPercent = $ratio; Message = "Within configured threshold" }
}

function ConvertFrom-ClanEconomyTsv {
    param([string]$Text)
    $lines = @($Text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { return @() }
    @($lines | ConvertFrom-Csv -Delimiter "`t")
}

function ConvertTo-ClanEconomyTsvCell {
    param($Value)
    if ($null -eq $Value) { return "" }
    ([string]$Value) -replace '[\x00-\x1F\x7F]', ' '
}

function Add-ClanEconomyReportRow {
    param(
        [System.Text.StringBuilder]$Builder,
        [string]$Section,
        [string]$Key,
        [string]$Label,
        $Value,
        $Secondary,
        [string]$Status,
        [string]$Note
    )
    $cells = @($Section, $Key, $Label, $Value, $Secondary, $Status, $Note)
    for ($i = 0; $i -lt $cells.Count; $i++) { $cells[$i] = ConvertTo-ClanEconomyTsvCell $cells[$i] }
    [void]$Builder.AppendLine(($cells -join "`t"))
}

function Get-ClanEconomyCurrencyLabel {
    param([string]$Currency)
    switch ($Currency) {
        "0" { "Clan gold" }
        "1" { "Clan gem" }
        "2" { "Capsule bang" }
        default { "Currency $Currency" }
    }
}

function Get-ClanEconomySignalLabel {
    param([string]$Signal)
    $labels = @{
        TREE_WATER = "Tree water"; TREE_FERTILIZE = "Tree fertilizer";
        TREE_VITALITY_REACHED = "Daily vitality reached"; TREE_HARVEST = "Tree harvest";
        TREE_LEVEL_UP = "Tree level up"; CLAN_LEVEL_UP = "Clan level up";
        GIFT_SENT = "Gift sent"; GIFT_BLOCKED_POLICY = "Gift blocked by policy";
        PENDING_GIFT_DELIVERED = "Pending gift delivered"; DUPLICATE_REQUEST = "Duplicate request";
        TRANSACTION_ROLLBACK = "Transaction rollback"; TERRITORY_CREATED = "Territory created";
        TERRITORY_DISPOSED = "Territory disposed"
    }
    if ($labels.ContainsKey($Signal)) { return $labels[$Signal] }
    $Signal
}

function Get-ClanEconomyReport {
    param([string]$LookbackDays, [string]$ConfigPath)
    $config = Get-ClanEconomyPropertyMap -Path $ConfigPath
    $maximum = Get-ClanEconomyConfigInteger $config "lookback_max_days" 90 7 365
    $defaultDays = Get-ClanEconomyConfigInteger $config "lookback_default_days" 14 1 $maximum
    $daysText = if ([string]::IsNullOrWhiteSpace($LookbackDays)) { [string]$defaultDays } else { $LookbackDays }
    $days = Assert-ClanEconomyLookbackDays -Value $daysText -Maximum $maximum
    $offsetDays = $days - 1

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.AppendLine("section`tkey`tlabel`tvalue`tsecondary`tstatus`tnote")
    Add-ClanEconomyReportRow $builder "META" "generated_at" "Generated at" (Get-Date -Format "yyyy-MM-dd HH:mm:ss") "" "OK" ""
    Add-ClanEconomyReportRow $builder "META" "lookback_days" "Window" $days "days" "OK" "Vietnam day boundary"

    $summarySql = @"
SELECT COUNT(*) AS total_clans,
COALESCE(SUM(clan_gold),0) AS current_gold,
COALESCE(SUM(clan_gem),0) AS current_gem,
COALESCE(SUM(clan_point),0) AS current_capsule,
COALESCE(ROUND(AVG(clan_value)),0) AS average_value,
COALESCE(MAX(clan_value),0) AS maximum_value,
COALESCE(ROUND(AVG(level),2),0) AS average_level
FROM clan;
"@
    $summary = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $summarySql))
    if ($summary.Count -gt 0) {
        $row = $summary[0]
        Add-ClanEconomyReportRow $builder "SUMMARY" "total_clans" "Total clans" $row.total_clans "clans" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_gold" "Current gold" $row.current_gold "gold" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_gem" "Current gem" $row.current_gem "gem" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_capsule" "Current capsule" $row.current_capsule "capsule" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "average_value" "Average Clan Value" $row.average_value "points" "OK" "Maximum: $($row.maximum_value)"
        Add-ClanEconomyReportRow $builder "SUMMARY" "average_level" "Average clan level" $row.average_level "level" "OK" ""
    }

    $activeSql = "SELECT COUNT(DISTINCT clan_id) AS active_clans, COUNT(*) AS active_clan_days FROM clan_economy_active_clan WHERE metric_day>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY);"
    $active = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $activeSql))
    if ($active.Count -gt 0) {
        Add-ClanEconomyReportRow $builder "SUMMARY" "active_clans" "Active clans" $active[0].active_clans "clans" "OK" "Within $days days"
        Add-ClanEconomyReportRow $builder "SUMMARY" "active_clan_days" "Active clan-days" $active[0].active_clan_days "clan-days" "OK" "Denominator for tree care"
    }

    $flowSql = "SELECT currency_type,COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) AS inflow,COALESCE(SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END),0) AS outflow,COUNT(*) AS transactions FROM clan_ledger WHERE created_at>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY currency_type ORDER BY currency_type;"
    $flowRows = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $flowSql))
    foreach ($row in $flowRows) {
        $currency = [string]$row.currency_type
        $minimum = switch ($currency) {
            "0" { Get-ClanEconomyConfigInteger $config "gold_sink_ratio_min_percent" 70 0 1000 }
            "1" { Get-ClanEconomyConfigInteger $config "gem_sink_ratio_min_percent" 50 0 1000 }
            default { Get-ClanEconomyConfigInteger $config "capsule_sink_ratio_min_percent" 70 0 1000 }
        }
        $maximumRatio = switch ($currency) {
            "0" { Get-ClanEconomyConfigInteger $config "gold_sink_ratio_max_percent" 110 $minimum 1000 }
            "1" { Get-ClanEconomyConfigInteger $config "gem_sink_ratio_max_percent" 150 $minimum 1000 }
            default { Get-ClanEconomyConfigInteger $config "capsule_sink_ratio_max_percent" 130 $minimum 1000 }
        }
        $assessment = Get-ClanEconomyBalanceAssessment -Inflow ([long]$row.inflow) -Outflow ([long]$row.outflow) -MinimumPercent $minimum -MaximumPercent $maximumRatio
        Add-ClanEconomyReportRow $builder "FLOW" $currency (Get-ClanEconomyCurrencyLabel $currency) $row.inflow $row.outflow $assessment.Status ("Sink/source {0}% | {1} | {2} transactions" -f $assessment.RatioPercent, $assessment.Message, $row.transactions)
    }
    foreach ($currency in @("0", "1", "2")) {
        if (-not ($flowRows | Where-Object { [string]$_.currency_type -eq $currency })) {
            Add-ClanEconomyReportRow $builder "FLOW" $currency (Get-ClanEconomyCurrencyLabel $currency) 0 0 "NO_FLOW" "No transactions in window"
        }
    }

    $metricSql = "SELECT signal_key,SUM(event_count) AS event_count,SUM(amount_total) AS amount_total FROM clan_economy_metric WHERE metric_day>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY signal_key ORDER BY signal_key;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $metricSql))) {
        $note = if ($row.signal_key -eq "TREE_LEVEL_UP") { "amount = total tree upgrade milliseconds" } elseif ($row.signal_key -eq "TERRITORY_DISPOSED") { "amount = total territory lifetime milliseconds" } else { "" }
        Add-ClanEconomyReportRow $builder "SIGNAL" $row.signal_key (Get-ClanEconomySignalLabel $row.signal_key) $row.event_count $row.amount_total "OK" $note
    }

    $actionSql = "SELECT currency_type,action_type,SUM(CASE WHEN amount>0 THEN amount ELSE 0 END) AS inflow,SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END) AS outflow,COUNT(*) AS transactions FROM clan_ledger WHERE created_at>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY currency_type,action_type ORDER BY currency_type,SUM(CASE WHEN amount>=0 THEN amount ELSE -amount END) DESC,action_type LIMIT 60;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $actionSql))) {
        Add-ClanEconomyReportRow $builder "ACTION" ("$($row.currency_type):$($row.action_type)") $row.action_type $row.inflow $row.outflow "OK" ((Get-ClanEconomyCurrencyLabel $row.currency_type) + " | $($row.transactions) transactions")
    }

    $potentialSql = "SELECT branch_key,COUNT(*) AS clans_with_rank,SUM(rank_value) AS total_rank,ROUND(AVG(rank_value),2) AS average_rank,MAX(rank_value) AS maximum_rank FROM clan_potential WHERE rank_value>0 GROUP BY branch_key ORDER BY branch_key;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $potentialSql))) {
        Add-ClanEconomyReportRow $builder "POTENTIAL" $row.branch_key $row.branch_key $row.total_rank $row.clans_with_rank "OK" ("Average rank $($row.average_rank) | maximum $($row.maximum_rank)")
    }

    $pendingSql = "SELECT COUNT(*) AS pending_count,COALESCE(SUM(gold_amount),0) AS pending_gold,COALESCE(SUM(ruby_amount),0) AS pending_ruby FROM clan_pending_reward WHERE status=0;"
    $pending = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $pendingSql))
    if ($pending.Count -gt 0) {
        $pendingStatus = if ([long]$pending[0].pending_count -gt 0) { "WARNING" } else { "OK" }
        Add-ClanEconomyReportRow $builder "SUMMARY" "pending_rewards" "Pending rewards" $pending[0].pending_count "rewards" $pendingStatus ("Gold $($pending[0].pending_gold) | bound gem $($pending[0].pending_ruby)")
    }
    $builder.ToString().TrimEnd()
}
