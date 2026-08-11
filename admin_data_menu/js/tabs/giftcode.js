var giftCodeRows = [];
var giftItemRows = [];
var giftItemNameMap = {};
var giftRewards = [];
var selectedGiftRewardId = "";
var giftItemFilterTimer = 0;
var giftOptionFilterTimer = 0;

function LoadGiftCodes() {
  var rows = ParseTsv(RunAdmin("listgiftcodes", { Search: V("giftSearch") }));
  if (rows.length && (rows[0][0] != "id" || rows[0][1] != "code")) {
    Msg("giftMessage", "Dữ liệu trả về không phải bảng giftcode. Hãy bấm Làm mới.");
    return;
  }
  giftCodeRows = rows;
  RenderGiftCodes();
  Msg("giftMessage", giftCodeRows.length > 1 ? "Đã tải " + (giftCodeRows.length - 1) + " Giftcode." : "Không có Giftcode phù hợp.");
  AdjustTableOffsets();
}

function RenderGiftCodes() {
  var html = '<colgroup><col style="width:6%"><col style="width:13%"><col style="width:25%"><col style="width:10%"><col style="width:17%"><col style="width:17%"><col style="width:12%"></colgroup>' +
    "<thead><tr><th>ID</th><th>Giftcode</th><th>Quà</th><th>Còn lại</th><th>Từ ngày</th><th>Đến ngày</th><th>Trạng thái</th></tr></thead><tbody>";
  for (var i = 1; i < giftCodeRows.length; i++) {
    var row = giftCodeRows[i];
    var status = GiftStatus(row[6]);
    html += '<tr onclick="PickGiftCode(' + i + ')">' +
      "<td>" + Html(row[0] || "") + "</td>" +
      "<td>" + Html(row[1] || "") + "</td>" +
      "<td>" + Html(GiftRewardSummary(row[3])) + "</td>" +
      "<td>" + Html(row[2] == "-1" ? "Không giới hạn" : FormatNumber(row[2] || "0")) + "</td>" +
      "<td>" + Html(FormatDateTime(row[4] || "")) + "</td>" +
      "<td>" + Html(FormatDateTime(row[5] || "")) + "</td>" +
      '<td class="gift-status-' + Html(row[6] || "expired") + '">' + status + "</td></tr>";
  }
  document.getElementById("giftCodesTable").innerHTML = html + "</tbody>";
}

function GiftStatus(status) {
  if (status == "active") return "Còn";
  if (status == "empty") return "Hết lượt";
  if (status == "waiting") return "Chưa bắt đầu";
  return "Hết hạn";
}

function ParseGiftRewards(detail) {
  try {
    var parsed = JSON.parse(detail || "[]");
    var rewards = [];
    for (var i = 0; i < parsed.length; i++) {
      var options = parsed[i].options || [];
      rewards.push({
        id: "" + parsed[i].id,
        name: GiftItemName(parsed[i].id),
        quantity: "" + (parsed[i].quantity || 1),
        options: options
      });
    }
    return rewards;
  } catch (e) {
    return [];
  }
}

function GiftRewardSummary(detail) {
  var rewards = ParseGiftRewards(detail);
  var labels = [];
  for (var i = 0; i < rewards.length; i++) labels.push("x" + rewards[i].quantity + " " + rewards[i].name);
  return labels.join("; ");
}

function PickGiftCode(index) {
  var row = giftCodeRows[index];
  Set("giftId", row[0]);
  Set("giftCode", row[1]);
  Set("giftCount", row[2] == "-1" ? "0" : row[2]);
  document.getElementById("giftUnlimited").checked = row[2] == "-1";
  ToggleGiftUnlimited();
  SetGiftExpiryMode("range");
  Set("giftStart", row[4]);
  Set("giftEnd", row[5]);
  giftRewards = ParseGiftRewards(row[3]);
  selectedGiftRewardId = "";
  RenderGiftItems();
  RenderGiftRewardsTable();
  HideGiftRewardEditor();
  Msg("giftMessage", "Đang sửa Giftcode " + row[1] + ".");
}

