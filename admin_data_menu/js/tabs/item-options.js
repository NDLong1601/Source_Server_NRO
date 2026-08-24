var defaultOptionItemRows = [];
var defaultOptionValues = [];
var defaultOptionSelectedId = "";

function LoadDefaultOptionItems() {
  defaultOptionItemRows = ParseTsv(RunAdmin("listitemdefaultoptionitems", { Search: V("defaultOptionItemSearch") }));
  RenderTable("defaultOptionItemsTable", defaultOptionItemRows, ["ID", "Tên", "Mô tả", "Option", "Shop"], "PickDefaultOptionItem");
}

function PickDefaultOptionItem(index) {
  var row = defaultOptionItemRows[index];
  if (!row) return;
  defaultOptionSelectedId = row[0];
  Set("defaultOptionItemId", row[0]);
  Set("defaultOptionItemName", row[1]);
  Set("defaultOptionCount", row[3] || "0");
  Set("defaultOptionShopCount", row[4] || "0");
  LoadDefaultOptions();
}

function LoadDefaultOptions() {
  if (!defaultOptionSelectedId) {
    defaultOptionValues = [];
    RenderDefaultOptionPicker();
    return;
  }
  var rows = ParseTsv(RunAdmin("listitemdefaultoptions", { Id: defaultOptionSelectedId }));
  defaultOptionValues = [];
  for (var i = 1; i < rows.length; i++) defaultOptionValues.push({ id: rows[i][2], param: rows[i][4] });
  RenderDefaultOptionPicker();
}

function FindDefaultOption(id) {
  for (var i = 0; i < defaultOptionValues.length; i++) if ("" + defaultOptionValues[i].id == "" + id) return defaultOptionValues[i];
  return null;
}

function RenderDefaultOptionPicker() {
  if (!document.getElementById("defaultOptionList")) return;
  var selected = {}, order = {}, params = {};
  var selectedOrder = [];
  for (var i = 0; i < defaultOptionValues.length; i++) {
    var id = "" + defaultOptionValues[i].id;
    selected[id] = true; selectedOrder.push(id); params[id] = defaultOptionValues[i].param;
  }
  RenderOptionChecklist({
    listId: "defaultOptionList", idPrefix: "defaultOption", query: V("defaultOptionSearch"),
    selected: selected, selectedOrder: selectedOrder, paramValues: params,
    toggleFunction: "ToggleDefaultOption", labelResolver: OptionName, limit: 180,
    showParam: true, paramFunction: "SetDefaultOptionParam",
    itemClass: "param-option-row", selectedClass: "param-option-selected", paramClass: "param-option-input"
  });
  Set("defaultOptionCount", "" + defaultOptionValues.length);
}

function ToggleDefaultOption(id, checked) {
  var option = FindDefaultOption(id);
  if (checked && !option) defaultOptionValues.push({ id: "" + id, param: "0" });
  if (!checked && option) {
    var next = [];
    for (var i = 0; i < defaultOptionValues.length; i++) if ("" + defaultOptionValues[i].id != "" + id) next.push(defaultOptionValues[i]);
    defaultOptionValues = next;
  }
  RenderDefaultOptionPicker();
}

function SetDefaultOptionParam(id, value) {
  var option = FindDefaultOption(id);
  if (option) option.param = value;
}

function SaveDefaultOptions() {
  if (!defaultOptionSelectedId) { Msg("defaultOptionMessage", "Chọn vật phẩm trước khi lưu."); return; }
  var payload = [];
  for (var i = 0; i < defaultOptionValues.length; i++) payload.push({ id: defaultOptionValues[i].id, param: defaultOptionValues[i].param });
  var text = RunAdmin("saveitemdefaultoptions", { Id: defaultOptionSelectedId, PayloadJson: JSON.stringify(payload) });
  Msg("defaultOptionMessage", StatusText(text));
  if (!IsAdminError(text)) { LoadDefaultOptionItems(); LoadDefaultOptions(); }
}

RegisterTab({
  id: "itemoptions", view: "item-options.html", panelId: "panelItemOptions", navId: "navItemOptions",
  title: "Option mặc định", subtitle: "Cấu hình option dùng chung cho vật phẩm, SHOP và nhiệm vụ",
  onOpen: function () { LoadOptions(false); LoadDefaultOptionItems(); RenderDefaultOptionPicker(); },
  onRefresh: function () { LoadDefaultOptionItems(); if (defaultOptionSelectedId) LoadDefaultOptions(); }
});
