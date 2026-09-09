var clanEconomyRows = [];

function ClanEconomyRows(section) {
  var result = [];
  for (var i = 1; i < clanEconomyRows.length; i++) {
    if (clanEconomyRows[i][0] == section) result.push(clanEconomyRows[i]);
  }
  return result;
}

function ClanEconomyRow(section, key) {
  var rows = ClanEconomyRows(section);
  for (var i = 0; i < rows.length; i++) if (rows[i][1] == key) return rows[i];
  return null;
}

function ClanEconomyLabel(key, fallback) {
  var labels = {
    "total_clans": "Tổng bang", "active_clans": "Bang có hoạt động",
    "active_clan_days": "Ngày-bang hoạt động", "current_gold": "Số dư Vàng",
    "current_gem": "Số dư Ngọc", "current_capsule": "Số dư Capsule",
    "average_value": "Clan Value trung bình", "average_level": "Cấp bang trung bình",
    "pending_rewards": "Quà đang chờ phát",
    "TREE_WATER": "Tưới cây", "TREE_FERTILIZE": "Bón phân",
    "TREE_VITALITY_REACHED": "Đạt đủ Sức sống ngày", "TREE_HARVEST": "Thu hoạch cây",
    "TREE_LEVEL_UP": "Cây lên cấp", "CLAN_LEVEL_UP": "Bang lên cấp",
    "GIFT_SENT": "Quà đã gửi", "GIFT_BLOCKED_POLICY": "Quà bị chặn theo luật",
    "PENDING_GIFT_DELIVERED": "Quà chờ đã phát", "DUPLICATE_REQUEST": "Request lặp",
    "TRANSACTION_ROLLBACK": "Transaction rollback", "TERRITORY_CREATED": "Khu bang được tạo",
    "TERRITORY_DISPOSED": "Khu bang được giải phóng",
    "ATTACK": "Sức đánh", "HP": "HP", "KI": "KI", "LUCK": "May mắn",
    "POWER": "Tiềm năng/Sức mạnh", "MOB_GOLD": "Vàng từ quái"
  };
  return labels[key] || fallback || key;
}

function ClanEconomyUnit(key, fallback) {
  var units = {
    "total_clans": "bang", "active_clans": "bang", "active_clan_days": "ngày-bang",
    "current_gold": "vàng", "current_gem": "ngọc", "current_capsule": "capsule",
    "average_value": "điểm", "average_level": "cấp", "pending_rewards": "quà"
  };
  return units[key] || fallback || "";
}

function ClanEconomyStatusClass(status) {
  if (status == "BALANCED" || status == "OK") return "healthy";
  if (status == "SURPLUS_RISK") return "surplus";
  if (status == "DEFICIT_RISK" || status == "WARNING") return "risk";
  return "neutral";
}

function ClanEconomyStatusLabel(status) {
  if (status == "BALANCED") return "TRONG NGƯỠNG";
  if (status == "SURPLUS_RISK") return "DƯ NGUỒN SINH";
  if (status == "DEFICIT_RISK") return "SINK QUÁ CAO";
  if (status == "WARNING") return "CẦN THEO DÕI";
  if (status == "OK") return "ỔN ĐỊNH";
  return "CHƯA ĐỦ DỮ LIỆU";
}

function ClanEconomyNumber(value) {
  return FormatNumber(value == null || value === "" ? "0" : value);
}

function ClanEconomyMetricNote(row, activeClanDays) {
  var key = row[1], events = parseInt(row[3], 10) || 0, amount = parseInt(row[4], 10) || 0;
  if (key == "TREE_WATER" || key == "TREE_FERTILIZE") {
    return activeClanDays > 0 ? (Math.round(events * 100 / activeClanDays) / 100) + " lượt/ngày-bang" : "Chưa có mẫu ngày-bang";
  }
  if (key == "TREE_VITALITY_REACHED") {
    return activeClanDays > 0 ? Math.round(events * 100 / activeClanDays) + "% ngày-bang đạt mục tiêu" : "Chưa có mẫu ngày-bang";
  }
  if ((key == "TREE_LEVEL_UP" || key == "TERRITORY_DISPOSED") && events > 0) {
    return "Trung bình " + FormatDurationMs(Math.floor(amount / events));
  }
  if (row[6]) return row[6].replace(/\|/g, "·");
  return "";
}

