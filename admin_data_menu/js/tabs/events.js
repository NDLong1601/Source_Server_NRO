var eventRows = [];
var eventStats = [];
var eventProfileRows = [];
var eventBossRows = [];
var eventItemRows = [];
var eventPlayerRows = [];
var eventGrantRows = [];
var eventBossSelectedMaps = [];
var selectedEventCode = "";

function LoadStatus() {
  var rows = ParseTsv(RunAdmin("status", {}));
  var eventValue = "none";
  var expRate = "1";
  var serverRunning = "0";
  var restartPending = "0";
  var startedAt = "";
  for (var i = 1; i < rows.length; i++) {
    if (rows[i][0] == "server.event") eventValue = rows[i][1] || "none";
    if (rows[i][0] == "server.expserver") expRate = rows[i][1] || "1";
    if (rows[i][0] == "server.running") serverRunning = rows[i][1] || "0";
    if (rows[i][0] == "server.restart_pending") restartPending = rows[i][1] || "0";
    if (rows[i][0] == "server.started_at") startedAt = rows[i][1] || "";
  }
  Set("eventValue", eventValue);
  Set("expRate", expRate);
  document.getElementById("statusBox").innerText = "server.event=" + eventValue + "\r\nserver.expserver=" + expRate +
    "\r\nServer: " + (serverRunning == "1" ? "đang chạy" : "đã dừng") +
    (restartPending == "1" ? " / chờ restart" : "") + (startedAt ? "\r\nKhởi động: " + startedAt : "");
}

function LoadEvents() {
  LoadStatus();
  eventRows = ParseTsv(RunAdmin("listevents", {}));
  eventStats = ParseTsv(RunAdmin("eventstats", {}));
  RenderEventCards();
  RenderEventSummary();
  var selectedIndex = FindEventIndex(selectedEventCode);
  if (selectedIndex < 1 && eventRows.length > 1) selectedIndex = 1;
  if (selectedIndex > 0) SelectEventConfig(selectedIndex);
}

function EventStateLabel(state) {
  if (state == "active") return "Đang chạy";
  if (state == "pending") return "Chờ restart";
  if (state == "configured") return "Đã cấu hình";
  return "Đang tắt";
}

function EventHealthLabel(health) {
  if (health == "ready") return "Sẵn sàng";
  if (health == "error") return "Có lỗi";
  return "Cần kiểm tra";
}

function RenderEventCards() {
  var html = "";
  for (var i = 1; i < eventRows.length; i++) {
    var row = eventRows[i];
    var checked = row[2] == "1";
    html += '<div id="eventCard_' + HtmlAttr(row[0]) + '" class="' + EventCardClass(i) + '" onclick="SelectEventConfig(' + i + ')">' +
      '<input type="checkbox" id="eventToggle_' + HtmlAttr(row[0]) + '" ' + (checked ? "checked " : "") +
      'onclick="ToggleEvent(' + i + ', this.checked); event.cancelBubble=true;">' +
      '<div><span class="event-card-name">' + Html(row[1]) + '</span></div>' +
      '<div class="event-card-code">' + Html(row[0]) + ' · ' + Html(row[6]) + ' boss · ' + Html(row[10] || "0") + ' item</div>' +
      '<div><span class="event-badge ' + HtmlAttr(row[3]) + '">' + Html(EventStateLabel(row[3])) + '</span>' +
      '<span class="event-badge ' + HtmlAttr(row[4]) + '">' + Html(EventHealthLabel(row[4])) + '</span>' +
      '<span class="event-badge configured">Buff riêng</span>' +
      '<span class="event-badge configured">Drop x' + Html(row[9] || "1") + '</span></div>' +
      '<div class="event-card-detail">' + Html(row[7]) + '</div>' +
      '<div class="event-card-health">' + Html(row[5]) + '</div></div>';
  }
  document.getElementById("eventCards").innerHTML = html;
  UpdateEventSelection();
}

function FindEventIndex(code) {
  for (var i = 1; i < eventRows.length; i++) if (eventRows[i][0] == code) return i;
  return -1;
}

function EventCardClass(index) {
  if (!eventRows[index]) return "event-card";
  var value = "event-card";
  if (eventRows[index][2] == "1") value += " enabled";
  if (eventRows[index][0] == selectedEventCode) value += " selected";
  return value;
}

