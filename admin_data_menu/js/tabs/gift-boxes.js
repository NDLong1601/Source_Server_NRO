var giftBoxRows = [];
var giftBoxItemRows = [];
var giftBoxConfig = null;
var giftBoxRewards = [];
var selectedGiftBoxRewardId = "";
var giftBoxItemFilterTimer = 0;

function GiftBoxCatalogItem(id) {
  return itemCatalogById["" + id] || null;
}

function GiftBoxImageHtml(iconId, className) {
  return ItemImageHtml(iconId, className || "gift-box-list-icon");
}

function RenderGiftBoxIdentityPreview(iconId, name, id) {
  var target = document.getElementById("giftBoxIconPreview");
  if (!target) return;
  if (iconId == null || iconId === "") {
    target.innerHTML = "Chưa chọn hộp";
    return;
  }
  target.innerHTML = GiftBoxImageHtml(iconId, "gift-box-preview-image") +
    '<span><b>' + Html(name || ("Hộp ID " + id)) + '</b><small>Icon ' + Html(iconId) + '</small></span>';
}

function UpdateGiftBoxIdentityPreview() {
  var id = V("giftBoxId");
  var item = GiftBoxCatalogItem(id);
  Set("giftBoxName", item ? item.name : "");
  RenderGiftBoxIdentityPreview(item ? item.iconId : "", item ? item.name : "", id);
}

function LoadGiftBoxes() {
  var rows = ParseTsv(RunAdmin("listgiftboxes", { Search: V("giftBoxSearch") }));
  giftBoxRows = rows;
  RenderGiftBoxes();
  Msg("giftBoxMessage", rows.length > 1 ? "Đã tải " + (rows.length - 1) + " cấu hình hộp." : "Chưa có cấu hình hộp.");
}

function RenderGiftBoxes() {
  var html = "<thead><tr><th>Ảnh</th><th>ID</th><th>Tên hộp</th><th>Type</th><th>Trạng thái</th><th>Ô trống</th><th>Rút</th><th>Cập nhật</th></tr></thead><tbody>";
  for (var i = 1; i < giftBoxRows.length; i++) {
    var row = giftBoxRows[i];
    html += '<tr onclick="PickGiftBox(' + i + ')"><td class="gift-box-icon-cell">' + GiftBoxImageHtml(row[9], "gift-box-list-icon") + '</td><td>' + Html(row[0]) + "</td><td>" + Html(row[1]) + "</td><td>" + Html(row[2]) +
      "</td><td>" + (row[3] == "1" ? "Bật" : "Khóa") + "</td><td>" + Html(row[4]) + "</td><td>" + Html(row[6]) +
      "</td><td>" + Html(row[8] || "") + "</td></tr>";
  }
  document.getElementById("giftBoxesTable").innerHTML = html + "</tbody>";
}

function LoadGiftBoxItems(force) {
  LoadItemCatalog(force);
  giftBoxItemRows = [];
  for (var i = 0; i < itemCatalogRows.length; i++) {
    var row = itemCatalogRows[i];
    if (i == 0 || row[3] == "27" || row[3] == "29" || row[3] == "75" || row[3] == "11" || row[3] == "14" || row[3] == "5" || row[3] == "23" || row[3] == "24") {
      giftBoxItemRows.push(row);
    }
  }
  RenderGiftBoxItems();
}

function FilterGiftBoxItems() {
  if (giftBoxItemFilterTimer) window.clearTimeout(giftBoxItemFilterTimer);
  giftBoxItemFilterTimer = window.setTimeout(function () { RenderGiftBoxItems(); }, 120);
}

function RenderGiftBoxItems() {
  if (!document.getElementById("giftBoxItemList")) return;
  var selected = {}, order = [], labels = {};
  for (var i = 0; i < giftBoxRewards.length; i++) {
    var id = "" + giftBoxRewards[i].itemId;
    selected[id] = true; order.push(id); labels[id] = ItemCatalogName(id);
  }
  RenderItemPicker({ listId: "giftBoxItemList", idPrefix: "giftBoxItem", rows: giftBoxItemRows, rowStart: 1,
    query: V("giftBoxItemSearch"), selected: selected, selectedOrder: order, selectedLabels: labels,
    toggleFunction: "ToggleGiftBoxItem", limit: 180, searchIndexes: [1, 2, 3, 4] });
  document.getElementById("giftBoxItemSummary").innerText = giftBoxRewards.length ? "Đã chọn " + giftBoxRewards.length + " vật phẩm" : "Chưa chọn phần thưởng";
}

