var adminBossRows = [];
var bossDrops = [];
var selectedBossDrop = -1;
var bossSkillRows = [];
var bossSkillNames = {};
var bossDefaultSkills = [];
var bossConfiguredSkills = [];
var selectedBossSkill = -1;
var bossSelectedMaps = [];

var bossMode = "server";
var customBossRows = [];
var customBossDrops = [];
var selectedCustomBossDrop = -1;
var customBossSkills = [];
var selectedCustomBossSkill = -1;
var customBossLevels = [];
var selectedCustomBossLevel = -1;
var customBossCostumeRows = [];
var customBossSelectedMap = "";
var customBossSelectedCostumeId = "";
var customBossLongMax = "9223372036854775807";

function BossHasClass(node, name) {
  return node && (" " + node.className + " ").indexOf(" " + name + " ") >= 0;
}

function BossSetClass(node, name, enabled) {
  if (!node) return;
  var classes = (" " + node.className + " ").replace(/\s+/g, " ");
  var has = classes.indexOf(" " + name + " ") >= 0;
  if (enabled && !has) classes += name + " ";
  if (!enabled && has) classes = classes.replace(" " + name + " ", " ");
  node.className = Trim(classes);
}

function BossSetHidden(id, hidden) {
  BossSetClass(document.getElementById(id), "admin-hidden", hidden);
}

function BossSetActive(id, active) {
  BossSetClass(document.getElementById(id), "active", active);
}

function AdjustBossTableOffsets() {
  var modeButton = document.getElementById("bossModeServer");
  if (!modeButton || !modeButton.parentNode || !modeButton.parentNode.parentNode) return;
  var toolbar = modeButton.parentNode.parentNode;
  var h = toolbar.scrollHeight > toolbar.offsetHeight ? toolbar.scrollHeight : toolbar.offsetHeight;
  if (h < 56) h = 56;
  var serverWrap = document.getElementById("bossServerTableWrap");
  var customWrap = document.getElementById("bossCustomTableWrap");
  if (serverWrap) serverWrap.style.top = h + "px";
  if (customWrap) customWrap.style.top = h + "px";
}

function BossBadge(text, kind) {
  return '<span class="event-badge ' + HtmlAttr(kind || "off") + '">' + Html(text) + "</span>";
}

function SetBossBadge(id, text, kind) {
  var target = document.getElementById(id);
  if (!target) return;
  target.className = "event-badge " + (kind || "off");
  target.innerText = text;
}

function BossGenderName(value) {
  if (value == "0") return "Trái Đất";
  if (value == "1") return "Namek";
  if (value == "2") return "Xayda";
  if (value == "3") return "Dùng chung";
  return "Gender " + value;
}

function BossScheduleBadge(useRange, start, end, useInterval, minutes) {
  return BossBadge(SpawnScheduleLabel(useRange, start, end, useInterval, minutes), useRange == "1" || useInterval == "1" ? "warning" : "active");
}

function BossDropOptionText(drop) {
  var text = SpawnOptionsText(drop.options);
  if (drop.useDefaultOptions) text = text ? "Mặc định + " + text : "Mặc định";
  return text || "Không thêm";
}

function NormalizeBossDropList(list) {
  for (var i = 0; i < list.length; i++) {
    var drop = list[i];
    var options = drop.options || [];
    if (options && options.length == 1 && options[0] && options[0].options != null && options[0].id == null) {
      if (drop.useDefaultOptions == null) drop.useDefaultOptions = options[0].useDefaultOptions;
      options = options[0].options || [];
    } else if (options && options.options != null) {
      if (drop.useDefaultOptions == null) drop.useDefaultOptions = options.useDefaultOptions;
      options = options.options || [];
    }
    if (!options || options.length == null) options = [];
    drop.options = options;
    drop.useDefaultOptions = drop.useDefaultOptions === true || drop.useDefaultOptions === 1 || drop.useDefaultOptions === "1" || drop.useDefaultOptions === "true";
    for (var o = 0; o < options.length; o++) {
      var fixed = options[o].param == null ? 0 : parseInt(options[o].param, 10);
      if (options[o].paramMin == null) options[o].paramMin = fixed;
      if (options[o].paramMax == null) options[o].paramMax = options[o].paramMin;
      delete options[o].param;
    }
  }
  return list;
}

function NormalizeBossDrops() {
  bossDrops = NormalizeBossDropList(bossDrops);
}

function NormalizeCustomBossDrops() {
  customBossDrops = NormalizeBossDropList(customBossDrops);
}

function SetBossMode(mode) {
  bossMode = mode == "custom" ? "custom" : "server";
  BossSetActive("bossModeServer", bossMode == "server");
  BossSetActive("bossModeCustom", bossMode == "custom");
  BossSetHidden("bossSearch", bossMode != "server");
  BossSetHidden("bossSearchButton", bossMode != "server");
  BossSetHidden("bossServerTableWrap", bossMode != "server");
  BossSetHidden("bossServerEditor", bossMode != "server");
  BossSetHidden("customBossSearch", bossMode != "custom");
  BossSetHidden("customBossSearchButton", bossMode != "custom");
  BossSetHidden("bossCustomTableWrap", bossMode != "custom");
  BossSetHidden("bossCustomEditor", bossMode != "custom");
  if (bossMode == "custom" && customBossRows.length == 0) LoadCustomBosses();
  if (bossMode == "custom") {
    RenderCustomBossCostumeChecklist();
    RenderCustomBossMapChecklist();
    RenderCustomBossItemChecklist();
    UpdateCustomBossAppearancePreview();
  }
  AdjustTableOffsets();
  AdjustBossTableOffsets();
}

function InitBossCombos() {
  SetComboItems("customBossGender", [
    ["0", "0 - Trái Đất"],
    ["1", "1 - Namek"],
    ["2", "2 - Xayda"],
    ["3", "3 - Dùng chung"]
  ]);
  LoadBossSkillCatalog();
}

function RenderBossMapChecklist() {
  if (!document.getElementById("bossMapChecklist")) return;
  var selected = {};
  for (var i = 0; i < bossSelectedMaps.length; i++) selected["" + bossSelectedMaps[i]] = true;
  RenderChecklist({
    listId: "bossMapChecklist", idPrefix: "bossMapChoice", rows: spawnMaps, rowStart: 1,
    query: V("bossMapSearch"), selected: selected, selectedOrder: bossSelectedMaps,
    toggleFunction: "ToggleBossMap", emptyText: "Không tìm thấy map phù hợp."
  });
  var names = [];
  for (var m = 0; m < bossSelectedMaps.length; m++) {
    names.push(bossSelectedMaps[m] + " - " + ChecklistLabelFromRows(spawnMaps, 1, 0, 1, "" + bossSelectedMaps[m]));
  }
  document.getElementById("bossMapSummary").innerText = names.length
    ? "Đã chọn " + names.length + " map; mỗi lần xuất hiện sẽ chọn ngẫu nhiên: " + names.join(", ")
    : "Chưa chọn map, dùng map mặc định trong BossesData";
}

function ToggleBossMap(id, checked) {
  var value = parseInt(id, 10);
  var next = [];
  for (var i = 0; i < bossSelectedMaps.length; i++) if (bossSelectedMaps[i] != value) next.push(bossSelectedMaps[i]);
  if (checked) next.push(value);
  bossSelectedMaps = next;
  RenderBossMapChecklist();
}

function FindBossDropIndex(itemId) {
  for (var i = 0; i < bossDrops.length; i++) if (parseInt(bossDrops[i].itemId, 10) == parseInt(itemId, 10)) return i;
  return -1;
}

function RenderBossItemChecklist() {
  if (!document.getElementById("bossItemChecklist")) return;
  var selected = {}; var order = [];
  for (var i = 0; i < bossDrops.length; i++) {
    selected["" + bossDrops[i].itemId] = true; order.push(bossDrops[i].itemId);
  }
  RenderItemPicker({
    listId: "bossItemChecklist", idPrefix: "bossDropChoice", rows: spawnItems, rowStart: 1,
    query: V("bossItemSearch"), selected: selected, selectedOrder: order,
    labelResolver: ItemCatalogLabel, toggleFunction: "ToggleBossDropItem", limit: 250,
    emptyText: "Không tìm thấy vật phẩm phù hợp."
  });
  document.getElementById("bossItemSummary").innerText = bossDrops.length
    ? "Đã chọn " + bossDrops.length + " vật phẩm rơi thêm. Chọn một dòng trong bảng dưới để chỉnh."
    : "Chưa chọn vật phẩm rơi thêm";
  SetBossBadge("bossDropCountBadge", "" + bossDrops.length, bossDrops.length ? "configured" : "pending");
}

