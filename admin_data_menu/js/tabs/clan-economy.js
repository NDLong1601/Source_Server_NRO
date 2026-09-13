var clanConfigRows = [];
var filteredClanConfigRows = [];
var selectedClanConfigKey = "";
var selectedClanConfigCategory = "";
var clanConfigDrafts = {};
var clanConfigStructuredCount = 0;
var clanConfigBusy = false;

function ClanConfigText(id, text) {
  document.getElementById(id).innerText = text;
}

function ClanConfigCategories() {
  var categories = [];
  for (var i = 1; i < clanConfigRows.length; i++) {
    if (!ArrayContains(categories, clanConfigRows[i][1])) categories.push(clanConfigRows[i][1]);
  }
  return categories;
}

function ClanConfigFold(text) {
  return ("" + text).toLowerCase()
    .replace(/[àáạảãâầấậẩẫăằắặẳẵ]/g, "a").replace(/[èéẹẻẽêềếệểễ]/g, "e")
    .replace(/[ìíịỉĩ]/g, "i").replace(/[òóọỏõôồốộổỗơờớợởỡ]/g, "o")
    .replace(/[ùúụủũưừứựửữ]/g, "u").replace(/[ỳýỵỷỹ]/g, "y").replace(/đ/g, "d");
}

function ClanConfigSearchMatches(row, query) {
  return ClanConfigFold(row.join(" ")).indexOf(ClanConfigFold(Trim(query))) >= 0;
}

function ClanConfigDraftCount() {
  var count = 0;
  for (var key in clanConfigDrafts) if (clanConfigDrafts.hasOwnProperty(key)) count++;
  return count;
}

function RenderClanConfigTabs() {
  var container = document.getElementById("clanConfigTabs");
  if (!container) return;
  var scrollTop = container.scrollTop;
  var focused = document.activeElement ? document.activeElement.id : "";
  var categories = ClanConfigCategories();
  if ((!selectedClanConfigCategory || !ArrayContains(categories, selectedClanConfigCategory)) && categories.length > 0) {
    selectedClanConfigCategory = categories[0];
  }
  var html = "";
  for (var c = 0; c < categories.length; c++) {
    var count = 0;
    for (var r = 1; r < clanConfigRows.length; r++) if (clanConfigRows[r][1] == categories[c]) count++;
    var active = categories[c] == selectedClanConfigCategory;
    html += '<button type="button" id="clanConfigGroup_' + c + '" class="clan-config-group' + (active ? ' active' : '') + '" aria-pressed="' + active + '" onclick="SelectClanConfigCategory(' + c + ')" title="' + HtmlAttr(categories[c]) + ' (' + count + ' cấu hình)">' + Html(categories[c]) + '<small>' + count + '</small></button>';
  }
  container.innerHTML = html;
  container.scrollTop = scrollTop;
  if (focused && focused.indexOf("clanConfigGroup_") == 0 && document.getElementById(focused)) document.getElementById(focused).focus();
  LayoutClanConfig();
}

function SelectClanConfigCategory(index) {
  var cats = ClanConfigCategories();
  selectedClanConfigCategory = cats[index] || (cats.length > 0 ? cats[0] : "");
  Set("clanConfigSearch", "");
  document.getElementById("clanConfigTable").scrollTop = 0;
  FilterClanConfigRows();
  if (filteredClanConfigRows.length > 1) PickClanConfigByKey(filteredClanConfigRows[1][0]);
  LayoutClanConfig();
}

