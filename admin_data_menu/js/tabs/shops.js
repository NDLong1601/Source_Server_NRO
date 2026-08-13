var shopRows = [];
var tabRows = [];
var shopItemRows = [];
var shopSelectedItemIds = [];
var shopOptionRows = [];
var shopOptionValues = [];

function LoadShops() {
  shopRows = ParseTsv(RunAdmin("listshops", {}));
  RenderTable("shopsTable", shopRows, ["ID", "NPC", "Tên NPC", "Tag", "Type"], "PickShop");
  Msg("npcMessage", shopRows.length > 1 ? "Đã tải " + (shopRows.length - 1) + " shop." : "");
}

function LoadNpcs() {
  shopRows = ParseTsv(RunAdmin("listnpcs", {}));
  RenderTable("shopsTable", shopRows, ["NPC", "Tên", "Head", "Body", "Leg", "Avatar"], "PickNpc");
  Msg("npcMessage", shopRows.length > 1 ? "Đã tải " + (shopRows.length - 1) + " NPC." : "");
}

function PickShop(index) {
  var r = shopRows[index];
  Set("shopId", r[0]); Set("shopNpcId", r[1]); Set("shopTag", r[3]); Set("shopType", r[4]);
  Set("tabId", ""); Set("tabName", "");
  NewShopItem();
  document.getElementById("shopItemsTable").innerHTML = "";
  LoadTabs();
}

function PickNpc(index) {
  Set("shopNpcId", shopRows[index][0]);
}

function NewShop() {
  Set("shopId", ""); Set("shopNpcId", ""); Set("shopTag", ""); Set("shopType", "0");
  ClearShopChildren();
}

function SaveShop() {
  var text = RunAdmin("saveshop", { ShopId: V("shopId"), NpcId: V("shopNpcId"), TagName: V("shopTag"), TypeShop: V("shopType") });
  Msg("npcMessage", StatusText(text) + "\r\nRestart server để áp dụng shop cache.");
  LoadShops();
}

function LoadTabs() {
  if (!V("shopId")) { Msg("npcMessage", "Chọn shop trước."); return; }
  tabRows = ParseTsv(RunAdmin("listtabs", { ShopId: V("shopId") }));
  RenderTable("tabsTable", tabRows, ["ID", "Shop", "Tên tab"], "PickTab");
}

function PickTab(index) {
  Set("tabId", tabRows[index][0]); Set("tabName", tabRows[index][2]);
  NewShopItem();
  LoadShopItems();
}

function NewTab() {
  Set("tabId", ""); Set("tabName", "");
}

function SaveTab() {
  var text = RunAdmin("savetab", { TabId: V("tabId"), ShopId: V("shopId"), Name: V("tabName") });
  Msg("npcMessage", StatusText(text));
  LoadTabs();
}

function DeleteTab() {
  if (!V("tabId")) {
    Msg("npcMessage", "Chọn tab cần xóa trước.");
    return;
  }
  if (!window.confirm("Xóa tab ID " + V("tabId") + "?\n\nToàn bộ item và option trong tab này cũng sẽ bị xóa.")) {
    return;
  }
  Msg("npcMessage", StatusText(RunAdmin("deletetab", { TabId: V("tabId") })));
  Set("tabId", "");
  Set("tabName", "");
  document.getElementById("shopItemsTable").innerHTML = "";
  document.getElementById("shopOptionsTable").innerHTML = "";
  LoadTabs();
}

function LoadShopItems() {
  if (!V("tabId")) { Msg("npcMessage", "Chọn tab trước."); return; }
  LoadItemCatalog(false);
  shopItemRows = ParseTsv(RunAdmin("listshopitems", { TabId: V("tabId") }));
  var displayRows = [["id", "temp_id", "item_name", "item_description", "cost", "type_sell", "is_sell"]];
  for (var i = 1; i < shopItemRows.length; i++) {
    var row = shopItemRows[i];
    displayRows.push([row[0], row[2], row[3], row[4], row[8], row[7], row[6]]);
  }
  RenderTableMapped("shopItemsTable", displayRows, ["ID", "Item", "Tên", "Phụ đề", "Giá", "Tiền", "Bán"], "PickShopItem", function (col, value) {
    if (col == 5) return MoneyLabel(value);
    if (col == 6) return ToggleLabel(value);
    return value;
  });
  RenderShopItemPicker();
}

