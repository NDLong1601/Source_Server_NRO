var itemRows = [];
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

function UpdateItemIconPreview() {
  var target = document.getElementById("itemIconPreview");
  if (!target) return;
  var iconId = Trim(V("itemIcon"));
  target.innerHTML = ItemImageHtml(iconId, "item-icon-preview-image") +
    '<span>Icon ' + Html(iconId || "mặc định") + '</span>';
}

function RenderItems() {
  var html = "<thead><tr><th>Ảnh</th><th>ID</th><th>Type</th><th>Gender</th><th>Tên</th><th>Mô tả</th><th>Level</th><th>Icon</th><th>Part</th></tr></thead><tbody>";
  for (var i = 1; i < itemRows.length; i++) {
    var row = itemRows[i];
    html += '<tr onclick="PickItem(' + i + ')"><td class="item-icon-cell">' + ItemImageHtml(row[6], "item-list-icon") + '</td>';
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
  if (info) info.innerText = hasItems
    ? "Trang " + itemCurrentPage + "/" + itemTotalPages + " · " + start + "–" + end + "/" + itemTotal
    : "Không có vật phẩm phù hợp";
  if (first) first.disabled = !hasItems || itemCurrentPage <= 1;
  if (previous) previous.disabled = !hasItems || itemCurrentPage <= 1;
  if (next) next.disabled = !hasItems || itemCurrentPage >= itemTotalPages;
  if (last) last.disabled = !hasItems || itemCurrentPage >= itemTotalPages;
}

function NormalizeItemRows(rows) {
  itemRows = [];
  itemTotal = 0;
  if (!rows.length) return;

  var header = [];
  for (var h = 1; h < rows[0].length; h++) header.push(rows[0][h]);
  itemRows.push(header);
  if (rows.length > 1 && /^\d+$/.test(rows[1][0] || "")) itemTotal = parseInt(rows[1][0], 10);

  for (var r = 1; r < rows.length; r++) {
    var item = [];
    for (var c = 1; c < rows[r].length; c++) item.push(rows[r][c]);
    itemRows.push(item);
  }
}

function LoadItems(page) {
  itemCurrentPage = page == null ? 1 : parseInt(page, 10);
  if (isNaN(itemCurrentPage) || itemCurrentPage < 1) itemCurrentPage = 1;
  var text = RunAdmin("listitems", {
    Type: V("itemTypeFilter"), Search: V("itemSearch"), Page: itemCurrentPage, PageSize: itemPageSize
  });
  NormalizeItemRows(ParseTsv(text));
  itemTotalPages = itemTotal ? Math.ceil(itemTotal / itemPageSize) : 0;
  if (itemTotalPages && itemCurrentPage > itemTotalPages) {
    itemCurrentPage = itemTotalPages;
    LoadItems(itemCurrentPage);
    return;
  }
  RenderItems();
  Msg("itemMessage", itemTotal ? "Đã tải " + (itemRows.length - 1) + "/" + itemTotal + " vật phẩm ở trang " + itemCurrentPage + "." : (text || "Không có vật phẩm phù hợp."));
  AdjustTableOffsets();
}

function GoItemPage(page) {
  page = parseInt(page, 10);
  if (isNaN(page) || page < 1 || (itemTotalPages && page > itemTotalPages) || page == itemCurrentPage) return;
  LoadItems(page);
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
  if (!IsAdminError(text)) { LoadItems(); LoadItemCatalog(true); }
}

RegisterTab({
  id: "items", view: "items.html", panelId: "panelItems", navId: "navItems",
  title: "Vật phẩm", subtitle: "Lọc theo type, xem ảnh và sửa item template",
  onOpen: function () { LoadItems(); },
  onRefresh: function () { LoadItems(); }
});