function NewGiftCode() {
  Set("giftId", "");
  Set("giftCode", "");
  Set("giftCount", "100");
  Set("giftDays", "30");
  Set("giftStart", "");
  Set("giftEnd", "");
  document.getElementById("giftUnlimited").checked = false;
  ToggleGiftUnlimited();
  SetGiftExpiryMode("days");
  giftRewards = [];
  selectedGiftRewardId = "";
  RenderGiftItems();
  RenderGiftRewardsTable();
  HideGiftRewardEditor();
  Msg("giftMessage", "Đang tạo Giftcode mới.");
}

function SetGiftExpiryMode(mode) {
  document.getElementById("giftExpiryDays").checked = mode == "days";
  document.getElementById("giftExpiryRange").checked = mode == "range";
  document.getElementById("giftDaysConfig").style.display = mode == "days" ? "block" : "none";
  document.getElementById("giftRangeConfig").style.display = mode == "range" ? "block" : "none";
}

function ToggleGiftUnlimited() {
  var unlimited = document.getElementById("giftUnlimited").checked;
  document.getElementById("giftCount").disabled = unlimited;
}

function LoadGiftItems(force) {
  LoadItemCatalog(force);
  SyncGiftItemCatalog();
  RenderGiftItems();
  RenderGiftRewardsTable();
}

function SyncGiftItemCatalog() {
  giftItemRows = [["id", "item_name", "description", "item_type", "gender"]];
  giftItemRows.push(["-1", "Vàng", "", "-1", ""]);
  giftItemRows.push(["-2", "Ngọc", "", "-1", ""]);
  giftItemRows.push(["-3", "Ngọc khóa", "", "-1", ""]);
  for (var c = 1; c < itemCatalogRows.length; c++) giftItemRows.push(itemCatalogRows[c]);
  giftItemNameMap = {};
  for (var r = 1; r < giftItemRows.length; r++) giftItemNameMap[giftItemRows[r][0]] = giftItemRows[r][1] || ("Vật phẩm " + giftItemRows[r][0]);
  for (var i = 0; i < giftRewards.length; i++) giftRewards[i].name = GiftItemName(giftRewards[i].id);
}

function FilterGiftItems() {
  if (giftItemFilterTimer) window.clearTimeout(giftItemFilterTimer);
  giftItemFilterTimer = window.setTimeout(function () { RenderGiftItems(); }, 140);
}

function FilterGiftOptions() {
  if (giftOptionFilterTimer) window.clearTimeout(giftOptionFilterTimer);
  giftOptionFilterTimer = window.setTimeout(function () { RenderGiftOptionPicker(); }, 140);
}

function GiftItemName(id) {
  id = "" + id;
  if (id == "-1") return "Vàng";
  if (id == "-2") return "Ngọc";
  if (id == "-3") return "Ngọc khóa";
  if (giftItemNameMap[id]) return giftItemNameMap[id];
  return ItemCatalogName(id);
}

function GiftItemLabel(id) {
  return parseInt(id, 10) < 0 ? GiftItemName(id) : ItemCatalogLabel(id);
}

function FindGiftReward(id) {
  id = "" + id;
  for (var i = 0; i < giftRewards.length; i++) if (giftRewards[i].id == id) return giftRewards[i];
  return null;
}