function PickShopItem(index) {
  var r = shopItemRows[index];
  shopSelectedItemIds = [r[2]];
  Set("shopItemId", r[0]); Set("shopTempId", r[2]); Set("shopIsNew", r[5]); Set("shopIsSell", r[6]);
  Set("shopTypeSell", r[7]); Set("shopCost", r[8]); Set("shopIconSpec", r[9]); Set("shopOptionMode", r[10] || "0");
  Set("shopItemSearch", "");
  RenderShopItemPicker();
  LoadShopOptions();
}

function NewShopItem() {
  shopSelectedItemIds = [];
  Set("shopItemId", ""); Set("shopTempId", ""); Set("shopCost", "0"); Set("shopTypeSell", "0");
  Set("shopIsNew", "0"); Set("shopIsSell", "1"); Set("shopIconSpec", "0"); Set("shopOptionMode", "0");
  Set("shopItemSearch", "");
  RenderShopItemPicker();
  ClearShopOptionEditor();
}

function RenderShopItemPicker() {
  if (!document.getElementById("shopItemList")) return;
  var selected = {};
  for (var i = 0; i < shopSelectedItemIds.length; i++) selected[shopSelectedItemIds[i]] = true;
  RenderItemPicker({
    listId: "shopItemList", idPrefix: "shopItem", query: V("shopItemSearch"),
    selected: selected, selectedOrder: shopSelectedItemIds,
    toggleFunction: "ToggleShopItem", limit: 150
  });
  var labels = [];
  for (var s = 0; s < shopSelectedItemIds.length && s < 3; s++) labels.push(shopSelectedItemIds[s] + " - " + ItemCatalogName(shopSelectedItemIds[s]));
  document.getElementById("shopItemSummary").innerText = shopSelectedItemIds.length
    ? "Đã chọn " + shopSelectedItemIds.length + " vật phẩm: " + labels.join("; ") + (shopSelectedItemIds.length > 3 ? "..." : "")
    : "Chưa chọn vật phẩm";
}

function ToggleShopItem(id, checked) {
  var next = [];
  var found = false;
  for (var i = 0; i < shopSelectedItemIds.length; i++) {
    if (shopSelectedItemIds[i] == id) found = true;
    if (!(shopSelectedItemIds[i] == id && !checked)) next.push(shopSelectedItemIds[i]);
  }
  if (checked && !found) next.push("" + id);
  shopSelectedItemIds = next;
  Set("shopTempId", shopSelectedItemIds.length == 1 ? shopSelectedItemIds[0] : "");
  RenderShopItemPicker();
}

function SaveShopItem() {
  if (!V("tabId")) { Msg("npcMessage", "Chọn tab shop trước."); return; }
  if (!shopSelectedItemIds.length) { Msg("npcMessage", "Tìm và tích chọn ít nhất một vật phẩm."); return; }
  if (V("shopItemId") && shopSelectedItemIds.length != 1) {
    Msg("npcMessage", "Khi sửa một dòng shop, chỉ chọn đúng một vật phẩm. Bấm 'Chọn nhóm mới' để thêm nhiều vật phẩm cùng lúc.");
    return;
  }
  var params = {
    Id: V("shopItemId"), TabId: V("tabId"), TempId: shopSelectedItemIds[0], Cost: V("shopCost"),
    TypeSell: V("shopTypeSell"), IsNew: V("shopIsNew"), IsSell: V("shopIsSell"), IconSpec: V("shopIconSpec"), OptionMode: V("shopOptionMode")
  };
  var action = "saveshopitem";
  if (!V("shopItemId")) {
    action = "saveshopitems";
    params.PayloadJson = JSON.stringify(shopSelectedItemIds);
  }
  var text = RunAdmin(action, params);
  Msg("npcMessage", StatusText(text));
  if (!IsAdminError(text)) { NewShopItem(); LoadShopItems(); }
}

function DeleteShopItem() {
  if (!V("shopItemId")) return;
  if (!window.confirm("Xóa vật phẩm shop ID " + V("shopItemId") + "?")) return;
  var text = RunAdmin("deleteshopitem", { Id: V("shopItemId") });
  Msg("npcMessage", StatusText(text));
  if (!IsAdminError(text)) { NewShopItem(); LoadShopItems(); }
}