function LoadClanConfig(keepDrafts, feedback) {
  if (clanConfigBusy) return;
  if (!keepDrafts && ClanConfigDraftCount() && !window.confirm("Tải lại sẽ bỏ " + ClanConfigDraftCount() + " mục chưa lưu. Tiếp tục?")) return;
  clanConfigBusy = true;
  var text;
  try { text = RunAdmin("listclanconfig", {}); }
  catch (e) { text = "ERROR\tKhông thể chạy lệnh tải cấu hình bang."; }
  clanConfigBusy = false;
  if (IsAdminError(text) || !text || text.indexOf("key\tcategory\t") < 0) {
    Msg("clanConfigMessage", (feedback ? feedback + " " : "") + "Không tải được cấu hình; dữ liệu đang sửa vẫn được giữ. Hãy thử Tải lại.");
    return;
  }
  if (!keepDrafts) clanConfigDrafts = {};
  clanConfigRows = ParseTsv(text);
  if (!selectedClanConfigCategory && !selectedClanConfigKey) selectedClanConfigCategory = clanConfigRows.length > 1 ? clanConfigRows[1][1] : "";
  FilterClanConfigRows();
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  if (!row && filteredClanConfigRows.length > 1) row = filteredClanConfigRows[1];
  selectedClanConfigKey = row ? row[0] : "";
  RenderClanConfigEditor();
  FilterClanConfigRows();
  Msg("clanConfigMessage", feedback || "Đã tải cấu hình từ file. Giá trị này chưa phản ánh trạng thái đã nạp trong server đang chạy.");
  LayoutClanConfig();
}

function ClanConfigValueSummary(row, value) {
  if (row[5] == "shop-item") {
    var parts = value.split(",");
    return "Tầng " + parts[0] + " · Kho " + parts[1] + " · Giá " + FormatNumber(parts[4] || "") + " Capsule";
  }
  if (row[5] == "int-list") return value.split(",").length + " mốc nâng cấp · ngày";
  return FormatAdminValue(value, row[5], row[0]);
}

function FilterClanConfigRows() {
  var list = document.getElementById("clanConfigTable");
  var scrollTop = list.scrollTop;
  var focused = document.activeElement ? document.activeElement.id : "";
  var categories = ClanConfigCategories();
  if ((!selectedClanConfigCategory || !ArrayContains(categories, selectedClanConfigCategory)) && categories.length > 0) {
    selectedClanConfigCategory = categories[0];
  }
  filteredClanConfigRows = [clanConfigRows[0] || []];
  for (var i = 1; i < clanConfigRows.length; i++) {
    var row = clanConfigRows[i];
    if (row[1] == selectedClanConfigCategory) filteredClanConfigRows.push(row);
  }
  var html = "";
  for (var r = 1; r < filteredClanConfigRows.length; r++) {
    var item = filteredClanConfigRows[r];
    var active = item[0] == selectedClanConfigKey;
    html += '<button type="button" id="clanConfigRow_' + r + '" class="clan-config-item' + (active ? ' selected' : '') + '" aria-pressed="' + active + '" onclick="PickFilteredClanConfig(' + r + ')"><strong>' + Html(item[2]) + '</strong><small>' + Html(item[1]) + '</small><span>' + Html(ClanConfigValueSummary(item, item[3])) + '</span><em id="clanConfigDirty_' + r + '">' + (clanConfigDrafts.hasOwnProperty(item[0]) ? 'Chưa lưu' : '') + '</em></button>';
  }
  if (filteredClanConfigRows.length < 2) html = '<div class="clan-config-empty">Không có cấu hình trong nhóm này.</div>';
  list.innerHTML = html;
  list.scrollTop = scrollTop;
  ClanConfigText("clanConfigResultCount", (filteredClanConfigRows.length - 1) + " MỤC");
  RenderClanConfigTabs();
  UpdateClanConfigEditState();
  if (focused && focused.indexOf("clanConfigRow_") == 0 && document.getElementById(focused)) document.getElementById(focused).focus();
}

function PickFilteredClanConfig(index) {
  if (filteredClanConfigRows[index]) PickClanConfigByKey(filteredClanConfigRows[index][0]);
}

function PickClanConfigByKey(key) {
  if (!FindConfigRow(clanConfigRows, key)) return;
  selectedClanConfigKey = key;
  RenderClanConfigEditor();
  FilterClanConfigRows();
}

