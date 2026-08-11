var petConfigRows = [];
var filteredPetConfigRows = [];
var selectedPetConfigKey = "";
var selectedPetConfigCategory = "";
var selectedPetConfigRow = null;

function LoadPetConfig() {
  petConfigRows = ParseTsv(RunAdmin("listpetconfig", {}));
  RenderPetConfigTabs();
  FilterPetConfigRows();
  var restored = false;
  if (selectedPetConfigKey) {
    for (var i = 1; i < petConfigRows.length; i++) {
      if (petConfigRows[i][0] == selectedPetConfigKey) { PickPetConfigByKey(selectedPetConfigKey); restored = true; break; }
    }
  }
  if (!restored && filteredPetConfigRows.length > 1) PickFilteredPetConfig(1);
  Msg("petConfigMessage", petConfigRows.length > 1 ? "Đã tải " + (petConfigRows.length - 1) + " cấu hình Đệ tử." : "Không tải được cấu hình Đệ tử.");
  AdjustTableOffsets();
}

function PetSkillCatalog() {
  return [
    [0, "Đấm Dragon", "Tấn công"], [1, "Kamejoko", "Tấn công"], [2, "Đấm Demon", "Tấn công"],
    [3, "Masenko", "Tấn công"], [4, "Galick", "Tấn công"], [5, "Antomic", "Tấn công"],
    [6, "Thái Dương Hạ San", "Hỗ trợ"], [7, "Trị Thương", "Hỗ trợ"], [8, "Tái Tạo Năng Lượng", "Hỗ trợ"],
    [9, "Kaioken", "Tấn công"], [10, "Quả Cầu Kênh Khí", "Tấn công"], [11, "Makankosappo", "Tấn công"],
    [12, "Đẻ Trứng", "Hỗ trợ"], [13, "Biến Khỉ", "Hỗ trợ"], [14, "Tự Sát", "Hỗ trợ"],
    [17, "Liên Hoàn", "Tấn công"], [18, "Socola", "Khống chế"], [19, "Khiên Năng Lượng", "Hỗ trợ"],
    [20, "Dịch Chuyển Tức Thời", "Khống chế"], [21, "Huýt Sáo", "Hỗ trợ"], [22, "Thôi Miên", "Khống chế"],
    [23, "Trói", "Khống chế"], [24, "Super Kame", "Tuyệt kỹ"], [25, "Liên Hoàn Chưởng", "Tuyệt kỹ"],
    [26, "Ma Phong Ba", "Tuyệt kỹ"]
  ];
}

function PetSkillOptions(key) {
  var catalog = PetSkillCatalog();
  var result = [];
  for (var i = 0; i < catalog.length; i++) {
    var id = catalog[i][0];
    var allowed = key == "pet.skill.slot5Pool" ? id >= 24 :
      (key == "pet.skill.slot2Pool" ? (catalog[i][2] == "Tấn công" || catalog[i][2] == "Khống chế") : id < 24);
    if (allowed) result.push(catalog[i]);
  }
  return result;
}

function PetSkillName(id) {
  var catalog = PetSkillCatalog();
  for (var i = 0; i < catalog.length; i++) if (catalog[i][0] == id) return catalog[i][1];
  return "Skill #" + id;
}

function ParsePetSkillPool(value) {
  var result = [];
  var parts = ("" + value).split(",");
  for (var i = 0; i < parts.length; i++) {
    var id = parseInt(Trim(parts[i]), 10);
    if (!isNaN(id) && !ArrayContains(result, id)) result.push(id);
  }
  return result;
}

function BalancedSkillRate(index, count) {
  if (count < 1) return 0;
  return Math.round((100 / count) * 100) / 100;
}

function FormatSkillRate(value) {
  return ("" + value).replace(".", ",") + "%";
}

function FormatPetSkillPool(value) {
  var ids = ParsePetSkillPool(value);
  if (!ids.length) return "Chưa chọn kỹ năng";
  var labels = [];
  for (var i = 0; i < ids.length; i++) labels.push(PetSkillName(ids[i]) + " " + FormatSkillRate(BalancedSkillRate(i, ids.length)));
  return labels.join("  •  ");
}