function RenderGiftItems() {
  if (!document.getElementById("giftItemList")) return;
  var selected = {};
  var selectedOrder = [];
  var selectedLabels = {};
  for (var s = 0; s < giftRewards.length; s++) {
    var reward = giftRewards[s];
    selected[reward.id] = true;
    selectedOrder.push(reward.id);
    selectedLabels[reward.id] = reward.name;
  }
  RenderItemPicker({
    listId: "giftItemList",
    idPrefix: "giftItem",
    rows: giftItemRows,
    rowStart: 1,
    query: V("giftItemSearch"),
    selected: selected,
    selectedOrder: selectedOrder,
    selectedLabels: selectedLabels,
    labelResolver: GiftItemLabel,
    searchIndexes: [1, 2, 3, 4],
    toggleFunction: "ToggleGiftItem",
    limit: 100,
    emptyText: "Không tìm thấy vật phẩm phù hợp."
  });
  UpdateGiftItemSummary();
}

function UpdateGiftItemSummary() {
  document.getElementById("giftItemSummary").innerText = giftRewards.length ? "Đã chọn " + giftRewards.length + " phần quà" : "Chưa chọn phần quà";
}

function ToggleGiftItem(id, checked) {
  var reward = FindGiftReward(id);
  if (checked && !reward) {
    giftRewards.push({ id: "" + id, name: GiftItemName(id), quantity: "1", options: [] });
    selectedGiftRewardId = "" + id;
  } else if (!checked && reward) {
    var next = [];
    for (var i = 0; i < giftRewards.length; i++) if (giftRewards[i].id != id) next.push(giftRewards[i]);
    giftRewards = next;
    if (selectedGiftRewardId == id) selectedGiftRewardId = "";
  }
  UpdateGiftItemSummary();
  RenderGiftRewardsTable();
  if (checked) ShowGiftRewardEditor();
  else if (!selectedGiftRewardId) HideGiftRewardEditor();
}

function RenderGiftRewardsTable() {
  var html = "<thead><tr><th>ID</th><th>Vật phẩm</th><th>Số lượng</th><th>Options</th></tr></thead><tbody>";
  for (var i = 0; i < giftRewards.length; i++) {
    var reward = giftRewards[i];
    var optionLabels = [];
    for (var o = 0; o < reward.options.length; o++) optionLabels.push(reward.options[o].id + ":" + reward.options[o].param);
    html += '<tr onclick="PickGiftReward(' + i + ')"><td>' + Html(reward.id) + "</td><td>" + Html(reward.name) +
      "</td><td>" + Html(reward.quantity) + "</td><td>" + Html(optionLabels.join(", ")) + "</td></tr>";
  }
  document.getElementById("giftRewardsTable").innerHTML = html + "</tbody>";
}

function PickGiftReward(index) {
  selectedGiftRewardId = giftRewards[index].id;
  ShowGiftRewardEditor();
}

function HideGiftRewardEditor() {
  document.getElementById("giftRewardEditor").style.display = "none";
}

function ShowGiftRewardEditor() {
  var reward = FindGiftReward(selectedGiftRewardId);
  if (!reward) { HideGiftRewardEditor(); return; }
  document.getElementById("giftRewardEditor").style.display = "block";
  document.getElementById("giftRewardTitle").innerText = "Cấu hình: " + reward.id + " - " + reward.name;
  Set("giftRewardQuantity", reward.quantity);
  Set("giftOptionSearch", "");
  RenderGiftOptionPicker();
}

function SetGiftRewardQuantity() {
  var reward = FindGiftReward(selectedGiftRewardId);
  if (!reward) return;
  reward.quantity = V("giftRewardQuantity");
  RenderGiftRewardsTable();
}

function FindGiftRewardOption(reward, id) {
  for (var i = 0; i < reward.options.length; i++) if (("" + reward.options[i].id) == ("" + id)) return reward.options[i];
  return null;
}