function ToggleEvent(index, checked) {
  if (!eventRows[index]) return;
  eventRows[index][2] = checked ? "1" : "0";
  var card = document.getElementById("eventCard_" + eventRows[index][0]);
  if (card) card.className = EventCardClass(index);
  UpdateEventSelection();
}

function SelectEventConfig(index) {
  if (!eventRows[index]) return;
  selectedEventCode = eventRows[index][0];
  Set("eventConfigCode", selectedEventCode);
  document.getElementById("eventConfigTitle").innerText = eventRows[index][1] + " (" + selectedEventCode + ")";
  for (var i = 1; i < eventRows.length; i++) {
    var card = document.getElementById("eventCard_" + eventRows[i][0]);
    if (card) card.className = EventCardClass(i);
  }
  LoadEventConfigDetail();
}

function LoadEventConfigDetail() {
  if (!selectedEventCode) return;
  eventProfileRows = ParseTsv(RunAdmin("geteventconfig", { EventValue: selectedEventCode }));
  eventBossRows = ParseTsv(RunAdmin("listeventbosses", { EventValue: selectedEventCode }));
  eventItemRows = ParseTsv(RunAdmin("listeventitems", { EventValue: selectedEventCode }));
  eventGrantRows = ParseTsv(RunAdmin("listeventpointgrants", { EventValue: selectedEventCode }));
  if (spawnMaps.length == 0) spawnMaps = ParseTsv(RunAdmin("listspawnmaps", {}));
  if (eventProfileRows.length > 1) {
    Set("eventConfigDescription", eventProfileRows[1][2]);
    Set("eventDropMultiplier", eventProfileRows[1][4]);
  }
  RenderEventBosses();
  RenderEventItems();
  RenderEventPointGrants();
  ClearEventBossForm();
  ClearEventItemForm();
}

function RenderEventBosses() {
  var html = '<thead><tr><th>ID</th><th>Boss</th><th>SL</th><th>Map</th><th>Trạng thái</th></tr></thead><tbody>';
  var enabledCount = 0;
  for (var i = 1; i < eventBossRows.length; i++) {
    var row = eventBossRows[i];
    if (row[3] == "1") enabledCount++;
    var mapCount = ParseNumberArray(row[5] || "[]").length;
    html += '<tr onclick="PickEventBoss(' + i + ')"><td>' + Html(row[0]) + '</td><td>' + Html(row[1]) +
      '</td><td>' + Html(row[2]) + '</td><td>' + Html(mapCount ? mapCount + " map" : "Mặc định") + '</td><td><span class="event-badge ' + (row[3] == "1" ? "active" : "disabled") + '">' +
      (row[3] == "1" ? "Đang bật" : "Đã tắt") + '</span></td></tr>';
  }
  document.getElementById("eventBossTable").innerHTML = html + '</tbody>';
  document.getElementById("eventBossCountBadge").innerText = enabledCount + " bật / " + Math.max(0, eventBossRows.length - 1);
}

function PickEventBoss(index) {
  var row = eventBossRows[index];
  Set("eventBossOriginalId", row[0]);
  Set("eventBossId", row[0]);
  document.getElementById("eventBossId").readOnly = true;
  Set("eventBossName", row[1]);
  Set("eventBossQuantity", row[2]);
  document.getElementById("eventBossEnabled").checked = row[3] == "1";
  Set("eventBossNotes", row[4]);
  eventBossSelectedMaps = ParseNumberArray(row[5] || "[]");
  Set("eventBossMapSearch", "");
  RenderEventBossMapChecklist();
}

function ClearEventBossForm() {
  Set("eventBossOriginalId", "");
  Set("eventBossId", "");
  document.getElementById("eventBossId").readOnly = false;
  Set("eventBossName", "");
  Set("eventBossQuantity", "1");
  Set("eventBossNotes", "");
  eventBossSelectedMaps = [];
  Set("eventBossMapSearch", "");
  RenderEventBossMapChecklist();
  document.getElementById("eventBossEnabled").checked = true;
}

function SaveEventBoss() {
  if (!selectedEventCode) return;
  var text = RunAdmin("saveeventboss", { EventValue: selectedEventCode, BossId: V("eventBossId"), Name: V("eventBossName"),
    BossQuantity: V("eventBossQuantity"), Enabled: CheckedValue("eventBossEnabled"), Notes: V("eventBossNotes"),
    MapIdsJson: JSON.stringify(eventBossSelectedMaps) });
  Msg("eventConfigMessage", StatusText(text));
  if (!IsAdminError(text)) { LoadEventConfigDetail(); RefreshEventCardsOnly(); }
}

