var itemRows = [];
var itemAllRows = [];
var itemFilteredRows = [];
var itemCurrentPage = 1;
var itemPageSize = 60;
var itemTotal = 0;
var itemTotalPages = 0;
var itemBrokenIconCache = {};
var itemDefaultIconSrc = "data/icon/x3/544.png";
var itemDefaultIconImage = new Image();
itemDefaultIconImage.src = itemDefaultIconSrc;

function ItemIconSrc(iconId) {
  iconId = Trim(iconId);
  if (!/^\d+$/.test(iconId) || itemBrokenIconCache[iconId]) return itemDefaultIconSrc;
  try {
    if (!fso.FileExists(rootDir + "\\data\\icon\\x3\\" + iconId + ".png")) {
      itemBrokenIconCache[iconId] = true;
      return itemDefaultIconSrc;
    }
  } catch (e) {
  }
  return "data/icon/x3/" + iconId + ".png";
}

function HandleItemImageError(image, iconId) {
  iconId = "" + iconId;
  itemBrokenIconCache[iconId] = true;
  image.onerror = null;
  image.src = itemDefaultIconSrc;
  image.title = "Icon " + iconId + " không tồn tại - đang dùng ảnh mặc định";
}

function ItemImageHtml(iconId, className) {
  var src = ItemIconSrc(iconId);
  var title = src == itemDefaultIconSrc ? "Ảnh mặc định" : "Icon " + iconId;
  return '<img class="' + className + '" src="' + HtmlAttr(src) + '" alt="Icon vật phẩm" title="' + HtmlAttr(title) +
    '" onerror="HandleItemImageError(this, \'' + EscapeJs(iconId) + '\')">';
}

function ItemAuraAssetId(itemType, part) {
  var type = parseInt(itemType, 10);
  var assetId = parseInt(part, 10);
  if (type != 11 || isNaN(assetId) || assetId < 0) return -1;
  return assetId;
}

function ItemAuraImageExists(assetId, layer) {
  try {
    return fso.FileExists(rootDir + "\\data\\img_by_name\\x4\\aura_" + assetId + "_" + layer + ".png");
  } catch (e) {
    return true;
  }
}

function ItemAuraSpriteHtml(assetId, layer, sizeClass) {
  if (assetId < 0 || !ItemAuraImageExists(assetId, layer)) return "";
  var title = "Hào quang asset " + assetId + " · frame đầu · layer _" + layer;
  return '<span class="aura-sprite ' + sizeClass + '" title="' + HtmlAttr(title) + '"><img src="data/img_by_name/x4/aura_' + assetId + '_' + layer + '.png" alt="' + HtmlAttr(title) + '"></span>';
}

function ItemAuraListHtml(itemType, part) {
  var assetId = ItemAuraAssetId(itemType, part);
  var sprite = ItemAuraSpriteHtml(assetId, 0, "item-aura-sprite-list");
  if (!sprite) return '<span class="item-aura-empty">—</span>';
  return sprite;
}

function ItemAuraPreviewHtml(itemType, part) {
  var assetId = ItemAuraAssetId(itemType, part);
  var layer0 = ItemAuraSpriteHtml(assetId, 0, "item-aura-sprite-preview");
  if (!layer0) return "";
  var layer1 = ItemAuraSpriteHtml(assetId, 1, "item-aura-sprite-preview");
  return '<div class="item-aura-preview">'
    + '<div class="item-aura-preview-stage">'
    + '<span class="item-aura-preview-ring"></span>'
    + '<span class="item-aura-preview-layer">' + layer0 + '</span>'
    + (layer1 ? '<span class="item-aura-preview-layer item-aura-preview-layer-top">' + layer1 + '</span>' : '')
    + '</div>'
    + '<span class="item-aura-preview-copy"><b>Hào quang · Asset ' + Html(assetId) + '</b><small>Frame đầu từ sprite x4</small></span>'
    + '</div>';
}

function UpdateItemIconPreview() {
  var target = document.getElementById("itemIconPreview");
  if (!target) return;
  var iconId = Trim(V("itemIcon"));
  target.innerHTML = '<div class="item-icon-preview-main">' + ItemImageHtml(iconId, "item-icon-preview-image") +
    '<span>Icon ' + Html(iconId || "mặc định") + '</span></div>' + ItemAuraPreviewHtml(V("itemType"), V("itemPart"));
}