function ClanConfigFieldLabels(row) {
  if (row[5] == "shop-item") return ["Tầng hàng (1–3)", "Trần tồn kho (vật phẩm)", "Phí nhập / món (Capsule bang)", "Phí nhập / món (Vàng bang)", "Giá mua (Capsule cá nhân)", "Cống hiến tối thiểu", "Giới hạn mua / ngày"];
  var labels = [];
  if (row[5] == "int-list") {
    for (var i = 0; i < parseInt(row[12], 10); i++) labels.push("Cấp " + (i + 2) + " · ngày");
  }
  return labels;
}

// Accept Vietnamese thousands grouping, but never turn an accidental 1.5 into 15.
function ClanConfigNormalizeValue(value, kind) {
  var text = Trim(value);
  if (kind == "int-list" || kind == "shop-item") {
    var parts = text.split(",");
    for (var i = 0; i < parts.length; i++) parts[i] = ClanConfigNormalizeValue(parts[i], "int");
    return parts.join(",");
  }
  if (IsIntegerConfigKind(kind) && /^\d{1,3}(\.\d{3})+$/.test(text)) return text.replace(/\./g, "");
  if (kind == "bool") return text == "1" ? "true" : (text == "0" ? "false" : text.toLowerCase());
  if (kind == "hex-color" && /^0x[0-9a-f]{6}$/i.test(text)) return "0x" + text.substring(2).toUpperCase();
  return text;
}

// Decimal strings retain all 64-bit digits; JavaScript Number cannot represent Long.MAX_VALUE.
function ClanConfigCompareInteger(left, right) {
  left = left.replace(/^0+(?=\d)/, "");
  right = right.replace(/^0+(?=\d)/, "");
  if (left.length != right.length) return left.length < right.length ? -1 : 1;
  return left == right ? 0 : (left < right ? -1 : 1);
}

function ClanConfigValidateValue(row, value) {
  var kind = row[5];
  if (/[\x00-\x1F\x7F]/.test(value)) return "Không được chứa ký tự điều khiển hoặc xuống dòng.";
  if (kind == "bool") return /^(true|false)$/.test(value) ? "" : "Chọn Bật hoặc Tắt.";
  if (kind == "java-text") return value.length > 0 && value.length <= 64 ? "" : "Nhập từ 1 đến 64 ký tự.";
  if (kind == "hex-color") return /^0x[0-9A-Fa-f]{6}$/.test(value) ? "" : "Nhập màu theo dạng 0xRRGGBB, ví dụ 0xFFE082.";
  if (kind == "resource-prefix") return /^[a-z0-9_]{1,32}$/.test(value) ? "" : "Chỉ nhập 1–32 ký tự a-z, 0-9 và gạch dưới; không nhập đường dẫn.";
  if (kind == "decimal") {
    if (!/^\d*\.?\d+$/.test(value)) return "Dùng dấu chấm cho số thập phân, ví dụ 1.40.";
    return Number(value) >= Number(row[10]) && Number(value) <= Number(row[11]) ? "" : "Giá trị phải từ " + row[10] + " đến " + row[11] + ".";
  }
  var parts = kind == "shop-item" || kind == "int-list" ? value.split(",") : [value];
  var expected = kind == "shop-item" || kind == "int-list" ? parseInt(row[12], 10) : 1;
  if (parts.length != expected) return "Cần đúng " + expected + " giá trị.";
  var labels = ClanConfigFieldLabels(row);
  var shopMin = ["1", "1", "0", "0", "1", "0", "1"];
  var shopMax = ["3", "1000000", "2147483647", "9223372036854775807", "2147483647", "9223372036854775807", "2147483647"];
  for (var p = 0; p < parts.length; p++) {
    var name = labels[p] || "Giá trị";
    var min = kind == "shop-item" ? shopMin[p] : row[10];
    var max = kind == "shop-item" ? shopMax[p] : row[11];
    if (!/^\d+$/.test(parts[p])) return name + ": nhập số nguyên không âm (không dùng số thập phân).";
    if (ClanConfigCompareInteger(parts[p], min) < 0 || ClanConfigCompareInteger(parts[p], max) > 0) return name + ": phải từ " + FormatNumber(min) + " đến " + FormatNumber(max) + ".";
  }
  return "";
}