function ToggleBossDropItem(id, checked) {
  var index = FindBossDropIndex(id);
  if (checked && index < 0) {
    bossDrops.push({ itemId: parseInt(id, 10), quantityMin: 1, quantityMax: 1, dropRate: 100, useDefaultOptions: false, options: [] });
    index = bossDrops.length - 1;
  } else if (!checked && index >= 0) {
    bossDrops.splice(index, 1);
    if (selectedBossDrop == index) selectedBossDrop = -1;
    else if (selectedBossDrop > index) selectedBossDrop--;
  }
  RenderBossItemChecklist(); RenderBossDrops();
  if (checked && index >= 0) PickBossDrop(index); else if (selectedBossDrop < 0) ClearBossDropSelection();
}

function RenderBossDrops() {
  var html = "<thead><tr><th>Item</th><th>Số lượng</th><th>Tỉ lệ</th><th>Option</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < bossDrops.length; i++) {
    var d = bossDrops[i];
    html += '<tr onclick="PickBossDrop(' + i + ')"><td>' + Html(d.itemId + " - " + (spawnItemNames[d.itemId] || "Không rõ")) + '</td><td>' + Html(d.quantityMin + "-" + d.quantityMax) + '</td><td>' + Html(d.dropRate + "%") + '</td><td>' + Html(BossDropOptionText(d)) + '</td><td><button class="danger" onclick="RemoveBossDrop(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById("bossDropsTable").innerHTML = html + "</tbody>";
  SetBossBadge("bossDropCountBadge", "" + bossDrops.length, bossDrops.length ? "configured" : "pending");
}

function PickBossDrop(index) {
  if (index < 0 || index >= bossDrops.length) return;
  selectedBossDrop = index;
  var d = bossDrops[index];
  Set("bossDropItem", d.itemId); Set("bossDropItemName", spawnItemNames[d.itemId] || "Không rõ");
  Set("bossDropMin", d.quantityMin); Set("bossDropMax", d.quantityMax); Set("bossDropRate", d.dropRate);
  if (document.getElementById("bossDropUseDefault")) document.getElementById("bossDropUseDefault").checked = !!d.useDefaultOptions;
  RenderBossOptionChecklist();
}

function RemoveBossDrop(index) {
  if (index < 0 || index >= bossDrops.length) return;
  bossDrops.splice(index, 1); selectedBossDrop = -1;
  ClearBossDropSelection(); RenderBossItemChecklist(); RenderBossDrops();
}

function ClearBossDropSelection() {
  selectedBossDrop = -1;
  Set("bossDropItem", ""); Set("bossDropItemName", ""); Set("bossDropMin", "1"); Set("bossDropMax", "1"); Set("bossDropRate", "100");
  if (document.getElementById("bossDropUseDefault")) document.getElementById("bossDropUseDefault").checked = false;
  RenderBossOptionChecklist();
}

function UpdateBossDrop() {
  if (selectedBossDrop < 0 || selectedBossDrop >= bossDrops.length) { Msg("bossMessage", "Hãy chọn một vật phẩm trong checklist hoặc bảng drop."); return; }
  if (!/^\d+$/.test(V("bossDropMin")) || !/^\d+$/.test(V("bossDropMax")) || !/^\d+(\.\d+)?$/.test(V("bossDropRate"))) {
    Msg("bossMessage", "Số lượng và tỉ lệ phải là số hợp lệ."); return;
  }
  var min = parseInt(V("bossDropMin"), 10), max = parseInt(V("bossDropMax"), 10), rate = parseFloat(V("bossDropRate"));
  if (min < 1 || min > max || rate < 0 || rate > 100) { Msg("bossMessage", "Số lượng min không lớn hơn max và tỉ lệ phải từ 0-100."); return; }
  var options = bossDrops[selectedBossDrop].options || [];
  for (var i = 0; i < options.length; i++) {
    if (!/^-?\d+$/.test("" + options[i].paramMin) || !/^-?\d+$/.test("" + options[i].paramMax) || parseInt(options[i].paramMin, 10) > parseInt(options[i].paramMax, 10)) {
      Msg("bossMessage", "Option " + options[i].id + " có khoảng chỉ số không hợp lệ."); return;
    }
  }
  bossDrops[selectedBossDrop].quantityMin = min;
  bossDrops[selectedBossDrop].quantityMax = max;
  bossDrops[selectedBossDrop].dropRate = rate;
  bossDrops[selectedBossDrop].useDefaultOptions = !!document.getElementById("bossDropUseDefault") && document.getElementById("bossDropUseDefault").checked;
  RenderBossDrops(); Msg("bossMessage", "Đã cập nhật drop; bấm Lưu cấu hình Boss để ghi database.");
}

function RenderBossOptionChecklist() {
  var target = document.getElementById("bossOptionChecklist");
  if (!target) return;
  if (selectedBossDrop < 0 || selectedBossDrop >= bossDrops.length) {
    target.innerHTML = "Chọn một vật phẩm trong checklist hoặc bảng drop để cấu hình option.";
    document.getElementById("bossOptionSummary").innerText = "Chưa chọn vật phẩm";
    return;
  }
  var options = bossDrops[selectedBossDrop].options || [];
  var selected = {}; var html = ""; var rendered = {}; var query = V("bossOptionSearch").toLowerCase();
  for (var i = 0; i < options.length; i++) {
    selected["" + options[i].id] = options[i];
    html += BossOptionItemHtml("" + options[i].id, OptionName(options[i].id), true, options[i]); rendered["" + options[i].id] = true;
  }
  for (var r = 1; r < optionRows.length; r++) {
    var id = "" + optionRows[r][0], name = optionRows[r][1] || "Chưa có tên";
    if (!id || rendered[id] || (query && (id + " " + name).toLowerCase().indexOf(query) < 0)) continue;
    html += BossOptionItemHtml(id, name, false, null); rendered[id] = true;
  }
  target.innerHTML = html || "Không tìm thấy option phù hợp.";
  document.getElementById("bossOptionSummary").innerText = "Đã chọn " + options.length + " option cho " + V("bossDropItemName") + ". Min = Max nếu không muốn random.";
}

function BossOptionItemHtml(id, name, checked, option) {
  var safeId = id.replace(/[^a-zA-Z0-9_-]/g, "_");
  var html = '<div class="option-picker-item boss-option-item' + (checked ? ' checklist-item-selected' : '') + '"><input type="checkbox" id="bossDropOption_' + safeId + '"' + (checked ? ' checked="checked"' : '') + ' onclick="ToggleBossDropOption(\'' + EscapeJs(id) + '\', this.checked)"><label for="bossDropOption_' + safeId + '">' + Html(id + " - " + name) + '</label>';
  if (checked) {
    html += '<span class="boss-option-range">Min <input value="' + HtmlAttr(option.paramMin) + '" onchange="UpdateBossDropOptionBound(\'' + EscapeJs(id) + '\', \'paramMin\', this.value)"> Max <input value="' + HtmlAttr(option.paramMax) + '" onchange="UpdateBossDropOptionBound(\'' + EscapeJs(id) + '\', \'paramMax\', this.value)"></span>';
  }
  return html + "</div>";
}

function ToggleBossDropOption(id, checked) {
  if (selectedBossDrop < 0 || selectedBossDrop >= bossDrops.length) return;
  var options = bossDrops[selectedBossDrop].options || [], next = [], found = false;
  for (var i = 0; i < options.length; i++) {
    if (("" + options[i].id) == id) { found = true; if (!checked) continue; }
    next.push(options[i]);
  }
  if (checked && !found) next.push({ id: parseInt(id, 10), paramMin: 0, paramMax: 0 });
  bossDrops[selectedBossDrop].options = next;
  RenderBossOptionChecklist(); RenderBossDrops();
}

function UpdateBossDropOptionBound(id, field, value) {
  if (selectedBossDrop < 0 || !/^-?\d+$/.test(value)) { Msg("bossMessage", "Chỉ số option phải là số nguyên."); return; }
  var options = bossDrops[selectedBossDrop].options || [];
  for (var i = 0; i < options.length; i++) if (("" + options[i].id) == id) options[i][field] = parseInt(value, 10);
  RenderBossDrops();
}