function RenderEventBossMapChecklist() {
  if (!document.getElementById("eventBossMapChecklist")) return;
  var selected = {};
  for (var i = 0; i < eventBossSelectedMaps.length; i++) selected["" + eventBossSelectedMaps[i]] = true;
  RenderChecklist({
    listId: "eventBossMapChecklist", idPrefix: "eventBossMapChoice", rows: spawnMaps, rowStart: 1,
    query: V("eventBossMapSearch"), selected: selected, selectedOrder: eventBossSelectedMaps,
    toggleFunction: "ToggleEventBossMap", emptyText: "Không tìm thấy map phù hợp."
  });
  var names = [];
  for (var m = 0; m < eventBossSelectedMaps.length; m++) {
    names.push(eventBossSelectedMaps[m] + " - " + ChecklistLabelFromRows(spawnMaps, 1, 0, 1, "" + eventBossSelectedMaps[m]));
  }
  document.getElementById("eventBossMapSummary").innerText = names.length
    ? "Đã chọn " + names.length + " map; boss chọn ngẫu nhiên: " + names.join(", ")
    : "Chưa chọn map — boss dùng map mặc định trong BossesData.";
}

function ToggleEventBossMap(id, checked) {
  var value = parseInt(id, 10);
  var next = [];
  for (var i = 0; i < eventBossSelectedMaps.length; i++) if (eventBossSelectedMaps[i] != value) next.push(eventBossSelectedMaps[i]);
  if (checked) next.push(value);
  eventBossSelectedMaps = next;
  RenderEventBossMapChecklist();
}

function SearchEventPlayers() {
  if (!Trim(V("eventPlayerSearch"))) { Msg("eventConfigMessage", "Nhập ID hoặc tên player cần tìm."); return; }
  eventPlayerRows = ParseTsv(RunAdmin("searcheventplayers", { Search: V("eventPlayerSearch") }));
  var html = '<thead><tr><th>ID</th><th>Player</th><th>Tài khoản</th><th>Điểm</th><th>Trạng thái</th></tr></thead><tbody>';
  for (var i = 1; i < eventPlayerRows.length; i++) {
    var row = eventPlayerRows[i];
    var statusClass = row[4] == "ONLINE" ? "active" : (row[4] == "ONLINE?" ? "warning" : "off");
    var statusLabel = row[4] == "ONLINE" ? "Online" : (row[4] == "ONLINE?" ? "Có thể online" : "Offline");
    html += '<tr onclick="PickEventPointPlayer(' + i + ')"><td>' + Html(row[0]) + '</td><td>' + Html(row[1]) +
      '</td><td>' + Html(row[2]) + '</td><td>' + Html(FormatNumber(row[3] || "0")) + '</td><td><span class="event-badge ' +
      statusClass + '">' + Html(statusLabel) + '</span></td></tr>';
  }
  document.getElementById("eventPlayerTable").innerHTML = html + '</tbody>';
  Msg("eventConfigMessage", eventPlayerRows.length > 1 ? "Chọn đúng player trong kết quả rồi nhập số điểm." : "Không tìm thấy player.");
}

function PickEventPointPlayer(index) {
  if (!eventPlayerRows[index]) return;
  Set("eventPlayerSearch", eventPlayerRows[index][0]);
  Msg("eventConfigMessage", "Đã chọn " + eventPlayerRows[index][1] + " (ID " + eventPlayerRows[index][0] + "), điểm hiện tại " + FormatNumber(eventPlayerRows[index][3] || "0") + ".");
}

function GrantEventPoints() {
  if (!selectedEventCode) return;
  if (!Trim(V("eventPlayerSearch"))) { Msg("eventConfigMessage", "Nhập hoặc chọn player cần buff."); return; }
  if (!window.confirm("Cộng " + V("eventPlayerPoints") + " điểm sự kiện cho player " + V("eventPlayerSearch") + "?")) return;
  var text = RunAdmin("granteventpoint", { EventValue: selectedEventCode, Search: V("eventPlayerSearch"), Points: V("eventPlayerPoints") });
  if (!IsAdminError(text)) {
    eventGrantRows = ParseTsv(RunAdmin("listeventpointgrants", { EventValue: selectedEventCode }));
    RenderEventPointGrants();
    SearchEventPlayers();
  }
  Msg("eventConfigMessage", StatusText(text));
}

