var itemCatalogRows = [];
var itemCatalogById = {};

function ItemTypeBaseItems() {
  return [
    ["0", "0 - Áo"],
    ["1", "1 - Quần"],
    ["2", "2 - Găng"],
    ["3", "3 - Giày"],
    ["4", "4 - Rada"],
    ["5", "5 - Cải trang / PET hiển thị"],
    ["6", "6 - Đậu / tiêu hao"],
    ["7", "7 - Sách / vật phẩm"],
    ["8", "8 - Nhóm item 8"],
    ["9", "9 - Nhóm item 9"],
    ["10", "10 - Nhóm item 10"],
    ["11", "11 - Cờ / túi / phụ kiện"],
    ["12", "12 - Nhóm item 12"],
    ["13", "13 - Nhóm item 13"],
    ["14", "14 - Nhóm item 14"],
    ["15", "15 - Nhóm item 15"],
    ["16", "16 - Nhóm item 16"],
    ["17", "17 - Nhóm item 17"],
    ["22", "22 - Nhóm item 22"],
    ["23", "23 - Thú cưỡi"],
    ["24", "24 - Linh thú"],
    ["25", "25 - Đệ tử / phụ kiện"],
    ["27", "27 - Pet đeo lưng / follow"],
    ["28", "28 - Nhóm item 28"],
    ["29", "29 - Nhóm item 29"],
    ["30", "30 - Nhóm item 30"],
    ["31", "31 - Nhóm item 31"],
    ["32", "32 - Giáp tập luyện"],
    ["33", "33 - Nhóm item 33"],
    ["34", "34 - Nhóm item 34"],
    ["36", "36 - Nhóm item 36"],
    ["37", "37 - Nhóm item 37"],
    ["75", "75 - Nhóm item 75"]
  ];
}

function ItemTypeBaseLabel(value) {
  var items = ItemTypeBaseItems();
  for (var i = 0; i < items.length; i++) {
    if (items[i][0] == value) return items[i][1];
  }
  return value == "" ? "Tất cả group" : value + " - Nhóm item " + value;
}

function ItemTypeLabel(value) {
  return ComboLabel("itemType", value, ItemTypeBaseLabel(value));
}

function GenderLabel(value) {
  return ComboLabel("itemGender", value, value);
}

function UpToUpLabel(value) {
  return ComboLabel("itemUp", value, value);
}

function LoadItemTypes() {
  var rows = ParseTsv(RunAdmin("listitemtypes", {}));
  if (rows.length <= 1) return;

  var filterTypes = [["", "Tất cả group"]];
  var detailTypes = [];
  for (var i = 1; i < rows.length; i++) {
    var id = rows[i][0] || "";
    if (id == "") continue;
    var count = rows[i][1] || "0";
    var label = ItemTypeBaseLabel(id);
    filterTypes.push([id, label + " (" + count + ")"]);
    detailTypes.push([id, label]);
  }
  SetComboItems("itemTypeFilter", filterTypes);
  SetComboItems("itemType", detailTypes);
  SetComboItems("equipTypeFilter", filterTypes);
}

function LoadItemCatalog(force) {
  if (!force && itemCatalogRows.length > 1) return;
  var rows = ParseTsv(RunAdmin("listitemcatalog", {}));
  if (!rows.length || rows[0][0] != "id") return;
  itemCatalogRows = rows;
  itemCatalogById = {};
  for (var i = 1; i < rows.length; i++) {
    itemCatalogById[rows[i][0]] = {
      id: rows[i][0], name: rows[i][1] || ("Vật phẩm " + rows[i][0]),
      description: rows[i][2] || "", type: rows[i][3] || "", gender: rows[i][4] || ""
    };
  }
  SyncGiftItemCatalog();
  RenderShopItemPicker();
}

function ItemCatalogName(id) {
  id = "" + id;
  return itemCatalogById[id] ? itemCatalogById[id].name : "Vật phẩm " + id;
}

function ItemCatalogLabel(id) {
  id = "" + id;
  var item = itemCatalogById[id];
  if (!item) return "Vật phẩm " + id;
  return item.name + (item.description ? " — " + item.description : "");
}

function RenderItemPicker(config) {
  config.rows = config.rows || itemCatalogRows;
  config.rowStart = config.rowStart == null ? 1 : config.rowStart;
  config.labelResolver = config.labelResolver || ItemCatalogLabel;
  config.searchIndexes = config.searchIndexes || [1, 2, 3, 4];
  config.emptyText = config.emptyText || "Không tìm thấy vật phẩm phù hợp.";
  RenderChecklist(config);
}
