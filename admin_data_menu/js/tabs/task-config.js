var taskConfigRows = [];
var filteredTaskRows = [];
var selectedTaskMode = "runtime";
var selectedTaskRuntimeKey = "";
var selectedTaskMainId = "";
var selectedTaskRangeType = "side";
var selectedTaskRewardType = "";
var selectedTaskRewardId = "";
var selectedTaskRewardItemIds = [];
var selectedTaskRewardItemOptions = {};

function TaskModes() {
  return [
    ["runtime", "Cấu hình runtime"],
    ["main", "Nhiệm vụ chính"],
    ["sub", "Bước nhiệm vụ"],
    ["side", "Nhiệm vụ ngày"],
    ["clan", "Nhiệm vụ bang"],
    ["kanao", "Nhiệm vụ Vô Hạn Thành"],
    ["badges", "Danh hiệu"]
  ];
}

function RenderTaskModeTabs() {
  var modes = TaskModes();
  var html = "";
  for (var i = 0; i < modes.length; i++) {
    var active = modes[i][0] == selectedTaskMode ? " active" : "";
    html += '<button class="player-config-tab' + active + '" onclick="SelectTaskMode(\'' + modes[i][0] + '\')">' + Html(modes[i][1]) + '</button>';
  }
  document.getElementById("taskConfigTabs").innerHTML = html;
  UpdateConfigColumnsOffset("panelTaskConfig", "taskConfigTabs");
}

function SelectTaskMode(mode) {
  selectedTaskMode = mode;
  Set("taskConfigSearch", "");
  RenderTaskModeTabs();
  LoadTaskMode();
}

function ShowTaskEditor(id) {
  var editors = ["taskConfigEditor", "taskMainEditor", "taskSubEditor", "taskRangeEditor", "taskBadgesEditor", "taskKanaoEditor"];
  for (var i = 0; i < editors.length; i++) document.getElementById(editors[i]).style.display = editors[i] == id ? "block" : "none";
  document.getElementById("taskRewardEditor").style.display = (id == "taskMainEditor" || id == "taskRangeEditor" || id == "taskBadgesEditor" || id == "taskKanaoEditor") ? "block" : "none";
  document.getElementById("taskSubMainSelect").style.display = selectedTaskMode == "sub" ? "inline-block" : "none";
}

function LoadTaskConfig() {
  LoadItemCatalog(false);
  RenderTaskModeTabs();
  LoadTaskMode();
}

function LoadTaskMode() {
  if (selectedTaskMode == "runtime") LoadTaskRuntimeConfig();
  else if (selectedTaskMode == "main") LoadTaskMains();
  else if (selectedTaskMode == "sub") LoadTaskSubMains();
  else if (selectedTaskMode == "side" || selectedTaskMode == "clan") LoadTaskRangeTemplates(selectedTaskMode);
  else if (selectedTaskMode == "kanao") LoadTaskKanaoConfig();
  else if (selectedTaskMode == "badges") LoadTaskBadgesTemplates();
  AdjustTableOffsets();
}

function TaskText(value) {
  return ("" + value).replace(/\\r/g, "\r").replace(/\\n/g, "\n");
}

function LoadTaskRuntimeConfig() {
  ShowTaskEditor("taskConfigEditor");
  taskConfigRows = ParseTsv(RunAdmin("listtaskconfig", {}));
  FilterTaskRows();
  var restored = false;
  if (selectedTaskRuntimeKey) {
    for (var i = 1; i < taskConfigRows.length; i++) {
      if (taskConfigRows[i][0] == selectedTaskRuntimeKey) { PickTaskRuntimeByKey(selectedTaskRuntimeKey); restored = true; break; }
    }
  }
  if (!restored && filteredTaskRows.length > 1) PickTaskRuntimeRow(1);
  Msg("taskConfigMessage", taskConfigRows.length > 1 ? "Đã tải " + (taskConfigRows.length - 1) + " cấu hình nhiệm vụ." : "Không có cấu hình nhiệm vụ.");
}

function FilterTaskRows() {
  if (selectedTaskMode == "runtime") return FilterTaskRuntimeRows();
  if (selectedTaskMode == "main") return FilterTaskMainRows();
  if (selectedTaskMode == "sub") return FilterTaskSubRows();
  if (selectedTaskMode == "side" || selectedTaskMode == "clan") return FilterTaskRangeRows();
  if (selectedTaskMode == "kanao") return FilterTaskKanaoRows();
  if (selectedTaskMode == "badges") return FilterTaskBadgesRows();
}

