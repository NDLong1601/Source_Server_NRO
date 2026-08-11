function RenderChecklist(config) {
  var target = document.getElementById(config.listId);
  if (!target) return;
  var rows = config.rows || [];
  var rowStart = config.rowStart == null ? 0 : config.rowStart;
  var idIndex = config.idIndex == null ? 0 : config.idIndex;
  var labelIndex = config.labelIndex == null ? 1 : config.labelIndex;
  var selected = config.selected || {};
  var selectedOrder = config.selectedOrder || [];
  var selectedLabels = config.selectedLabels || {};
  var paramValues = config.paramValues || {};
  var query = (config.query || "").toLowerCase();
  var limit = config.limit || 0;
  var html = "";
  var rendered = {};
  var visible = 0;

  for (var s = 0; s < selectedOrder.length; s++) {
    var selectedId = "" + selectedOrder[s];
    var selectedLabel = selectedLabels[selectedId];
    if (!selectedLabel && config.labelResolver) selectedLabel = config.labelResolver(selectedId);
    if (!selectedLabel) selectedLabel = ChecklistLabelFromRows(rows, rowStart, idIndex, labelIndex, selectedId);
    html += ChecklistItemHtml(config, selectedId, selectedLabel || "Chưa có tên", true, paramValues[selectedId]);
    rendered[selectedId] = true;
  }

  for (var r = rowStart; r < rows.length; r++) {
    var id = "" + (rows[r][idIndex] == null ? "" : rows[r][idIndex]);
    var name = rows[r][labelIndex] || "Chưa có tên";
    if (!id || rendered[id]) continue;
    var searchText = id + " " + name;
    var searchIndexes = config.searchIndexes || [];
    for (var x = 0; x < searchIndexes.length; x++) searchText += " " + (rows[r][searchIndexes[x]] || "");
    if (query && searchText.toLowerCase().indexOf(query) < 0) continue;
    if (limit && visible >= limit) break;
    if (config.labelResolver) name = config.labelResolver(id, rows[r]);
    html += ChecklistItemHtml(config, id, name, !!selected[id], paramValues[id]);
    rendered[id] = true;
    visible++;
  }
  target.innerHTML = html || (config.emptyText || "Không tìm thấy dữ liệu phù hợp.");
}

function ChecklistLabelFromRows(rows, rowStart, idIndex, labelIndex, wantedId) {
  for (var i = rowStart; i < rows.length; i++) {
    if (("" + rows[i][idIndex]) == wantedId) return rows[i][labelIndex] || "Chưa có tên";
  }
  return "";
}

function ChecklistItemHtml(config, id, name, checked, param) {
  var safeId = ("" + id).replace(/[^a-zA-Z0-9_-]/g, "_").replace(/-/g, "neg");
  var checkboxId = (config.idPrefix || config.listId) + "_" + safeId;
  var selectedClass = config.selectedClass == null ? "checklist-item-selected" : config.selectedClass;
  var itemClass = "option-picker-item" + (config.itemClass ? " " + config.itemClass : "") +
    (checked && selectedClass ? " " + selectedClass : "") +
    (checked && config.showParam ? " checklist-item-with-param" : "");
  var displayText = config.showId === false ? name : id + " - " + name;
  var html = '<div class="' + itemClass + '"><input type="checkbox" id="' + HtmlAttr(checkboxId) + '" value="' + HtmlAttr(id) + '"' +
    (checked ? ' checked="checked"' : '') + ' onclick="' + config.toggleFunction + '(\'' + EscapeJs(id) + '\', this.checked)">' +
    '<label for="' + HtmlAttr(checkboxId) + '">' + Html(displayText) + '</label>';
  if (config.showParam && checked) {
    html += '<input class="' + (config.paramClass || "checklist-param-input") + '" value="' + HtmlAttr(param == null ? 0 : param) +
      '" title="Param" onchange="' + config.paramFunction + '(\'' + EscapeJs(id) + '\', this.value)">';
  }
  return html + "</div>";
}
