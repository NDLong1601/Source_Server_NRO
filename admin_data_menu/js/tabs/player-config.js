var playerConfigRows = [];
var filteredPlayerConfigRows = [];
var selectedPlayerConfigKey = "";
var selectedPlayerConfigCategory = "";

function LoadPlayerConfig() {
  playerConfigRows = ParseTsv(RunAdmin("listplayerconfig", {}));
  RenderPlayerConfigTabs();
  FilterPlayerConfigRows();
  var restored = false;
  if (selectedPlayerConfigKey) {
    for (var i = 1; i < playerConfigRows.length; i++) {
      if (playerConfigRows[i][0] == selectedPlayerConfigKey) { PickPlayerConfigByKey(selectedPlayerConfigKey); restored = true; break; }
    }
  }
  if (!restored && filteredPlayerConfigRows.length > 1) PickFilteredPlayerConfig(1);
  Msg("playerConfigMessage", playerConfigRows.length > 1 ? "Đã tải " + (playerConfigRows.length - 1) + " cấu hình Player." : "Không tải được cấu hình Player.");
  AdjustTableOffsets();
}

function EnsurePlayerConfigRows() {
  if (!playerConfigRows || playerConfigRows.length < 2) {
    playerConfigRows = ParseTsv(RunAdmin("listplayerconfig", {}));
    RenderPlayerConfigTabs();
  }
}

function PlayerConfigCategories() {
  var categories = [];
  var seen = {};
  for (var i = 1; i < playerConfigRows.length; i++) {
    var category = playerConfigRows[i][1];
    if (category && !seen[category]) { seen[category] = true; categories.push(category); }
  }
  return categories;
}

function RenderPlayerConfigTabs() {
  var categories = PlayerConfigCategories();
  var categoryExists = false;
  for (var i = 0; i < categories.length; i++) if (categories[i] == selectedPlayerConfigCategory) categoryExists = true;
  if (!categoryExists) selectedPlayerConfigCategory = categories.length ? categories[0] : "";
  var html = "";
  for (var c = 0; c < categories.length; c++) {
    var active = categories[c] == selectedPlayerConfigCategory ? " active" : "";
    html += '<button class="player-config-tab' + active + '" onclick="SelectPlayerConfigCategory(' + c + ')">' + Html(categories[c]) + '</button>';
  }
  document.getElementById("playerConfigTabs").innerHTML = html;
  UpdateConfigColumnsOffset("panelPlayerConfig", "playerConfigTabs");
}

function SelectPlayerConfigCategory(index) {
  var categories = PlayerConfigCategories();
  if (!categories[index]) return;
  selectedPlayerConfigCategory = categories[index];
  selectedPlayerConfigKey = "";
  Set("playerConfigKey", ""); Set("playerConfigCategory", selectedPlayerConfigCategory);
  Set("playerConfigName", ""); Set("playerConfigValue", ""); Set("playerConfigDefault", "");
  Set("playerConfigKind", ""); Set("playerConfigScope", ""); Set("playerConfigDescription", "");
  RenderPlayerConfigTabs();
  FilterPlayerConfigRows();
  if (filteredPlayerConfigRows.length > 1) PickFilteredPlayerConfig(1);
}

function FilterPlayerConfigRows() {
  var q = Trim(V("playerConfigSearch")).toLowerCase();
  filteredPlayerConfigRows = [playerConfigRows[0] || []];
  for (var i = 1; i < playerConfigRows.length; i++) {
    if (playerConfigRows[i][1] != selectedPlayerConfigCategory) continue;
    if (!q || playerConfigRows[i].join(" ").toLowerCase().indexOf(q) >= 0) filteredPlayerConfigRows.push(playerConfigRows[i]);
  }
  var html = "<thead><tr><th>Cấu hình</th><th>Giá trị</th><th>Phạm vi</th></tr></thead><tbody>";
  for (var r = 1; r < filteredPlayerConfigRows.length; r++) {
    var row = filteredPlayerConfigRows[r];
    html += '<tr onclick="PickFilteredPlayerConfig(' + r + ')"><td>' + Html(row[2]) + '</td><td title="' + HtmlAttr(FormatAdminValue(row[3], row[5], row[0])) + '">' + Html(FormatAdminValue(row[3], row[5], row[0])) + '</td><td>' + Html(row[6]) + '</td></tr>';
  }
  document.getElementById("playerConfigTable").innerHTML = html + "</tbody>";
}

function PickFilteredPlayerConfig(index) {
  if (!filteredPlayerConfigRows[index]) return;
  PickPlayerConfigByKey(filteredPlayerConfigRows[index][0]);
}

function PickPlayerConfigByKey(key) {
  for (var i = 1; i < playerConfigRows.length; i++) {
    var row = playerConfigRows[i];
    if (row[0] != key) continue;
    selectedPlayerConfigKey = key;
    if (selectedPlayerConfigCategory != row[1]) {
      selectedPlayerConfigCategory = row[1];
      RenderPlayerConfigTabs();
      FilterPlayerConfigRows();
    }
    Set("playerConfigKey", row[0]); Set("playerConfigCategory", row[1]); Set("playerConfigName", row[2]);
    Set("playerConfigValue", FormatConfigEditValue(row[3], row[5])); Set("playerConfigDefault", FormatAdminValue(row[4], row[5], row[0])); Set("playerConfigKind", ConfigKindLabel(row[5]));
    Set("playerConfigScope", row[6]); Set("playerConfigDescription", row[7]);
    return;
  }
}

function PlayerConfigCurrentValue(key) {
  EnsurePlayerConfigRows();
  for (var i = 1; i < playerConfigRows.length; i++) if (playerConfigRows[i][0] == key) return playerConfigRows[i][3];
  return "";
}

function SavePlayerConfig() {
  if (!V("playerConfigKey")) { Msg("playerConfigMessage", "Chọn một cấu hình trước."); return; }
  var row = FindConfigRow(playerConfigRows, V("playerConfigKey"));
  var kind = row ? row[5] : "";
  var result = RunAdmin("saveplayerconfig", { ConfigKey: V("playerConfigKey"), ConfigValue: NormalizeConfigEditValue(V("playerConfigValue"), kind) });
  Msg("playerConfigMessage", StatusText(result));
  if (!IsAdminError(result)) LoadPlayerConfig();
}

function ResetPlayerConfig() {
  if (!V("playerConfigKey")) { Msg("playerConfigMessage", "Chọn một cấu hình trước."); return; }
  if (!window.confirm("Đưa " + V("playerConfigName") + " về mặc định " + V("playerConfigDefault") + "?")) return;
  var result = RunAdmin("resetplayerconfig", { ConfigKey: V("playerConfigKey") });
  Msg("playerConfigMessage", StatusText(result));
  if (!IsAdminError(result)) LoadPlayerConfig();
}

RegisterTab({
  id: "playerconfig", view: "player-config.html", panelId: "panelPlayerConfig", navId: "navPlayerConfig",
  title: "Cấu hình Player toàn server", subtitle: "Chỉ số khởi tạo, tiền tệ, sức chứa và giới hạn 10 cấp",
  onOpen: function () { LoadPlayerConfig(); },
  onRefresh: function () { LoadPlayerConfig(); },
  onLayout: function () { UpdateConfigColumnsOffset("panelPlayerConfig", "playerConfigTabs"); }
});
