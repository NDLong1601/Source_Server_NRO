var adminMobRows = [];
var mobDrops = [];
var selectedMobDrop = -1;

function LoadAdminMobs() {
  LoadSpawnLookups();
  adminMobRows = ParseTsv(RunAdmin("listadminmobs", { Search: V("mobSearch") }));
  var html = "<thead><tr><th>ID</th><th>Tên Mob</th><th>HP</th><th>Trạng thái</th><th>Lịch</th><th>Cấu hình</th></tr></thead><tbody>";
  for (var i = 1; i < adminMobRows.length; i++) {
    var row = adminMobRows[i];
    html += '<tr onclick="PickAdminMob(' + i + ')"><td>' + Html(row[1]) + '</td><td>' + Html(row[2]) + '</td><td>' + Html(row[3]) + '</td><td>' + (row[5] == "1" ? "Bật" : "Tắt") + '</td><td>' + Html(SpawnScheduleLabel(row[6], row[7], row[8], row[9], row[10])) + '</td><td>' + (row[0] == "0" ? "Mặc định" : "Tùy chỉnh") + '</td></tr>';
  }
  document.getElementById("mobTable").innerHTML = html + "</tbody>";
  Msg("mobMessage", adminMobRows.length > 1 ? "Đã tải " + (adminMobRows.length - 1) + " mob template." : "Không tìm thấy Mob.");
  AdjustTableOffsets();
}

function PickAdminMob(index) {
  var row = adminMobRows[index];
  Set("mobConfigId", row[0]); Set("mobTemplateId", row[1]); Set("mobName", row[1] + " - " + row[2]);
  Set("mobBaseHp", row[3]); Set("mobBaseDamage", row[4]); document.getElementById("mobEnabled").checked = row[5] == "1";
  document.getElementById("mobUseRange").checked = row[6] == "1"; Set("mobTimeStart", row[7] || "08:00"); Set("mobTimeEnd", row[8] || "22:00");
  document.getElementById("mobUseInterval").checked = row[9] == "1"; Set("mobInterval", row[10] || "1");
  mobDrops = ParseSpawnDrops(row[11]); selectedMobDrop = -1;
  ToggleSpawnSchedule("mob"); ClearSpawnDropEditor("mob"); RenderSpawnDrops("mob");
  Msg("mobMessage", "Đang cấu hình Mob " + row[2] + ".");
}

function SaveAdminMob() {
  if (!V("mobTemplateId")) { Msg("mobMessage", "Hãy chọn một Mob trong bảng bên trái."); return; }
  var text = RunAdmin("saveadminmob", {
    OwnerId: V("mobConfigId"), TemplateId: V("mobTemplateId"), Enabled: CheckedValue("mobEnabled"),
    UseTimeRange: CheckedValue("mobUseRange"), TimeStart: V("mobTimeStart"), TimeEnd: V("mobTimeEnd"),
    UseInterval: CheckedValue("mobUseInterval"), IntervalMinutes: V("mobInterval"), DropsJson: JSON.stringify(mobDrops)
  });
  Msg("mobMessage", StatusText(text));
  if (text.indexOf("OK\t") == 0 || text.indexOf("OK ") == 0) LoadAdminMobs();
}

function DeleteAdminMob() {
  if (!V("mobConfigId") || V("mobConfigId") == "0") { Msg("mobMessage", "Mob này đang dùng cấu hình mặc định."); return; }
  if (!window.confirm("Gỡ lịch và drop tùy chỉnh của " + V("mobName") + "?")) return;
  Msg("mobMessage", StatusText(RunAdmin("deleteadminmob", { OwnerId: V("mobConfigId") })));
  LoadAdminMobs();
}

RegisterTab({
  id: "mob", view: "mob.html", panelId: "panelMob", navId: "navMob",
  title: "Quản lý Mob / Quái", subtitle: "Bật tắt, đặt lịch hồi sinh và drop theo mob template",
  onOpen: function () { LoadSpawnLookups(); LoadAdminMobs(); },
  onRefresh: function () { LoadAdminMobs(); }
});