function ClanToHex2(n) {
  var s = Number(n).toString(16).toUpperCase();
  return s.length < 2 ? "0" + s : s;
}

function SyncClanColorPickerFromValue(hex) {
  if (!/^0x[0-9A-Fa-f]{6}$/.test(hex)) return;
  var r = parseInt(hex.substring(2, 4), 16);
  var g = parseInt(hex.substring(4, 6), 16);
  var b = parseInt(hex.substring(6, 8), 16);
  Set("clanColorSliderR", r);
  Set("clanColorSliderG", g);
  Set("clanColorSliderB", b);
  Set("clanColorNumR", r);
  Set("clanColorNumG", g);
  Set("clanColorNumB", b);
  var formatted = "0x" + hex.substring(2).toUpperCase();
  var badge = document.getElementById("clanColorHexBadge");
  if (badge) badge.innerText = formatted;
  var swatch = document.getElementById("clanColorActiveSwatch");
  if (swatch) swatch.style.backgroundColor = "#" + hex.substring(2);
}

function ClanColorSliderMoved() {
  var r = Math.min(255, Math.max(0, parseInt(V("clanColorSliderR"), 10) || 0));
  var g = Math.min(255, Math.max(0, parseInt(V("clanColorSliderG"), 10) || 0));
  var b = Math.min(255, Math.max(0, parseInt(V("clanColorSliderB"), 10) || 0));
  Set("clanColorNumR", r);
  Set("clanColorNumG", g);
  Set("clanColorNumB", b);
  var hex = "0x" + ClanToHex2(r) + ClanToHex2(g) + ClanToHex2(b);
  Set("clanConfigValue", hex);
  var badge = document.getElementById("clanColorHexBadge");
  if (badge) badge.innerText = hex;
  var swatch = document.getElementById("clanColorActiveSwatch");
  if (swatch) swatch.style.backgroundColor = "#" + hex.substring(2);
  ClanConfigEditorChanged();
}

function ClanColorNumChanged() {
  var r = Math.min(255, Math.max(0, parseInt(V("clanColorNumR"), 10) || 0));
  var g = Math.min(255, Math.max(0, parseInt(V("clanColorNumG"), 10) || 0));
  var b = Math.min(255, Math.max(0, parseInt(V("clanColorNumB"), 10) || 0));
  Set("clanColorSliderR", r);
  Set("clanColorSliderG", g);
  Set("clanColorSliderB", b);
  var hex = "0x" + ClanToHex2(r) + ClanToHex2(g) + ClanToHex2(b);
  Set("clanConfigValue", hex);
  var badge = document.getElementById("clanColorHexBadge");
  if (badge) badge.innerText = hex;
  var swatch = document.getElementById("clanColorActiveSwatch");
  if (swatch) swatch.style.backgroundColor = "#" + hex.substring(2);
  ClanConfigEditorChanged();
}

function PickClanPresetColor(hex) {
  Set("clanConfigValue", hex);
  SyncClanColorPickerFromValue(hex);
  ClanConfigEditorChanged();
}

