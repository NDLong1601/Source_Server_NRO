var combineRows = [];
var selectedCombineKey = "";
var filteredCombineRows = [];

function LoadCombineConfig() {
  if (!optionRows || optionRows.length < 2) LoadOptions();
  combineRows = ParseTsv(RunAdmin("listcombineconfig", {}));
  filteredCombineRows = VisibleCombineRows();
  RenderCombineRows(filteredCombineRows);
  if (selectedCombineKey) {
    for (var i = 1; i < combineRows.length; i++) {
      if (combineRows[i][0] == selectedCombineKey) {
        PickCombineConfig(i);
        break;
      }
    }
  }
  Msg("combineMessage", filteredCombineRows.length > 1 ? "Đã tải " + (filteredCombineRows.length - 1) + " nhóm cấu hình combine." : "Không tải được cấu hình combine.");
  AdjustTableOffsets();
}

function IsCombineCompanionKey(key) {
  return key == "earring.level2.paramMin" || key == "earring.level2.paramMax" ||
    key == "earring.level3.paramMin" || key == "earring.level3.paramMax" ||
    key == "earring.level3.allowDuplicate" ||
    key == "angel.bonusParamMin" || key == "angel.bonusParamMax";
}

function VisibleCombineRows() {
  var rows = [combineRows[0]];
  for (var i = 1; i < combineRows.length; i++) {
    if (!IsCombineCompanionKey(combineRows[i][0])) rows.push(combineRows[i]);
  }
  return rows;
}

function RenderCombineRows(rows) {
  filteredCombineRows = rows;
  var html = "<thead><tr><th>Nhóm</th><th>Chức năng</th><th>Giá trị đang dùng</th><th>Mặc định</th></tr></thead><tbody>";
  for (var r = 1; r < rows.length; r++) {
    var currentValue = rows[r][5] == "option-list" ? FormatCombineOptionValues(rows[r][3]) : rows[r][3];
    var defaultValue = rows[r][5] == "option-list" ? FormatCombineOptionValues(rows[r][4]) : rows[r][4];
    var optionKeys = OptionGroupKeys(rows[r][0]);
    if (optionKeys) {
      var minRow = FindCombineRow(optionKeys.min);
      var maxRow = FindCombineRow(optionKeys.max);
      currentValue += " | Param " + (minRow ? minRow[3] : "") + "-" + (maxRow ? maxRow[3] : "");
      defaultValue += " | Param " + (minRow ? minRow[4] : "") + "-" + (maxRow ? maxRow[4] : "");
      if (optionKeys.duplicate) currentValue += IsTrueValue(CombineConfigValue(optionKeys.duplicate)) ? " | Có thể trùng" : " | Không trùng";
    }
    html += '<tr onclick="PickFilteredCombineConfig(' + r + ')">';
    html += "<td>" + Html(rows[r][1] || "") + "</td>";
    html += "<td>" + Html(rows[r][2] || "") + "</td>";
    html += "<td>" + Html(currentValue) + "</td>";
    html += "<td>" + Html(defaultValue) + "</td></tr>";
  }
  html += "</tbody>";
  document.getElementById("combineTable").innerHTML = html;
}

function FilterCombineRows() {
  var q = V("combineSearch").toLowerCase();
  var filtered = [combineRows[0]];
  for (var i = 1; i < combineRows.length; i++) {
    if (!IsCombineCompanionKey(combineRows[i][0]) &&
        (combineRows[i].join(" ") || "").toLowerCase().indexOf(q) >= 0) filtered.push(combineRows[i]);
  }
  RenderCombineRows(filtered);
}

function PickFilteredCombineConfig(index) {
  var key = filteredCombineRows[index][0];
  for (var i = 1; i < combineRows.length; i++) {
    if (combineRows[i][0] == key) {
      PickCombineConfig(i);
      return;
    }
  }
}

function PickCombineConfig(index) {
  var r = combineRows[index];
  selectedCombineKey = r[0];
  Set("combineKey", r[0]);
  Set("combineCategory", r[1]);
  Set("combineName", r[2]);
  Set("combineValue", r[3]);
  Set("combineDefault", r[4]);
  Set("combineKind", r[5]);
  Set("combineDescription", r[6]);
  var isOptionGroup = r[5] == "option-list" && OptionGroupKeys(r[0]) != null;
  document.getElementById("combineValueBox").style.display = isOptionGroup ? "none" : "block";
  document.getElementById("combineOptionBox").style.display = isOptionGroup ? "block" : "none";
  if (isOptionGroup) {
    var keys = OptionGroupKeys(r[0]);
    Set("combineParamMin", CombineConfigValue(keys.min));
    Set("combineParamMax", CombineConfigValue(keys.max));
    Set("combineOptionSearch", "");
    document.getElementById("combineDuplicateBox").style.display = keys.duplicate ? "block" : "none";
    document.getElementById("combineAllowDuplicate").checked = keys.duplicate ? IsTrueValue(CombineConfigValue(keys.duplicate)) : false;
    RenderCombineOptionPicker();
  }
  AdjustTableOffsets();
}

function FindCombineRow(key) {
  for (var i = 1; i < combineRows.length; i++) {
    if (combineRows[i][0] == key) return combineRows[i];
  }
  return null;
}

function CombineConfigValue(key) {
  var row = FindCombineRow(key);
  return row ? row[3] : "";
}

