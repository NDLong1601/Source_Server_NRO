var auditRows = [];
var selectedAuditIndex = -1;

function LoadAuditHistory() {
  auditRows = ParseTsv(RunAdmin("listaudit", { Search: V("auditSearch") }));
  selectedAuditIndex = -1;
  var html = "<thead><tr><th>ID</th><th>Thời gian</th><th>Hành động</th><th>Nội dung</th><th>Trạng thái</th><th>Hoàn tác</th></tr></thead><tbody>";
  for (var i = 1; i < auditRows.length; i++) {
    var row = auditRows[i];
    var status = row[4] == "success" ? "Thành công" : (row[4] == "failed" ? "Thất bại" : row[4]);
    var undo = row[6] ? "Đã hoàn tác" : (row[7] == "1" ? "Có thể hoàn tác" : (row[5] == "1" ? "Đang khóa" : "Không hỗ trợ"));
    html += '<tr onclick="PickAuditEntry(' + i + ')"><td>' + Html(row[0]) + '</td><td>' + Html(FormatDateTime(row[1])) + '</td><td>' + Html(row[2]) + '</td><td>' + Html(row[3]) + '</td><td>' + Html(status) + '</td><td>' + Html(undo) + '</td></tr>';
  }
  document.getElementById("auditTable").innerHTML = html + "</tbody>";
  ClearAuditDetail();
  Msg("auditMessage", auditRows.length > 1 ? "Đã tải " + (auditRows.length - 1) + " thao tác gần nhất." : "Chưa có thao tác thay đổi nào được ghi lại.");
  AdjustTableOffsets();
}

function PickAuditEntry(index) {
  var row = auditRows[index]; selectedAuditIndex = index;
  Set("auditId", row[0]); Set("auditCanUndo", row[7]); Set("auditDetailId", row[0]); Set("auditDetailTime", FormatDateTime(row[1]));
  Set("auditDetailAction", row[2]); Set("auditDetailSummary", row[3]); Set("auditDetailResult", row[8]);
  var state = row[6] ? "Đã hoàn tác lúc " + FormatDateTime(row[6]) : (row[7] == "1" ? "Có thể hoàn tác ngay" : (row[5] == "1" ? "Phải hoàn tác các thay đổi mới hơn trước" : "Thao tác này không hỗ trợ hoàn tác"));
  Set("auditUndoState", state);
  document.getElementById("auditUndoButton").disabled = row[7] != "1";
  Msg("auditMessage", "Đang xem lịch sử #" + row[0] + ".");
}

function ClearAuditDetail() {
  Set("auditId", ""); Set("auditCanUndo", "0"); Set("auditDetailId", ""); Set("auditDetailTime", "");
  Set("auditDetailAction", ""); Set("auditDetailSummary", ""); Set("auditDetailResult", ""); Set("auditUndoState", "Chọn một dòng lịch sử để xem chi tiết");
  document.getElementById("auditUndoButton").disabled = true;
}

function UndoAuditEntry() {
  if (!V("auditId") || V("auditCanUndo") != "1") { Msg("auditMessage", "Chỉ thay đổi gần nhất mới có thể hoàn tác."); return; }
  if (!window.confirm("Hoàn tác thay đổi #" + V("auditId") + "? Dữ liệu sẽ trở về trạng thái ngay trước thao tác này.")) return;
  var text = RunAdmin("undoaudit", { Id: V("auditId") });
  Msg("auditMessage", StatusText(text));
  LoadAuditHistory();
}

RegisterTab({
  id: "audit", view: "audit.html", panelId: "panelAudit", navId: "navAudit",
  title: "Lịch sử thay đổi", subtitle: "Theo dõi thao tác ghi dữ liệu và hoàn tác thay đổi gần nhất",
  onOpen: function () { LoadAuditHistory(); },
  onRefresh: function () { LoadAuditHistory(); }
});