function FindGiftBoxReward(id) {
  id = "" + id;
  for (var i = 0; i < giftBoxRewards.length; i++) if (("" + giftBoxRewards[i].itemId) == id) return giftBoxRewards[i];
  return null;
}

function NewGiftBox() {
  giftBoxConfig = { boxTemplateId: "", enabled: true, minEmptySlots: 1, consumeQuantity: 1, drawCount: 1, rewards: [] };
  giftBoxRewards = []; selectedGiftBoxRewardId = "";
  Set("giftBoxId", ""); Set("giftBoxName", ""); Set("giftBoxMinSlots", "1"); Set("giftBoxConsume", "1"); Set("giftBoxDrawCount", "1");
  document.getElementById("giftBoxEnabled").checked = true;
  RenderGiftBoxIdentityPreview("", "", "");
  RenderGiftBoxItems(); RenderGiftBoxRewards(); HideGiftBoxRewardEditor();
  Msg("giftBoxMessage", "Đang tạo cấu hình hộp mới.");
}

function PickGiftBox(index) {
  var id = giftBoxRows[index][0];
  var rows = ParseTsv(RunAdmin("getgiftbox", { Id: id }));
  if (rows.length <= 1) { Msg("giftBoxMessage", "Không tải được cấu hình hộp ID " + id); return; }
  var row = rows[1];
  try { giftBoxConfig = JSON.parse(row[7] || "{}"); } catch (e) { giftBoxConfig = {}; }
  giftBoxConfig.boxTemplateId = row[0];
  giftBoxConfig.enabled = row[3] == "1";
  giftBoxConfig.minEmptySlots = parseInt(row[4] || "1", 10);
  giftBoxConfig.consumeQuantity = parseInt(row[5] || "1", 10);
  giftBoxConfig.drawCount = parseInt(row[6] || "1", 10);
  giftBoxRewards = giftBoxConfig.rewards || [];
  for (var i = 0; i < giftBoxRewards.length; i++) NormalizeGiftBoxReward(giftBoxRewards[i]);
  selectedGiftBoxRewardId = giftBoxRewards.length ? "" + giftBoxRewards[0].itemId : "";
  Set("giftBoxId", row[0]); Set("giftBoxName", row[1]); Set("giftBoxMinSlots", row[4]); Set("giftBoxConsume", row[5]); Set("giftBoxDrawCount", row[6]);
  document.getElementById("giftBoxEnabled").checked = row[3] == "1";
  RenderGiftBoxIdentityPreview(row[9], row[1], row[0]);
  RenderGiftBoxItems(); RenderGiftBoxRewards();
  if (selectedGiftBoxRewardId) ShowGiftBoxRewardEditor(); else HideGiftBoxRewardEditor();
  Msg("giftBoxMessage", "Đang sửa cấu hình hộp ID " + row[0] + ".");
}

function NormalizeGiftBoxReward(reward) {
  reward.itemId = parseInt(reward.itemId || 0, 10);
  reward.weight = parseInt(reward.weight == null ? 1 : reward.weight, 10);
  reward.quantityMin = parseInt(reward.quantityMin == null ? 1 : reward.quantityMin, 10);
  reward.quantityMax = parseInt(reward.quantityMax == null ? reward.quantityMin : reward.quantityMax, 10);
  reward.gender = parseInt(reward.gender == null ? 3 : reward.gender, 10);
  reward.options = reward.options || [];
  reward.expiry = reward.expiry || [];
}

function ToggleGiftBoxItem(id, checked) {
  var reward = FindGiftBoxReward(id);
  if (checked && !reward) {
    reward = { itemId: parseInt(id, 10), weight: 1, quantityMin: 1, quantityMax: 1, gender: 3, initBaseOptions: false, useDefaultOptions: false, options: [], expiry: [] };
    giftBoxRewards.push(reward); selectedGiftBoxRewardId = "" + id; NormalizeGiftBoxReward(reward);
  } else if (!checked && reward) {
    var next = [];
    for (var i = 0; i < giftBoxRewards.length; i++) if (("" + giftBoxRewards[i].itemId) != ("" + id)) next.push(giftBoxRewards[i]);
    giftBoxRewards = next;
    if (selectedGiftBoxRewardId == ("" + id)) selectedGiftBoxRewardId = giftBoxRewards.length ? "" + giftBoxRewards[0].itemId : "";
  }
  RenderGiftBoxItems(); RenderGiftBoxRewards();
  if (selectedGiftBoxRewardId) ShowGiftBoxRewardEditor(); else HideGiftBoxRewardEditor();
}

