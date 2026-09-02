var auraRows = [];
var auraSelectedItemIds = [];
var auraShopRows = [];
var auraShopTabRows = [];
var auraShopItemRows = [];
var auraActiveItemId = "";

function AuraItemSelected(itemId) {
  return ArrayContains(auraSelectedItemIds, "" + itemId);
}

function AuraToneClass(auraId) {
  var value = parseInt(auraId, 10);
  if (isNaN(value)) value = 0;
  return "aura-tone-" + ((value % 6 + 6) % 6);
}

function AuraNumberText(value) {
  value = parseInt(value, 10);
  if (isNaN(value)) return "--";
  return value < 10 ? "0" + value : "" + value;
}

function AuraFrameCount(value) {
  value = parseInt(value, 10);
  return isNaN(value) || value < 1 ? 1 : value;
}

function AuraSpriteSource(auraId, layer) {
  return "data/img_by_name/x4/aura_" + auraId + "_" + layer + ".png";
}

function ResetAuraSpriteImage(image) {
  image.style.display = "block";
  image.style.visibility = "hidden";
  image.style.width = "";
  image.style.height = "";
  image.style.left = "0";
  image.style.top = "0";
}

function UseAuraSpriteFallback(image, auraId, fallbackLayer, fallbackFrames) {
  if (fallbackLayer < 0 || image._auraFallbackUsed) return false;
  image._auraFallbackUsed = true;
  image._auraCurrentFrames = AuraFrameCount(fallbackFrames);
  ResetAuraSpriteImage(image);
  image.title = "Aura asset " + auraId + ", layer _" + fallbackLayer;
  image.src = AuraSpriteSource(auraId, fallbackLayer);
  return true;
}

function HideAuraSpriteImage(image, showMissing) {
  var container = image ? image.parentNode : null;
  if (container && showMissing) {
    if (container.className.indexOf("aura-sprite-missing") < 0) {
      container.className += " aura-sprite-missing";
    }
    container.innerHTML = "?";
  } else if (image) {
    image.style.display = "none";
  }
}

function FitAuraSpriteFrame(image, frameCount) {
  var sourceWidth = image.naturalWidth || image.width;
  var sourceHeight = image.naturalHeight || image.height;
  var frameHeight = Math.max(1, Math.floor(sourceHeight / AuraFrameCount(frameCount)));
  var container = image.parentNode;
  var boxWidth = container.clientWidth || container.offsetWidth;
  var boxHeight = container.clientHeight || container.offsetHeight;
  var scale = Math.min(boxWidth / sourceWidth, boxHeight / frameHeight);
  var displayWidth = Math.max(1, Math.round(sourceWidth * scale));
  var displayHeight = Math.max(1, Math.round(sourceHeight * scale));
  var displayedFrameHeight = frameHeight * scale;

  image.style.width = displayWidth + "px";
  image.style.height = displayHeight + "px";
  image.style.left = Math.round((boxWidth - displayWidth) / 2) + "px";
  image.style.top = Math.round((boxHeight - displayedFrameHeight) / 2) + "px";
  image.style.visibility = "visible";
}

function HandleAuraSpriteLoad(image, auraId, layer, frameCount, fallbackLayer, fallbackFrames, showMissing) {
  var sourceWidth = image.naturalWidth || image.width;
  var sourceHeight = image.naturalHeight || image.height;
  if (sourceWidth <= 4 && sourceHeight <= 4) {
    if (!UseAuraSpriteFallback(image, auraId, fallbackLayer, fallbackFrames)) {
      HideAuraSpriteImage(image, showMissing);
    }
    return;
  }
  FitAuraSpriteFrame(image, image._auraCurrentFrames || frameCount);
}

function HandleAuraSpriteError(image, auraId, fallbackLayer, fallbackFrames, showMissing) {
  if (!UseAuraSpriteFallback(image, auraId, fallbackLayer, fallbackFrames)) {
    HideAuraSpriteImage(image, showMissing);
  }
}

