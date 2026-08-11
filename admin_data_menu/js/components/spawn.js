var spawnMaps = [];
var spawnItems = [];
var spawnItemNames = {};

function LoadSpawnLookups() {
  if (spawnMaps.length == 0) {
    spawnMaps = ParseTsv(RunAdmin("listspawnmaps", {}));
  }
  RenderBossMapChecklist();
  LoadItemCatalog(false);
  spawnItems = itemCatalogRows;
  spawnItemNames = {};
  for (var r = 1; r < spawnItems.length; r++) spawnItemNames[spawnItems[r][0]] = ItemCatalogName(spawnItems[r][0]);
  RenderBossItemChecklist();
}

function ToggleSpawnSchedule(prefix) {
  document.getElementById(prefix + "TimeStart").disabled = !document.getElementById(prefix + "UseRange").checked;
  document.getElementById(prefix + "TimeEnd").disabled = !document.getElementById(prefix + "UseRange").checked;
  document.getElementById(prefix + "Interval").disabled = !document.getElementById(prefix + "UseInterval").checked;
}

function SpawnScheduleLabel(useRange, start, end, useInterval, minutes) {
  var labels = [];
  if (useRange == "1") labels.push((start || "--:--") + "–" + (end || "--:--"));
  if (useInterval == "1") labels.push(minutes + " phút/lần");
  return labels.length ? labels.join(" + ") : "Luôn hoạt động";
}

function ParseNumberArray(json) {
  try {
    var parsed = JSON.parse(json || "[]");
    var result = [];
    if (!parsed || parsed.length == null) return result;
    for (var i = 0; i < parsed.length; i++) {
      var value = parseInt(parsed[i], 10);
      if (!isNaN(value) && result.indexOf(value) < 0) result.push(value);
    }
    return result;
  } catch (e) { return []; }
}

function ParseSpawnDrops(json) {
  try {
    var parsed = JSON.parse(json || "[]");
    return parsed && parsed.length != null ? parsed : [];
  } catch (e) { return []; }
}

function ParseSpawnOptions(text, messageId) {
  var result = [];
  text = Trim(text);
  if (!text) return result;
  var parts = text.split(",");
  for (var i = 0; i < parts.length; i++) {
    var pair = Trim(parts[i]).split(":");
    if (pair.length != 2 || !/^\d+$/.test(Trim(pair[0])) || !/^-?\d+$/.test(Trim(pair[1]))) {
      Msg(messageId, "Chỉ số không hợp lệ. Dùng dạng optionId:param, ví dụ 77:15,103:20.");
      return null;
    }
    result.push({ id: parseInt(pair[0], 10), param: parseInt(pair[1], 10) });
  }
  return result;
}

function SpawnOptionsText(options) {
  var labels = [];
  options = options || [];
  for (var i = 0; i < options.length; i++) {
    var min = options[i].paramMin == null ? options[i].param : options[i].paramMin;
    var max = options[i].paramMax == null ? min : options[i].paramMax;
    labels.push(options[i].id + ":" + (min == max ? min : min + "–" + max));
  }
  return labels.join(",");
}

function AddSpawnDrop(prefix) {
  var messageId = prefix + "Message";
  if (!/^\d+$/.test(V(prefix + "DropItem")) || !/^\d+$/.test(V(prefix + "DropMin")) || !/^\d+$/.test(V(prefix + "DropMax")) || !/^\d+(\.\d+)?$/.test(V(prefix + "DropRate"))) {
    Msg(messageId, "Item, số lượng và tỉ lệ phải là số hợp lệ."); return;
  }
  var rate = parseFloat(V(prefix + "DropRate"));
  if (rate < 0 || rate > 100 || parseInt(V(prefix + "DropMin"), 10) < 1 || parseInt(V(prefix + "DropMin"), 10) > parseInt(V(prefix + "DropMax"), 10)) {
    Msg(messageId, "Tỉ lệ phải từ 0–100 và số lượng min không lớn hơn max."); return;
  }
  var options = ParseSpawnOptions(V(prefix + "DropOptions"), messageId); if (options == null) return;
  var drop = { itemId: parseInt(V(prefix + "DropItem"), 10), quantityMin: parseInt(V(prefix + "DropMin"), 10), quantityMax: parseInt(V(prefix + "DropMax"), 10), dropRate: rate, options: options };
  var list = prefix == "boss" ? bossDrops : mobDrops;
  var selected = prefix == "boss" ? selectedBossDrop : selectedMobDrop;
  if (selected >= 0) list[selected] = drop; else list.push(drop);
  if (prefix == "boss") selectedBossDrop = -1; else selectedMobDrop = -1;
  ClearSpawnDropEditor(prefix); RenderSpawnDrops(prefix);
  Msg(messageId, "Đã cập nhật danh sách drop; bấm Lưu để ghi vào database.");
}

function RenderSpawnDrops(prefix) {
  var list = prefix == "boss" ? bossDrops : mobDrops;
  var html = "<thead><tr><th>Item</th><th>Số lượng</th><th>Tỉ lệ</th><th>Chỉ số</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < list.length; i++) {
    var d = list[i];
    html += '<tr onclick="PickSpawnDrop(\'' + prefix + '\',' + i + ')"><td>' + Html(d.itemId + " - " + (spawnItemNames[d.itemId] || "Không rõ")) + '</td><td>' + Html(d.quantityMin + "–" + d.quantityMax) + '</td><td>' + Html(d.dropRate + "%") + '</td><td>' + Html(SpawnOptionsText(d.options)) + '</td><td><button class="danger" onclick="RemoveSpawnDrop(\'' + prefix + '\',' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  document.getElementById(prefix + "DropsTable").innerHTML = html + "</tbody>";
}

function PickSpawnDrop(prefix, index) {
  var list = prefix == "boss" ? bossDrops : mobDrops; var d = list[index];
  if (prefix == "boss") selectedBossDrop = index; else selectedMobDrop = index;
  Set(prefix + "DropItem", d.itemId); Set(prefix + "DropMin", d.quantityMin); Set(prefix + "DropMax", d.quantityMax); Set(prefix + "DropRate", d.dropRate); Set(prefix + "DropOptions", SpawnOptionsText(d.options));
  ShowSpawnItemName(prefix);
}

function RemoveSpawnDrop(prefix, index) {
  var list = prefix == "boss" ? bossDrops : mobDrops; list.splice(index, 1);
  if (prefix == "boss") selectedBossDrop = -1; else selectedMobDrop = -1;
  ClearSpawnDropEditor(prefix); RenderSpawnDrops(prefix);
}

function ClearSpawnDropEditor(prefix) {
  Set(prefix + "DropItem", ""); Set(prefix + "DropItemName", ""); Set(prefix + "DropMin", "1"); Set(prefix + "DropMax", "1"); Set(prefix + "DropRate", "100"); Set(prefix + "DropOptions", "");
  if (prefix == "boss") selectedBossDrop = -1; else selectedMobDrop = -1;
}

function ShowSpawnItemName(prefix) {
  Set(prefix + "DropItemName", spawnItemNames[V(prefix + "DropItem")] || "Không tìm thấy item");
}
