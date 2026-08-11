var playerRows = [];
var selectedPlayerId = "";
var selectedPlayerDetail = null;

function PlayerGenderLabel(value) {
  return value == "0" ? "Trái Đất" : (value == "1" ? "Namek" : (value == "2" ? "Xayda" : value));
}

function PlayerSearchKey(e) {
  e = e || window.event;
  if (e.keyCode == 13) LoadPlayers();
}

function PlayerStateCode(onlineState, ban, active) {
  if (ban == "1") return "locked";
  if (active != "1") return "inactive";
  if (onlineState == "ONLINE") return "online";
  if (onlineState == "ONLINE?") return "suspect";
  return "offline";
}

function PlayerStateLabel(code) {
  if (code == "online") return "Online";
  if (code == "suspect") return "Có thể online";
  if (code == "locked") return "Bị khóa";
  if (code == "inactive") return "Chưa active";
  return "Offline";
}

function PlayerStateBadge(onlineState, ban, active) {
  var code = PlayerStateCode(onlineState, ban, active);
  return '<span class="player-state ' + code + '">' + Html(PlayerStateLabel(code)) + '</span>';
}

function LoadPlayers() {
  EnsurePlayerConfigRows();
  playerRows = ParseTsv(RunAdmin("listplayers", { Search: V("playerSearch") }));
  var html = "<thead><tr><th>ID</th><th>Player</th><th>Tài khoản</th><th>Hành tinh</th><th>Sức mạnh</th><th>Hành trang</th><th>Rương</th><th>Trạng thái</th></tr></thead><tbody>";
  for (var i = 1; i < playerRows.length; i++) {
    var row = playerRows[i];
    html += '<tr onclick="PickPlayer(' + i + ')"><td>' + Html(row[0]) + '</td><td>' + Html(row[1]) + '</td><td>' + Html(row[2]) + '</td><td>' + Html(PlayerGenderLabel(row[3])) + '</td><td>' + Html(FormatNumber(row[4])) + '</td><td>' + Html(row[5]) + '</td><td>' + Html(row[6]) + '</td><td>' + PlayerStateBadge(row[9], row[7], row[8]) + '</td></tr>';
  }
  document.getElementById("playersTable").innerHTML = html + "</tbody>";
  Msg("playerMessage", playerRows.length > 1 ? "Đã tải " + (playerRows.length - 1) + " player." : "Không tìm thấy player phù hợp.");
  if (selectedPlayerId) ReloadSelectedPlayer();
  AdjustTableOffsets();
}

function PickPlayer(index) {
  if (!playerRows[index]) return;
  selectedPlayerId = playerRows[index][0];
  LoadPlayerDetail(selectedPlayerId);
}

function ReloadSelectedPlayer() {
  if (!selectedPlayerId) { Msg("playerMessage", "Chọn một player trước."); return; }
  LoadPlayerDetail(selectedPlayerId);
}

function LoadPlayerDetail(playerId) {
  var rows = ParseTsv(RunAdmin("getplayerdetail", { Id: playerId }));
  if (rows.length < 2) { Msg("playerMessage", "Không tải được chi tiết player."); return; }
  var r = rows[1];
  selectedPlayerDetail = r;
  selectedPlayerId = r[0];
  Set("playerId", r[0]); Set("playerVersion", r[38]); Set("playerName", r[2]); Set("playerUsername", r[3]);
  Set("playerIdentity", r[0] + " / " + r[1]); Set("playerGender", PlayerGenderLabel(r[4]) + " (" + r[4] + ")");
  Set("playerClanId", r[6]); Set("playerLocation", "Map " + r[35] + " / " + r[36] + ", " + r[37]);
  document.getElementById("playerLoginMeta").innerText = "Tạo: " + FormatDateTime(r[7]) + " | Login: " + FormatDateTime(r[8]) + " | Logout: " + FormatDateTime(r[9]) + (r[13] == "1" ? " | Tài khoản admin" : "");
  var badge = document.getElementById("playerOnlineBadge");
  var badgeCode = PlayerStateCode(r[10], r[11], r[12]);
  badge.innerText = PlayerStateLabel(badgeCode); badge.className = "player-state " + badgeCode;
  Set("playerLimitPower", r[14]); Set("playerPower", FormatNumber(r[15])); Set("playerPotential", FormatNumber(r[16]));
  Set("playerStamina", FormatNumber(r[17])); Set("playerMaxStamina", FormatNumber(r[18])); Set("playerHpG", FormatNumber(r[19])); Set("playerMpG", FormatNumber(r[20]));
  Set("playerDamageG", FormatNumber(r[21])); Set("playerDefenseG", FormatNumber(r[22])); Set("playerCriticalG", FormatNumber(r[23])); Set("playerCriticalDragon", FormatNumber(r[24]));
  Set("playerGold", FormatNumber(r[27])); Set("playerGem", FormatNumber(r[28])); Set("playerRuby", FormatNumber(r[29])); Set("playerCoupon", FormatNumber(r[30]));
  Set("playerBagSlots", r[31]); Set("playerBagUsed", r[32] + " / " + r[31]); Set("playerBoxSlots", r[33]); Set("playerBoxUsed", r[34] + " / " + r[33]);
  Set("playerBan", r[11]); Set("playerActive", r[12]); document.getElementById("playerForceOffline").checked = false;
  UpdatePlayerTierNote();
  document.getElementById("playerAssetNote").innerText = "Giới hạn: vàng " + FormatNumber(r[44]) + ", ngọc " + FormatNumber(r[45]) + ", ruby " + FormatNumber(r[46]) + ", coupon " + FormatNumber(r[47]) + ", hành trang " + FormatNumber(r[48]) + " ô, rương " + FormatNumber(r[49]) + " ô.";
  Msg("playerMessage", "Đã tải player " + r[2] + ".");
}