function LoadAdminBosses() {
  LoadBossSkillCatalog();
  adminBossRows = ParseTsv(RunAdmin("listexistingbosses", { Search: V("bossSearch") }));
  var html = "<thead><tr><th>Boss</th><th>Class</th><th>Map</th><th>Skill</th><th>Cấu hình</th></tr></thead><tbody>";
  for (var i = 1; i < adminBossRows.length; i++) {
    var row = adminBossRows[i];
    var skills = ParseBossSkills(row[6]);
    var configuredMaps = ParseNumberArray(row[14]);
    var mapLabel = configuredMaps.length ? ((row[15] || configuredMaps.join(", ")) + " (ghi đè)") : (row[4] || "Theo class");
    var statusText = row[8] == "0" ? "Đã tắt" : (row[7] == "1" ? "Tùy chỉnh" : "Mặc định");
    var statusClass = row[8] == "0" ? "off" : (row[7] == "1" ? "configured" : "active");
    var drops = ParseSpawnDrops(row[16]);
    html += '<tr onclick="PickAdminBoss(' + i + ')"><td><div class="boss-table-name">' + Html(row[2]) + '</div><div class="boss-table-muted">' + Html(row[0] + " / " + row[1]) + '</div></td><td>' + Html(row[3]) + '</td><td>' + Html(mapLabel) + '</td><td>' + BossBadge(skills.length + " skill", "configured") + '</td><td>' + BossBadge(statusText, statusClass) + (drops.length ? BossBadge(drops.length + " drop", "pending") : "") + '</td></tr>';
  }
  document.getElementById("bossTable").innerHTML = html + "</tbody>";
  Msg("bossMessage", adminBossRows.length > 1 ? "Đã đọc " + (adminBossRows.length - 1) + " Boss được đăng ký trong BossManager." : "Không tìm thấy Boss phù hợp.");
  AdjustTableOffsets();
  AdjustBossTableOffsets();
}

function PickAdminBoss(index) {
  var row = adminBossRows[index];
  Set("bossId", row[0]); Set("bossKey", row[1]); Set("bossHasOverride", row[7]);
  Set("bossServerId", row[0]); Set("bossServerKey", row[1]); Set("bossServerName", row[2]);
  Set("bossServerClass", row[3]); Set("bossDefaultMaps", row[4] || "Được class tự quyết định");
  document.getElementById("bossEnabled").checked = row[8] != "0";
  document.getElementById("bossUseRange").checked = row[9] == "1";
  Set("bossTimeStart", row[10] || "08:00"); Set("bossTimeEnd", row[11] || "22:00");
  document.getElementById("bossUseInterval").checked = row[12] == "1";
  Set("bossInterval", row[13] || "10"); bossSelectedMaps = ParseNumberArray(row[14]);
  bossDefaultSkills = ParseBossSkills(row[5]);
  bossConfiguredSkills = ParseBossSkills(row[6]);
  bossDrops = ParseSpawnDrops(row[16]); selectedBossDrop = -1;
  selectedBossSkill = -1;
  NormalizeBossDrops();
  ToggleSpawnSchedule("boss"); RenderBossMapChecklist(); RenderBossDefaultSkills(); RenderBossConfiguredSkills(); ClearBossSkillEditor(); ClearBossDropSelection(); RenderBossItemChecklist(); RenderBossDrops();
  var statusText = row[8] == "0" ? "Đã tắt" : (row[7] == "1" ? "Tùy chỉnh" : "Mặc định");
  SetBossBadge("bossServerStatusBadge", statusText, row[8] == "0" ? "off" : (row[7] == "1" ? "configured" : "active"));
  document.getElementById("bossServerSummary").innerHTML = BossBadge(row[8] == "0" ? "Không spawn" : "Có spawn", row[8] == "0" ? "off" : "active") +
    BossScheduleBadge(row[9], row[10], row[11], row[12], row[13]) +
    BossBadge((bossSelectedMaps.length || (row[4] ? ("" + row[4]).split(",").length : 0)) + " map", "boss") +
    BossBadge(bossDrops.length + " drop", bossDrops.length ? "pending" : "off");
  Msg("bossMessage", "Đang cấu hình " + row[2] + ". Skill bên trên được đọc trực tiếp từ BossesData.java.");
}

function LoadBossSkillCatalog() {
  if (bossSkillRows.length <= 1) bossSkillRows = ParseTsv(RunAdmin("listbossskills", {}));
  var items = [["", "Chọn skill"]];
  bossSkillNames = {};
  for (var i = 1; i < bossSkillRows.length; i++) {
    var id = bossSkillRows[i][0];
    if (bossSkillNames[id]) continue;
    bossSkillNames[id] = bossSkillRows[i][1] || ("Skill " + id);
    items.push([id, id + " - " + bossSkillNames[id]]);
  }
  SetComboItems("bossSkillId", items);
  SetComboItems("customBossSkillId", items);
}

function ParseBossSkills(json) {
  try {
    var data = JSON.parse(json || "[]");
    var result = [];
    for (var i = 0; i < data.length; i++) {
      if (data[i] == null || data[i].id == null) continue;
      result.push({ id: parseInt(data[i].id, 10), level: parseInt(data[i].level, 10), cooldown: parseInt(data[i].cooldown, 10) });
    }
    return result;
  } catch (e) { return []; }
}

function BossSkillName(id) { return bossSkillNames["" + id] || ("Skill " + id); }

function RenderBossDefaultSkills() {
  var html = "<thead><tr><th>ID</th><th>Tên skill</th><th>Cấp</th><th>Hồi chiêu</th></tr></thead><tbody>";
  for (var i = 0; i < bossDefaultSkills.length; i++) {
    var s = bossDefaultSkills[i];
    html += "<tr><td>" + s.id + "</td><td>" + Html(BossSkillName(s.id)) + "</td><td>" + s.level + "</td><td>" + s.cooldown + " ms</td></tr>";
  }
  document.getElementById("bossDefaultSkillsTable").innerHTML = html + "</tbody>";
}

function RenderBossConfiguredSkills() {
  var html = "<thead><tr><th>ID</th><th>Tên skill</th><th>Cấp</th><th>Hồi chiêu</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < bossConfiguredSkills.length; i++) {
    var s = bossConfiguredSkills[i];
    html += '<tr onclick="PickBossSkill(' + i + ')"><td>' + s.id + '</td><td>' + Html(BossSkillName(s.id)) + '</td><td>' + s.level + '</td><td>' + s.cooldown + ' ms</td><td><button class="danger" onclick="RemoveBossSkill(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById("bossConfiguredSkillsTable").innerHTML = html + "</tbody>";
  SetBossBadge("bossSkillCountBadge", "" + bossConfiguredSkills.length, bossConfiguredSkills.length ? "configured" : "error");
}

function PickBossSkill(index) {
  var s = bossConfiguredSkills[index]; selectedBossSkill = index;
  Set("bossSkillId", s.id); Set("bossSkillLevel", s.level); Set("bossSkillCooldown", s.cooldown); ShowBossSkillInfo();
}

function AddBossSkill() {
  if (!V("bossId")) { Msg("bossMessage", "Hãy chọn Boss trong bảng bên trái."); return; }
  if (!/^\d+$/.test(V("bossSkillId")) || !/^[1-7]$/.test(V("bossSkillLevel")) || !/^\d+$/.test(V("bossSkillCooldown"))) {
    Msg("bossMessage", "Hãy chọn skill, cấp từ 1-7 và nhập hồi chiêu bằng mili giây."); return;
  }
  var cooldown = parseInt(V("bossSkillCooldown"), 10);
  if (cooldown < 50 || cooldown > 3600000) { Msg("bossMessage", "Hồi chiêu phải từ 50 ms đến 3.600.000 ms."); return; }
  var skill = { id: parseInt(V("bossSkillId"), 10), level: parseInt(V("bossSkillLevel"), 10), cooldown: cooldown };
  var target = selectedBossSkill;
  if (target < 0) {
    for (var i = 0; i < bossConfiguredSkills.length; i++) if (bossConfiguredSkills[i].id == skill.id) { target = i; break; }
  }
  if (target >= 0) bossConfiguredSkills[target] = skill; else bossConfiguredSkills.push(skill);
  ClearBossSkillEditor(); RenderBossConfiguredSkills();
  Msg("bossMessage", "Đã cập nhật bộ skill; bấm Lưu cấu hình Boss để ghi database.");
}

function RemoveBossSkill(index) {
  bossConfiguredSkills.splice(index, 1); ClearBossSkillEditor(); RenderBossConfiguredSkills();
}

function ClearBossSkillEditor() {
  selectedBossSkill = -1; Set("bossSkillId", ""); Set("bossSkillLevel", "7"); Set("bossSkillCooldown", "1000"); ShowBossSkillInfo();
}