function RenderClanConfigEditor() {
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  document.getElementById("clanConfigEmpty").style.display = row ? "none" : "block";
  document.getElementById("clanConfigDetail").style.display = row ? "block" : "none";
  document.getElementById("clanConfigEditorScroll").scrollTop = 0;
  clanConfigStructuredCount = 0;
  if (!row) { UpdateClanConfigEditState(); return; }
  ClanConfigText("clanConfigCategory", row[1]);
  ClanConfigText("clanConfigName", row[2]);
  ClanConfigText("clanConfigProperty", row[8]);
  ClanConfigText("clanConfigSaved", FormatAdminValue(row[3], row[5], row[0]));
  ClanConfigText("clanConfigDefault", FormatAdminValue(row[4], row[5], row[0]));
  ClanConfigText("clanConfigDescription", row[9]);
  ClanConfigText("clanConfigFile", "data/" + row[7]);
  ClanConfigText("clanConfigScope", ConfigScopeLabel(row[6]));
  var value = clanConfigDrafts.hasOwnProperty(row[0]) ? clanConfigDrafts[row[0]] : row[3];
  var labels = ClanConfigFieldLabels(row);
  var parts = value.split(",");
  var structured = labels.length > 0 && parts.length == labels.length;
  document.getElementById("clanConfigValue").style.display = structured || row[5] == "bool" ? "none" : "block";
  document.getElementById("clanConfigValueLabel").style.display = structured || row[5] == "bool" ? "none" : "block";
  Set("clanConfigValue", value);
  var html = "";
  if (structured) {
    clanConfigStructuredCount = labels.length;
    for (var i = 0; i < labels.length; i++) {
      html += '<div class="clan-config-field' + (row[5] == "int-list" ? ' schedule' : '') + '"><label for="clanConfigPart_' + i + '">' + Html(labels[i]) + '</label><input type="text" id="clanConfigPart_' + i + '" value="' + HtmlAttr(parts[i]) + '" onkeyup="ClanConfigEditorChanged()" onchange="ClanConfigEditorChanged()" aria-describedby="clanConfigError"></div>';
    }
  } else if (row[5] == "bool") {
    html = '<div class="clan-config-bool" role="group" aria-label="Bật hoặc tắt chức năng"><button id="clanConfigBoolOn" type="button" onclick="SetClanConfigBool(true)">Bật</button><button id="clanConfigBoolOff" type="button" onclick="SetClanConfigBool(false)">Tắt</button></div>';
  }
  document.getElementById("clanConfigFields").innerHTML = html;

  var pickerEl = document.getElementById("clanConfigColorPicker");
  if (pickerEl) {
    if (row[5] == "hex-color") {
      pickerEl.style.display = "block";
      SyncClanColorPickerFromValue(value);
    } else {
      pickerEl.style.display = "none";
    }
  }

  var range = row[5] == "resource-prefix" ? "Bộ ảnh phải tồn tại đủ x1–x4 trước khi lưu." : (row[10] !== "" && row[11] !== "" ? "Giới hạn: " + row[10] + " – " + row[11] + (structured ? " cho mỗi ô." : ".") : "Kiểu dữ liệu: " + ConfigKindLabel(row[5]));
  if (IsIntegerConfigKind(row[5])) range += " Có thể dùng dấu chấm phân cách hàng nghìn.";
  ClanConfigText("clanConfigRange", range);
  var schedule = row[0].indexOf("tree.upgrade_days_") == 0;
  document.getElementById("clanConfigScheduleHint").style.display = schedule ? "block" : "none";
  if (schedule) {
    var maximum = FindConfigRow(clanConfigRows, "tree.max_tree_level");
    ClanConfigText("clanConfigScheduleHint", "Trần cấp cây đã lưu: " + (maximum ? maximum[3] : "20") + ". Lịch đang chỉnh áp dụng cho trần cấp " + (labels.length + 1) + ". Các ô là thời gian của từng lần nâng, không phải thời gian cộng dồn.");
  }
  UpdateClanConfigPreview(row, value);
  ClanConfigText("clanConfigError", ClanConfigValidateValue(row, ClanConfigNormalizeValue(value, row[5])));
  UpdateClanConfigEditState();
}