function PlayerLimitAt(key, level) {
  var parts = PlayerConfigCurrentValue(key).split(",");
  return level >= 0 && level < parts.length ? Trim(parts[level]) : "?";
}

function UpdatePlayerTierNote() {
  var level = parseInt(V("playerLimitPower") || "0", 10);
  document.getElementById("playerTierNote").innerText = "Trần cấp " + level + ": sức mạnh " + FormatNumber(PlayerLimitAt("player.limit.power", level)) + ", HP/KI " + FormatNumber(PlayerLimitAt("player.limit.hpMp", level)) + ", sức đánh " + FormatNumber(PlayerLimitAt("player.limit.damage", level)) + ", giáp " + FormatNumber(PlayerLimitAt("player.limit.defense", level)) + ", chí mạng " + FormatNumber(PlayerLimitAt("player.limit.critical", level)) + ".";
}

function PlayerPayload() {
  return {
    version: V("playerVersion"), forceOffline: document.getElementById("playerForceOffline").checked ? "1" : "0",
    limitPower: V("playerLimitPower"), power: NormalizeInteger(V("playerPower")), potential: NormalizeInteger(V("playerPotential")),
    stamina: NormalizeInteger(V("playerStamina")), maxStamina: NormalizeInteger(V("playerMaxStamina")), hpg: NormalizeInteger(V("playerHpG")), mpg: NormalizeInteger(V("playerMpG")),
    damage: NormalizeInteger(V("playerDamageG")), defense: NormalizeInteger(V("playerDefenseG")), critical: NormalizeInteger(V("playerCriticalG")), criticalDragon: NormalizeInteger(V("playerCriticalDragon")),
    gold: NormalizeInteger(V("playerGold")), gem: NormalizeInteger(V("playerGem")), ruby: NormalizeInteger(V("playerRuby")), coupon: NormalizeInteger(V("playerCoupon")),
    bagSlots: Trim(V("playerBagSlots")), boxSlots: Trim(V("playerBoxSlots")), ban: V("playerBan"), active: V("playerActive")
  };
}

function SavePlayerCore() {
  if (!V("playerId")) { Msg("playerMessage", "Chọn một player trước."); return; }
  if (!window.confirm("Lưu chỉ số, tài sản và sức chứa cho " + V("playerName") + "?")) return;
  var result = RunAdmin("saveplayercore", { Id: V("playerId"), PayloadJson: JSON.stringify(PlayerPayload()) });
  Msg("playerMessage", StatusText(result));
  if (!IsAdminError(result)) { LoadPlayerDetail(V("playerId")); LoadPlayers(); }
}

function RescuePlayer() {
  if (!V("playerId")) { Msg("playerMessage", "Chọn một player trước."); return; }
  if (!window.confirm("Đưa " + V("playerName") + " về map nhà, tọa độ 300/336?")) return;
  var payload = { version: V("playerVersion"), forceOffline: document.getElementById("playerForceOffline").checked ? "1" : "0" };
  var result = RunAdmin("rescueplayer", { Id: V("playerId"), PayloadJson: JSON.stringify(payload) });
  Msg("playerMessage", StatusText(result));
  if (!IsAdminError(result)) LoadPlayerDetail(V("playerId"));
}

RegisterTab({
  id: "players", view: "players.html", panelId: "panelPlayers", navId: "navPlayers",
  title: "Quản lý Player", subtitle: "Tìm và sửa chỉ số, tài sản, hành trang/rương của player offline",
  onOpen: function () { LoadPlayers(); },
  onRefresh: function () { LoadPlayers(); }
});