function AuraSpriteMarkup(auraId, layer, sizeClass, frameCount, fallbackLayer, fallbackFrames, showMissing) {
  var value = parseInt(auraId, 10);
  if (isNaN(value) || value < 0) {
    return showMissing ? '<span class="aura-sprite ' + sizeClass + ' aura-sprite-missing">?</span>' : "";
  }
  layer = parseInt(layer, 10);
  fallbackLayer = parseInt(fallbackLayer, 10);
  if (isNaN(fallbackLayer)) fallbackLayer = -1;
  frameCount = AuraFrameCount(frameCount);
  fallbackFrames = AuraFrameCount(fallbackFrames);
  showMissing = showMissing ? 1 : 0;
  var title = "Aura asset " + value + ", layer _" + layer;
  return '<span class="aura-sprite ' + sizeClass + '" title="' + title + '">'
    + '<img src="' + AuraSpriteSource(value, layer) + '" alt="" title="' + title + '"'
    + ' onload="HandleAuraSpriteLoad(this,' + value + ',' + layer + ',' + frameCount + ',' + fallbackLayer + ',' + fallbackFrames + ',' + showMissing + ')"'
    + ' onerror="HandleAuraSpriteError(this,' + value + ',' + fallbackLayer + ',' + fallbackFrames + ',' + showMissing + ')"></span>';
}

function AuraBadgeMarkup(value, toneClass, extraClass) {
  return '<span class="aura-badge ' + toneClass + (extraClass ? ' ' + extraClass : '') + '">' + Html(value) + '</span>';
}

function RenderAuraPreview(row) {
  var preview = document.getElementById("auraPreview");
  if (!preview) return;
  if (!row) {
    preview.className = "aura-preview aura-preview-empty";
    preview.innerHTML = '<div class="aura-preview-empty-icon">✦</div><div><b>Chọn một hào quang</b><br><span>Preview sẽ ghép frame đầu của hai layer aura.</span></div>';
    return;
  }
  var tone = AuraToneClass(row[5]);
  preview.className = "aura-preview " + tone;
  preview.innerHTML = '<div class="aura-preview-stage">'
    + '<div class="aura-preview-ring"></div>'
    + '<div class="aura-preview-layer aura-preview-layer-0">' + AuraSpriteMarkup(row[5], 0, "aura-sprite-preview", row[6], -1, 1, 0) + '</div>'
    + '<div class="aura-preview-layer aura-preview-layer-1">' + AuraSpriteMarkup(row[5], 1, "aura-sprite-preview", row[7], -1, 1, 0) + '</div>'
    + '</div>'
    + '<div class="aura-preview-copy">'
    + '<div class="aura-preview-overline">AURA ĐANG CHỌN</div>'
    + '<div class="aura-preview-name">' + Html(row[2]) + '</div>'
    + '<div class="aura-preview-badges">'
    + AuraBadgeMarkup("Asset " + row[5], tone, "")
    + AuraBadgeMarkup("Frame " + row[6] + "/" + row[7], "aura-badge-cyan", "")
    + AuraBadgeMarkup(row[8] > 0 ? row[8] + " shop" : "Chưa bán", row[8] > 0 ? "aura-badge-green" : "aura-badge-slate", "")
    + '</div>'
    + '<div class="aura-preview-note">Hiển thị frame đầu của layer _0 và _1 ở zoom x4.</div>'
    + '</div>';
}

function RenderAuraItems() {
  var query = Trim(V("auraSearch")).toLowerCase();
  var visible = 0;
  var selectedVisible = 0;
  var rowsHtml = "";
  for (var i = 1; i < auraRows.length; i++) {
    var row = auraRows[i];
    var searchable = (row[0] + " " + row[1] + " " + row[2] + " " + row[5]).toLowerCase();
    if (query && searchable.indexOf(query) < 0) continue;
    visible++;
    var checked = AuraItemSelected(row[0]) ? " checked" : "";
    if (checked) selectedVisible++;
    var tone = AuraToneClass(row[5]);
    var active = "" + row[0] == "" + auraActiveItemId ? " aura-row-active" : "";
    rowsHtml += '<tr class="aura-row ' + tone + active + '" onclick="PickAuraItem(' + i + ')">';
    rowsHtml += '<td class="aura-check-cell"><label class="aura-check-control"><input type="checkbox"' + checked + ' onclick="ToggleAuraItem(' + row[0] + ', this.checked); event.cancelBubble = true;"><span class="aura-check-mark"></span></label></td>';
    rowsHtml += '<td class="aura-number-cell">' + AuraBadgeMarkup(AuraNumberText(row[1]), tone, "aura-number-badge") + '</td>';
    rowsHtml += '<td class="aura-thumb-cell">' + AuraSpriteMarkup(row[5], 0, "aura-sprite-list", row[6], 1, row[7], 1) + '</td>';
    rowsHtml += '<td class="aura-id-cell">' + Html(row[0]) + '</td><td class="aura-name-cell">' + Html(row[2]) + '</td>';
    rowsHtml += '<td>' + AuraBadgeMarkup("#" + row[5], tone, "") + '</td>';
    rowsHtml += '<td>' + AuraBadgeMarkup(row[6] + "/" + row[7], "aura-badge-cyan", "") + '</td>';
    rowsHtml += '<td>' + AuraBadgeMarkup(row[8] > 0 ? row[8] : "–", row[8] > 0 ? "aura-badge-green" : "aura-badge-slate", "aura-shop-badge") + '</td></tr>';
  }
  var masterChecked = visible > 0 && visible == selectedVisible ? " checked" : "";
  var tableHtml = '<thead><tr><th class="aura-check-cell"><label class="aura-check-control aura-check-all"><input type="checkbox"' + masterChecked + ' onclick="ToggleAllAuraItems(this.checked); event.cancelBubble = true;"><span class="aura-check-mark"></span></label></th><th>#</th><th>Aura</th><th>Item</th><th>Tên</th><th>Asset</th><th>Frame</th><th>Shop</th></tr></thead><tbody>';
  document.getElementById("auraItemsTable").innerHTML = tableHtml + rowsHtml + "</tbody>";
  document.getElementById("auraSelectionSummary").innerText = auraSelectedItemIds.length
    ? "Đã chọn " + auraSelectedItemIds.length + " / " + (auraRows.length - 1) + " để phát hoặc bán"
    : (visible ? "Chọn nhiều aura để phát hoặc bán hàng loạt" : "Chưa có dữ liệu");
  AdjustTableOffsets();
}