function RenderItems() {
  var html = "<thead><tr><th>Ảnh</th><th>Aura</th><th>ID</th><th>Type</th><th>Gender</th><th>Tên</th><th>Mô tả</th><th>Level</th><th>Icon</th><th>Part</th></tr></thead><tbody>";
  for (var i = 1; i < itemRows.length; i++) {
    var row = itemRows[i];
    html += '<tr onclick="PickItem(' + i + ')"><td class="item-icon-cell">' + ItemImageHtml(row[6], "item-list-icon") + '</td>';
    html += '<td class="item-aura-cell">' + ItemAuraListHtml(row[1], row[7]) + '</td>';
    html += '<td>' + Html(row[0]) + '</td><td>' + Html(ItemTypeLabel(row[1])) + '</td><td>' + Html(GenderLabel(row[2])) + '</td>';
    html += '<td>' + Html(row[3]) + '</td><td>' + Html(row[4]) + '</td><td>' + Html(row[5]) + '</td>';
    html += '<td>' + Html(row[6]) + '</td><td>' + Html(row[7]) + '</td></tr>';
  }
  document.getElementById("itemsTable").innerHTML = html + "</tbody>";
  RenderItemPagination();
}

function RenderItemPagination() {
  var hasItems = itemTotal > 0;
  var start = hasItems ? ((itemCurrentPage - 1) * itemPageSize + 1) : 0;
  var end = hasItems ? Math.min(itemCurrentPage * itemPageSize, itemTotal) : 0;
  var info = document.getElementById("itemPageInfo");
  var first = document.getElementById("itemFirstPage");
  var previous = document.getElementById("itemPreviousPage");
  var next = document.getElementById("itemNextPage");
  var last = document.getElementById("itemLastPage");
  var numbers = document.getElementById("itemPageNumbers");
  if (info) info.innerText = hasItems
    ? "Trang " + itemCurrentPage + "/" + itemTotalPages + " · " + start + "–" + end + "/" + itemTotal
    : "Không có vật phẩm phù hợp";
  if (first) first.disabled = !hasItems || itemCurrentPage <= 1;
  if (previous) previous.disabled = !hasItems || itemCurrentPage <= 1;
  if (next) next.disabled = !hasItems || itemCurrentPage >= itemTotalPages;
  if (last) last.disabled = !hasItems || itemCurrentPage >= itemTotalPages;
  if (numbers) {
    var html = "";
    var firstNumber = Math.max(1, itemCurrentPage - 2);
    var lastNumber = Math.min(itemTotalPages, firstNumber + 4);
    firstNumber = Math.max(1, lastNumber - 4);
    for (var page = firstNumber; page <= lastNumber; page++) {
      html += '<button type="button" class="pagination-page' + (page == itemCurrentPage ? ' active' : '') + '" onclick="GoItemPage(' + page + ')">' + page + '</button>';
    }
    numbers.innerHTML = html;
  }
}

function NormalizeAllItemRows(rows) {
  itemAllRows = [];
  if (!rows.length) return;

  var header = [];
  for (var h = 1; h < rows[0].length; h++) header.push(rows[0][h]);
  itemAllRows.push(header);

  for (var r = 1; r < rows.length; r++) {
    var item = [];
    for (var c = 1; c < rows[r].length; c++) item.push(rows[r][c]);
    itemAllRows.push(item);
  }
  SyncItemCaches();
}

function SyncItemCaches() {
  var counts = {};
  var catalog = [["id", "item_name", "description", "item_type", "gender", "icon_id"]];
  for (var i = 1; i < itemAllRows.length; i++) {
    var row = itemAllRows[i];
    counts[row[1]] = (counts[row[1]] || 0) + 1;
    catalog.push([row[0], row[3], row[4], row[1], row[2], row[6]]);
  }
  var filterTypes = [["", "Tất cả group (" + Math.max(0, itemAllRows.length - 1) + ")"]];
  var detailTypes = [];
  var ids = [];
  for (var key in counts) ids.push(key);
  ids.sort(function (a, b) { return parseInt(a, 10) - parseInt(b, 10); });
  for (var t = 0; t < ids.length; t++) {
    var label = ItemTypeBaseLabel(ids[t]);
    filterTypes.push([ids[t], label + " (" + counts[ids[t]] + ")"]);
    detailTypes.push([ids[t], label]);
  }
  SetComboItems("itemTypeFilter", filterTypes);
  SetComboItems("itemType", detailTypes);
  SetComboItems("equipTypeFilter", filterTypes);
  CacheItemCatalogRows(catalog);
}

