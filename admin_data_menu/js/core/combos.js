function InitCombos() {
  var baseTypes = ItemTypeBaseItems();
  var filterTypes = [["", "Tất cả group"]];
  for (var i = 0; i < baseTypes.length; i++) filterTypes.push(baseTypes[i]);
  SetComboItems("itemTypeFilter", filterTypes);
  SetComboItems("itemType", baseTypes);
  SetComboItems("equipTypeFilter", filterTypes);
  SetComboItems("radarRankFilter", [
    ["-1", "T\u1ea5t c\u1ea3 rank"],
    ["0", "Rank 0"],
    ["1", "Rank 1"],
    ["2", "Rank 2"],
    ["3", "Rank 3"],
    ["4", "Rank 4"],
    ["5", "Rank 5"],
    ["6", "Rank 6"]
  ]);
  SetComboItems("radarRank", [
    ["0", "Rank 0"],
    ["1", "Rank 1"],
    ["2", "Rank 2"],
    ["3", "Rank 3"],
    ["4", "Rank 4"],
    ["5", "Rank 5"],
    ["6", "Rank 6"]
  ]);
  SetComboItems("radarType", [
    ["0", "Qu\u00e1i / Mob"],
    ["1", "Nh\u00e2n v\u1eadt / part"]
  ]);
  SetComboItems("radarRequireLevel", [
    ["0", "M\u1ed1c 0"],
    ["1", "M\u1ed1c 1"],
    ["2", "M\u1ed1c 2"]
  ]);
  SetComboItems("radarOptionActive", [
    ["0", "M\u1ed1c 0"],
    ["1", "M\u1ed1c 1"],
    ["2", "M\u1ed1c 2"]
  ]);
  SetComboItems("expRate", [
    ["1", "x1"],
    ["2", "x2"],
    ["3", "x3"],
    ["5", "x5"],
    ["10", "x10"]
  ]);
  SetComboItems("eventItemSource", [
    ["mob", "Mob (t\u1ef1 \u0111\u1ed9ng r\u01a1i)"],
    ["boss", "Boss (t\u1ef1 \u0111\u1ed9ng r\u01a1i)"],
    ["gameplay", "Gameplay / \u0111\u1ed5i qu\u00e0"],
    ["reward", "Ph\u1ea7n th\u01b0\u1edfng"],
    ["built_in", "Code c\u00f3 s\u1eb5n"]
  ]);
  var playerLimitOptions = [];
  for (var tier = 0; tier <= 9; tier++) playerLimitOptions.push(["" + tier, "C\u1ea5p " + tier]);
  SetComboItems("playerLimitPower", playerLimitOptions);
  SetComboItems("playerBan", [
    ["0", "Kh\u00f4ng kh\u00f3a"],
    ["1", "\u0110ang kh\u00f3a"]
  ]);
  SetComboItems("playerActive", [
    ["1", "\u0110\u00e3 k\u00edch ho\u1ea1t"],
    ["0", "Ch\u01b0a k\u00edch ho\u1ea1t"]
  ]);
  SetComboItems("bossSkillId", [["", "Ch\u1ecdn skill"]]);
  SetComboItems("itemGender", [
    ["0", "0 - Trái Đất"],
    ["1", "1 - Namek"],
    ["2", "2 - Xayda"],
    ["3", "3 - Dùng chung"]
  ]);
  SetComboItems("itemUp", [
    ["0", "0 - Không cho nâng cấp"],
    ["1", "1 - Cho nâng cấp"]
  ]);
  SetComboItems("shopTypeSell", [
    ["0", "Vàng"],
    ["1", "Ngọc"],
    ["2", "Ngọc khóa (chưa hỗ trợ)"],
    ["3", "Ruby"],
    ["4", "Coupon"]
  ]);
  SetComboItems("shopIsNew", [["1", "Bật"], ["0", "Tắt"]]);
  SetComboItems("shopIsSell", [["1", "Bật"], ["0", "Tắt"]]);
  SetComboItems("shopOptionMode", [["0", "Kế thừa mặc định"], ["1", "Tùy chỉnh riêng"]]);
  RenderEquipPresets();
}

function OnComboChanged(id) {
  if (id == "itemTypeFilter") LoadItems();
  if (id == "equipTypeFilter") LoadEquipItems();
  if (id == "radarRankFilter") LoadRadarCards();
  if (id == "radarType" || id == "radarRequireLevel") UpdateRadarMetaHints();
  if (id == "playerLimitPower") UpdatePlayerTierNote();
  if (id == "bossSkillId") ShowBossSkillInfo();
}
