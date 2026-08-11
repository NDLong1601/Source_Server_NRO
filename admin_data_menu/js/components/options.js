var optionRows = [];

function LoadOptions() {
  optionRows = ParseTsv(RunAdmin("listoptions", {}));
  RenderOptionTable(optionRows);
  RenderEquipOptionPicker();
  RenderShopOptionPicker();
  RenderBossOptionChecklist();
  RenderRadarOptionPicker();
}

function FilterOptionRows() {
  var q = V("optionSearch").toLowerCase();
  var filtered = [optionRows[0]];
  for (var i = 1; i < optionRows.length; i++) {
    if ((optionRows[i].join(" ") || "").toLowerCase().indexOf(q) >= 0) filtered.push(optionRows[i]);
  }
  RenderOptionTable(filtered);
}

function RenderOptionTable(rows) {
  RenderTable("optionsTable", rows, ["ID", "Tên option"], "Noop");
}

function OptionName(optionId) {
  for (var i = 1; i < optionRows.length; i++) {
    if (optionRows[i][0] == optionId) return optionRows[i][1] || "Option chưa có tên";
  }
  return "Option chưa có tên";
}

function RenderOptionPicker(listId, searchId, selectedIds, toggleFunction, idPrefix) {
  var selected = {};
  for (var i = 0; i < selectedIds.length; i++) selected[selectedIds[i]] = true;
  RenderOptionChecklist({
    listId: listId,
    idPrefix: idPrefix,
    query: V(searchId),
    selected: selected,
    selectedOrder: selectedIds,
    labelResolver: OptionName,
    toggleFunction: toggleFunction,
    emptyText: "Không tìm thấy option phù hợp."
  });
}

function RenderOptionChecklist(config) {
  config.rows = optionRows;
  config.rowStart = 1;
  config.labelResolver = config.labelResolver || OptionName;
  config.emptyText = config.emptyText || "Không tìm thấy option phù hợp.";
  RenderChecklist(config);
}

// Renderer checklist dùng chung cho Combine, trang bị, Giftcode và các tab thêm sau này.
// Cấu hình chính: listId, rows, query, selected, selectedOrder, toggleFunction, limit.
// Bật showParam và truyền paramValues/paramFunction khi mỗi mục cần một ô Param.