function RenderEventPointGrants() {
  var html = '<thead><tr><th>Player</th><th>Điểm</th><th>Thời gian</th><th>Trạng thái</th></tr></thead><tbody>';
  for (var i = 1; i < eventGrantRows.length; i++) {
    var row = eventGrantRows[i];
    var badgeClass = row[4] == "processed" ? "active" : (row[4] == "failed" ? "error" : "pending");
    var label = row[4] == "processed" ? "Đã cộng" : (row[4] == "failed" ? "Lỗi" : "Đang xử lý");
    html += '<tr><td>' + Html(row[1] + " - " + row[2]) + '</td><td>+' + Html(FormatNumber(row[3])) +
      '</td><td>' + Html(row[5]) + '</td><td><span class="event-badge ' + badgeClass + '">' + label + '</span></td></tr>';
  }
  document.getElementById("eventGrantTable").innerHTML = html + '</tbody>';
}

function DeleteEventBoss() {
  if (!V("eventBossOriginalId")) { Msg("eventConfigMessage", "Chọn một boss cần xóa."); return; }
  if (!window.confirm("Xóa Boss " + V("eventBossName") + " khỏi cấu hình " + selectedEventCode + "?")) return;
  var text = RunAdmin("deleteeventboss", { EventValue: selectedEventCode, BossId: V("eventBossOriginalId") });
  Msg("eventConfigMessage", StatusText(text));
  if (!IsAdminError(text)) { LoadEventConfigDetail(); RefreshEventCardsOnly(); }
}

function EventSourceLabel(source) {
  if (source == "mob") return "Mob";
  if (source == "boss") return "Boss";
  if (source == "built_in") return "Code sẵn";
  if (source == "reward") return "Phần thưởng";
  return "Gameplay";
}

function RenderEventItems() {
  var html = '<thead><tr><th>Item</th><th>Nguồn</th><th>SL</th><th>Tỉ lệ</th><th>TT</th></tr></thead><tbody>';
  var enabledCount = 0;
  for (var i = 1; i < eventItemRows.length; i++) {
    var row = eventItemRows[i];
    if (row[8] == "1") enabledCount++;
    html += '<tr onclick="PickEventItem(' + i + ')"><td>' + Html(row[1] + " - " + row[2]) + '</td><td><span class="event-badge ' +
      HtmlAttr(row[3]) + '">' + Html(EventSourceLabel(row[3])) + '</span></td><td>' + Html(row[5] + "-" + row[6]) +
      '</td><td>' + Html(row[7]) + '%</td><td><span class="event-badge ' + (row[8] == "1" ? "active" : "disabled") + '">' +
      (row[8] == "1" ? "Bật" : "Tắt") + '</span></td></tr>';
  }
  document.getElementById("eventItemTable").innerHTML = html + '</tbody>';
  document.getElementById("eventItemCountBadge").innerText = enabledCount + " bật / " + Math.max(0, eventItemRows.length - 1);
}

function PickEventItem(index) {
  var row = eventItemRows[index];
  Set("eventItemRowId", row[0]);
  Set("eventItemId", row[1]);
  Set("eventItemSource", row[3]);
  Set("eventItemSourceId", row[4]);
  Set("eventItemMin", row[5]);
  Set("eventItemMax", row[6]);
  Set("eventItemRate", row[7]);
  document.getElementById("eventItemEnabled").checked = row[8] == "1";
  Set("eventItemNotes", row[9]);
}

function ClearEventItemForm() {
  Set("eventItemRowId", "");
  Set("eventItemId", "");
  Set("eventItemSource", "mob");
  Set("eventItemSourceId", "-1");
  Set("eventItemMin", "1");
  Set("eventItemMax", "1");
  Set("eventItemRate", "100");
  Set("eventItemNotes", "");
  document.getElementById("eventItemEnabled").checked = true;
}

function SaveEventItem() {
  if (!selectedEventCode) return;
  var text = RunAdmin("saveeventitem", { EventValue: selectedEventCode, Id: V("eventItemRowId"), TemplateId: V("eventItemId"),
    SourceType: V("eventItemSource"), SourceId: V("eventItemSourceId"), QuantityMin: V("eventItemMin"), QuantityMax: V("eventItemMax"),
    DropRate: V("eventItemRate"), Enabled: CheckedValue("eventItemEnabled"), Notes: V("eventItemNotes") });
  Msg("eventConfigMessage", StatusText(text));
  if (!IsAdminError(text)) { LoadEventConfigDetail(); RefreshEventCardsOnly(); }
}