function ShowBossSkillInfo() {
  var name = V("bossSkillId") ? BossSkillName(V("bossSkillId")) : "1000 ms = 1 giây";
  Set("bossSkillHint", name);
}

function ResetBossSkillsToDefault() {
  bossConfiguredSkills = ParseBossSkills(JSON.stringify(bossDefaultSkills));
  ClearBossSkillEditor(); RenderBossConfiguredSkills();
  Msg("bossMessage", "Đã nạp lại skill gốc. Bấm Lưu nếu muốn ghi thành cấu hình hiện tại.");
}

function SaveBossOverride() {
  if (!V("bossId")) { Msg("bossMessage", "Hãy chọn Boss trong bảng bên trái."); return; }
  if (bossConfiguredSkills.length == 0) { Msg("bossMessage", "Boss phải có ít nhất một skill."); return; }
  var text = RunAdmin("savebossoverride", {
    OwnerId: V("bossId"), Name: V("bossKey"), Enabled: CheckedValue("bossEnabled"),
    SkillsJson: JSON.stringify(bossConfiguredSkills), DropsJson: JSON.stringify(bossDrops),
    UseTimeRange: CheckedValue("bossUseRange"), TimeStart: V("bossTimeStart"), TimeEnd: V("bossTimeEnd"),
    UseInterval: CheckedValue("bossUseInterval"), IntervalMinutes: V("bossInterval"), MapIdsJson: JSON.stringify(bossSelectedMaps)
  });
  Msg("bossMessage", StatusText(text));
  if (text.indexOf("OK\t") == 0 || text.indexOf("OK ") == 0) LoadAdminBosses();
}

function DeleteBossOverride() {
  if (!V("bossId")) return;
  if (V("bossHasOverride") != "1") { Msg("bossMessage", "Boss này đang dùng skill mặc định, không có cấu hình để gỡ."); return; }
  if (!window.confirm("Trả " + V("bossServerName") + " về bộ skill mặc định trong mã nguồn?")) return;
  Msg("bossMessage", StatusText(RunAdmin("deletebossoverride", { OwnerId: V("bossId") })));
  LoadAdminBosses();
}

function LoadCustomBossCostumes() {
  if (customBossCostumeRows.length <= 1) customBossCostumeRows = ParseTsv(RunAdmin("listcostumeoutfits", {}));
}

function FindCustomBossCostume(id) {
  for (var i = 1; i < customBossCostumeRows.length; i++) if (customBossCostumeRows[i][0] == id) return customBossCostumeRows[i];
  return null;
}

function CustomBossCostumeLabel(id, row) {
  row = row || FindCustomBossCostume(id);
  if (!row) return "Cải trang " + id;
  return row[1] + " | " + BossGenderName(row[3]) + " | H/B/L " + row[5] + "/" + row[6] + "/" + row[7];
}

function RenderCustomBossCostumeChecklist() {
  if (!document.getElementById("customBossCostumeChecklist")) return;
  LoadCustomBossCostumes();
  var selected = {};
  var order = [];
  if (customBossSelectedCostumeId) {
    selected[customBossSelectedCostumeId] = true;
    order.push(customBossSelectedCostumeId);
  }
  RenderChecklist({
    listId: "customBossCostumeChecklist", idPrefix: "customBossCostumeChoice", rows: customBossCostumeRows,
    rowStart: 1, query: V("customBossCostumeSearch"), selected: selected, selectedOrder: order,
    labelResolver: CustomBossCostumeLabel, searchIndexes: [1, 2, 3, 5, 6, 7],
    toggleFunction: "ToggleCustomBossCostume", limit: 220, emptyText: "Không tìm thấy cải trang phù hợp."
  });
  document.getElementById("customBossCostumeSummary").innerText = customBossSelectedCostumeId
    ? "Đang dùng cải trang " + customBossSelectedCostumeId + " - " + CustomBossCostumeLabel(customBossSelectedCostumeId)
    : "Chọn cải trang để tự điền HEAD/BODY/LEG.";
}

function ToggleCustomBossCostume(id, checked) {
  if (!checked && customBossSelectedCostumeId == id) {
    customBossSelectedCostumeId = "";
    RenderCustomBossCostumeChecklist();
    UpdateCustomBossAppearancePreview();
    return;
  }
  if (checked) ApplyCustomBossCostume(id);
}

function ApplyCustomBossCostume(id) {
  var row = FindCustomBossCostume(id);
  if (!row) return;
  customBossSelectedCostumeId = id;
  Set("customBossHead", row[5]);
  Set("customBossBody", row[6]);
  Set("customBossLeg", row[7]);
  if (row[3] != "3") Set("customBossGender", row[3]);
  if (!Trim(V("customBossName"))) Set("customBossName", row[1]);
  RenderCustomBossCostumeChecklist();
  UpdateCustomBossAppearancePreview();
}

function UpdateCustomBossAppearancePreview() {
  var target = document.getElementById("customBossAppearancePreview");
  if (!target) return;
  var costume = customBossSelectedCostumeId ? FindCustomBossCostume(customBossSelectedCostumeId) : null;
  var iconId = costume ? costume[4] : "";
  var title = costume ? costume[1] : "Nhập trực tiếp HEAD/BODY/LEG";
  target.innerHTML = ItemImageHtml(iconId, "boss-preview-icon") +
    '<div class="boss-preview-info"><div class="boss-preview-title">' + Html(title) + '</div><div class="boss-part-grid">' +
    '<span class="boss-part-token head">HEAD ' + Html(V("customBossHead")) + '</span>' +
    '<span class="boss-part-token body">BODY ' + Html(V("customBossBody")) + '</span>' +
    '<span class="boss-part-token leg">LEG ' + Html(V("customBossLeg")) + '</span>' +
    '<span class="boss-part-token gender">' + Html(BossGenderName(V("customBossGender"))) + '</span>' +
    '</div></div>';
  SetBossBadge("customBossLookBadge", "H " + V("customBossHead") + " / B " + V("customBossBody") + " / L " + V("customBossLeg"), "configured");
}

function RenderCustomBossMapChecklist() {
  if (!document.getElementById("customBossMapChecklist")) return;
  var selected = {};
  var order = [];
  if (customBossSelectedMap !== "") {
    selected["" + customBossSelectedMap] = true;
    order.push(customBossSelectedMap);
  }
  RenderChecklist({
    listId: "customBossMapChecklist", idPrefix: "customBossMapChoice", rows: spawnMaps, rowStart: 1,
    query: V("customBossMapSearch"), selected: selected, selectedOrder: order,
    toggleFunction: "ToggleCustomBossMap", limit: 250, emptyText: "Không tìm thấy map phù hợp."
  });
  var mapLabel = customBossSelectedMap === "" ? "" : ChecklistLabelFromRows(spawnMaps, 1, 0, 1, "" + customBossSelectedMap);
  document.getElementById("customBossMapSummary").innerText = customBossSelectedMap === ""
    ? "Chưa chọn map xuất hiện."
    : "Map xuất hiện: " + customBossSelectedMap + " - " + (mapLabel || "Không rõ");
}

function ToggleCustomBossMap(id, checked) {
  customBossSelectedMap = checked ? id : "";
  Set("customBossMapId", customBossSelectedMap);
  RenderCustomBossMapChecklist();
}

function CustomBossStripIntegerZeros(value) {
  var text = ("" + value).replace(/^0+/, "");
  return text == "" ? "0" : text;
}

function CustomBossCompareUnsignedText(a, b) {
  a = CustomBossStripIntegerZeros(a);
  b = CustomBossStripIntegerZeros(b);
  if (a.length != b.length) return a.length > b.length ? 1 : -1;
  if (a == b) return 0;
  return a > b ? 1 : -1;
}

function CustomBossTextInteger(value, label, min, max) {
  var text = NormalizeInteger(value);
  if (!/^-?\d+$/.test(text)) {
    Msg("bossMessage", label + " phải là số nguyên.");
    return null;
  }
  var number = parseInt(text, 10);
  if (number < min || number > max) {
    Msg("bossMessage", label + " phải từ " + FormatNumber(min) + " đến " + FormatNumber(max) + ".");
    return null;
  }
  return "" + number;
}

function CustomBossTextLong(value, label) {
  var text = NormalizeInteger(value);
  if (!/^\d+$/.test(text) || CustomBossStripIntegerZeros(text) == "0") {
    Msg("bossMessage", label + " phải là số nguyên lớn hơn 0.");
    return null;
  }
  text = CustomBossStripIntegerZeros(text);
  if (CustomBossCompareUnsignedText(text, customBossLongMax) > 0) {
    Msg("bossMessage", label + " tối đa là " + FormatNumber(customBossLongMax) + ".");
    return null;
  }
  return text;
}