function RenderGiftBoxRewards() {
  var html = "<thead><tr><th>ID</th><th>Vật phẩm</th><th>Trọng số</th><th>Số lượng</th><th>Option/HSD</th></tr></thead><tbody>";
  for (var i = 0; i < giftBoxRewards.length; i++) {
    var reward = giftBoxRewards[i];
    var expiry = GiftBoxExpirySummary(reward);
    html += '<tr onclick="PickGiftBoxReward(' + i + ')"><td>' + Html(reward.itemId) + "</td><td>" + Html(ItemCatalogName(reward.itemId)) +
      "</td><td>" + Html(reward.weight) + "</td><td>" + Html(reward.quantityMin + "-" + reward.quantityMax) + "</td><td>" + Html((reward.useDefaultOptions ? "mặc định" : "riêng") + ", " + reward.options.length + " option, " + expiry) + "</td></tr>";
  }
  document.getElementById("giftBoxRewardsTable").innerHTML = html + "</tbody>";
}

function PickGiftBoxReward(index) {
  selectedGiftBoxRewardId = "" + giftBoxRewards[index].itemId;
  ShowGiftBoxRewardEditor();
}

function HideGiftBoxRewardEditor() { document.getElementById("giftBoxRewardEditor").style.display = "none"; }

function ShowGiftBoxRewardEditor() {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId);
  if (!reward) { HideGiftBoxRewardEditor(); return; }
  NormalizeGiftBoxReward(reward);
  document.getElementById("giftBoxRewardEditor").style.display = "block";
  document.getElementById("giftBoxRewardTitle").innerText = "Cấu hình: " + reward.itemId + " - " + ItemCatalogName(reward.itemId);
  Set("giftBoxRewardWeight", reward.weight); Set("giftBoxRewardQtyMin", reward.quantityMin); Set("giftBoxRewardQtyMax", reward.quantityMax); Set("giftBoxRewardGender", reward.gender);
  document.getElementById("giftBoxRewardInitBase").checked = !!reward.initBaseOptions;
  document.getElementById("giftBoxRewardUseDefault").checked = !!reward.useDefaultOptions;
  Set("giftBoxOptionSearch", ""); RenderGiftBoxOptions(); LoadGiftBoxExpiryEditor();
}

function SetGiftBoxRewardField(field, value) {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId); if (!reward) return;
  reward[field] = (field == "initBaseOptions" || field == "useDefaultOptions") ? !!value : parseInt(value || "0", 10);
  RenderGiftBoxRewards();
}

function FindGiftBoxOption(reward, id) {
  for (var i = 0; i < reward.options.length; i++) if (("" + reward.options[i].id) == ("" + id)) return reward.options[i];
  return null;
}

function RenderGiftBoxOptions() {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId);
  if (!reward || !document.getElementById("giftBoxOptionList")) return;
  var q = (V("giftBoxOptionSearch") || "").toLowerCase();
  var html = "";
  for (var i = 1; i < optionRows.length; i++) {
    var id = optionRows[i][0], name = optionRows[i][1] || "";
    if (q && (id + " " + name).toLowerCase().indexOf(q) < 0) continue;
    var option = FindGiftBoxOption(reward, id);
    html += '<div class="param-option-row ' + (option ? "param-option-selected" : "") + '"><label><input type="checkbox" ' + (option ? "checked" : "") + ' onclick="ToggleGiftBoxOption(' + id + ', this.checked)"> ' + Html(id + " - " + name) + '</label>';
    if (option) html += '<span class="option-param-heading">min</span><input class="option-param-input" type="text" value="' + HtmlAttr(option.paramMin) + '" onchange="SetGiftBoxOptionParam(' + id + ',\'min\',this.value)"><span class="option-param-heading">max</span><input class="option-param-input" type="text" value="' + HtmlAttr(option.paramMax) + '" onchange="SetGiftBoxOptionParam(' + id + ',\'max\',this.value)">';
    html += "</div>";
  }
  document.getElementById("giftBoxOptionList").innerHTML = html || "Không tìm thấy option phù hợp.";
}

function ToggleGiftBoxOption(id, checked) {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId); if (!reward) return;
  var option = FindGiftBoxOption(reward, id);
  if (checked && !option) reward.options.push({ id: parseInt(id, 10), paramMin: 0, paramMax: 0 });
  if (!checked && option) {
    var next = []; for (var i = 0; i < reward.options.length; i++) if (("" + reward.options[i].id) != ("" + id)) next.push(reward.options[i]); reward.options = next;
  }
  RenderGiftBoxOptions(); RenderGiftBoxRewards();
}