function RenderGiftOptionPicker() {
  var reward = FindGiftReward(selectedGiftRewardId);
  if (!reward || !document.getElementById("giftOptionList")) return;
  if (parseInt(reward.id, 10) < 0) {
    reward.options = [];
    document.getElementById("giftOptionList").innerHTML = "Vàng, ngọc và ngọc khóa không sử dụng option.";
    Set("giftRewardOptionCount", "0");
    return;
  }
  var selectedMap = {};
  var selectedOrder = [];
  var paramValues = {};
  for (var s = 0; s < reward.options.length; s++) {
    var option = reward.options[s];
    var optionId = "" + option.id;
    selectedMap[optionId] = true;
    selectedOrder.push(optionId);
    paramValues[optionId] = option.param;
  }
  RenderOptionChecklist({
    listId: "giftOptionList",
    idPrefix: "giftOption",
    query: V("giftOptionSearch"),
    selected: selectedMap,
    selectedOrder: selectedOrder,
    paramValues: paramValues,
    labelResolver: OptionName,
    toggleFunction: "ToggleGiftRewardOption",
    limit: 120,
    showParam: true,
    paramFunction: "SetGiftRewardOptionParam",
    itemClass: "param-option-row",
    selectedClass: "param-option-selected",
    paramClass: "param-option-input",
    emptyText: "Không tìm thấy option phù hợp."
  });
  Set("giftRewardOptionCount", "" + reward.options.length);
}

function ToggleGiftRewardOption(id, checked) {
  var reward = FindGiftReward(selectedGiftRewardId);
  if (!reward) return;
  var option = FindGiftRewardOption(reward, id);
  if (checked && !option) reward.options.push({ id: parseInt(id, 10), param: 0 });
  if (!checked && option) {
    var next = [];
    for (var i = 0; i < reward.options.length; i++) if (("" + reward.options[i].id) != ("" + id)) next.push(reward.options[i]);
    reward.options = next;
  }
  RenderGiftOptionPicker();
  RenderGiftRewardsTable();
}

function SetGiftRewardOptionParam(id, value) {
  var reward = FindGiftReward(selectedGiftRewardId);
  var option = reward ? FindGiftRewardOption(reward, id) : null;
  if (option) option.param = value;
  RenderGiftRewardsTable();
}

function SaveGiftCode() {
  if (!Trim(V("giftCode"))) { Msg("giftMessage", "Cần nhập mã Giftcode."); return; }
  if (!giftRewards.length) { Msg("giftMessage", "Chọn ít nhất một phần quà."); return; }
  var mode = document.getElementById("giftExpiryRange").checked ? "range" : "days";
  var detail = [];
  for (var i = 0; i < giftRewards.length; i++) {
    detail.push({ id: parseInt(giftRewards[i].id, 10), quantity: parseInt(giftRewards[i].quantity, 10), options: giftRewards[i].options });
  }
  var text = RunAdmin("savegiftcode", {
    Id: V("giftId"), GiftCode: V("giftCode"), CountLeft: document.getElementById("giftUnlimited").checked ? "-1" : V("giftCount"),
    GiftDetail: JSON.stringify(detail), ExpiryMode: mode, ValidDays: V("giftDays"), StartDate: V("giftStart"), EndDate: V("giftEnd")
  });
  if (text.indexOf("OK\t") == 0) LoadGiftCodes();
  Msg("giftMessage", StatusText(text));
}

function DeleteGiftCode() {
  if (!V("giftId")) { Msg("giftMessage", "Chọn Giftcode cần xóa."); return; }
  if (!window.confirm("Xóa Giftcode " + V("giftCode") + "?")) return;
  var text = RunAdmin("deletegiftcode", { Id: V("giftId") });
  if (text.indexOf("OK\t") == 0) { NewGiftCode(); LoadGiftCodes(); }
  Msg("giftMessage", StatusText(text));
}

RegisterTab({
  id: "gift", view: "giftcode.html", panelId: "panelGift", navId: "navGift",
  title: "Quản lý Giftcode", subtitle: "Quản lý mã, số lượt, thời gian và quà kèm option riêng",
  onOpen: function () { LoadGiftItems(false); LoadGiftCodes(); },
  onRefresh: function () { LoadGiftItems(true); LoadGiftCodes(); }
});