function LoadTaskKanaoConfig() {
  ShowTaskEditor("taskKanaoEditor");
  taskConfigRows = ParseTsv(RunAdmin("listtaskconfig", {}));
  var mapRow = FindConfigRow(taskConfigRows, "task.kanao.mapIds");
  var minRow = FindConfigRow(taskConfigRows, "task.kanao.killCountMin");
  var maxRow = FindConfigRow(taskConfigRows, "task.kanao.killCountMax");
  Set("taskKanaoMapIds", mapRow ? mapRow[3] : "187,188,189,190");
  Set("taskKanaoKillMin", minRow ? minRow[3] : "10");
  Set("taskKanaoKillMax", maxRow ? maxRow[3] : "20");
  FilterTaskKanaoRows();
  LoadTaskReward("kanao", "0", "Phần thưởng nhiệm vụ Kanao");
  Msg("taskConfigMessage", "Đã tải cấu hình nhiệm vụ Kanao.");
}

function FilterTaskKanaoRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>Cấu hình Kanao</th><th>Giá trị</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (row[1] != "Nhiệm vụ Kanao") continue;
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    html += "<tr><td>" + Html(row[2]) + "<br><small>" + Html(row[0]) + "</small></td><td>" + Html(FormatAdminValue(row[3], row[5], row[0])) + "</td></tr>";
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function SaveTaskKanaoConfig() {
  var payload = {
    MapIds: NormalizeNumberList(V("taskKanaoMapIds")),
    KillCountMin: NormalizeInteger(V("taskKanaoKillMin")),
    KillCountMax: NormalizeInteger(V("taskKanaoKillMax"))
  };
  var text = RunAdmin("savekanaotaskconfig", { PayloadJson: JSON.stringify(payload) });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskKanaoConfig();
}

function FilterTaskRuntimeRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>Cấu hình</th><th>Giá trị</th><th>Phạm vi</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    var displayValue = FormatAdminValue(row[3], row[5], row[0]);
    html += '<tr onclick="PickTaskRuntimeRow(' + (filteredTaskRows.length - 1) + ')"><td>' + Html(row[2]) + '<br><small>' + Html(row[0]) + '</small></td><td title="' + HtmlAttr(displayValue) + '">' + Html(displayValue) + '</td><td>' + Html(row[6]) + '</td></tr>';
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function PickTaskRuntimeRow(index) {
  if (!filteredTaskRows[index]) return;
  PickTaskRuntimeByKey(filteredTaskRows[index][0]);
}

function PickTaskRuntimeByKey(key) {
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (row[0] != key) continue;
    selectedTaskRuntimeKey = key;
    Set("taskRuntimeKey", row[0]); Set("taskRuntimeCategory", row[1]); Set("taskRuntimeName", row[2]);
    Set("taskRuntimeValue", FormatConfigEditValue(row[3], row[5])); Set("taskRuntimeDefault", FormatAdminValue(row[4], row[5], row[0]));
    Set("taskRuntimeKind", ConfigKindLabel(row[5])); Set("taskRuntimeScope", ConfigScopeLabel(row[6])); Set("taskRuntimeDescription", row[7]);
    return;
  }
}

function SaveTaskRuntimeConfig() {
  if (!V("taskRuntimeKey")) { Msg("taskConfigMessage", "Chọn một cấu hình trước."); return; }
  var row = FindConfigRow(taskConfigRows, V("taskRuntimeKey"));
  var kind = row ? row[5] : "";
  var text = RunAdmin("savetaskconfig", { ConfigKey: V("taskRuntimeKey"), ConfigValue: NormalizeConfigEditValue(V("taskRuntimeValue"), kind) });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskRuntimeConfig();
}

function ResetTaskRuntimeConfig() {
  if (!V("taskRuntimeKey")) { Msg("taskConfigMessage", "Chọn một cấu hình trước."); return; }
  if (!window.confirm("Đưa " + V("taskRuntimeName") + " về mặc định " + V("taskRuntimeDefault") + "?")) return;
  var text = RunAdmin("resettaskconfig", { ConfigKey: V("taskRuntimeKey") });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskRuntimeConfig();
}