function LoadAuraItems() {
  auraRows = ParseTsv(RunAdmin("listauraitems", {}));
  var retained = [];
  for (var i = 0; i < auraSelectedItemIds.length; i++) {
    for (var r = 1; r < auraRows.length; r++) {
      if ("" + auraRows[r][0] == "" + auraSelectedItemIds[i]) retained.push("" + auraSelectedItemIds[i]);
    }
  }
  auraSelectedItemIds = retained;
  var activeRow = null;
  for (var a = 1; a < auraRows.length; a++) {
    if ("" + auraRows[a][0] == "" + auraActiveItemId) { activeRow = auraRows[a]; break; }
  }
  if (!activeRow) auraActiveItemId = "";
  RenderAuraPreview(activeRow);
  RenderAuraItems();
  Msg("auraMessage", auraRows.length > 1 ? "Đã nạp " + (auraRows.length - 1) + " hào quang." : "Chưa có item aura trong database. Bấm Khởi tạo dữ liệu.");
}

function ToggleAuraItem(itemId, checked) {
  itemId = "" + itemId;
  if (checked && !AuraItemSelected(itemId)) auraSelectedItemIds.push(itemId);
  if (!checked && AuraItemSelected(itemId)) {
    var next = [];
    for (var i = 0; i < auraSelectedItemIds.length; i++) if (auraSelectedItemIds[i] != itemId) next.push(auraSelectedItemIds[i]);
    auraSelectedItemIds = next;
  }
  RenderAuraItems();
}

function ToggleAllAuraItems(checked) {
  auraSelectedItemIds = [];
  if (checked) {
    for (var i = 1; i < auraRows.length; i++) auraSelectedItemIds.push("" + auraRows[i][0]);
  }
  RenderAuraItems();
}

function PickAuraItem(index) {
  var row = auraRows[index];
  auraActiveItemId = "" + row[0];
  Set("auraItemId", row[0]);
  Set("auraNumber", row[1]);
  Set("auraItemName", row[2]);
  Set("auraItemDescription", row[3]);
  Set("auraItemIcon", row[4]);
  Set("auraAssetId", row[5]);
  Set("auraFrame0", row[6]);
  Set("auraFrame1", row[7]);
  Set("auraShopCount", row[8]);
  RenderAuraPreview(row);
  RenderAuraItems();
}

function InstallAuraItems() {
  if (!window.confirm("Khởi tạo các item aura chưa có trong database?\n\nCác item và cấu hình đã có sẽ được giữ nguyên.")) return;
  var text = RunAdmin("installauraitems", {});
  Msg("auraMessage", StatusText(text));
  if (!IsAdminError(text)) {
    LoadAuraItems();
    LoadItemCatalog(true);
  }
}

function SaveAuraItem() {
  if (!/^\d+$/.test(V("auraItemId"))) {
    Msg("auraMessage", "Chọn một hào quang trước.");
    return;
  }
  var text = RunAdmin("saveauraitem", {
    Id: V("auraItemId"),
    Name: V("auraItemName"),
    Description: V("auraItemDescription"),
    IconId: V("auraItemIcon"),
    AuraId: V("auraAssetId"),
    AuraFrame0: V("auraFrame0"),
    AuraFrame1: V("auraFrame1")
  });
  Msg("auraMessage", StatusText(text));
  if (!IsAdminError(text)) {
    LoadAuraItems();
    LoadItemCatalog(true);
  }
}