function DeleteEventItem() {
  if (!V("eventItemRowId")) { Msg("eventConfigMessage", "Chọn một vật phẩm cần xóa."); return; }
  if (!window.confirm("Xóa Item " + V("eventItemId") + " khỏi cấu hình " + selectedEventCode + "?")) return;
  var text = RunAdmin("deleteeventitem", { EventValue: selectedEventCode, Id: V("eventItemRowId") });
  Msg("eventConfigMessage", StatusText(text));
  if (!IsAdminError(text)) { LoadEventConfigDetail(); RefreshEventCardsOnly(); }
}

function SaveEventConfig() {
  if (!selectedEventCode) return;
  var text = RunAdmin("saveeventconfig", { EventValue: selectedEventCode, Description: V("eventConfigDescription"),
    DropMultiplier: V("eventDropMultiplier") });
  Msg("eventConfigMessage", StatusText(text));
  if (!IsAdminError(text)) RefreshEventCardsOnly();
}

function RefreshEventCardsOnly() {
  eventRows = ParseTsv(RunAdmin("listevents", {}));
  RenderEventCards();
  RenderEventSummary();
}

function UpdateEventSelection() {
  var codes = [];
  var names = [];
  var bossCount = 0;
  for (var i = 1; i < eventRows.length; i++) {
    if (eventRows[i][2] != "1") continue;
    codes.push(eventRows[i][0]);
    names.push(eventRows[i][1]);
    bossCount += parseInt(eventRows[i][6] || "0", 10);
  }
  Set("eventValue", codes.length ? codes.join(",") : "none");
  document.getElementById("eventSelection").innerText = codes.length ?
    ("Đã chọn " + codes.length + " sự kiện · dự kiến " + bossCount + " boss\r\n" + names.join(", ")) :
    "Không có sự kiện theo mùa được chọn.";
}

function RenderEventSummary() {
  if (eventStats.length < 2) return;
  var s = eventStats[1];
  var enabled = 0;
  for (var i = 1; i < eventRows.length; i++) if (eventRows[i][2] == "1") enabled++;
  var runtimeText = s[0] == "1" ? (s[1] == "1" ? "Chờ restart" : "Đang chạy") : "Đã dừng";
  var runtimeClass = s[0] != "1" ? "off" : (s[1] == "1" ? "pending" : "active");
  document.getElementById("eventRuntimeBadge").className = "event-badge " + runtimeClass;
  document.getElementById("eventRuntimeBadge").innerText = runtimeText;
  document.getElementById("eventSummary").innerHTML =
    EventStatHtml("Runtime", runtimeText) +
    EventStatHtml("Sự kiện đã chọn", enabled) +
    EventStatHtml("Player có điểm", FormatNumber(s[4] || "0")) +
    EventStatHtml("Tổng điểm sự kiện", FormatNumber(s[5] || "0"));
}

function EventStatHtml(label, value) {
  return '<div class="event-stat"><div class="event-stat-label">' + Html(label) +
    '</div><div class="event-stat-value">' + Html(value) + '</div></div>';
}

function SaveEvent() {
  UpdateEventSelection();
  var warningNames = [];
  var errorNames = [];
  for (var i = 1; i < eventRows.length; i++) {
    if (eventRows[i][2] != "1") continue;
    if (eventRows[i][4] == "error") errorNames.push(eventRows[i][1]);
    if (eventRows[i][4] == "warning") warningNames.push(eventRows[i][1]);
  }
  if (errorNames.length) {
    Msg("eventMessage", "ERROR: Không thể bật event đang thiếu dependency: " + errorNames.join(", "));
    return;
  }
  if (warningNames.length && !window.confirm("Các event sau còn cảnh báo: " + warningNames.join(", ") + ". Vẫn lưu cấu hình?")) return;
  var a = StatusText(RunAdmin("setevent", { EventValue: V("eventValue") }));
  var b = StatusText(RunAdmin("setexp", { ExpRate: V("expRate") }));
  Msg("eventMessage", a + "\r\n" + b);
  LoadEvents();
}

RegisterTab({
  id: "events", view: "events.html", panelId: "panelEvents", navId: "navEvents",
  title: "Sự kiện", subtitle: "Boss nhiều map, vật phẩm và buff điểm cho từng player",
  onOpen: function () { LoadEvents(); },
  onRefresh: function () { LoadEvents(); }
});