function LoadTaskMains() {
  ShowTaskEditor("taskMainEditor");
  taskConfigRows = ParseTsv(RunAdmin("listtaskmains", { Search: V("taskConfigSearch") }));
  FilterTaskMainRows();
  if (filteredTaskRows.length > 1) PickTaskMainRow(1); else ClearTaskMainEditor();
  Msg("taskConfigMessage", taskConfigRows.length > 1 ? "Đã tải " + (taskConfigRows.length - 1) + " Nhiệm vụ chính." : "Không có nhiệm vụ chính.");
}

function FilterTaskMainRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>ID</th><th>Tên</th><th>Số bước</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    html += '<tr onclick="PickTaskMainRow(' + (filteredTaskRows.length - 1) + ')"><td>' + Html(row[0]) + '</td><td>' + Html(TaskText(row[1])) + '</td><td>' + Html(row[3]) + '</td></tr>';
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function PickTaskMainRow(index) {
  var row = filteredTaskRows[index];
  if (!row) return;
  selectedTaskMainId = row[0];
  Set("taskMainId", row[0]); Set("taskMainName", TaskText(row[1])); Set("taskMainDetail", TaskText(row[2]));
  LoadTaskReward("main", row[0], "Phần thưởng nhiệm vụ chính #" + row[0]);
}

function ClearTaskMainEditor() {
  Set("taskMainId", ""); Set("taskMainName", ""); Set("taskMainDetail", "");
}

function SaveTaskMainTemplate() {
  var text = RunAdmin("savetaskmain", { Id: V("taskMainId"), Name: V("taskMainName"), Description: V("taskMainDetail") });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskMains();
}

function LoadTaskSubMains() {
  ShowTaskEditor("taskSubEditor");
  var mains = ParseTsv(RunAdmin("listtaskmains", {}));
  var html = "";
  for (var i = 1; i < mains.length; i++) html += '<option value="' + HtmlAttr(mains[i][0]) + '">' + Html(mains[i][0] + " - " + TaskText(mains[i][1])) + '</option>';
  document.getElementById("taskSubMainSelect").innerHTML = html;
  if (selectedTaskMainId) document.getElementById("taskSubMainSelect").value = selectedTaskMainId;
  if (!document.getElementById("taskSubMainSelect").value && mains.length > 1) document.getElementById("taskSubMainSelect").value = mains[1][0];
  LoadTaskSubsForSelectedMain();
}

function LoadTaskSubsForSelectedMain() {
  selectedTaskMainId = document.getElementById("taskSubMainSelect").value;
  taskConfigRows = ParseTsv(RunAdmin("listtasksubs", { Id: selectedTaskMainId }));
  FilterTaskSubRows();
  if (filteredTaskRows.length > 1) PickTaskSubRow(1); else ClearTaskSubEditor();
  Msg("taskConfigMessage", taskConfigRows.length > 1 ? "Đã tải " + (taskConfigRows.length - 1) + " bước nhiệm vụ." : "Nhiệm vụ này chưa có bước.");
}

function FilterTaskSubRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>ID</th><th>Tên bước</th><th>Số lượng</th><th>NPC/Map</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    html += '<tr onclick="PickTaskSubRow(' + (filteredTaskRows.length - 1) + ')"><td>' + Html(row[0]) + '</td><td>' + Html(TaskText(row[2])) + '</td><td>' + Html(row[3]) + '</td><td>' + Html(row[5] + "/" + row[6]) + '</td></tr>';
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function PickTaskSubRow(index) {
  var row = filteredTaskRows[index];
  if (!row) return;
  Set("taskSubId", row[0]); Set("taskSubOwnerId", row[1]); Set("taskSubName", TaskText(row[2])); Set("taskSubCount", row[3]);
  Set("taskSubNotify", TaskText(row[4])); Set("taskSubNpcId", row[5]); Set("taskSubMapId", row[6]);
}

function ClearTaskSubEditor() {
  Set("taskSubId", ""); Set("taskSubOwnerId", selectedTaskMainId); Set("taskSubName", ""); Set("taskSubCount", "");
  Set("taskSubNotify", ""); Set("taskSubNpcId", ""); Set("taskSubMapId", "");
}