function RenderClanEconomySummary() {
  var keys = ["total_clans", "active_clans", "current_gold", "current_gem", "current_capsule", "average_value", "pending_rewards"];
  var html = "";
  for (var i = 0; i < keys.length; i++) {
    var row = ClanEconomyRow("SUMMARY", keys[i]);
    if (!row) continue;
    html += '<div class="clan-economy-card"><span>' + Html(ClanEconomyLabel(row[1], row[2])) + '</span>' +
      '<strong>' + Html(ClanEconomyNumber(row[3])) + '</strong><small>' + Html(ClanEconomyUnit(row[1], row[4])) + '</small>' +
      (row[6] ? '<em>' + Html(row[6].replace(/\|/g, "·")) + '</em>' : "") + '</div>';
  }
  document.getElementById("clanEconomySummary").innerHTML = html || '<div class="clan-economy-empty">Chưa có dữ liệu tổng quan.</div>';
}

function RenderClanEconomyFlows() {
  var rows = ClanEconomyRows("FLOW"), html = "", overall = "BALANCED";
  for (var i = 0; i < rows.length; i++) {
    var row = rows[i], statusClass = ClanEconomyStatusClass(row[5]);
    if (row[5] == "DEFICIT_RISK") overall = "DEFICIT_RISK";
    else if (row[5] == "SURPLUS_RISK" && overall != "DEFICIT_RISK") overall = "SURPLUS_RISK";
    else if (row[5] == "NO_FLOW" && overall == "BALANCED") overall = "NO_FLOW";
    var currencyLabel = row[1] == "0" ? "Vàng bang" : (row[1] == "1" ? "Ngọc bang" : "Capsule bang");
    html += '<div class="clan-economy-flow-card ' + statusClass + '"><div><strong>' + Html(currencyLabel) + '</strong>' +
      '<span class="clan-economy-badge ' + statusClass + '">' + Html(ClanEconomyStatusLabel(row[5])) + '</span></div>' +
      '<p><b>+' + Html(ClanEconomyNumber(row[3])) + '</b> nguồn sinh <b>−' + Html(ClanEconomyNumber(row[4])) + '</b> sink</p>' +
      '<small>' + Html((row[6] || "").replace(/\|/g, "·")) + '</small></div>';
  }
  document.getElementById("clanEconomyFlows").innerHTML = html;
  var badge = document.getElementById("clanEconomyOverallBadge");
  badge.className = "clan-economy-badge " + ClanEconomyStatusClass(overall);
  badge.innerText = overall == "BALANCED" ? "KINH TẾ TRONG NGƯỠNG" : ClanEconomyStatusLabel(overall);
}

function RenderClanEconomySignals() {
  var rows = ClanEconomyRows("SIGNAL"), activeRow = ClanEconomyRow("SUMMARY", "active_clan_days");
  var activeClanDays = activeRow ? (parseInt(activeRow[3], 10) || 0) : 0;
  var html = "<thead><tr><th>Tín hiệu</th><th>Số lượt</th><th>Diễn giải</th></tr></thead><tbody>";
  for (var i = 0; i < rows.length; i++) {
    html += "<tr><td>" + Html(ClanEconomyLabel(rows[i][1], rows[i][2])) + "</td><td>" +
      Html(ClanEconomyNumber(rows[i][3])) + "</td><td>" + Html(ClanEconomyMetricNote(rows[i], activeClanDays)) + "</td></tr>";
  }
  document.getElementById("clanEconomySignalTable").innerHTML = html + (rows.length ? "" : '<tr><td colspan="3">Chưa có metric trong cửa sổ.</td></tr>') + "</tbody>";
}

