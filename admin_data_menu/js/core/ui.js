var comboData = {};

function RenderTable(tableId, rows, cols, clickFn) {
  var html = "<thead><tr>";
  for (var i = 0; i < cols.length; i++) html += "<th>" + cols[i] + "</th>";
  html += "</tr></thead><tbody>";
  for (var r = 1; r < rows.length; r++) {
    html += '<tr onclick="' + clickFn + '(' + r + ')">';
    for (var c = 0; c < cols.length; c++) {
      html += "<td>" + Html(rows[r][c] || "") + "</td>";
    }
    html += "</tr>";
  }
  html += "</tbody>";
  document.getElementById(tableId).innerHTML = html;
}


function RenderTableMapped(tableId, rows, cols, clickFn, mapper) {
  var html = "<thead><tr>";
  for (var i = 0; i < cols.length; i++) html += "<th>" + cols[i] + "</th>";
  html += "</tr></thead><tbody>";
  for (var r = 1; r < rows.length; r++) {
    html += '<tr onclick="' + clickFn + '(' + r + ')">';
    for (var c = 0; c < cols.length; c++) {
      var value = rows[r][c] || "";
      if (mapper) value = mapper(c, value, rows[r]);
      html += "<td>" + Html(value) + "</td>";
    }
    html += "</tr>";
  }
  html += "</tbody>";
  document.getElementById(tableId).innerHTML = html;
}


function ComboLabel(id, value, fallback) {
  var items = comboData[id] || [];
  for (var i = 0; i < items.length; i++) {
    if (items[i][0] == value) return items[i][1];
  }
  return fallback;
}


function SetComboItems(id, items) {
  comboData[id] = items;
  var menu = document.getElementById("combo_" + id + "_menu");
  if (!menu) return;
  var html = "";
  var current = V(id);
  for (var i = 0; i < items.length; i++) {
    var active = items[i][0] == current ? " active" : "";
    html += '<div class="combo-option' + active + '" onclick="ChooseCombo(\'' + id + '\', \'' + EscapeJs(items[i][0]) + '\'); event.cancelBubble = true;">' + Html(items[i][1]) + "</div>";
  }
  menu.innerHTML = html;
  SyncComboDisplay(id);
}


function ToggleCombo(id) {
  var combo = document.getElementById("combo_" + id);
  if (!combo) return;
  var isOpen = /(^|\s)open(\s|$)/.test(combo.className);
  var baseClass = ComboBaseClass(combo);
  CloseCombos(id);
  combo.className = isOpen ? baseClass : baseClass + " open";
}


function ChooseCombo(id, value) {
  Set(id, value);
  CloseCombos("");
  OnComboChanged(id);
}


function CloseCombos(exceptId) {
  var nodes = document.getElementsByTagName("div");
  for (var i = 0; i < nodes.length; i++) {
    var combo = nodes[i];
    if (combo.id.indexOf("combo_") !== 0 || (" " + combo.className + " ").indexOf(" combo ") < 0) continue;
    if (combo.id.substring(6) == exceptId) continue;
    combo.className = ComboBaseClass(combo);
  }
}


function ComboBaseClass(combo) {
  var names = (combo && combo.className ? combo.className : "combo").split(/\s+/);
  var kept = [];
  for (var i = 0; i < names.length; i++) {
    if (names[i] && names[i] != "open") kept.push(names[i]);
  }
  return kept.length ? kept.join(" ") : "combo";
}


function SyncComboDisplay(id) {
  var text = document.getElementById("combo_" + id + "_text");
  if (!text) return;
  var value = V(id);
  var items = comboData[id] || [];
  var label = value;
  for (var i = 0; i < items.length; i++) {
    if (items[i][0] == value) {
      label = items[i][1];
      break;
    }
  }
  if (label == "") label = "Chọn option";
  text.innerText = label;
}


function EscapeJs(value) {
  return ("" + value).replace(/\\/g, "\\\\").replace(/'/g, "\\'");
}

// Nguồn dữ liệu vật phẩm dùng chung cho Giftcode, Shop và các checklist khác.
// Tên/phụ đề luôn lấy từ item_template theo ID, không lưu bản sao ở từng màn hình.

function CheckedValue(id) { return document.getElementById(id).checked ? "1" : "0"; }


function Html(value) {
  return ("" + value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}


function HtmlAttr(value) {
  return Html(value).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}


function V(id) {
  return document.getElementById(id).value;
}


function Set(id, value) {
  document.getElementById(id).value = value == null ? "" : value;
  SyncComboDisplay(id);
}


function Msg(id, text) {
  var message = text == null ? "" : "" + text;
  var target = document.getElementById(id);
  if (target) target.innerText = message;
  ShowToast(message);
}

function ShowToast(text) {
  if (!Trim(text)) return;
  var container = document.getElementById("toastContainer");
  if (!container) {
    container = document.createElement("div");
    container.id = "toastContainer";
    container.className = "toast-container";
    document.body.appendChild(container);
  }

  var isError = /(^|\r?\n)\s*(ERROR|LỖI)\b/i.test(text);
  var isSuccess = !isError && /(^|\r?\n)\s*(OK|Đã|Đang|Cập nhật|Đang cấu hình|Đã chọn|Đã tải|Đã đọc|Đã nạp)/i.test(text);
  var type = isError ? "error" : (isSuccess ? "success" : "info");
  var toast = document.createElement("div");
  toast.className = "toast toast-" + type;

  var badge = document.createElement("span");
  badge.className = "toast-badge";
  badge.innerText = isError ? "LỖI" : (isSuccess ? "THÀNH CÔNG" : "THÔNG TIN");
  toast.appendChild(badge);

  var body = document.createElement("div");
  body.className = "toast-body";
  body.innerText = text;
  toast.appendChild(body);

  var close = document.createElement("button");
  close.type = "button";
  close.className = "toast-close";
  close.innerText = "×";
  close.onclick = function () {
    if (toast.parentNode) toast.parentNode.removeChild(toast);
  };
  toast.appendChild(close);

  container.appendChild(toast);
  while (container.childNodes.length > 5) container.removeChild(container.firstChild);
  window.setTimeout(function () {
    if (toast.parentNode) toast.parentNode.removeChild(toast);
  }, isError ? 6500 : 4500);
}


function Trim(value) {
  return ("" + value).replace(/^\s+|\s+$/g, "");
}


function Noop(index) {}