function SaveTaskSubTemplate() {
  var text = RunAdmin("savetasksub", { Id: V("taskSubId"), OwnerId: V("taskSubOwnerId"), Name: V("taskSubName"), CountLeft: V("taskSubCount"), Notify: V("taskSubNotify"), NpcId: V("taskSubNpcId"), MapId: V("taskSubMapId") });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskSubsForSelectedMain();
}

function LoadTaskRangeTemplates(type) {
  selectedTaskRangeType = type;
  ShowTaskEditor("taskRangeEditor");
  document.getElementById("taskRangeTitle").innerText = type == "clan" ? "Template nhiệm vụ bang" : "Template nhiệm vụ ngày";
  taskConfigRows = ParseTsv(RunAdmin("listtasktemplates", { Type: type }));
  FilterTaskRangeRows();
  if (filteredTaskRows.length > 1) PickTaskRangeRow(1); else ClearTaskRangeEditor();
  Msg("taskConfigMessage", taskConfigRows.length > 1 ? "Đã tải " + (taskConfigRows.length - 1) + " template." : "Không có template.");
}

function FilterTaskRangeRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>ID</th><th>Tên</th><th>Dễ</th><th>Bình thường</th><th>Khó</th><th>Rất khó</th><th>Địa ngục</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    html += '<tr onclick="PickTaskRangeRow(' + (filteredTaskRows.length - 1) + ')"><td>' + Html(row[0]) + (row[7] == "1" ? '<br><small>core</small>' : '') + '</td><td>' + Html(TaskText(row[1])) + '</td><td>' + Html(row[2]) + '</td><td>' + Html(row[3]) + '</td><td>' + Html(row[4]) + '</td><td>' + Html(row[5]) + '</td><td>' + Html(row[6]) + '</td></tr>';
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function PickTaskRangeRow(index) {
  var row = filteredTaskRows[index];
  if (!row) return;
  Set("taskRangeId", row[0]); Set("taskRangeName", TaskText(row[1])); Set("taskRangeLv1", row[2]); Set("taskRangeLv2", row[3]);
  Set("taskRangeLv3", row[4]); Set("taskRangeLv4", row[5]); Set("taskRangeLv5", row[6]);
  LoadTaskReward(selectedTaskRangeType, row[0], "Phần thưởng " + (selectedTaskRangeType == "clan" ? "nhiệm vụ bang" : "nhiệm vụ ngày") + " #" + row[0]);
}

function ClearTaskRangeEditor() {
  Set("taskRangeId", ""); Set("taskRangeName", ""); Set("taskRangeLv1", ""); Set("taskRangeLv2", ""); Set("taskRangeLv3", ""); Set("taskRangeLv4", ""); Set("taskRangeLv5", "");
}

function SaveTaskRangeTemplate() {
  var payload = { lv1: V("taskRangeLv1"), lv2: V("taskRangeLv2"), lv3: V("taskRangeLv3"), lv4: V("taskRangeLv4"), lv5: V("taskRangeLv5") };
  var text = RunAdmin("savetasktemplate", { Type: selectedTaskRangeType, Id: V("taskRangeId"), Name: V("taskRangeName"), PayloadJson: JSON.stringify(payload) });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskRangeTemplates(selectedTaskRangeType);
}

function LoadTaskBadgesTemplates() {
  ShowTaskEditor("taskBadgesEditor");
  taskConfigRows = ParseTsv(RunAdmin("listbadgestasks", {}));
  FilterTaskBadgesRows();
  if (filteredTaskRows.length > 1) PickTaskBadgesRow(1); else ClearTaskBadgesEditor();
  Msg("taskConfigMessage", taskConfigRows.length > 1 ? "Đã tải " + (taskConfigRows.length - 1) + " nhiệm vụ danh hiệu." : "Không có nhiệm vụ danh hiệu.");
}

function FilterTaskBadgesRows() {
  var q = Trim(V("taskConfigSearch")).toLowerCase();
  filteredTaskRows = [taskConfigRows[0] || []];
  var html = "<thead><tr><th>ID</th><th>Tên</th><th>Số lượng</th><th>Danh hiệu</th></tr></thead><tbody>";
  for (var i = 1; i < taskConfigRows.length; i++) {
    var row = taskConfigRows[i];
    if (q && row.join(" ").toLowerCase().indexOf(q) < 0) continue;
    filteredTaskRows.push(row);
    html += '<tr onclick="PickTaskBadgesRow(' + (filteredTaskRows.length - 1) + ')"><td>' + Html(row[0]) + '</td><td>' + Html(TaskText(row[1])) + '</td><td>' + Html(row[2]) + '</td><td>' + Html(row[3] + " - " + TaskText(row[4])) + '</td></tr>';
  }
  document.getElementById("taskConfigTable").innerHTML = html + "</tbody>";
}