function CustomBossIntOrDefault(value, fallback, min, max) {
  var text = NormalizeInteger(value == null ? "" : "" + value);
  if (!/^-?\d+$/.test(text)) return fallback;
  var number = parseInt(text, 10);
  if (number < min) return min;
  if (number > max) return max;
  return number;
}

function CustomBossLongOrDefault(value, fallback) {
  var text = NormalizeInteger(value == null ? "" : "" + value);
  if (!/^\d+$/.test(text) || CustomBossStripIntegerZeros(text) == "0") return fallback;
  text = CustomBossStripIntegerZeros(text);
  return CustomBossCompareUnsignedText(text, customBossLongMax) > 0 ? customBossLongMax : text;
}

function ReadCustomBossLevelFromForm() {
  var name = Trim(V("customBossName")) || "Boss tự tạo";
  var gender = CustomBossTextInteger(V("customBossGender"), "Gender", 0, 3); if (gender == null) return null;
  var head = CustomBossTextInteger(V("customBossHead"), "HEAD", -32768, 32767); if (head == null) return null;
  var body = CustomBossTextInteger(V("customBossBody"), "BODY", -32768, 32767); if (body == null) return null;
  var leg = CustomBossTextInteger(V("customBossLeg"), "LEG", -32768, 32767); if (leg == null) return null;
  var hp = CustomBossTextLong(V("customBossHp"), "HP"); if (hp == null) return null;
  var damage = CustomBossTextInteger(V("customBossDamage"), "Sát thương", 1, 2147483647); if (damage == null) return null;
  var defense = CustomBossTextInteger(V("customBossDefense"), "Giáp", 0, 2147483647); if (defense == null) return null;
  var dodge = CustomBossTextInteger(V("customBossDodge"), "Né", 0, 100); if (dodge == null) return null;
  var crit = CustomBossTextInteger(V("customBossCrit"), "Chí mạng", 0, 100); if (crit == null) return null;
  return {
    name: name,
    gender: parseInt(gender, 10),
    head: parseInt(head, 10),
    body: parseInt(body, 10),
    leg: parseInt(leg, 10),
    hp: hp,
    damage: parseInt(damage, 10),
    defense: parseInt(defense, 10),
    dodge: parseInt(dodge, 10),
    crit: parseInt(crit, 10)
  };
}

function CustomBossLevelFromRow(row) {
  row = row || [];
  return {
    name: Trim(row[1] || "") || "Boss tự tạo",
    gender: CustomBossIntOrDefault(row[13], 0, 0, 3),
    head: CustomBossIntOrDefault(row[14], 0, -32768, 32767),
    body: CustomBossIntOrDefault(row[15], 0, -32768, 32767),
    leg: CustomBossIntOrDefault(row[16], 0, -32768, 32767),
    hp: CustomBossLongOrDefault(row[17], "1000000"),
    damage: CustomBossIntOrDefault(row[18], 10000, 1, 2147483647),
    defense: CustomBossIntOrDefault(row[19], 0, 0, 2147483647),
    dodge: CustomBossIntOrDefault(row[20], 0, 0, 100),
    crit: CustomBossIntOrDefault(row[21], 0, 0, 100)
  };
}

function StoredCustomBossLevel(value, fallback) {
  value = value || {};
  fallback = fallback || CustomBossLevelFromRow([]);
  return {
    name: Trim(value.name == null ? "" : "" + value.name) || fallback.name,
    gender: CustomBossIntOrDefault(value.gender, fallback.gender, 0, 3),
    head: CustomBossIntOrDefault(value.head, fallback.head, -32768, 32767),
    body: CustomBossIntOrDefault(value.body, fallback.body, -32768, 32767),
    leg: CustomBossIntOrDefault(value.leg, fallback.leg, -32768, 32767),
    hp: CustomBossLongOrDefault(value.hp, fallback.hp),
    damage: CustomBossIntOrDefault(value.damage, fallback.damage, 1, 2147483647),
    defense: CustomBossIntOrDefault(value.defense, fallback.defense, 0, 2147483647),
    dodge: CustomBossIntOrDefault(value.dodge, fallback.dodge, 0, 100),
    crit: CustomBossIntOrDefault(value.crit, fallback.crit, 0, 100)
  };
}

function ParseCustomBossLevels(json, row) {
  var fallback = CustomBossLevelFromRow(row);
  var result = [];
  try {
    var data = JSON.parse(json || "[]");
    for (var i = 0; i < data.length; i++) result.push(StoredCustomBossLevel(data[i], fallback));
  } catch (e) {
  }
  if (result.length == 0) result.push(fallback);
  return result;
}

function SetCustomBossLevelToForm(level) {
  level = StoredCustomBossLevel(level, CustomBossLevelFromRow([]));
  customBossSelectedCostumeId = "";
  Set("customBossName", level.name);
  Set("customBossGender", level.gender);
  Set("customBossHead", level.head);
  Set("customBossBody", level.body);
  Set("customBossLeg", level.leg);
  Set("customBossHp", level.hp);
  Set("customBossDamage", level.damage);
  Set("customBossDefense", level.defense);
  Set("customBossDodge", level.dodge);
  Set("customBossCrit", level.crit);
  RenderCustomBossCostumeChecklist();
  UpdateCustomBossAppearancePreview();
}

function DefaultCustomBossLevels() {
  return [StoredCustomBossLevel({
    name: Trim(V("customBossName")) || "Boss tự tạo",
    gender: V("customBossGender"),
    head: V("customBossHead"),
    body: V("customBossBody"),
    leg: V("customBossLeg"),
    hp: V("customBossHp"),
    damage: V("customBossDamage"),
    defense: V("customBossDefense"),
    dodge: V("customBossDodge"),
    crit: V("customBossCrit")
  }, CustomBossLevelFromRow([]))];
}

function RenderCustomBossLevels() {
  var target = document.getElementById("customBossLevelsTable");
  if (!target) return;
  if (customBossLevels.length == 0) customBossLevels = DefaultCustomBossLevels();
  var html = "<thead><tr><th>#</th><th>Boss</th><th>Ngoại hình</th><th>Chỉ số</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < customBossLevels.length; i++) {
    var level = StoredCustomBossLevel(customBossLevels[i], CustomBossLevelFromRow([]));
    customBossLevels[i] = level;
    var active = i == selectedCustomBossLevel ? ' class="boss-level-selected"' : "";
    html += '<tr' + active + ' onclick="PickCustomBossLevel(' + i + ')"><td>' + (i + 1) + '</td><td><div class="boss-table-name">' + Html(level.name) + '</div><div class="boss-table-muted">' + Html(BossGenderName(level.gender)) + '</div></td><td>' +
      BossBadge("H " + level.head, "configured") + BossBadge("B " + level.body, "pending") + BossBadge("L " + level.leg, "boss") +
      '</td><td>' + BossBadge("HP " + FormatNumber(level.hp), "active") + BossBadge("Dame " + FormatNumber(level.damage), "configured") + BossBadge("Giáp " + FormatNumber(level.defense), "boss") + BossBadge("Né " + level.dodge + "%", level.dodge ? "warning" : "off") + BossBadge("Crit " + level.crit + "%", level.crit ? "pending" : "off") +
      '</td><td><button type="button" onclick="PickCustomBossLevel(' + i + '); event.cancelBubble=true">Chọn</button></td></tr>';
  }
  target.innerHTML = html + "</tbody>";
  SetBossBadge("customBossLevelCountBadge", customBossLevels.length + " phase", customBossLevels.length > 1 ? "configured" : "warning");
}

function PickCustomBossLevel(index) {
  if (index < 0 || index >= customBossLevels.length) return;
  selectedCustomBossLevel = index;
  SetCustomBossLevelToForm(customBossLevels[index]);
  RenderCustomBossLevels();
}

function AddCustomBossLevel() {
  var level = ReadCustomBossLevelFromForm();
  if (!level) return;
  customBossLevels.push(level);
  selectedCustomBossLevel = customBossLevels.length - 1;
  RenderCustomBossLevels();
  Msg("bossMessage", "Đã thêm phase " + customBossLevels.length + ". Bấm Lưu Boss mới để ghi database.");
}