function SetGiftBoxOptionParam(id, side, value) {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId), option = reward ? FindGiftBoxOption(reward, id) : null;
  if (!option) return;
  option[side == "min" ? "paramMin" : "paramMax"] = parseInt(value || "0", 10);
  RenderGiftBoxRewards();
}

function LoadGiftBoxExpiryEditor() {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId); if (!reward) return;
  var permanent = { weight: 0 }, days = { weight: 0, daysMin: 1, daysMax: 1 };
  for (var i = 0; i < reward.expiry.length; i++) {
    if ((reward.expiry[i].mode || "").toLowerCase() == "permanent") permanent = reward.expiry[i];
    if ((reward.expiry[i].mode || "").toLowerCase() == "days") days = reward.expiry[i];
  }
  Set("giftBoxExpiryPermanentWeight", permanent.weight || 0); Set("giftBoxExpiryDaysWeight", days.weight || 0); Set("giftBoxExpiryDaysMin", days.daysMin || 1); Set("giftBoxExpiryDaysMax", days.daysMax || 1);
}

function SetGiftBoxExpiry() {
  var reward = FindGiftBoxReward(selectedGiftBoxRewardId); if (!reward) return;
  var next = [];
  var permanentWeight = parseInt(V("giftBoxExpiryPermanentWeight") || "0", 10), daysWeight = parseInt(V("giftBoxExpiryDaysWeight") || "0", 10);
  if (permanentWeight > 0) next.push({ mode: "permanent", weight: permanentWeight, daysMin: 0, daysMax: 0 });
  if (daysWeight > 0) next.push({ mode: "days", weight: daysWeight, daysMin: parseInt(V("giftBoxExpiryDaysMin") || "1", 10), daysMax: parseInt(V("giftBoxExpiryDaysMax") || "1", 10) });
  reward.expiry = next; RenderGiftBoxRewards();
}

function GiftBoxExpirySummary(reward) {
  if (!reward.expiry || !reward.expiry.length) return "mặc định";
  var labels = [];
  for (var i = 0; i < reward.expiry.length; i++) labels.push(reward.expiry[i].mode == "permanent" ? "vĩnh viễn" : (reward.expiry[i].daysMin + "-" + reward.expiry[i].daysMax + " ngày"));
  return labels.join("/");
}

function SaveGiftBox() {
  var id = V("giftBoxId");
  if (!/^\d+$/.test(id)) { Msg("giftBoxMessage", "Cần nhập ID hộp hợp lệ."); return; }
  if (!giftBoxRewards.length) { Msg("giftBoxMessage", "Chọn ít nhất một vật phẩm trong checklist."); return; }
  var config = { boxTemplateId: parseInt(id, 10), enabled: document.getElementById("giftBoxEnabled").checked,
    minEmptySlots: parseInt(V("giftBoxMinSlots") || "1", 10), consumeQuantity: parseInt(V("giftBoxConsume") || "1", 10), drawCount: parseInt(V("giftBoxDrawCount") || "1", 10), rewards: giftBoxRewards };
  var text = RunAdmin("savegiftbox", { Id: id, PayloadJson: JSON.stringify(config) });
  Msg("giftBoxMessage", StatusText(text));
  if (!IsAdminError(text)) LoadGiftBoxes();
}

function DisableGiftBox() {
  if (!V("giftBoxId")) { Msg("giftBoxMessage", "Chọn hộp cần khóa trước."); return; }
  if (!window.confirm("Khóa cấu hình hộp ID " + V("giftBoxId") + "?")) return;
  var text = RunAdmin("deletegiftbox", { Id: V("giftBoxId") });
  Msg("giftBoxMessage", StatusText(text));
  if (!IsAdminError(text)) LoadGiftBoxes();
}

RegisterTab({
  id: "giftbox", view: "gift-boxes.html", panelId: "panelGiftBox", navId: "navGiftBox",
  title: "Cấu hình Hộp quà", subtitle: "Checklist vật phẩm, pool trọng số, option random min–max và hạn sử dụng",
  onOpen: function () { LoadOptions(false); LoadGiftBoxItems(false); LoadGiftBoxes(); },
  onRefresh: function () { LoadGiftBoxItems(true); LoadGiftBoxes(); }
});