function PickTaskBadgesRow(index) {
  var row = filteredTaskRows[index];
  if (!row) return;
  Set("taskBadgesId", row[0]); Set("taskBadgesName", TaskText(row[1])); Set("taskBadgesCount", row[2]); Set("taskBadgesRewardId", row[3]); Set("taskBadgesRewardName", TaskText(row[4]));
  Set("taskBadgesItemId", row[5] || "");
  var image = document.getElementById("taskBadgesImage");
  var badgeIconId = row[6] || "-1";
  if (badgeIconId != "-1") { image.src = "data/icon/x2/" + badgeIconId + ".png"; image.style.display = "inline-block"; }
  else { image.removeAttribute("src"); image.style.display = "none"; }
  LoadTaskReward("badges", row[0], "Phần thưởng nhiệm vụ danh hiệu #" + row[0]);
}

function ClearTaskBadgesEditor() {
  Set("taskBadgesId", ""); Set("taskBadgesName", ""); Set("taskBadgesCount", ""); Set("taskBadgesRewardId", ""); Set("taskBadgesRewardName", ""); Set("taskBadgesItemId", "");
  document.getElementById("taskBadgesImage").style.display = "none";
}

function SaveTaskBadgesTemplate() {
  var text = RunAdmin("savebadgestask", { Id: V("taskBadgesId"), Name: V("taskBadgesName"), CountLeft: V("taskBadgesCount"), RequireId: V("taskBadgesRewardId") });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskBadgesTemplates();
}

function LoadTaskReward(type, id, title) {
  selectedTaskRewardType = type;
  selectedTaskRewardId = "" + id;
  document.getElementById("taskRewardTitle").innerText = title;
  var rows = ParseTsv(RunAdmin("gettaskreward", { Type: type, Id: id }));
  var row = rows.length > 1 ? rows[1] : [id, "0", "0", "0", "0", ""];
  document.getElementById("taskRewardEnabledRow").style.display = type == "kanao" ? "none" : "flex";
  document.getElementById("taskRewardEnabled").checked = type == "kanao" ? true : row[1] == "1";
  Set("taskRewardPotential", row[2] || "0"); Set("taskRewardGold", row[3] || "0"); Set("taskRewardGem", row[4] || "0");
  selectedTaskRewardItemIds = row[5] ? row[5].split(",") : [];
  selectedTaskRewardItemOptions = {};
  if (row[6]) {
    try {
      var configs = JSON.parse(row[6] || "[]");
      for (var c = 0; c < configs.length; c++) selectedTaskRewardItemOptions["" + configs[c].itemId] = configs[c];
    } catch (e) { selectedTaskRewardItemOptions = {}; }
  }
  Set("taskRewardItemSearch", "");
  RenderTaskRewardItemPicker();
  if (selectedTaskRewardItemIds.length > 0) PickTaskRewardOptionItem(selectedTaskRewardItemIds[0]);
}

function RenderTaskRewardItemPicker() {
  if (!document.getElementById("taskRewardItemList")) return;
  var selected = {};
  for (var i = 0; i < selectedTaskRewardItemIds.length; i++) selected[selectedTaskRewardItemIds[i]] = true;
  RenderItemPicker({
    listId: "taskRewardItemList", idPrefix: "taskRewardItem", query: V("taskRewardItemSearch"),
    selected: selected, selectedOrder: selectedTaskRewardItemIds, toggleFunction: "ToggleTaskRewardItem", limit: 150,
    emptyText: "Không tìm thấy vật phẩm phù hợp."
  });
  var labels = [];
  for (var s = 0; s < selectedTaskRewardItemIds.length && s < 3; s++) labels.push(selectedTaskRewardItemIds[s] + " - " + ItemCatalogName(selectedTaskRewardItemIds[s]));
  document.getElementById("taskRewardItemSummary").innerText = selectedTaskRewardItemIds.length
    ? "Đã chọn " + selectedTaskRewardItemIds.length + " vật phẩm: " + labels.join("; ") + (selectedTaskRewardItemIds.length > 3 ? "..." : "")
    : "Chưa chọn vật phẩm";
  var optionButtons = [];
  for (var b = 0; b < selectedTaskRewardItemIds.length; b++) {
    var buttonId = selectedTaskRewardItemIds[b];
    optionButtons.push('<button type="button" onclick="PickTaskRewardOptionItem(\'' + EscapeJs(buttonId) + '\')">Cấu hình ' + Html(buttonId + " - " + ItemCatalogName(buttonId)) + '</button>');
  }
  if (document.getElementById("taskRewardOptionItems")) document.getElementById("taskRewardOptionItems").innerHTML = optionButtons.join(" ");
}