function LoadAuraShops() {
  auraShopRows = ParseTsv(RunAdmin("listshops", {}));
  RenderTable("auraShopsTable", auraShopRows, ["ID", "NPC", "Tên NPC", "Tag", "Type"], "PickAuraShop");
}

function PickAuraShop(index) {
  var row = auraShopRows[index];
  Set("auraShopId", row[0]);
  Set("auraShopLabel", "Shop " + row[0] + " / " + (row[2] || row[3] || "NPC " + row[1]));
  Set("auraShopTabId", "");
  Set("auraShopTabLabel", "");
  document.getElementById("auraShopItemsTable").innerHTML = "";
  LoadAuraShopTabs();
}

function LoadAuraShopTabs() {
  if (!V("auraShopId")) {
    Msg("auraMessage", "Chọn shop trước.");
    return;
  }
  auraShopTabRows = ParseTsv(RunAdmin("listtabs", { ShopId: V("auraShopId") }));
  RenderTable("auraShopTabsTable", auraShopTabRows, ["ID", "Shop", "Tên tab"], "PickAuraShopTab");
}

function PickAuraShopTab(index) {
  var row = auraShopTabRows[index];
  Set("auraShopTabId", row[0]);
  Set("auraShopTabLabel", "Tab " + row[0] + " - " + (row[2] || ""));
  LoadAuraShopItems();
}

function LoadAuraShopItems() {
  if (!V("auraShopTabId")) return;
  auraShopItemRows = ParseTsv(RunAdmin("listshopitems", { TabId: V("auraShopTabId") }));
  var rows = [["item_shop_id", "item_id", "name", "cost", "currency", "selling"]];
  for (var i = 1; i < auraShopItemRows.length; i++) {
    var row = auraShopItemRows[i];
    if (parseInt(row[2], 10) >= 2154 && parseInt(row[2], 10) <= 2217) {
      rows.push([row[0], row[2], row[3], row[8], MoneyLabel(row[7]), ToggleLabel(row[6])]);
    }
  }
  RenderTable("auraShopItemsTable", rows, ["Dòng", "Item", "Hào quang", "Giá", "Tiền", "Bán"], "Noop");
}

function AddSelectedAurasToShop() {
  if (!V("auraShopTabId")) {
    Msg("auraMessage", "Chọn tab shop trước.");
    return;
  }
  if (!auraSelectedItemIds.length) {
    Msg("auraMessage", "Chọn ít nhất một hào quang ở bảng bên trái.");
    return;
  }
  var text = RunAdmin("saveshopitems", {
    TabId: V("auraShopTabId"),
    PayloadJson: JSON.stringify(auraSelectedItemIds),
    Cost: V("auraShopCost"),
    TypeSell: V("auraShopTypeSell"),
    IsNew: document.getElementById("auraShopIsNew").checked ? "1" : "0",
    IsSell: document.getElementById("auraShopIsSell").checked ? "1" : "0",
    IconSpec: "0",
    OptionMode: "0"
  });
  Msg("auraMessage", StatusText(text));
  if (!IsAdminError(text)) {
    LoadAuraItems();
    LoadAuraShopItems();
  }
}

function OpenAuraGiftcode() {
  if (!V("auraItemId")) { Msg("auraMessage", "Chọn một hào quang trước."); return; }
  ShowTab("gift");
  LoadGiftItems(true);
  Set("giftItemSearch", V("auraItemId"));
  RenderGiftItems();
}

function OpenAuraGiftBox() {
  if (!V("auraItemId")) { Msg("auraMessage", "Chọn một hào quang trước."); return; }
  ShowTab("giftbox");
  LoadGiftBoxItems(true);
  Set("giftBoxItemSearch", V("auraItemId"));
  RenderGiftBoxItems();
}

RegisterTab({
  id: "auras", view: "auras.html", panelId: "panelAuras", navId: "navAuras",
  title: "Hào quang", subtitle: "Cấu hình 64 item aura, phát qua Giftcode/Hộp quà và thêm hàng loạt vào shop",
  onOpen: function () { LoadAuraItems(); LoadAuraShops(); },
  onRefresh: function () { LoadAuraItems(); LoadAuraShops(); }
});
