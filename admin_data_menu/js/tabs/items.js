var itemRows = [];

function LoadItems() {
  var text = RunAdmin("listitems", { Type: V("itemTypeFilter"), Search: V("itemSearch") });
  itemRows = ParseTsv(text);
  RenderTableMapped("itemsTable", itemRows, ["ID", "Type", "Gender", "Tên", "Mô tả", "Level", "Icon", "Part"], "PickItem", function (col, value) {
    if (col == 1) return ItemTypeLabel(value);
    if (col == 2) return GenderLabel(value);
    return value;
  });
  Msg("itemMessage", itemRows.length > 1 ? "Đã tải " + (itemRows.length - 1) + " vật phẩm." : text);
  AdjustTableOffsets();
}

function PickItem(index) {
  var r = itemRows[index];
  Set("itemId", r[0]); Set("itemType", r[1]); Set("itemGender", r[2]); Set("itemName", r[3]);
  Set("itemDesc", r[4]); Set("itemLevel", r[5]); Set("itemIcon", r[6]); Set("itemPart", r[7]);
  Set("itemUp", r[8]); Set("itemPower", r[9]); Set("itemGold", r[10]); Set("itemGem", r[11]);
  Set("itemHead", r[12]); Set("itemBody", r[13]); Set("itemLeg", r[14]);
  AdjustTableOffsets();
}

function NewItem() {
  Set("itemId", ""); Set("itemType", V("itemTypeFilter") || "27"); Set("itemGender", "3"); Set("itemName", "");
  Set("itemDesc", ""); Set("itemLevel", "0"); Set("itemIcon", "0"); Set("itemPart", "-1");
  Set("itemUp", "1"); Set("itemPower", "0"); Set("itemGold", "0"); Set("itemGem", "0");
  Set("itemHead", "-1"); Set("itemBody", "-1"); Set("itemLeg", "-1");
}

function SaveItem() {
  if (!V("itemId")) { Msg("itemMessage", "Cần nhập ID vật phẩm."); return; }
  var text = RunAdmin("saveitem", {
    Id: V("itemId"), Type: V("itemType"), Gender: V("itemGender"), Name: V("itemName"),
    Description: V("itemDesc"), Level: V("itemLevel"), IconId: V("itemIcon"), Part: V("itemPart"),
    IsUpToUp: V("itemUp"), PowerRequire: V("itemPower"), Gold: V("itemGold"), Gem: V("itemGem"),
    Head: V("itemHead"), Body: V("itemBody"), Leg: V("itemLeg")
  });
  Msg("itemMessage", StatusText(text));
  LoadItems();
  if (!IsAdminError(text)) LoadItemCatalog(true);
}

RegisterTab({
  id: "items", view: "items.html", panelId: "panelItems", navId: "navItems",
  title: "Vật phẩm", subtitle: "Lọc theo type, xem và sửa item template",
  onOpen: function () { LoadItems(); },
  onRefresh: function () { LoadItems(); }
});