function ClanConfigEditorValue(row) {
  if (clanConfigStructuredCount) {
    var parts = [];
    for (var i = 0; i < clanConfigStructuredCount; i++) parts.push(V("clanConfigPart_" + i));
    return ClanConfigNormalizeValue(parts.join(","), row[5]);
  }
  return ClanConfigNormalizeValue(V("clanConfigValue"), row[5]);
}

function ClanConfigEditorChanged() {
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  if (!row) return;
  var value = ClanConfigEditorValue(row);
  if (value == ClanConfigNormalizeValue(row[3], row[5])) delete clanConfigDrafts[row[0]];
  else clanConfigDrafts[row[0]] = value;
  ClanConfigText("clanConfigError", ClanConfigValidateValue(row, value));
  if (row[5] == "hex-color" && /^0x[0-9A-Fa-f]{6}$/.test(value)) {
    SyncClanColorPickerFromValue(value);
  }
  UpdateClanConfigPreview(row, value);
  UpdateClanConfigEditState();
}

function SetClanConfigBool(enabled) {
  Set("clanConfigValue", enabled ? "true" : "false");
  ClanConfigEditorChanged();
}

function UpdateClanConfigPreview(row, value) {
  if (row[5] == "bool") {
    var normalized = ClanConfigNormalizeValue(value, "bool");
    var on = document.getElementById("clanConfigBoolOn");
    var off = document.getElementById("clanConfigBoolOff");
    on.className = normalized == "true" ? "active" : "";
    off.className = normalized == "false" ? "active" : "";
    on.setAttribute("aria-pressed", normalized == "true" ? "true" : "false");
    off.setAttribute("aria-pressed", normalized == "false" ? "true" : "false");
  }
  var color = row[5] == "hex-color" && /^0x[0-9A-Fa-f]{6}$/.test(value);
  document.getElementById("clanConfigColorPreview").style.display = color ? "block" : "none";
  if (color) {
    var hexCss = "#" + value.substring(2);
    var dayEl = document.getElementById("clanConfigColorDay");
    var nightEl = document.getElementById("clanConfigColorNight");
    var noteEl = document.getElementById("clanConfigPreviewNote");
    dayEl.style.color = hexCss;
    nightEl.style.color = hexCss;
    if (row[0].indexOf("chat.") == 0) {
      var sampleText = "Mẫu tin nhắn chat bang";
      if (row[0] == "chat.leader_color") sampleText = "[Bang Chủ] SonGoku: Chào mừng toàn thể thành viên bang!";
      else if (row[0] == "chat.deputy_color") sampleText = "[Phó Bang] Vegeta: Anh em tập trung làm nhiệm vụ bang.";
      else if (row[0] == "chat.member_color") sampleText = "[Thành Viên] Gohan: Em vừa cống hiến 50.000 vàng.";
      else if (row[0] == "chat.tree_color") sampleText = "[Cây Bang] Cây Đậu Thần đã đạt cấp 10! Thu hoạch ngay.";
      else if (row[0] == "chat.notify_color") sampleText = "[Thông Báo] Bang hội đã được thăng lên cấp 5!";
      dayEl.innerText = sampleText;
      nightEl.innerText = sampleText;
      if (noteEl) noteEl.innerText = "Mẫu hiển thị trên nền sáng và nền tối trong khung chat bang.";
    } else {
      dayEl.innerText = "Mẫu màu chữ mốc ngoại hình (Ban ngày)";
      nightEl.innerText = "Mẫu màu chữ mốc ngoại hình (Ban đêm)";
      if (noteEl) noteEl.innerText = "Mẫu màu tham khảo trong game.";
    }
  }
}