function RenderPetConfigValueEditor(row) {
  var isSkillPool = row && row[5] == "skill-pool";
  document.getElementById("petConfigRawEditor").style.display = isSkillPool ? "none" : "block";
  document.getElementById("petSkillPoolEditor").style.display = isSkillPool ? "block" : "none";
  if (!isSkillPool) return;
  RenderPetSkillPoolChoices();
}

function RenderPetSkillPoolChoices() {
  if (!selectedPetConfigRow || selectedPetConfigRow[5] != "skill-pool") return;
  var ids = ParsePetSkillPool(V("petConfigValue"));
  var options = PetSkillOptions(selectedPetConfigRow[0]);
  var html = "";
  for (var i = 0; i < options.length; i++) {
    var selectedIndex = ArrayIndexOf(ids, options[i][0]);
    var checked = selectedIndex >= 0;
    var rate = checked ? BalancedSkillRate(selectedIndex, ids.length) : 0;
    html += '<label class="pet-skill-choice' + (checked ? ' checked' : '') + '">' +
      '<input type="checkbox" ' + (checked ? 'checked ' : '') + 'onclick="TogglePetSkill(' + options[i][0] + ',this.checked)">' +
      '<span class="pet-skill-choice-name">' + Html(options[i][1]) + '<br><small>' + Html(options[i][2]) + '</small></span>' +
      (checked ? '<span class="pet-skill-rate">' + FormatSkillRate(rate) + '</span>' : '') + '</label>';
  }
  document.getElementById("petSkillPoolChoices").innerHTML = html;
  document.getElementById("petSkillPoolSummary").innerText = ids.length ?
    "Đã chọn " + ids.length + " kỹ năng · Tỉ lệ được tự cân bằng: " + FormatPetSkillPool(V("petConfigValue")) :
    "Hãy chọn ít nhất một kỹ năng.";
}

function TogglePetSkill(skillId, checked) {
  var ids = ParsePetSkillPool(V("petConfigValue"));
  var index = ArrayIndexOf(ids, skillId);
  if (checked && index < 0) ids.push(skillId);
  if (!checked && index >= 0) ids.splice(index, 1);
  var ordered = [];
  var options = PetSkillOptions(selectedPetConfigRow ? selectedPetConfigRow[0] : "");
  for (var i = 0; i < options.length; i++) if (ArrayContains(ids, options[i][0])) ordered.push(options[i][0]);
  Set("petConfigValue", ordered.join(","));
  RenderPetSkillPoolChoices();
}

function PetConfigCategories() {
  var categories = [];
  var seen = {};
  for (var i = 1; i < petConfigRows.length; i++) {
    var category = petConfigRows[i][1];
    if (category && !seen[category]) { seen[category] = true; categories.push(category); }
  }
  return categories;
}

function RenderPetConfigTabs() {
  var categories = PetConfigCategories();
  var categoryExists = false;
  for (var i = 0; i < categories.length; i++) if (categories[i] == selectedPetConfigCategory) categoryExists = true;
  if (!categoryExists) selectedPetConfigCategory = categories.length ? categories[0] : "";
  var html = "";
  for (var c = 0; c < categories.length; c++) {
    var active = categories[c] == selectedPetConfigCategory ? " active" : "";
    html += '<button class="player-config-tab' + active + '" onclick="SelectPetConfigCategory(' + c + ')">' + Html(categories[c]) + '</button>';
  }
  document.getElementById("petConfigTabs").innerHTML = html;
  UpdateConfigColumnsOffset("panelPetConfig", "petConfigTabs");
}

function SelectPetConfigCategory(index) {
  var categories = PetConfigCategories();
  if (!categories[index]) return;
  selectedPetConfigCategory = categories[index];
  selectedPetConfigKey = "";
  selectedPetConfigRow = null;
  Set("petConfigKey", ""); Set("petConfigCategory", selectedPetConfigCategory);
  Set("petConfigName", ""); Set("petConfigValue", ""); Set("petConfigDefault", "");
  Set("petConfigKind", ""); Set("petConfigScope", ""); Set("petConfigDescription", "");
  RenderPetConfigValueEditor(null);
  RenderPetConfigTabs();
  FilterPetConfigRows();
  if (filteredPetConfigRows.length > 1) PickFilteredPetConfig(1);
}