function ToggleTaskRewardItem(id, checked) {
  var next = [];
  var found = false;
  for (var i = 0; i < selectedTaskRewardItemIds.length; i++) {
    if (selectedTaskRewardItemIds[i] == id) found = true;
    if (!(selectedTaskRewardItemIds[i] == id && !checked)) next.push(selectedTaskRewardItemIds[i]);
  }
  if (checked && !found) next.push("" + id);
  selectedTaskRewardItemIds = next;
  if (checked) PickTaskRewardOptionItem(id);
  else if (selectedTaskRewardItemOptions["" + id]) delete selectedTaskRewardItemOptions["" + id];
  RenderTaskRewardItemPicker();
}

function PickTaskRewardOptionItem(id) {
  id = "" + id;
  var config = selectedTaskRewardItemOptions[id] || { itemId: parseInt(id, 10), useDefaultOptions: true, options: [] };
  selectedTaskRewardItemOptions[id] = config;
  Set("taskRewardOptionItemId", id);
  document.getElementById("taskRewardUseDefault").checked = config.useDefaultOptions !== false;
  Set("taskRewardExtraOptions", TaskRewardOptionsText(config.options));
}

function TaskRewardOptionsText(options) {
  var result = [];
  for (var i = 0; i < (options || []).length; i++) result.push(options[i].id + ":" + options[i].param);
  return result.join(",");
}

function SaveTaskRewardOptionEditor() {
  var id = V("taskRewardOptionItemId");
  if (!id) return;
  var options = ParseSpawnOptions(V("taskRewardExtraOptions"), "taskConfigMessage");
  if (options == null) return;
  selectedTaskRewardItemOptions[id] = { itemId: parseInt(id, 10), useDefaultOptions: document.getElementById("taskRewardUseDefault").checked, options: options };
}

function SaveTaskReward() {
  if (!selectedTaskRewardType || selectedTaskRewardId === "") { Msg("taskConfigMessage", "Chọn nhiệm vụ trước khi lưu phần thưởng."); return; }
  SaveTaskRewardOptionEditor();
  var itemOptions = [];
  for (var optionItemId in selectedTaskRewardItemOptions) if (selectedTaskRewardItemOptions.hasOwnProperty(optionItemId)) itemOptions.push(selectedTaskRewardItemOptions[optionItemId]);
  var payload = {
    Enabled: document.getElementById("taskRewardEnabled").checked ? "1" : "0",
    Potential: V("taskRewardPotential"), Gold: V("taskRewardGold"), Gem: V("taskRewardGem"),
    ItemIds: selectedTaskRewardItemIds, ItemOptions: itemOptions
  };
  var text = RunAdmin("savetaskreward", { Type: selectedTaskRewardType, Id: selectedTaskRewardId, PayloadJson: JSON.stringify(payload) });
  Msg("taskConfigMessage", StatusText(text));
  if (!IsAdminError(text)) LoadTaskReward(selectedTaskRewardType, selectedTaskRewardId, document.getElementById("taskRewardTitle").innerText);
}

RegisterTab({
  id: "taskconfig", view: "task-config.html", panelId: "panelTaskConfig", navId: "navTaskConfig",
  title: "Cấu hình Nhiệm vụ", subtitle: "Runtime task.properties và template nhiệm vụ trong database",
  onOpen: function () { LoadTaskConfig(); },
  onRefresh: function () { LoadTaskConfig(); },
  onLayout: function () { UpdateConfigColumnsOffset("panelTaskConfig", "taskConfigTabs"); }
});