function UpdateCustomBossLevel() {
  var level = ReadCustomBossLevelFromForm();
  if (!level) return;
  if (customBossLevels.length == 0) customBossLevels = [level];
  var index = selectedCustomBossLevel >= 0 && selectedCustomBossLevel < customBossLevels.length ? selectedCustomBossLevel : 0;
  customBossLevels[index] = level;
  selectedCustomBossLevel = index;
  RenderCustomBossLevels();
  Msg("bossMessage", "Đã cập nhật phase " + (index + 1) + ". Bấm Lưu Boss mới để ghi database.");
}

function ClearCustomBossLevelSelection() {
  selectedCustomBossLevel = -1;
  RenderCustomBossLevels();
}

function RemoveCustomBossLevel() {
  if (selectedCustomBossLevel < 0 || selectedCustomBossLevel >= customBossLevels.length) {
    Msg("bossMessage", "Hãy chọn phase cần xóa.");
    return;
  }
  if (customBossLevels.length <= 1) {
    Msg("bossMessage", "Boss tự tạo phải có ít nhất một phase.");
    return;
  }
  customBossLevels.splice(selectedCustomBossLevel, 1);
  selectedCustomBossLevel = -1;
  RenderCustomBossLevels();
  Msg("bossMessage", "Đã xóa phase. Bấm Lưu Boss mới để ghi database.");
}

function SyncCustomBossSelectedLevelFromForm() {
  var level = ReadCustomBossLevelFromForm();
  if (!level) return null;
  if (customBossLevels.length == 0) {
    customBossLevels.push(level);
    selectedCustomBossLevel = 0;
  } else {
    var index = selectedCustomBossLevel >= 0 && selectedCustomBossLevel < customBossLevels.length ? selectedCustomBossLevel : 0;
    customBossLevels[index] = level;
  }
  RenderCustomBossLevels();
  return customBossLevels;
}

function LoadCustomBosses() {
  customBossRows = ParseTsv(RunAdmin("listadminbosses", { Search: V("customBossSearch") }));
  var html = "<thead><tr><th>Boss</th><th>Map</th><th>Ngoại hình</th><th>Chỉ số</th><th>Trạng thái</th></tr></thead><tbody>";
  for (var i = 1; i < customBossRows.length; i++) {
    var row = customBossRows[i];
    var skillCount = ParseBossSkills(row[22]).length;
    var levels = ParseCustomBossLevels(row[24], row);
    var drops = ParseSpawnDrops(row[25]);
    var mapLabel = row[8] + (row[9] ? " - " + row[9] : "");
    var dodge = CustomBossIntOrDefault(row[20], 0, 0, 100);
    var crit = CustomBossIntOrDefault(row[21], 0, 0, 100);
    html += '<tr onclick="PickCustomBoss(' + i + ')"><td><div class="boss-table-name">' + Html(row[1]) + '</div><div class="boss-table-muted">ID ' + Html(row[0]) + '</div></td><td>' + Html(mapLabel) + '</td><td>' + BossBadge("H " + row[14], "configured") + BossBadge("B " + row[15], "pending") + BossBadge("L " + row[16], "boss") + BossBadge(levels.length + " phase", levels.length > 1 ? "configured" : "warning") + '</td><td>' + BossBadge("HP " + FormatNumber(row[17]), "active") + BossBadge("Dame " + FormatNumber(row[18]), "configured") + BossBadge("Giáp " + FormatNumber(row[19] || 0), "boss") + BossBadge("Né " + dodge + "%", dodge ? "warning" : "off") + BossBadge("Crit " + crit + "%", crit ? "pending" : "off") + '</td><td>' + BossBadge(row[2] == "1" ? "Bật" : "Tắt", row[2] == "1" ? "active" : "off") + BossScheduleBadge(row[3], row[4], row[5], row[6], row[7]) + BossBadge(skillCount ? skillCount + " skill" : "Skill mặc định", skillCount ? "configured" : "warning") + (drops.length ? BossBadge(drops.length + " drop", "pending") : "") + '</td></tr>';
  }
  document.getElementById("customBossTable").innerHTML = html + "</tbody>";
  Msg("bossMessage", customBossRows.length > 1 ? "Đã đọc " + (customBossRows.length - 1) + " Boss tự tạo." : "Chưa có Boss tự tạo phù hợp.");
  AdjustTableOffsets();
  AdjustBossTableOffsets();
}

function PickCustomBossById(id) {
  for (var i = 1; i < customBossRows.length; i++) {
    if (customBossRows[i][0] == id) {
      PickCustomBoss(i);
      return;
    }
  }
}

function PickCustomBoss(index) {
  var row = customBossRows[index];
  Set("customBossId", row[0]); Set("customBossConfigId", row[0]); Set("customBossName", row[1]);
  document.getElementById("customBossEnabled").checked = row[2] == "1";
  document.getElementById("customBossUseRange").checked = row[3] == "1";
  Set("customBossTimeStart", row[4] || "08:00"); Set("customBossTimeEnd", row[5] || "22:00");
  document.getElementById("customBossUseInterval").checked = row[6] == "1";
  Set("customBossInterval", row[7] || "10");
  customBossSelectedMap = row[8] || "";
  Set("customBossMapId", customBossSelectedMap);
  Set("customBossZone", row[10] || "-1"); Set("customBossSpawnX", row[11] || "-1"); Set("customBossSpawnY", row[12] || "-1");
  Set("customBossGender", row[13] || "0"); Set("customBossHead", row[14] || "0"); Set("customBossBody", row[15] || "0"); Set("customBossLeg", row[16] || "0");
  Set("customBossHp", row[17] || "1000000"); Set("customBossDamage", row[18] || "10000");
  Set("customBossDefense", row[19] || "0"); Set("customBossDodge", row[20] || "0"); Set("customBossCrit", row[21] || "0");
  customBossSkills = ParseBossSkills(row[22]);
  document.getElementById("customBossAnnounce").checked = row[23] == "1";
  customBossLevels = ParseCustomBossLevels(row[24], row); selectedCustomBossLevel = -1;
  customBossDrops = ParseSpawnDrops(row[25]); selectedCustomBossDrop = -1; selectedCustomBossSkill = -1; customBossSelectedCostumeId = "";
  NormalizeCustomBossDrops();
  ToggleSpawnSchedule("customBoss"); RenderCustomBossMapChecklist(); RenderCustomBossLevels(); RenderCustomBossSkills(); ClearCustomBossSkillEditor();
  ClearCustomBossDropSelection(); RenderCustomBossItemChecklist(); RenderCustomBossDrops(); RenderCustomBossCostumeChecklist(); UpdateCustomBossAppearancePreview();
  SetBossBadge("customBossStatusBadge", row[2] == "1" ? "Đang bật" : "Đang tắt", row[2] == "1" ? "active" : "off");
  Msg("bossMessage", "Đang chỉnh Boss tự tạo " + row[1] + " với " + customBossLevels.length + " phase.");
}

function NewCustomBoss() {
  Set("customBossId", ""); Set("customBossConfigId", "Tạo mới"); Set("customBossName", "");
  document.getElementById("customBossEnabled").checked = true;
  document.getElementById("customBossAnnounce").checked = true;
  document.getElementById("customBossUseRange").checked = false;
  Set("customBossTimeStart", "08:00"); Set("customBossTimeEnd", "22:00");
  document.getElementById("customBossUseInterval").checked = false;
  Set("customBossInterval", "10");
  customBossSelectedMap = ""; Set("customBossMapId", "");
  Set("customBossZone", "-1"); Set("customBossSpawnX", "-1"); Set("customBossSpawnY", "-1");
  Set("customBossGender", "0"); Set("customBossHead", "0"); Set("customBossBody", "0"); Set("customBossLeg", "0");
  Set("customBossHp", "1000000"); Set("customBossDamage", "10000"); Set("customBossDefense", "0"); Set("customBossDodge", "0"); Set("customBossCrit", "0");
  customBossSkills = []; customBossDrops = []; customBossLevels = DefaultCustomBossLevels(); selectedCustomBossDrop = -1; selectedCustomBossSkill = -1; selectedCustomBossLevel = -1; customBossSelectedCostumeId = "";
  ToggleSpawnSchedule("customBoss"); RenderCustomBossMapChecklist(); RenderCustomBossLevels(); RenderCustomBossSkills(); ClearCustomBossSkillEditor();
  ClearCustomBossDropSelection(); RenderCustomBossItemChecklist(); RenderCustomBossDrops(); RenderCustomBossCostumeChecklist(); UpdateCustomBossAppearancePreview();
  SetBossBadge("customBossStatusBadge", "Boss mới", "pending");
  Msg("bossMessage", "Đang tạo Boss mới. Có thể chọn cải trang để tự điền ngoại hình.");
}