function FilterPetConfigRows() {
  var q = Trim(V("petConfigSearch")).toLowerCase();
  filteredPetConfigRows = [petConfigRows[0] || []];
  for (var i = 1; i < petConfigRows.length; i++) {
    if (petConfigRows[i][1] != selectedPetConfigCategory) continue;
    if (!q || petConfigRows[i].join(" ").toLowerCase().indexOf(q) >= 0) filteredPetConfigRows.push(petConfigRows[i]);
  }
  var html = "<thead><tr><th>Cấu hình</th><th>Giá trị</th><th>Phạm vi</th></tr></thead><tbody>";
  for (var r = 1; r < filteredPetConfigRows.length; r++) {
    var row = filteredPetConfigRows[r];
    var displayValue = FormatAdminValue(row[3], row[5], row[0]);
    html += '<tr onclick="PickFilteredPetConfig(' + r + ')"><td>' + Html(row[2]) + '</td><td title="' + HtmlAttr(displayValue) + '">' + Html(displayValue) + '</td><td>' + Html(row[6]) + '</td></tr>';
  }
  document.getElementById("petConfigTable").innerHTML = html + "</tbody>";
}

function PickFilteredPetConfig(index) {
  if (!filteredPetConfigRows[index]) return;
  PickPetConfigByKey(filteredPetConfigRows[index][0]);
}

function PickPetConfigByKey(key) {
  for (var i = 1; i < petConfigRows.length; i++) {
    var row = petConfigRows[i];
    if (row[0] != key) continue;
    selectedPetConfigKey = key;
    selectedPetConfigRow = row;
    if (selectedPetConfigCategory != row[1]) {
      selectedPetConfigCategory = row[1];
      RenderPetConfigTabs();
      FilterPetConfigRows();
    }
    Set("petConfigKey", row[0]); Set("petConfigCategory", row[1]); Set("petConfigName", row[2]);
    Set("petConfigValue", FormatConfigEditValue(row[3], row[5])); Set("petConfigDefault", FormatAdminValue(row[4], row[5], row[0])); Set("petConfigKind", ConfigKindLabel(row[5]));
    Set("petConfigScope", row[6]); Set("petConfigDescription", row[7]);
    RenderPetConfigValueEditor(row);
    return;
  }
}

function SavePetConfig() {
  if (!V("petConfigKey")) { Msg("petConfigMessage", "Chọn một cấu hình trước."); return; }
  var kind = selectedPetConfigRow ? selectedPetConfigRow[5] : "";
  var result = RunAdmin("savepetconfig", { ConfigKey: V("petConfigKey"), ConfigValue: NormalizeConfigEditValue(V("petConfigValue"), kind) });
  Msg("petConfigMessage", StatusText(result));
  if (!IsAdminError(result)) LoadPetConfig();
}

function ResetPetConfig() {
  if (!V("petConfigKey")) { Msg("petConfigMessage", "Chọn một cấu hình trước."); return; }
  if (!window.confirm("Đưa " + V("petConfigName") + " về mặc định " + V("petConfigDefault") + "?")) return;
  var result = RunAdmin("resetpetconfig", { ConfigKey: V("petConfigKey") });
  Msg("petConfigMessage", StatusText(result));
  if (!IsAdminError(result)) LoadPetConfig();
}

RegisterTab({
  id: "petconfig", view: "pet-config.html", panelId: "panelPetConfig", navId: "navPetConfig",
  title: "Cấu hình Đệ tử toàn server", subtitle: "Khởi tạo, kỹ năng, AI, trang bị, thu nhận và hợp thể",
  onOpen: function () { LoadPetConfig(); },
  onRefresh: function () { LoadPetConfig(); },
  onLayout: function () { UpdateConfigColumnsOffset("panelPetConfig", "petConfigTabs"); }
});
