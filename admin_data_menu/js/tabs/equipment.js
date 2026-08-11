var currentEquipGender = "";
var equipRows = [];
var equipOptionRows = [];

function SetEquipGender(gender) {
  currentEquipGender = gender;
  var ids = ["All", "0", "1", "2", "3"];
  for (var i = 0; i < ids.length; i++) {
    var suffix = ids[i];
    var el = document.getElementById("equipGender" + suffix);
    if (!el) continue;
    var active = (gender == "" && suffix == "All") || gender == suffix;
    el.className = active ? "seg-button active" : "seg-button";
  }
  LoadEquipItems();
}

function LoadEquipItems() {
  equipRows = ParseTsv(RunAdmin("listequipmentshopitems", {
    Gender: currentEquipGender,
    Type: V("equipTypeFilter"),
    Search: V("equipSearch")
  }));
  RenderTableMapped("equipItemsTable", equipRows, ["Row", "Item", "Tên", "Type", "Gender", "Tab", "Tên tab", "Shop", "NPC", "Tiền", "Giá", "Opt"], "PickEquipItem", function (col, value, row) {
    if (col == 3) return ItemTypeLabel(value);
    if (col == 4) return GenderLabel(value);
    if (col == 9) return MoneyLabel(value);
    return value;
  });
  Msg("equipMessage", equipRows.length > 1 ? "Đã tải " + (equipRows.length - 1) + " trang bị shop." : "Không có trang bị phù hợp.");
  AdjustTableOffsets();
  AdjustTableOffsetsLater();
}

function PickEquipItem(index) {
  var r = equipRows[index];
  Set("equipShopItemId", r[0]);
  Set("equipTempId", r[1]);
  Set("equipItemName", r[2]);
  Set("equipTypeLabel", ItemTypeLabel(r[3]));
  Set("equipGenderLabel", GenderLabel(r[4]));
  Set("equipShopLabel", "Shop " + r[7] + " / Tab " + r[5] + " - " + (r[6] || "") + " / NPC " + (r[8] || ""));
  NewEquipOption();
  LoadEquipOptions();
}

function LoadEquipOptions() {
  if (!V("equipShopItemId")) { Msg("equipMessage", "Chọn một trang bị shop trước."); return; }
  equipOptionRows = ParseTsv(RunAdmin("listshopoptions", { Id: V("equipShopItemId") }));
  RenderTableMapped("equipOptionsTable", equipOptionRows, ["ID", "Item shop", "Option", "Tên", "Param"], "PickEquipOption", function (col, value, row) {
    if (col == 2) return value + " - " + (row[3] || "");
    if (col == 3) return row[3] || "";
    return value;
  });
}

function PickEquipOption(index) {
  Set("equipOptionRowId", equipOptionRows[index][0]);
  Set("equipOptionId", equipOptionRows[index][2]);
  Set("equipOptionParam", equipOptionRows[index][4]);
  Set("equipOptionSearch", "");
  RenderEquipOptionPicker();
}

function NewEquipOption() {
  Set("equipOptionRowId", "");
  Set("equipOptionId", "");
  Set("equipOptionParam", "0");
  Set("equipOptionSearch", "");
  RenderEquipOptionPicker();
}

function RenderEquipOptionPicker() {
  if (!document.getElementById("equipOptionList") || !optionRows || optionRows.length < 2) return;
  var selectedIds = V("equipOptionId") ? [V("equipOptionId")] : [];
  RenderOptionPicker("equipOptionList", "equipOptionSearch", selectedIds, "ToggleEquipOption", "equipPicker");
  document.getElementById("equipOptionSummary").innerText = selectedIds.length
    ? "Đang chọn: " + selectedIds[0] + " - " + OptionName(selectedIds[0])
    : "Chưa chọn option";
}

function ToggleEquipOption(id, checked) {
  if (checked) {
    Set("equipOptionId", id);
  } else if (V("equipOptionId") == id) {
    Set("equipOptionId", "");
  }
  RenderEquipOptionPicker();
}

function SaveEquipOption() {
  if (!V("equipShopItemId")) { Msg("equipMessage", "Chọn một trang bị shop trước."); return; }
  if (!V("equipOptionId")) { Msg("equipMessage", "Chọn Option ID."); return; }
  var text = RunAdmin("saveshopoption", {
    Id: V("equipOptionRowId"),
    TempId: V("equipShopItemId"),
    OptionId: V("equipOptionId"),
    Param: V("equipOptionParam")
  });
  Msg("equipMessage", StatusText(text));
  LoadEquipOptions();
  LoadEquipItems();
}

function DeleteEquipOption() {
  if (!V("equipOptionRowId")) return;
  if (!window.confirm("Xóa option ID " + V("equipOptionRowId") + "?")) return;
  Msg("equipMessage", StatusText(RunAdmin("deleteshopoption", { Id: V("equipOptionRowId") })));
  LoadEquipOptions();
  LoadEquipItems();
}

function RenderEquipPresets() {
  var rows = [
    ["Option", "Tên", "Gợi ý param"],
    ["0", "Tấn công +#", "1000"],
    ["2", "HP, KI +#000", "20000 = 20 triệu"],
    ["6", "HP +#", "20000000"],
    ["7", "KI +#", "20000000"],
    ["47", "Giáp +#", "100"],
    ["48", "HP, KI +#", "20000000"],
    ["50", "Sức đánh +#%", "20"],
    ["77", "HP +#%", "20"],
    ["94", "Giáp #%", "20"],
    ["103", "KI +#%", "20"],
    ["14", "Chí mạng +#%", "10"],
    ["108", "Né đòn #%", "10"],
    ["93", "Hạn sử dụng # ngày", "30"]
  ];
  RenderTable("equipPresetTable", rows, ["Option", "Tên", "Param"], "Noop");
}

RegisterTab({
  id: "equip", view: "equipment.html", panelId: "panelEquip", navId: "navEquip",
  title: "Chỉ số trang bị", subtitle: "Quản lý option của trang bị bán trong shop theo gender",
  onOpen: function () { LoadEquipItems(); },
  onRefresh: function () { LoadEquipItems(); }
});