function UpdateClanConfigEditState() {
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  var dirty = row && clanConfigDrafts.hasOwnProperty(row[0]);
  document.getElementById("clanConfigSave").disabled = !dirty || clanConfigBusy;
  document.getElementById("clanConfigDiscard").disabled = !dirty || clanConfigBusy;
  document.getElementById("clanConfigReset").disabled = !row || clanConfigBusy;
  ClanConfigText("clanConfigEditState", !row ? "Chưa chọn cấu hình" : (dirty ? "Có thay đổi chưa lưu · chỉ lưu mục đang chọn" : "Chưa có thay đổi ở mục này"));
  ClanConfigText("clanConfigSummary", Math.max(0, clanConfigRows.length - 1) + " cấu hình · " + ClanConfigCategories().length + " nhóm · " + ClanConfigDraftCount() + " mục chưa lưu");
  for (var i = 1; i < filteredClanConfigRows.length; i++) {
    var badge = document.getElementById("clanConfigDirty_" + i);
    if (badge) badge.innerText = clanConfigDrafts.hasOwnProperty(filteredClanConfigRows[i][0]) ? "Chưa lưu" : "";
  }
}

function DiscardClanConfig() {
  if (!selectedClanConfigKey || clanConfigBusy) return;
  delete clanConfigDrafts[selectedClanConfigKey];
  RenderClanConfigEditor();
  UpdateClanConfigEditState();
  Msg("clanConfigMessage", "Đã hủy phần sửa của mục đang chọn; không thay đổi file.");
}

function SaveClanConfig() {
  if (clanConfigBusy) return;
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  if (!row) return;
  ClanConfigEditorChanged();
  var value = ClanConfigEditorValue(row);
  var error = ClanConfigValidateValue(row, value);
  if (error) {
    ClanConfigText("clanConfigError", error);
    Msg("clanConfigMessage", "Chưa lưu: " + error);
    document.getElementById(clanConfigStructuredCount ? "clanConfigPart_0" : "clanConfigValue").focus();
    return;
  }
  if (!clanConfigDrafts.hasOwnProperty(row[0])) return;
  WriteClanConfigAction("saveclanconfig", { ConfigKey: row[0], ConfigValue: value });
}

function ResetClanConfig() {
  if (clanConfigBusy) return;
  var row = FindConfigRow(clanConfigRows, selectedClanConfigKey);
  if (!row || !window.confirm("Đưa “" + row[2] + "” về mặc định server?\n\n" + row[4] + "\n\nThao tác này ghi ngay vào file và bỏ phần sửa của mục này.")) return;
  WriteClanConfigAction("resetclanconfig", { ConfigKey: row[0] });
}

function WriteClanConfigAction(action, params) {
  clanConfigBusy = true;
  UpdateClanConfigEditState();
  var text;
  try { text = RunAdmin(action, params); }
  catch (e) { text = "ERROR\tKhông thể ghi cấu hình. Hãy kiểm tra quyền truy cập file."; }
  clanConfigBusy = false;
  if (text && text.indexOf("OK\t") == 0) {
    delete clanConfigDrafts[params.ConfigKey];
    LoadClanConfig(true, StatusText(text));
  } else {
    ClanConfigText("clanConfigError", StatusText(text || "ERROR\tKhông nhận được kết quả; tải lại để kiểm tra trước khi thử lưu tiếp."));
    Msg("clanConfigMessage", StatusText(text || "ERROR\tKhông nhận được kết quả."));
  }
  UpdateClanConfigEditState();
}

function LayoutClanConfig() {
  var header = document.getElementById("clanConfigHeader");
  var workspace = document.getElementById("clanConfigWorkspace");
  if (!workspace) return;
  var top = 175;
  if (header) {
    top = header.offsetTop + header.offsetHeight + 8;
  }
  workspace.style.top = top + "px";
}

RegisterTab({
  id: "claneconomy", view: "clan-economy.html", panelId: "panelClanEconomy", navId: "navClanEconomy",
  title: "Cấu hình bang", subtitle: "Chức năng, Cây bang, cửa hàng, quà, buff và ngoại hình",
  onOpen: function () { LoadClanConfig(); },
  onRefresh: function () { LoadClanConfig(); },
  onLayout: function () { LayoutClanConfig(); }
});