function RenderClanEconomyPotential() {
  var rows = ClanEconomyRows("POTENTIAL");
  var html = "<thead><tr><th>Nhánh</th><th>Tổng bậc</th><th>Số bang</th><th>Phân bố</th></tr></thead><tbody>";
  for (var i = 0; i < rows.length; i++) {
    html += "<tr><td>" + Html(ClanEconomyLabel(rows[i][1], rows[i][2])) + "</td><td>" + Html(ClanEconomyNumber(rows[i][3])) +
      "</td><td>" + Html(ClanEconomyNumber(rows[i][4])) + "</td><td>" + Html((rows[i][6] || "").replace(/\|/g, "·")) + "</td></tr>";
  }
  document.getElementById("clanEconomyPotentialTable").innerHTML = html + (rows.length ? "" : '<tr><td colspan="4">Chưa có điểm Tiềm năng đã phân bổ.</td></tr>') + "</tbody>";
}

function RenderClanEconomyActions() {
  var rows = ClanEconomyRows("ACTION");
  var html = "<thead><tr><th>Hành động</th><th>Nguồn sinh</th><th>Sink</th><th>Tiền tệ / giao dịch</th></tr></thead><tbody>";
  for (var i = 0; i < rows.length; i++) {
    html += "<tr><td>" + Html(rows[i][2]) + "</td><td class=\"clan-economy-inflow\">+" + Html(ClanEconomyNumber(rows[i][3])) +
      "</td><td class=\"clan-economy-outflow\">−" + Html(ClanEconomyNumber(rows[i][4])) + "</td><td>" + Html((rows[i][6] || "").replace(/\|/g, "·")) + "</td></tr>";
  }
  document.getElementById("clanEconomyActionTable").innerHTML = html + (rows.length ? "" : '<tr><td colspan="4">Chưa có giao dịch sổ cái trong cửa sổ.</td></tr>') + "</tbody>";
}

function LoadClanEconomyReport() {
  var lookback = V("clanEconomyLookback");
  if (!/^(7|14|28|90)$/.test(lookback)) lookback = "14";
  Msg("clanEconomyMessage", "Đang tổng hợp dữ liệu kinh tế bang...");
  var text = RunAdmin("getclaneconomy", { LookbackDays: lookback });
  if (IsAdminError(text)) {
    clanEconomyRows = [];
    Msg("clanEconomyMessage", "Không thể tải báo cáo. Chi tiết kỹ thuật đã được ghi vào admin_data.log.");
    document.getElementById("clanEconomyOverallBadge").className = "clan-economy-badge risk";
    document.getElementById("clanEconomyOverallBadge").innerText = "KHÔNG KHẢ DỤNG";
    return;
  }
  clanEconomyRows = ParseTsv(text);
  RenderClanEconomySummary();
  RenderClanEconomyFlows();
  RenderClanEconomySignals();
  RenderClanEconomyPotential();
  RenderClanEconomyActions();
  var generated = ClanEconomyRow("META", "generated_at");
  document.getElementById("clanEconomyUpdated").innerText = generated ? "Cập nhật " + FormatDateTime(generated[3]) : "Đã tải dữ liệu";
  Msg("clanEconomyMessage", "Đã tải báo cáo " + lookback + " ngày. Số liệu tài chính đọc từ sổ cái bất biến; metric vận hành được gom theo ngày.");
}

RegisterTab({
  id: "claneconomy", view: "clan-economy.html", panelId: "panelClanEconomy", navId: "navClanEconomy",
  title: "Kinh tế bang hội", subtitle: "Theo dõi nguồn–sink, hoạt động Cây bang và tín hiệu cân bằng",
  onOpen: function () { LoadClanEconomyReport(); },
  onRefresh: function () { LoadClanEconomyReport(); }
});