function RenderCustomBossSkills() {
  var html = "<thead><tr><th>ID</th><th>Tên skill</th><th>Cấp</th><th>Hồi chiêu</th><th></th></tr></thead><tbody>";
  if (customBossSkills.length == 0) {
    html += '<tr><td colspan="5"><span class="boss-empty-note">Không cấu hình skill riêng, runtime dùng skill mặc định theo gender.</span></td></tr>';
  }
  for (var i = 0; i < customBossSkills.length; i++) {
    var s = customBossSkills[i];
    html += '<tr onclick="PickCustomBossSkill(' + i + ')"><td>' + s.id + '</td><td>' + Html(BossSkillName(s.id)) + '</td><td>' + s.level + '</td><td>' + s.cooldown + ' ms</td><td><button class="danger" onclick="RemoveCustomBossSkill(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById("customBossSkillsTable").innerHTML = html + "</tbody>";
  SetBossBadge("customBossSkillCountBadge", customBossSkills.length ? customBossSkills.length + " skill" : "Mặc định", customBossSkills.length ? "configured" : "warning");
}

function PickCustomBossSkill(index) {
  var s = customBossSkills[index]; selectedCustomBossSkill = index;
  Set("customBossSkillId", s.id); Set("customBossSkillLevel", s.level); Set("customBossSkillCooldown", s.cooldown); ShowCustomBossSkillInfo();
}

function AddCustomBossSkill() {
  if (!/^\d+$/.test(V("customBossSkillId")) || !/^[1-7]$/.test(V("customBossSkillLevel")) || !/^\d+$/.test(V("customBossSkillCooldown"))) {
    Msg("bossMessage", "Hãy chọn skill, cấp từ 1-7 và nhập hồi chiêu bằng mili giây."); return;
  }
  var cooldown = parseInt(V("customBossSkillCooldown"), 10);
  if (cooldown < 50 || cooldown > 3600000) { Msg("bossMessage", "Hồi chiêu phải từ 50 ms đến 3.600.000 ms."); return; }
  var skill = { id: parseInt(V("customBossSkillId"), 10), level: parseInt(V("customBossSkillLevel"), 10), cooldown: cooldown };
  var target = selectedCustomBossSkill;
  if (target < 0) {
    for (var i = 0; i < customBossSkills.length; i++) if (customBossSkills[i].id == skill.id) { target = i; break; }
  }
  if (target >= 0) customBossSkills[target] = skill; else customBossSkills.push(skill);
  ClearCustomBossSkillEditor(); RenderCustomBossSkills();
  Msg("bossMessage", "Đã cập nhật bộ skill cho Boss tự tạo.");
}

function RemoveCustomBossSkill(index) {
  customBossSkills.splice(index, 1); ClearCustomBossSkillEditor(); RenderCustomBossSkills();
}

function ClearCustomBossSkillEditor() {
  selectedCustomBossSkill = -1; Set("customBossSkillId", ""); Set("customBossSkillLevel", "7"); Set("customBossSkillCooldown", "1000"); ShowCustomBossSkillInfo();
}

function ShowCustomBossSkillInfo() {
  var name = V("customBossSkillId") ? BossSkillName(V("customBossSkillId")) : "Không thêm skill thì dùng mặc định theo gender";
  Set("customBossSkillHint", name);
}

function ResetCustomBossSkillsToDefault() {
  customBossSkills = [];
  ClearCustomBossSkillEditor(); RenderCustomBossSkills();
  Msg("bossMessage", "Boss tự tạo sẽ dùng skill mặc định theo gender nếu không thêm skill riêng.");
}

function FindCustomBossDropIndex(itemId) {
  for (var i = 0; i < customBossDrops.length; i++) if (parseInt(customBossDrops[i].itemId, 10) == parseInt(itemId, 10)) return i;
  return -1;
}

function RenderCustomBossItemChecklist() {
  if (!document.getElementById("customBossItemChecklist")) return;
  var selected = {}; var order = [];
  for (var i = 0; i < customBossDrops.length; i++) {
    selected["" + customBossDrops[i].itemId] = true; order.push(customBossDrops[i].itemId);
  }
  RenderItemPicker({
    listId: "customBossItemChecklist", idPrefix: "customBossDropChoice", rows: spawnItems, rowStart: 1,
    query: V("customBossItemSearch"), selected: selected, selectedOrder: order,
    labelResolver: ItemCatalogLabel, toggleFunction: "ToggleCustomBossDropItem", limit: 250,
    emptyText: "Không tìm thấy vật phẩm phù hợp."
  });
  document.getElementById("customBossItemSummary").innerText = customBossDrops.length
    ? "Đã chọn " + customBossDrops.length + " vật phẩm rơi thêm. Chọn một dòng trong bảng dưới để chỉnh."
    : "Chưa chọn vật phẩm rơi thêm";
  SetBossBadge("customBossDropCountBadge", "" + customBossDrops.length, customBossDrops.length ? "configured" : "pending");
}

function ToggleCustomBossDropItem(id, checked) {
  var index = FindCustomBossDropIndex(id);
  if (checked && index < 0) {
    customBossDrops.push({ itemId: parseInt(id, 10), quantityMin: 1, quantityMax: 1, dropRate: 100, useDefaultOptions: false, options: [] });
    index = customBossDrops.length - 1;
  } else if (!checked && index >= 0) {
    customBossDrops.splice(index, 1);
    if (selectedCustomBossDrop == index) selectedCustomBossDrop = -1;
    else if (selectedCustomBossDrop > index) selectedCustomBossDrop--;
  }
  RenderCustomBossItemChecklist(); RenderCustomBossDrops();
  if (checked && index >= 0) PickCustomBossDrop(index); else if (selectedCustomBossDrop < 0) ClearCustomBossDropSelection();
}

function RenderCustomBossDrops() {
  var html = "<thead><tr><th>Item</th><th>Số lượng</th><th>Tỉ lệ</th><th>Option</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < customBossDrops.length; i++) {
    var d = customBossDrops[i];
    html += '<tr onclick="PickCustomBossDrop(' + i + ')"><td>' + Html(d.itemId + " - " + (spawnItemNames[d.itemId] || "Không rõ")) + '</td><td>' + Html(d.quantityMin + "-" + d.quantityMax) + '</td><td>' + Html(d.dropRate + "%") + '</td><td>' + Html(BossDropOptionText(d)) + '</td><td><button class="danger" onclick="RemoveCustomBossDrop(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById("customBossDropsTable").innerHTML = html + "</tbody>";
  SetBossBadge("customBossDropCountBadge", "" + customBossDrops.length, customBossDrops.length ? "configured" : "pending");
}

function PickCustomBossDrop(index) {
  if (index < 0 || index >= customBossDrops.length) return;
  selectedCustomBossDrop = index;
  var d = customBossDrops[index];
  Set("customBossDropItem", d.itemId); Set("customBossDropItemName", spawnItemNames[d.itemId] || "Không rõ");
  Set("customBossDropMin", d.quantityMin); Set("customBossDropMax", d.quantityMax); Set("customBossDropRate", d.dropRate);
  if (document.getElementById("customBossDropUseDefault")) document.getElementById("customBossDropUseDefault").checked = !!d.useDefaultOptions;
  RenderCustomBossOptionChecklist();
}

function RemoveCustomBossDrop(index) {
  if (index < 0 || index >= customBossDrops.length) return;
  customBossDrops.splice(index, 1); selectedCustomBossDrop = -1;
  ClearCustomBossDropSelection(); RenderCustomBossItemChecklist(); RenderCustomBossDrops();
}

function ClearCustomBossDropSelection() {
  selectedCustomBossDrop = -1;
  Set("customBossDropItem", ""); Set("customBossDropItemName", ""); Set("customBossDropMin", "1"); Set("customBossDropMax", "1"); Set("customBossDropRate", "100");
  if (document.getElementById("customBossDropUseDefault")) document.getElementById("customBossDropUseDefault").checked = false;
  RenderCustomBossOptionChecklist();
}

function UpdateCustomBossDrop() {
  if (selectedCustomBossDrop < 0 || selectedCustomBossDrop >= customBossDrops.length) { Msg("bossMessage", "Hãy chọn một vật phẩm trong checklist hoặc bảng drop."); return; }
  if (!/^\d+$/.test(V("customBossDropMin")) || !/^\d+$/.test(V("customBossDropMax")) || !/^\d+(\.\d+)?$/.test(V("customBossDropRate"))) {
    Msg("bossMessage", "Số lượng và tỉ lệ phải là số hợp lệ."); return;
  }
  var min = parseInt(V("customBossDropMin"), 10), max = parseInt(V("customBossDropMax"), 10), rate = parseFloat(V("customBossDropRate"));
  if (min < 1 || min > max || rate < 0 || rate > 100) { Msg("bossMessage", "Số lượng min không lớn hơn max và tỉ lệ phải từ 0-100."); return; }
  var options = customBossDrops[selectedCustomBossDrop].options || [];
  for (var i = 0; i < options.length; i++) {
    if (!/^-?\d+$/.test("" + options[i].paramMin) || !/^-?\d+$/.test("" + options[i].paramMax) || parseInt(options[i].paramMin, 10) > parseInt(options[i].paramMax, 10)) {
      Msg("bossMessage", "Option " + options[i].id + " có khoảng chỉ số không hợp lệ."); return;
    }
  }
  customBossDrops[selectedCustomBossDrop].quantityMin = min;
  customBossDrops[selectedCustomBossDrop].quantityMax = max;
  customBossDrops[selectedCustomBossDrop].dropRate = rate;
  customBossDrops[selectedCustomBossDrop].useDefaultOptions = !!document.getElementById("customBossDropUseDefault") && document.getElementById("customBossDropUseDefault").checked;
  RenderCustomBossDrops(); Msg("bossMessage", "Đã cập nhật drop; bấm Lưu Boss mới để ghi database.");
}

function RenderCustomBossOptionChecklist() {
  var target = document.getElementById("customBossOptionChecklist");
  if (!target) return;
  if (selectedCustomBossDrop < 0 || selectedCustomBossDrop >= customBossDrops.length) {
    target.innerHTML = "Chọn một vật phẩm trong checklist hoặc bảng drop để cấu hình option.";
    document.getElementById("customBossOptionSummary").innerText = "Chưa chọn vật phẩm";
    return;
  }
  var options = customBossDrops[selectedCustomBossDrop].options || [];
  var html = ""; var rendered = {}; var query = V("customBossOptionSearch").toLowerCase();
  for (var i = 0; i < options.length; i++) {
    html += CustomBossOptionItemHtml("" + options[i].id, OptionName(options[i].id), true, options[i]); rendered["" + options[i].id] = true;
  }
  for (var r = 1; r < optionRows.length; r++) {
    var id = "" + optionRows[r][0], name = optionRows[r][1] || "Chưa có tên";
    if (!id || rendered[id] || (query && (id + " " + name).toLowerCase().indexOf(query) < 0)) continue;
    html += CustomBossOptionItemHtml(id, name, false, null); rendered[id] = true;
  }
  target.innerHTML = html || "Không tìm thấy option phù hợp.";
  document.getElementById("customBossOptionSummary").innerText = "Đã chọn " + options.length + " option cho " + V("customBossDropItemName") + ". Min = Max nếu không muốn random.";
}

function CustomBossOptionItemHtml(id, name, checked, option) {
  var safeId = id.replace(/[^a-zA-Z0-9_-]/g, "_");
  var html = '<div class="option-picker-item boss-option-item' + (checked ? ' checklist-item-selected' : '') + '"><input type="checkbox" id="customBossDropOption_' + safeId + '"' + (checked ? ' checked="checked"' : '') + ' onclick="ToggleCustomBossDropOption(\'' + EscapeJs(id) + '\', this.checked)"><label for="customBossDropOption_' + safeId + '">' + Html(id + " - " + name) + '</label>';
  if (checked) {
    html += '<span class="boss-option-range">Min <input value="' + HtmlAttr(option.paramMin) + '" onchange="UpdateCustomBossDropOptionBound(\'' + EscapeJs(id) + '\', \'paramMin\', this.value)"> Max <input value="' + HtmlAttr(option.paramMax) + '" onchange="UpdateCustomBossDropOptionBound(\'' + EscapeJs(id) + '\', \'paramMax\', this.value)"></span>';
  }
  return html + "</div>";
}

function ToggleCustomBossDropOption(id, checked) {
  if (selectedCustomBossDrop < 0 || selectedCustomBossDrop >= customBossDrops.length) return;
  var options = customBossDrops[selectedCustomBossDrop].options || [], next = [], found = false;
  for (var i = 0; i < options.length; i++) {
    if (("" + options[i].id) == id) { found = true; if (!checked) continue; }
    next.push(options[i]);
  }
  if (checked && !found) next.push({ id: parseInt(id, 10), paramMin: 0, paramMax: 0 });
  customBossDrops[selectedCustomBossDrop].options = next;
  RenderCustomBossOptionChecklist(); RenderCustomBossDrops();
}

function UpdateCustomBossDropOptionBound(id, field, value) {
  if (selectedCustomBossDrop < 0 || !/^-?\d+$/.test(value)) { Msg("bossMessage", "Chỉ số option phải là số nguyên."); return; }
  var options = customBossDrops[selectedCustomBossDrop].options || [];
  for (var i = 0; i < options.length; i++) if (("" + options[i].id) == id) options[i][field] = parseInt(value, 10);
  RenderCustomBossDrops();
}

function CustomBossIntegerValue(id, label, positive) {
  var value = NormalizeInteger(V(id));
  if (!/^-?\d+$/.test(value) || (positive && parseInt(value, 10) < 1)) {
    Msg("bossMessage", label + " phải là số nguyên" + (positive ? " lớn hơn 0." : "."));
    return null;
  }
  Set(id, value);
  return value;
}

function SaveCustomBoss() {
  var mapId = customBossSelectedMap || V("customBossMapId");
  if (!/^\d+$/.test("" + mapId)) { Msg("bossMessage", "Hãy chọn map xuất hiện cho Boss."); return; }
  var levels = SyncCustomBossSelectedLevelFromForm();
  if (!levels) return;
  var primary = StoredCustomBossLevel(levels[0], CustomBossLevelFromRow([]));
  var name = Trim(primary.name);
  if (!name) { Msg("bossMessage", "Tên Boss không được để trống."); return; }
  var text = RunAdmin("saveadminboss", {
    OwnerId: V("customBossId"), Name: name, Enabled: CheckedValue("customBossEnabled"),
    UseTimeRange: CheckedValue("customBossUseRange"), TimeStart: V("customBossTimeStart"), TimeEnd: V("customBossTimeEnd"),
    UseInterval: CheckedValue("customBossUseInterval"), IntervalMinutes: V("customBossInterval"),
    MapId: mapId, ZoneId: V("customBossZone"), SpawnX: V("customBossSpawnX"), SpawnY: V("customBossSpawnY"),
    Gender: primary.gender, Head: primary.head, Body: primary.body, Leg: primary.leg, Hp: primary.hp, Damage: primary.damage,
    Defense: primary.defense, Dodge: primary.dodge, Crit: primary.crit, LevelsJson: JSON.stringify(levels),
    Announce: CheckedValue("customBossAnnounce"), SkillsJson: JSON.stringify(customBossSkills), DropsJson: JSON.stringify(customBossDrops)
  });
  Msg("bossMessage", StatusText(text));
  if (text.indexOf("OK\t") == 0 || text.indexOf("OK ") == 0) {
    var match = /Boss ID\s+(\d+)/.exec(text);
    LoadCustomBosses();
    if (match) PickCustomBossById(match[1]);
  }
}

function DeleteCustomBoss() {
  if (!V("customBossId")) { Msg("bossMessage", "Boss mới chưa có ID để xóa."); return; }
  if (!window.confirm("Xóa Boss tự tạo " + V("customBossName") + " khỏi cấu hình runtime?")) return;
  var text = RunAdmin("deleteadminboss", { OwnerId: V("customBossId") });
  Msg("bossMessage", StatusText(text));
  if (text.indexOf("OK\t") == 0 || text.indexOf("OK ") == 0) {
    LoadCustomBosses();
    NewCustomBoss();
  }
}

RegisterTab({
  id: "boss", view: "boss.html", panelId: "panelBoss", navId: "navBoss",
  title: "Quản lý Boss", subtitle: "Boss server và Boss tự tạo: ngoại hình, skill, map, lịch xuất hiện và vật phẩm rơi",
  onOpen: function () {
    LoadOptions(false);
    InitBossCombos();
    LoadSpawnLookups();
    LoadCustomBossCostumes();
    NewCustomBoss();
    LoadAdminBosses();
    SetBossMode(bossMode);
  },
  onRefresh: function () {
    LoadBossSkillCatalog();
    LoadSpawnLookups();
    if (bossMode == "custom") LoadCustomBosses(); else LoadAdminBosses();
  }
});
