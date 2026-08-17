var adminBossRows = [];
var bossDrops = [];
var selectedBossDrop = -1;
var bossSkillRows = [];
var bossSkillNames = {};
var bossDefaultSkills = [];
var bossConfiguredSkills = [];
var selectedBossSkill = -1;
var bossSelectedMaps = [];

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
    : "Chưa chọn map — dùng map mặc định trong BossesData";
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

function NormalizeBossDrops() {
  for (var i = 0; i < bossDrops.length; i++) {
    var options = bossDrops[i].options || [];
    bossDrops[i].useDefaultOptions = bossDrops[i].useDefaultOptions === true || bossDrops[i].useDefaultOptions === 1 || bossDrops[i].useDefaultOptions === "1" || bossDrops[i].useDefaultOptions === "true";
    for (var o = 0; o < options.length; o++) {
      var fixed = options[o].param == null ? 0 : parseInt(options[o].param, 10);
      if (options[o].paramMin == null) options[o].paramMin = fixed;
      if (options[o].paramMax == null) options[o].paramMax = options[o].paramMin;
      delete options[o].param;
    }
  }
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
  var html = "<thead><tr><th>Item</th><th>Số lượng</th><th>Tỉ lệ</th><th>Option random</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < bossDrops.length; i++) {
    var d = bossDrops[i];
    html += '<tr onclick="PickBossDrop(' + i + ')"><td>' + Html(d.itemId + " - " + (spawnItemNames[d.itemId] || "Không rõ")) + '</td><td>' + Html(d.quantityMin + "–" + d.quantityMax) + '</td><td>' + Html(d.dropRate + "%") + '</td><td>' + Html(SpawnOptionsText(d.options)) + '</td><td><button class="danger" onclick="RemoveBossDrop(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById("bossDropsTable").innerHTML = html + "</tbody>";
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
  if (min < 1 || min > max || rate < 0 || rate > 100) { Msg("bossMessage", "Số lượng min không lớn hơn max và tỉ lệ phải từ 0–100."); return; }
  var options = bossDrops[selectedBossDrop].options || [];
  for (var i = 0; i < options.length; i++) {
    if (!/^-?\d+$/.test("" + options[i].paramMin) || !/^-?\d+$/.test("" + options[i].paramMax) || parseInt(options[i].paramMin, 10) > parseInt(options[i].paramMax, 10)) {
      Msg("bossMessage", "Option " + options[i].id + " có khoảng chỉ số không hợp lệ."); return;
    }
  }
  bossDrops[selectedBossDrop].quantityMin = min; bossDrops[selectedBossDrop].quantityMax = max; bossDrops[selectedBossDrop].dropRate = rate;
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
  var html = "<thead><tr><th>Boss ID</th><th>Tên Boss</th><th>Class</th><th>Map mặc định</th><th>Skill</th><th>Cấu hình</th></tr></thead><tbody>";
  for (var i = 1; i < adminBossRows.length; i++) {
    var row = adminBossRows[i];
    var skills = ParseBossSkills(row[6]);
    var status = row[8] == "0" ? "Đã tắt" : (row[7] == "1" ? "Tùy chỉnh" : "Mặc định");
    var configuredMaps = ParseNumberArray(row[14]);
    var mapLabel = configuredMaps.length ? ((row[15] || configuredMaps.join(", ")) + " (ghi đè)") : (row[4] || "Theo class");
    html += '<tr onclick="PickAdminBoss(' + i + ')"><td>' + Html(row[0]) + '</td><td>' + Html(row[2] + " (" + row[1] + ")") + '</td><td>' + Html(row[3]) + '</td><td>' + Html(mapLabel) + '</td><td>' + skills.length + '</td><td>' + status + '</td></tr>';
  }
  document.getElementById("bossTable").innerHTML = html + "</tbody>";
  Msg("bossMessage", adminBossRows.length > 1 ? "Đã đọc " + (adminBossRows.length - 1) + " Boss được đăng ký trong BossManager." : "Không tìm thấy Boss phù hợp.");
  AdjustTableOffsets();
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
  Msg("bossMessage", "Đang cấu hình " + row[2] + ". Skill bên trên được đọc trực tiếp từ BossesData.java.");
}

function LoadBossSkillCatalog() {
  if (bossSkillRows.length <= 1) bossSkillRows = ParseTsv(RunAdmin("listbossskills", {}));
  var items = [["", "Ch\u1ecdn skill"]];
  bossSkillNames = {};
  for (var i = 1; i < bossSkillRows.length; i++) {
    var id = bossSkillRows[i][0];
    if (bossSkillNames[id]) continue;
    bossSkillNames[id] = bossSkillRows[i][1] || ("Skill " + id);
    items.push([id, id + " - " + bossSkillNames[id]]);
  }
  SetComboItems("bossSkillId", items);
}

function ParseBossSkills(json) {
  try {
    var data = JSON.parse(json || "[]");
    var result = [];
    for (var i = 0; i < data.length; i++) result.push({ id: parseInt(data[i].id, 10), level: parseInt(data[i].level, 10), cooldown: parseInt(data[i].cooldown, 10) });
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
}

function PickBossSkill(index) {
  var s = bossConfiguredSkills[index]; selectedBossSkill = index;
  Set("bossSkillId", s.id); Set("bossSkillLevel", s.level); Set("bossSkillCooldown", s.cooldown); ShowBossSkillInfo();
}

function AddBossSkill() {
  if (!V("bossId")) { Msg("bossMessage", "Hãy chọn Boss trong bảng bên trái."); return; }
  if (!/^\d+$/.test(V("bossSkillId")) || !/^[1-7]$/.test(V("bossSkillLevel")) || !/^\d+$/.test(V("bossSkillCooldown"))) {
    Msg("bossMessage", "Hãy chọn skill, cấp từ 1–7 và nhập hồi chiêu bằng mili giây."); return;
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

RegisterTab({
  id: "boss", view: "boss.html", panelId: "panelBoss", navId: "navBoss",
  title: "Quản lý Boss", subtitle: "Boss có trong server: skill, map, lịch xuất hiện và vật phẩm rơi",
  onOpen: function () { LoadOptions(false); LoadBossSkillCatalog(); LoadSpawnLookups(); LoadAdminBosses(); },
  onRefresh: function () { LoadAdminBosses(); }
});