function LoadShopOptions() {
  if (!V("shopItemId")) { Msg("npcMessage", "Chọn item shop trước."); return; }
  shopOptionRows = ParseTsv(RunAdmin("listshopoptions", { Id: V("shopItemId") }));
  shopOptionValues = [];
  for (var i = 1; i < shopOptionRows.length; i++) {
    var existing = FindShopOption(shopOptionRows[i][2]);
    if (existing) {
      existing.rowId = shopOptionRows[i][0];
      existing.param = shopOptionRows[i][4];
    } else {
      shopOptionValues.push({ rowId: shopOptionRows[i][0], id: shopOptionRows[i][2], param: shopOptionRows[i][4] });
    }
  }
  RenderTableMapped("shopOptionsTable", shopOptionRows, ["ID", "Item shop", "Option", "Tên", "Param"], "Noop", function (col, value, row) {
    if (col == 2) return value + " - " + (row[3] || "");
    if (col == 3) return row[3] || "";
    return value;
  });
  Set("shopOptionSearch", "");
  RenderShopOptionPicker();
}

function FindShopOption(id) {
  for (var i = 0; i < shopOptionValues.length; i++) if (("" + shopOptionValues[i].id) == ("" + id)) return shopOptionValues[i];
  return null;
}

function RenderShopOptionPicker() {
  if (!document.getElementById("shopOptionList")) return;
  if (!V("shopItemId")) {
    document.getElementById("shopOptionList").innerHTML = "Chọn một dòng vật phẩm trong bảng shop trước.";
    Set("shopOptionItemLabel", "");
    Set("shopOptionCount", "0");
    return;
  }
  var selected = {}, order = [], params = {};
  for (var i = 0; i < shopOptionValues.length; i++) {
    var id = "" + shopOptionValues[i].id;
    selected[id] = true; order.push(id); params[id] = shopOptionValues[i].param;
  }
  RenderOptionChecklist({
    listId: "shopOptionList", idPrefix: "shopOption",
    query: V("shopOptionSearch"), selected: selected, selectedOrder: order,
    labelResolver: OptionName, toggleFunction: "ToggleShopOption", limit: 120,
    showParam: true, paramFunction: "SetShopOptionParam",
    itemClass: "param-option-row", selectedClass: "param-option-selected", paramClass: "param-option-input",
    emptyText: "Không tìm thấy option phù hợp."
  });
  var itemId = shopSelectedItemIds[0] || V("shopTempId");
  Set("shopOptionItemLabel", itemId + " - " + ItemCatalogName(itemId));
  Set("shopOptionCount", "" + shopOptionValues.length);
}

function ToggleShopOption(id, checked) {
  var option = FindShopOption(id);
  if (checked && !option) shopOptionValues.push({ rowId: "", id: "" + id, param: "0" });
  if (!checked && option) {
    var next = [];
    for (var i = 0; i < shopOptionValues.length; i++) if (("" + shopOptionValues[i].id) != ("" + id)) next.push(shopOptionValues[i]);
    shopOptionValues = next;
  }
  RenderShopOptionPicker();
}

function SetShopOptionParam(id, value) {
  var option = FindShopOption(id);
  if (option) option.param = value;
}

function SaveShopOptions() {
  if (!V("shopItemId")) { Msg("npcMessage", "Chọn một vật phẩm shop trước."); return; }
  var payload = [];
  for (var i = 0; i < shopOptionValues.length; i++) payload.push({ id: shopOptionValues[i].id, param: shopOptionValues[i].param });
  var text = RunAdmin("saveshopoptions", { Id: V("shopItemId"), PayloadJson: JSON.stringify(payload) });
  Msg("npcMessage", StatusText(text));
  if (!IsAdminError(text)) LoadShopOptions();
}

function ClearShopOptionEditor() {
  shopOptionRows = [];
  shopOptionValues = [];
  Set("shopOptionSearch", "");
  document.getElementById("shopOptionsTable").innerHTML = "";
  RenderShopOptionPicker();
}

function ClearShopChildren() {
  shopSelectedItemIds = [];
  shopOptionRows = [];
  shopOptionValues = [];
  Set("tabId", ""); Set("tabName", ""); Set("shopItemId", ""); Set("shopTempId", "");
  document.getElementById("tabsTable").innerHTML = "";
  document.getElementById("shopItemsTable").innerHTML = "";
  document.getElementById("shopOptionsTable").innerHTML = "";
  RenderShopItemPicker();
  RenderShopOptionPicker();
}

RegisterTab({
  id: "npc", view: "shops.html", panelId: "panelNpc", navId: "navNpc",
  title: "NPC & Shop", subtitle: "Gán shop, tab, item và option cho NPC",
  onOpen: function () { LoadItemCatalog(false); LoadShops(); },
  onRefresh: function () { LoadItemCatalog(true); LoadShops(); }
});