function OptionGroupKeys(key) {
  if (key == "earring.level2.options") return { min: "earring.level2.paramMin", max: "earring.level2.paramMax", duplicate: "" };
  if (key == "earring.level3.options") return { min: "earring.level3.paramMin", max: "earring.level3.paramMax", duplicate: "earring.level3.allowDuplicate" };
  if (key == "angel.bonusOptions") return { min: "angel.bonusParamMin", max: "angel.bonusParamMax", duplicate: "" };
  return null;
}

function ParseIdList(value) {
  var parts = (value || "").split(",");
  var ids = [];
  for (var i = 0; i < parts.length; i++) {
    var id = Trim(parts[i]);
    if (id && ids.join(",").split(",").indexOf(id) < 0) ids.push(id);
  }
  return ids;
}

function FormatCombineOptionValues(value) {
  var ids = ParseIdList(value);
  var labels = [];
  for (var i = 0; i < ids.length; i++) labels.push(ids[i] + " - " + OptionName(ids[i]));
  return labels.join("; ");
}

function RenderCombineOptionPicker() {
  if (!V("combineKey") || !OptionGroupKeys(V("combineKey"))) return;
  var selectedIds = ParseIdList(V("combineValue"));
  RenderOptionPicker("combineOptionList", "combineOptionSearch", selectedIds, "ToggleCombineOption", "combinePicker");
  document.getElementById("combineOptionSummary").innerText = "Đã chọn " + selectedIds.length + " option: " + FormatCombineOptionValues(V("combineValue"));
}

function ToggleCombineOption(id, checked) {
  var ids = ParseIdList(V("combineValue"));
  var next = [];
  var found = false;
  for (var i = 0; i < ids.length; i++) {
    if (ids[i] == id) found = true;
    if (!(ids[i] == id && !checked)) next.push(ids[i]);
  }
  if (checked && !found) next.push(id);
  Set("combineValue", next.join(","));
  document.getElementById("combineOptionSummary").innerText = "Đã chọn " + next.length + " option: " + FormatCombineOptionValues(V("combineValue"));
}

function SaveCombineValue(key, value) {
  return RunAdmin("savecombineconfig", { ConfigKey: key, ConfigValue: value });
}

function SaveCombineConfig() {
  if (!V("combineKey")) { Msg("combineMessage", "Chọn một cấu hình trước."); return; }
  var keys = OptionGroupKeys(V("combineKey"));
  if (keys) {
    var ids = ParseIdList(V("combineValue"));
    var minValue = Trim(V("combineParamMin"));
    var maxValue = Trim(V("combineParamMax"));
    if (ids.length < 1) { Msg("combineMessage", "Cần chọn ít nhất một option."); return; }
    if (!/^\d+$/.test(minValue) || !/^\d+$/.test(maxValue)) { Msg("combineMessage", "Param Min/Max phải là số nguyên không âm."); return; }
    if (parseInt(minValue, 10) > parseInt(maxValue, 10)) { Msg("combineMessage", "Param Min không được lớn hơn Param Max."); return; }
    var messages = [];
    var result = SaveCombineValue(V("combineKey"), ids.join(","));
    messages.push(StatusText(result));
    if (IsAdminError(result)) { Msg("combineMessage", messages.join("\r\n")); return; }
    result = SaveCombineValue(keys.min, minValue);
    messages.push(StatusText(result));
    if (IsAdminError(result)) { Msg("combineMessage", messages.join("\r\n")); return; }
    result = SaveCombineValue(keys.max, maxValue);
    messages.push(StatusText(result));
    if (keys.duplicate && !IsAdminError(result)) {
      result = SaveCombineValue(keys.duplicate, document.getElementById("combineAllowDuplicate").checked ? "true" : "false");
      messages.push(StatusText(result));
    }
    Msg("combineMessage", messages.join("\r\n"));
    if (!IsAdminError(result)) LoadCombineConfig();
  } else {
    var text = SaveCombineValue(V("combineKey"), V("combineValue"));
    Msg("combineMessage", StatusText(text));
    if (!IsAdminError(text)) LoadCombineConfig();
  }
}

function ResetCombineConfig() {
  if (!V("combineKey")) { Msg("combineMessage", "Chọn một cấu hình trước."); return; }
  if (!window.confirm("Đưa " + V("combineName") + " về mặc định " + V("combineDefault") + "?")) return;
  var keys = OptionGroupKeys(V("combineKey"));
  var resetKeys = [V("combineKey")];
  if (keys) {
    resetKeys.push(keys.min);
    resetKeys.push(keys.max);
    if (keys.duplicate) resetKeys.push(keys.duplicate);
  }
  var messages = [];
  var text = "";
  for (var i = 0; i < resetKeys.length; i++) {
    text = RunAdmin("resetcombineconfig", { ConfigKey: resetKeys[i] });
    messages.push(StatusText(text));
    if (IsAdminError(text)) break;
  }
  Msg("combineMessage", messages.join("\r\n"));
  if (!IsAdminError(text)) LoadCombineConfig();
}

RegisterTab({
  id: "combine", view: "combine.html", panelId: "panelCombine", navId: "navCombine",
  title: "Quản lý Combine", subtitle: "Điều chỉnh tỉ lệ, mức tăng chỉ số và option random theo thời gian thực",
  onOpen: function () { LoadCombineConfig(); },
  onRefresh: function () { LoadCombineConfig(); }
});