function LoadItems(force) {
  var text = "";
  if (force || itemAllRows.length < 2) {
    text = RunAdmin("listitems", { Page: 1, PageSize: 0 });
    NormalizeAllItemRows(ParseTsv(text));
  }
  ApplyItemFilters(true);
  Msg("itemMessage", itemTotal ? "Đã nạp " + (itemAllRows.length - 1) + " vật phẩm; đang hiển thị " + itemTotal + " kết quả từ bộ nhớ." : (text || "Không có vật phẩm phù hợp."));
  AdjustTableOffsets();
}

function ApplyItemFilters(resetPage) {
  var type = V("itemTypeFilter");
  var query = Trim(V("itemSearch")).toLowerCase();
  itemFilteredRows = [itemAllRows[0] || []];
  for (var i = 1; i < itemAllRows.length; i++) {
    var row = itemAllRows[i];
    if (type && row[1] != type) continue;
    if (query && ((row[0] || "").toLowerCase().indexOf(query) < 0) && ((row[3] || "").toLowerCase().indexOf(query) < 0)) continue;
    itemFilteredRows.push(row);
  }
  itemTotal = Math.max(0, itemFilteredRows.length - 1);
  itemTotalPages = itemTotal ? Math.ceil(itemTotal / itemPageSize) : 0;
  if (resetPage) itemCurrentPage = 1;
  if (itemTotalPages && itemCurrentPage > itemTotalPages) {
    itemCurrentPage = itemTotalPages;
  }
  RenderItemPage();
}

function RenderItemPage() {
  itemRows = [itemFilteredRows[0] || []];
  var start = (itemCurrentPage - 1) * itemPageSize + 1;
  var end = Math.min(start + itemPageSize, itemFilteredRows.length);
  for (var i = start; i < end; i++) itemRows.push(itemFilteredRows[i]);
  RenderItems();
}

function GoItemPage(page) {
  page = parseInt(page, 10);
  if (isNaN(page) || page < 1 || (itemTotalPages && page > itemTotalPages) || page == itemCurrentPage) return;
  itemCurrentPage = page;
  RenderItemPage();
}

function PickItem(index) {
  var r = itemRows[index];
  Set("itemId", r[0]); Set("itemType", r[1]); Set("itemGender", r[2]); Set("itemName", r[3]);
  Set("itemDesc", r[4]); Set("itemLevel", r[5]); Set("itemIcon", r[6]); Set("itemPart", r[7]);
  Set("itemUp", r[8]); Set("itemPower", r[9]); Set("itemGold", r[10]); Set("itemGem", r[11]);
  Set("itemHead", r[12]); Set("itemBody", r[13]); Set("itemLeg", r[14]);
  UpdateItemIconPreview();
  AdjustTableOffsets();
}

function NewItem() {
  Set("itemId", ""); Set("itemType", V("itemTypeFilter") || "27"); Set("itemGender", "3"); Set("itemName", "");
  Set("itemDesc", ""); Set("itemLevel", "0"); Set("itemIcon", "0"); Set("itemPart", "-1");
  Set("itemUp", "1"); Set("itemPower", "0"); Set("itemGold", "0"); Set("itemGem", "0");
  Set("itemHead", "-1"); Set("itemBody", "-1"); Set("itemLeg", "-1");
  UpdateItemIconPreview();
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
  if (!IsAdminError(text)) LoadItems(true);
}

RegisterTab({
  id: "items", view: "items.html", panelId: "panelItems", navId: "navItems",
  title: "Vật phẩm", subtitle: "Lọc theo type, xem ảnh và sửa item template",
  onOpen: function () { LoadItems(false); },
  onRefresh: function () { LoadItems(true); }
});
