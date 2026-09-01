var activityRuntime = null;
var activityEditingConfig = null;
var activityDraftVersionNo = 0;
var activitySelectedSection = "overview";
var activitySelectedSourceKey = "";
var activitySelectedTierPeriod = "DAILY";
var activitySelectedTierIndex = -1;
var activitySelectedRewardIndex = -1;
var activityVersionRows = [];
var activitySelectedVersion = null;
var activityPlayerRows = [];
var activitySelectedPlayer = null;

function ActivityJsonText(text) {
  return RepairMojibake((text || "").replace(/^\uFEFF/, ""));
}

function ActivityRunJson(action, params) {
  var text = RunAdmin(action, params || {});
  if (IsAdminError(text)) {
    Msg("activityMessage", StatusText(text));
    return null;
  }
  try {
    return JSON.parse(ActivityJsonText(text));
  } catch (e) {
    Msg("activityMessage", "ERROR Không đọc được phản hồi " + action + ": " + e.message);
    return null;
  }
}

function ActivityClone(value) {
  return value == null ? null : JSON.parse(JSON.stringify(value));
}

function ActivityBool(value) {
  return value === true || value == "true" || value == "1";
}

function ActivityNumber(value, fallback) {
  var parsed = parseInt(Trim(value == null ? "" : value), 10);
  return isNaN(parsed) ? (fallback == null ? 0 : fallback) : parsed;
}

function ActivitySetChecked(id, value) {
  var node = document.getElementById(id);
  if (node) node.checked = !!value;
}

function ActivityConfigReady() {
  if (activityEditingConfig && activityEditingConfig.global) return true;
  Msg("activityMessage", "Hãy nạp cấu hình Năng Động trước.");
  return false;
}

function ActivityConfigOrigin(text) {
  var node = document.getElementById("activityConfigOrigin");
  if (node) node.innerText = text || "Bản đang sửa";
}

function ActivitySourceLabel(key) {
  var labels = {
    DAILY_LOGIN: "Đăng nhập ngày", DAILY_CHECKIN: "Điểm danh Bò Mộng", PEA_HARVEST: "Thu hoạch đậu",
    SIDE_TASK_EASY: "Nhiệm vụ phụ dễ", SIDE_TASK_NORMAL: "Nhiệm vụ phụ thường", SIDE_TASK_HARD: "Nhiệm vụ phụ khó",
    SIDE_TASK_VERY_HARD: "Nhiệm vụ phụ rất khó", SIDE_TASK_SPECIAL: "Nhiệm vụ phụ đặc biệt",
    CLAN_CHECKIN: "Điểm danh bang", FISH_CATCH: "Câu cá", PVP_WIN: "Thắng PvP", DUNGEON_CLEAR: "Hoàn thành phó bản",
    BOSS_KILL: "Hạ Boss", DIVERSITY_BONUS: "Thưởng đa dạng"
  };
  return labels[key] || key;
}

function ActivityLoadOverview() {
  var response = ActivityRunJson("getactivityoverview", {});
  if (!response || response.status != "ok") return;
  activityRuntime = response;
  if (!activityEditingConfig) {
    activityEditingConfig = ActivityClone(response.config);
    activityDraftVersionNo = 0;
    ActivityConfigOrigin("Runtime v" + response.versionNo);
  }
  ActivityRenderOverview();
  ActivityRenderSources();
  ActivityRenderTiers();
  ActivityRenderPlayerClaimTiers();
  Msg("activityMessage", "Đã nạp runtime revision " + response.revision + ".");
}

function ActivityLoadDraft() {
  var response = ActivityRunJson("getactivitydraft", {});
  if (!response) return;
  if (response.status == "empty") {
    activityDraftVersionNo = 0;
    if (activityRuntime) {
      activityEditingConfig = ActivityClone(activityRuntime.config);
      ActivityConfigOrigin("Runtime v" + activityRuntime.versionNo);
      ActivityRenderOverview(); ActivityRenderSources(); ActivityRenderTiers();
    }
    Msg("activityMessage", "Chưa có draft; đang dùng snapshot runtime làm bản sửa.");
    return;
  }
  if (!response.config) {
    Msg("activityMessage", "ERROR Draft Năng Động không có config hợp lệ.");
    return;
  }
  activityEditingConfig = ActivityClone(response.config);
  activityDraftVersionNo = ActivityNumber(response.versionNo, 0);
  ActivityConfigOrigin("Draft v" + activityDraftVersionNo);
  ActivityRenderOverview(); ActivityRenderSources(); ActivityRenderTiers();
  Msg("activityMessage", "Đã nạp draft version " + activityDraftVersionNo + ".");
}

function ActivityLoadVersions() {
  var response = ActivityRunJson("listactivityversions", {});
  if (!response || response.status != "ok") return;
  activityVersionRows = response.versions || [];
  ActivityRenderVersions();
}

function ActivityLoadLogs() {
  var response = ActivityRunJson("listactivitylogs", { PageSize: "100" });
  if (!response || response.status != "ok") return;
  var target = document.getElementById("activityLogsList");
  if (!target) return;
  var html = "";
  var admin = response.admin || [], claims = response.claims || [];
  for (var i = 0; i < admin.length; i++) {
    html += '<div class="activity-log-row"><b>' + Html(admin[i].action) + '</b> · ' + Html(FormatDateTime(admin[i].createdAt)) +
      '<br>' + Html(admin[i].reason || "") + (admin[i].playerId ? (" · Player " + Html(admin[i].playerId)) : "") + '</div>';
  }
  for (var j = 0; j < claims.length; j++) {
    html += '<div class="activity-log-row"><b>Claim ' + Html(claims[j].period + "/" + claims[j].tierId) + '</b> · ' + Html(claims[j].status) +
      '<br>Player ' + Html(claims[j].playerId) + ' · ' + Html(FormatDateTime(claims[j].createdAt)) + '</div>';
  }
  target.innerHTML = html || "Chưa có audit Năng Động.";
}

function ActivityRenderRuntimeStrip() {
  var node = document.getElementById("activityRuntimeStrip");
  if (!node) return;
  if (!activityRuntime) { node.innerText = "Chưa nạp trạng thái runtime."; return; }
  var g = activityRuntime.config.global;
  var draft = activityDraftVersionNo ? (" · Draft v" + activityDraftVersionNo) : " · Không có draft";
  node.innerText = "Runtime v" + activityRuntime.versionNo + " / revision " + activityRuntime.revision +
    " · " + (g.enabled ? "Đang bật" : "Đang tắt") +
    " · shadow=" + (!!g.shadowMode) + " · rewards=" + (!!g.rewardEnabled) +
    " · emergency=" + (!!activityRuntime.emergencyDisabled) + draft;
}

function ActivityRenderOverview() {
  ActivityRenderRuntimeStrip();
  if (!ActivityConfigReady()) return;
  var g = activityEditingConfig.global;
  ActivitySetChecked("activityEnabled", ActivityBool(g.enabled));
  ActivitySetChecked("activityShadowMode", ActivityBool(g.shadowMode));
  ActivitySetChecked("activityRewardEnabled", ActivityBool(g.rewardEnabled));
  Set("activityTimezone", g.timezone || "Asia/Ho_Chi_Minh");
  Set("activityDailyResetHour", g.dailyResetHour);
  Set("activityWeeklyResetDay", g.weeklyResetDay || "MONDAY");
  Set("activityDailyMax", g.dailyMax);
  Set("activityQualifiedDailyPoints", g.qualifiedDailyPoints);
  Set("activityDiversityCategoryCount", g.diversityCategoryCount);
  Set("activityDiversityBonus", g.diversityBonus);
  var runtimeSummary = activityRuntime ? activityRuntime.summary : {};
  var html = '<div class="activity-summary-card"><h3>Runtime đang phát hành</h3>' +
    '<div class="activity-summary-line"><b>Version / revision:</b> ' + Html(activityRuntime ? (activityRuntime.versionNo + " / " + activityRuntime.revision) : "-") + '</div>' +
    '<div class="activity-summary-line"><b>Nguồn:</b> ' + Html(runtimeSummary.sourceCount || 0) + ' · <b>Mốc ngày:</b> ' + Html(runtimeSummary.dailyTierCount || 0) + ' · <b>Mốc tuần:</b> ' + Html(runtimeSummary.weeklyTierCount || 0) + '</div>' +
    '<div class="activity-summary-line"><b>Ghi chú:</b> ' + Html(activityRuntime ? activityRuntime.note : "") + '</div></div>';
  html += '<div class="activity-summary-card"><h3>Bản đang sửa</h3>' +
    '<div class="activity-summary-line"><b>Max ngày:</b> ' + Html(g.dailyMax) + ' · <b>Đạt chuẩn:</b> ' + Html(g.qualifiedDailyPoints) + '</div>' +
    '<div class="activity-summary-line"><b>Diversity:</b> ' + Html(g.diversityCategoryCount + " nhóm + " + g.diversityBonus + " điểm") + '</div>' +
    '<div class="activity-summary-line">Lưu draft trước khi publish. Backend sẽ kiểm tra lại toàn bộ JSON.</div></div>';
  document.getElementById("activityOverviewSummary").innerHTML = html;
}

function ActivitySyncGlobalFromForm() {
  if (!ActivityConfigReady()) return;
  var g = activityEditingConfig.global;
  g.enabled = document.getElementById("activityEnabled").checked;
  g.shadowMode = document.getElementById("activityShadowMode").checked;
  g.rewardEnabled = document.getElementById("activityRewardEnabled").checked;
  g.timezone = V("activityTimezone") || "Asia/Ho_Chi_Minh";
  g.dailyResetHour = ActivityNumber(V("activityDailyResetHour"), g.dailyResetHour);
  g.weeklyResetDay = V("activityWeeklyResetDay");
  g.dailyMax = ActivityNumber(V("activityDailyMax"), g.dailyMax);
  g.qualifiedDailyPoints = ActivityNumber(V("activityQualifiedDailyPoints"), g.qualifiedDailyPoints);
  g.diversityCategoryCount = ActivityNumber(V("activityDiversityCategoryCount"), g.diversityCategoryCount);
  g.diversityBonus = ActivityNumber(V("activityDiversityBonus"), g.diversityBonus);
  ActivityRenderOverview();
}

function ActivityCurrentSource() {
  if (!ActivityConfigReady()) return null;
  var sources = activityEditingConfig.sources || [];
  for (var i = 0; i < sources.length; i++) if (sources[i].key == activitySelectedSourceKey) return sources[i];
  return null;
}

function ActivityRenderSources() {
  if (!ActivityConfigReady()) return;
  var sources = activityEditingConfig.sources || [];
  if (!activitySelectedSourceKey && sources.length) activitySelectedSourceKey = sources[0].key;
  var query = Trim(V("activitySourceSearch") || "").toLowerCase();
  var html = "<thead><tr><th>Mã / hoạt động</th><th>Bật</th><th>Category</th><th>Điểm / cap</th><th>Nhóm</th><th>Lượt tối đa</th></tr></thead><tbody>";
  var shown = 0;
  for (var i = 0; i < sources.length; i++) {
    var source = sources[i];
    var text = (source.key + " " + ActivitySourceLabel(source.key) + " " + source.category + " " + (source.groupKey || "")).toLowerCase();
    if (query && text.indexOf(query) < 0) continue;
    var maxRuns = source.points > 0 ? Math.floor(source.dailyCap / source.points) : 0;
    var active = source.key == activitySelectedSourceKey ? ' class="activity-row-selected"' : "";
    html += '<tr' + active + ' onclick="ActivityPickSource(\'' + source.key + '\')"><td><b>' + Html(source.key) + '</b><br>' + Html(ActivitySourceLabel(source.key)) + '</td><td>' + (source.enabled ? "Bật" : "Tắt") + '</td><td>' + Html(source.category) + '</td><td>' + Html(source.points + " / " + source.dailyCap) + '</td><td>' + Html(source.groupKey || "—") + '</td><td>' + Html(maxRuns) + '</td></tr>';
    shown++;
  }
  if (!shown) html += '<tr><td colspan="6">Không tìm thấy source phù hợp.</td></tr>';
  document.getElementById("activitySourcesTable").innerHTML = html + "</tbody>";
  ActivityRenderSourceEditor();
}

function ActivityPickSource(key) {
  activitySelectedSourceKey = key;
  ActivityRenderSources();
}

function ActivityRenderSourceEditor() {
  var source = ActivityCurrentSource();
  if (!source) return;
  document.getElementById("activitySourceEditorTitle").innerText = ActivitySourceLabel(source.key);
  Set("activitySourceKey", source.key); Set("activitySourceDedupe", source.dedupePolicy || "NONE");
  ActivitySetChecked("activitySourceEnabled", ActivityBool(source.enabled));
  Set("activitySourceCategory", source.category || "PVE"); Set("activitySourcePoints", source.points);
  Set("activitySourceDailyCap", source.dailyCap); Set("activitySourceGroupKey", source.groupKey || ""); Set("activitySourceGroupDailyCap", source.groupDailyCap || 0);
  var maxRuns = source.points > 0 ? Math.floor(source.dailyCap / source.points) : 0;
  document.getElementById("activitySourceMath").innerText = source.points > 0
    ? (source.points + " điểm/lần, cap " + source.dailyCap + " = tối đa " + maxRuns + " lần còn được tính điểm mỗi ngày.")
    : "Source tắt hoặc 0 điểm; backend chỉ cho publish rule hợp lệ.";
}

function ActivityUpdateSourceFromForm() {
  var source = ActivityCurrentSource();
  if (!source) return;
  source.enabled = document.getElementById("activitySourceEnabled").checked;
  source.category = V("activitySourceCategory"); source.points = ActivityNumber(V("activitySourcePoints"), source.points);
  source.dailyCap = ActivityNumber(V("activitySourceDailyCap"), source.dailyCap);
  source.groupKey = Trim(V("activitySourceGroupKey")).toUpperCase();
  if (!source.groupKey) source.groupKey = null;
  source.groupDailyCap = ActivityNumber(V("activitySourceGroupDailyCap"), source.groupDailyCap);
  ActivityRenderSources();
}

function ActivityTierList(period) {
  if (!ActivityConfigReady()) return [];
  return period == "WEEKLY" ? (activityEditingConfig.weeklyTiers || []) : (activityEditingConfig.dailyTiers || []);
}

function ActivityCurrentTier() {
  var tiers = ActivityTierList(activitySelectedTierPeriod);
  return activitySelectedTierIndex >= 0 && activitySelectedTierIndex < tiers.length ? tiers[activitySelectedTierIndex] : null;
}

function ActivityRenderTiers() {
  if (!ActivityConfigReady()) return;
  var period = activitySelectedTierPeriod;
  var tiers = ActivityTierList(period);
  if (activitySelectedTierIndex < 0 && tiers.length) activitySelectedTierIndex = 0;
  if (activitySelectedTierIndex >= tiers.length) activitySelectedTierIndex = tiers.length - 1;
  var html = "<thead><tr><th>ID</th><th>Bật</th><th>Bit</th><th>Ngưỡng</th><th>Ngày chuẩn</th><th>Bundle</th></tr></thead><tbody>";
  for (var i = 0; i < tiers.length; i++) {
    var tier = tiers[i], rewards = tier.rewards || [];
    var active = i == activitySelectedTierIndex ? ' class="activity-row-selected"' : "";
    html += '<tr' + active + ' onclick="ActivityPickTier(\'' + period + '\',' + i + ')"><td><b>' + Html(tier.id) + '</b></td><td>' + (tier.enabled ? "Bật" : "Tắt") + '</td><td>' + Html(tier.claimBit) + '</td><td>' + Html(tier.threshold) + '</td><td>' + Html(tier.minQualifiedDays || 0) + '</td><td>' + Html(rewards.length + " reward") + '</td></tr>';
  }
  if (!tiers.length) html += '<tr><td colspan="6">Chưa có mốc. Thêm mốc rồi validate trước khi lưu.</td></tr>';
  document.getElementById("activityTiersTable").innerHTML = html + "</tbody>";
  ActivityRenderTierEditor();
}

function ActivityPickTier(period, index) {
  activitySelectedTierPeriod = period;
  activitySelectedTierIndex = index;
  activitySelectedRewardIndex = -1;
  ActivityRenderTiers();
}

function ActivityAddTier() {
  if (!ActivityConfigReady()) return;
  var period = activitySelectedTierPeriod;
  var tiers = ActivityTierList(period);
  var suggested = period + "_CUSTOM_" + (tiers.length + 1);
  var tierId = window.prompt("Tier ID mới (chỉ chữ hoa, số và _)", suggested);
  if (tierId == null) return;
  tierId = Trim(tierId).toUpperCase();
  if (!/^[A-Z][A-Z0-9_]{2,63}$/.test(tierId)) { Msg("activityMessage", "Tier ID không hợp lệ."); return; }
  for (var i = 0; i < tiers.length; i++) if (tiers[i].id == tierId) { Msg("activityMessage", "Tier ID đã tồn tại."); return; }
  var used = {}, bit = 0;
  for (var j = 0; j < tiers.length; j++) used[tiers[j].claimBit] = true;
  while (used[bit] && bit < 63) bit++;
  if (bit > 62) { Msg("activityMessage", "Không còn claimBit an toàn."); return; }
  var max = period == "WEEKLY" ? ActivityNumber(activityEditingConfig.global.dailyMax, 100) * 7 : ActivityNumber(activityEditingConfig.global.dailyMax, 100);
  var threshold = ActivityNumber(window.prompt("Ngưỡng điểm (1–" + max + ")", "1"), 0);
  if (threshold < 1 || threshold > max) { Msg("activityMessage", "Ngưỡng không hợp lệ."); return; }
  tiers.push({ id: tierId, claimBit: bit, enabled: false, threshold: threshold, minQualifiedDays: 0, rewards: [] });
  activitySelectedTierIndex = tiers.length - 1; activitySelectedRewardIndex = -1;
  ActivityRenderTiers();
}

function ActivityRenderTierEditor() {
  var tier = ActivityCurrentTier();
  var editor = document.getElementById("activityRightTiers");
  if (!tier) { document.getElementById("activityTierEditorTitle").innerText = "Chưa chọn"; return; }
  document.getElementById("activityTierEditorTitle").innerText = activitySelectedTierPeriod + " · " + tier.id;
  Set("activityTierId", tier.id); Set("activityTierClaimBit", tier.claimBit); ActivitySetChecked("activityTierEnabled", ActivityBool(tier.enabled));
  Set("activityTierThreshold", tier.threshold); Set("activityTierQualifiedDays", tier.minQualifiedDays || 0);
  if (activitySelectedRewardIndex < 0 && tier.rewards && tier.rewards.length) activitySelectedRewardIndex = 0;
  if (activitySelectedRewardIndex >= (tier.rewards || []).length) activitySelectedRewardIndex = -1;
  ActivityRenderRewards();
}

function ActivityUpdateTierFromForm() {
  var tier = ActivityCurrentTier();
  if (!tier) return;
  tier.id = Trim(V("activityTierId")).toUpperCase(); tier.claimBit = ActivityNumber(V("activityTierClaimBit"), tier.claimBit);
  tier.enabled = document.getElementById("activityTierEnabled").checked; tier.threshold = ActivityNumber(V("activityTierThreshold"), tier.threshold);
  tier.minQualifiedDays = ActivityNumber(V("activityTierQualifiedDays"), tier.minQualifiedDays || 0);
  ActivityRenderTiers();
}

function ActivityCurrentReward() {
  var tier = ActivityCurrentTier();
  return tier && tier.rewards && activitySelectedRewardIndex >= 0 && activitySelectedRewardIndex < tier.rewards.length ? tier.rewards[activitySelectedRewardIndex] : null;
}

function ActivityAddReward() {
  var tier = ActivityCurrentTier();
  if (!tier) { Msg("activityMessage", "Chọn mốc trước khi thêm reward."); return; }
  var kind = window.prompt("Loại reward: ITEM, GOLD, GEM hoặc RUBY", "ITEM");
  if (kind == null) return;
  kind = Trim(kind).toUpperCase();
  if (kind != "ITEM" && kind != "GOLD" && kind != "GEM" && kind != "RUBY") { Msg("activityMessage", "Loại reward không hợp lệ."); return; }
  tier.rewards = tier.rewards || [];
  tier.rewards.push(ActivityRewardEditorDefault(kind)); activitySelectedRewardIndex = tier.rewards.length - 1;
  ActivityRenderRewards();
}

function ActivityRemoveReward() {
  var tier = ActivityCurrentTier();
  if (!tier || activitySelectedRewardIndex < 0) { Msg("activityMessage", "Chọn reward cần bỏ trước."); return; }
  if (!window.confirm("Bỏ reward đang chọn khỏi mốc " + tier.id + "?")) return;
  tier.rewards.splice(activitySelectedRewardIndex, 1); activitySelectedRewardIndex = tier.rewards.length ? 0 : -1;
  ActivityRenderRewards();
}

function ActivityRenderRewards() {
  var tier = ActivityCurrentTier();
  var table = document.getElementById("activityRewardsTable");
  var editor = document.getElementById("activityRewardEditor");
  if (!tier) { table.innerHTML = ""; editor.style.display = "none"; return; }
  var rewards = tier.rewards || [], html = "<thead><tr><th>#</th><th>Loại</th><th>Phần thưởng</th><th>Chi tiết</th></tr></thead><tbody>";
  for (var i = 0; i < rewards.length; i++) {
    var reward = ActivityRewardEditorNormalize(rewards[i]);
    var active = i == activitySelectedRewardIndex ? ' class="activity-row-selected"' : "";
    var label = reward.kind == "ITEM" ? (ItemCatalogName(reward.itemId) + " (#" + reward.itemId + ")") : reward.kind;
    html += '<tr' + active + ' onclick="ActivityPickReward(' + i + ')"><td>' + (i + 1) + '</td><td>' + Html(reward.kind) + '</td><td>' + Html(label) + '</td><td>' + Html(ActivityRewardEditorSummary(reward)) + '</td></tr>';
  }
  if (!rewards.length) html += '<tr><td colspan="4">Chưa có reward.</td></tr>';
  table.innerHTML = html + "</tbody>";
  var rewardSelected = ActivityCurrentReward();
  if (!rewardSelected) { editor.style.display = "none"; return; }
  editor.style.display = "block"; ActivityRenderRewardEditor();
}

function ActivityPickReward(index) {
  activitySelectedRewardIndex = index;
  ActivityRenderRewards();
}

function ActivityRenderRewardEditor() {
  var reward = ActivityCurrentReward();
  if (!reward) return;
  ActivityRewardEditorNormalize(reward);
  document.getElementById("activityRewardEditorTitle").innerText = "Reward #" + (activitySelectedRewardIndex + 1) + ": " + ActivityRewardEditorSummary(reward);
  Set("activityRewardKind", reward.kind); Set("activityRewardItemId", reward.itemId); Set("activityRewardGender", reward.gender); Set("activityRewardBindMode", reward.bindMode);
  Set("activityRewardQtyMin", reward.quantityMin); Set("activityRewardQtyMax", reward.quantityMax); Set("activityRewardExpiryMin", reward.expiryDaysMin); Set("activityRewardExpiryMax", reward.expiryDaysMax);
  ActivitySetChecked("activityRewardInitBase", reward.initBaseOptions); ActivitySetChecked("activityRewardUseDefault", reward.useDefaultOptions);
  var isItem = ActivityRewardEditorIsItem(reward);
  document.getElementById("activityRewardItemId").disabled = !isItem;
  document.getElementById("activityRewardBindMode").disabled = !isItem;
  document.getElementById("activityRewardInitBase").disabled = !isItem;
  document.getElementById("activityRewardUseDefault").disabled = !isItem;
  document.getElementById("activityRewardExpiryMin").disabled = !isItem;
  document.getElementById("activityRewardExpiryMax").disabled = !isItem;
  document.getElementById("activityRewardOptionBlock").style.display = isItem ? "block" : "none";
  var preview = document.getElementById("activityRewardItemPreview");
  if (isItem) {
    var item = itemCatalogById["" + reward.itemId];
    preview.innerHTML = item ? ItemImageHtml(item.iconId, "") + Html(item.name + " · ID " + item.id) : "Nhập ID item hợp lệ; backend sẽ kiểm tra item_template khi validate.";
  } else preview.innerText = "Tiền tệ không mang item, option hoặc HSD.";
  ActivityRenderRewardOptions();
}

function ActivityUpdateRewardFromForm() {
  var reward = ActivityCurrentReward();
  if (!reward) return;
  reward.kind = V("activityRewardKind"); reward.quantityMin = ActivityNumber(V("activityRewardQtyMin"), reward.quantityMin);
  reward.quantityMax = ActivityNumber(V("activityRewardQtyMax"), reward.quantityMax); reward.gender = ActivityNumber(V("activityRewardGender"), reward.gender);
  if (reward.kind == "ITEM") {
    reward.itemId = ActivityNumber(V("activityRewardItemId"), reward.itemId); reward.bindMode = V("activityRewardBindMode");
    reward.initBaseOptions = document.getElementById("activityRewardInitBase").checked; reward.useDefaultOptions = document.getElementById("activityRewardUseDefault").checked;
    reward.expiryDaysMin = ActivityNumber(V("activityRewardExpiryMin"), reward.expiryDaysMin); reward.expiryDaysMax = ActivityNumber(V("activityRewardExpiryMax"), reward.expiryDaysMax);
  } else {
    reward.itemId = 0; reward.bindMode = "BOUND"; reward.initBaseOptions = false; reward.useDefaultOptions = false;
    reward.options = []; reward.expiryDaysMin = 0; reward.expiryDaysMax = 0;
  }
  ActivityRenderRewards();
}

function ActivityRenderRewardOptions() {
  var reward = ActivityCurrentReward();
  var target = document.getElementById("activityRewardOptionList");
  if (!reward || !target || !ActivityRewardEditorIsItem(reward)) { if (target) target.innerHTML = ""; return; }
  var query = Trim(V("activityRewardOptionSearch") || "").toLowerCase(), html = "", shown = 0;
  for (var i = 1; i < optionRows.length; i++) {
    var id = optionRows[i][0], name = optionRows[i][1] || "";
    if (query && (id + " " + name).toLowerCase().indexOf(query) < 0) continue;
    var option = ActivityRewardEditorFindOption(reward, id);
    html += '<div class="activity-option-row"><label><input type="checkbox"' + (option ? " checked" : "") + ' onclick="ActivityToggleRewardOption(' + ActivityNumber(id, 0) + ',this.checked)"> ' + Html(id + " - " + name) + '</label>';
    if (option) html += '<span class="activity-option-caption">min</span><input class="activity-option-param" type="text" value="' + HtmlAttr(option.paramMin) + '" onchange="ActivitySetRewardOptionParam(' + ActivityNumber(id, 0) + ',\'min\',this.value)"><span class="activity-option-caption">max</span><input class="activity-option-param" type="text" value="' + HtmlAttr(option.paramMax) + '" onchange="ActivitySetRewardOptionParam(' + ActivityNumber(id, 0) + ',\'max\',this.value)">';
    html += "</div>"; shown++;
    if (shown >= 180) break;
  }
  target.innerHTML = html || "Không tìm thấy option phù hợp.";
}

function ActivityToggleRewardOption(optionId, checked) {
  var reward = ActivityCurrentReward();
  if (!reward) return;
  var option = ActivityRewardEditorFindOption(reward, optionId);
  if (checked && !option) reward.options.push({ id: optionId, paramMin: 0, paramMax: 0 });
  if (!checked && option) {
    var next = [];
    for (var i = 0; i < reward.options.length; i++) if (("" + reward.options[i].id) != ("" + optionId)) next.push(reward.options[i]);
    reward.options = next;
  }
  ActivityRenderRewards();
}

function ActivitySetRewardOptionParam(optionId, side, value) {
  var reward = ActivityCurrentReward(), option = ActivityRewardEditorFindOption(reward, optionId);
  if (!option) return;
  option[side == "min" ? "paramMin" : "paramMax"] = ActivityNumber(value, 0);
  ActivityRenderRewards();
}

function ActivityRequireReason(inputId) {
  var reason = Trim(V(inputId));
  if (reason.length < 5) { Msg("activityMessage", "Nhập lý do từ 5 ký tự trước khi ghi."); return ""; }
  return reason;
}

function ActivityConfigPayload(reason) {
  return { expectedRevision: activityRuntime ? activityRuntime.revision : 0, reason: reason, config: activityEditingConfig };
}

function ActivityValidateConfig() {
  if (!ActivityConfigReady()) return;
  ActivitySyncGlobalFromForm();
  var response = ActivityRunJson("validateactivityconfig", { PayloadJson: JSON.stringify({ config: activityEditingConfig }) });
  if (!response) return;
  Msg("activityMessage", "Cấu hình hợp lệ: " + (response.config.sources || []).length + " source, " + (response.config.dailyTiers || []).length + " mốc ngày, " + (response.config.weeklyTiers || []).length + " mốc tuần.");
}

function ActivitySaveDraft() {
  if (!ActivityConfigReady() || !activityRuntime) return;
  ActivitySyncGlobalFromForm();
  var reason = ActivityRequireReason("activityReason"); if (!reason) return;
  var response = ActivityRunJson("saveactivitydraft", { PayloadJson: JSON.stringify(ActivityConfigPayload(reason)) });
  if (!response || response.status != "ok") return;
  activityDraftVersionNo = ActivityNumber(response.versionNo, 0); ActivityConfigOrigin("Draft v" + activityDraftVersionNo);
  ActivityLoadVersions(); ActivityRenderRuntimeStrip();
  Msg("activityMessage", response.message || ("Đã lưu draft v" + activityDraftVersionNo + "."));
}

function ActivityPublishDraft() {
  if (!activityRuntime || !activityDraftVersionNo) { Msg("activityMessage", "Lưu hoặc nạp một draft trước khi publish."); return; }
  var reason = ActivityRequireReason("activityReason"); if (!reason) return;
  var releaseToken = "";
  if (ActivityBool(activityEditingConfig.global.rewardEnabled)) {
    if (ActivityBool(activityEditingConfig.global.shadowMode)) { Msg("activityMessage", "Tắt Shadow mode trước khi mở phát quà."); return; }
    if (!window.confirm("MỞ PHÁT QUÀ THẬT từ draft v" + activityDraftVersionNo + "? Player đủ mốc sẽ có thể nhận quà ngay.")) return;
    releaseToken = window.prompt("Nhập chính xác ENABLE_ACTIVITY_REWARDS để xác nhận mở phát quà:", "");
    if (releaseToken !== "ENABLE_ACTIVITY_REWARDS") { Msg("activityMessage", "Đã hủy mở phát quà: xác nhận không khớp."); return; }
  } else if (!window.confirm("Publish draft v" + activityDraftVersionNo + "? Runtime sẽ đổi revision.")) return;
  var response = ActivityRunJson("publishactivityconfig", { PayloadJson: JSON.stringify({ expectedRevision: activityRuntime.revision, versionNo: activityDraftVersionNo, reason: reason, releaseToken: releaseToken }) });
  if (!response || response.status != "ok") return;
  activityEditingConfig = null; activityDraftVersionNo = 0; activitySelectedVersion = null;
  ActivityLoadOverview(); ActivityLoadVersions(); ActivityLoadLogs();
  Msg("activityMessage", response.message || "Đã publish config.");
}

function ActivityToggleEmergency() {
  if (!activityRuntime) return;
  var reason = ActivityRequireReason("activityReason"); if (!reason) return;
  var disabled = !ActivityBool(activityRuntime.emergencyDisabled);
  if (!window.confirm((disabled ? "TẮT" : "MỞ LẠI") + " cộng điểm Năng Động khẩn cấp? Điểm và claim không bị xóa.")) return;
  var response = ActivityRunJson("disableactivityemergency", { PayloadJson: JSON.stringify({ expectedRevision: activityRuntime.revision, reason: reason, disabled: disabled }) });
  if (!response || response.status != "ok") return;
  ActivityLoadOverview(); ActivityLoadLogs(); Msg("activityMessage", response.message || "Đã đổi emergency flag.");
}

function ActivityShowDiff() {
  if (!activityRuntime || !ActivityConfigReady()) return;
  var before = activityRuntime.config, after = activityEditingConfig, changes = [], keys = ["enabled", "shadowMode", "rewardEnabled", "dailyResetHour", "weeklyResetDay", "dailyMax", "qualifiedDailyPoints", "diversityCategoryCount", "diversityBonus"];
  for (var i = 0; i < keys.length; i++) if (("" + before.global[keys[i]]) != ("" + after.global[keys[i]])) changes.push(keys[i] + ": " + before.global[keys[i]] + " → " + after.global[keys[i]]);
  var sourceChanges = 0, beforeSources = {};
  for (var j = 0; j < (before.sources || []).length; j++) beforeSources[before.sources[j].key] = JSON.stringify(before.sources[j]);
  for (var k = 0; k < (after.sources || []).length; k++) if (beforeSources[after.sources[k].key] != JSON.stringify(after.sources[k])) sourceChanges++;
  if (sourceChanges) changes.push(sourceChanges + " source rule thay đổi");
  if (JSON.stringify(before.dailyTiers || []) != JSON.stringify(after.dailyTiers || [])) changes.push("Mốc ngày hoặc bundle đã thay đổi");
  if (JSON.stringify(before.weeklyTiers || []) != JSON.stringify(after.weeklyTiers || [])) changes.push("Mốc tuần hoặc bundle đã thay đổi");
  document.getElementById("activityDiffOutput").innerText = changes.length ? ("Thay đổi so với runtime v" + activityRuntime.versionNo + ":\n• " + changes.join("\n• ")) : "Không có thay đổi so với runtime.";
}

function ActivityLoadPlayers() {
  var response = ActivityRunJson("listactivityplayers", { Search: V("activityPlayerSearch"), Page: "1", PageSize: "100" });
  if (!response || response.status != "ok") return;
  activityPlayerRows = response.players || []; ActivityRenderPlayers();
}

function ActivityPlayerSearchKey(e) {
  e = e || window.event;
  if (e.keyCode == 13) ActivityLoadPlayers();
}

function ActivityRenderPlayers() {
  var html = "<thead><tr><th>ID</th><th>Player</th><th>Trạng thái</th><th>Ngày</th><th>Tuần</th><th>Chu kỳ</th></tr></thead><tbody>";
  for (var i = 0; i < activityPlayerRows.length; i++) {
    var row = activityPlayerRows[i], active = activitySelectedPlayer && activitySelectedPlayer.id == row.id ? ' class="activity-row-selected"' : "";
    html += '<tr' + active + ' onclick="ActivityLoadPlayer(' + row.id + ')"><td>' + Html(row.id) + '</td><td>' + Html(row.name) + '</td><td>' + Html(row.onlineState) + '</td><td>' + Html(row.dailyPoints) + '</td><td>' + Html(row.weeklyPoints) + '</td><td>' + Html(row.dayKey + " / " + row.weekKey) + '</td></tr>';
  }
  if (!activityPlayerRows.length) html += '<tr><td colspan="6">Không tìm thấy player.</td></tr>';
  document.getElementById("activityPlayersTable").innerHTML = html + "</tbody>";
}

function ActivityLoadPlayer(playerId) {
  var response = ActivityRunJson("getactivityplayer", { Id: "" + playerId });
  if (!response || response.status != "ok") return;
  activitySelectedPlayer = response.player; ActivityRenderPlayers(); ActivityRenderPlayerEditor();
}

function ActivityDecimalMaskHas(maskValue, bit) {
  var value = Trim(maskValue == null ? "0" : maskValue);
  if (!/^\d+$/.test(value) || bit < 0) return false;
  for (var count = 0; count < bit; count++) {
    var carry = 0, next = "";
    for (var i = 0; i < value.length; i++) {
      var number = carry * 10 + (value.charCodeAt(i) - 48);
      if (next.length || number >= 2) next += "" + Math.floor(number / 2);
      carry = number % 2;
    }
    value = next || "0";
  }
  return (parseInt(value.charAt(value.length - 1), 10) % 2) == 1;
}

function ActivityRenderPlayerEditor() {
  var player = activitySelectedPlayer, badge = document.getElementById("activityPlayerOnlineBadge");
  if (!player) return;
  var state = player.state || {}, offline = player.onlineState == "OFFLINE";
  badge.innerText = offline ? "Offline" : player.onlineState; badge.className = "player-state " + (offline ? "offline" : "online");
  document.getElementById("activityPlayerOverview").innerHTML = '<b>' + Html(player.name) + ' (#' + Html(player.id) + ')</b> · ' + Html(player.onlineState) +
    '<br>Ngày ' + Html(state.dayKey || "-") + ': <b>' + Html(state.dailyPoints || 0) + '</b> điểm · Claim mask ' + Html(state.dailyClaimMask || 0) +
    '<br>Tuần ' + Html(state.weekKey || "-") + ': <b>' + Html(state.weeklyPoints || 0) + '</b> điểm · ' + Html(state.qualifiedDays || 0) + ' ngày chuẩn · Claim mask ' + Html(state.weeklyClaimMask || 0) +
    '<br>Mirror data_point[11]: ' + Html(player.legacyDailyMirror) + ' · Version hash: ' + Html((player.version || "").substring(0, 12)) + '…';
  var counters = state.dailyCounters || {}, keys = [], html = "<table><thead><tr><th>Counter</th><th>Điểm</th></tr></thead><tbody>";
  for (var key in counters) if (counters.hasOwnProperty(key)) keys.push(key);
  keys.sort();
  for (var i = 0; i < keys.length; i++) html += '<tr><td>' + Html(keys[i]) + '</td><td>' + Html(counters[keys[i]]) + '</td></tr>';
  html += '</tbody></table><div class="activity-summary-line">Category: ' + Html((state.dailyCategories || []).join(", ") || "—") + '</div>';
  document.getElementById("activityPlayerCounters").innerHTML = html;
  var sourceSelect = document.getElementById("activityPlayerSourceKey"); sourceSelect.innerHTML = "";
  for (var j = 0; j < keys.length; j++) sourceSelect.options[sourceSelect.options.length] = new Option(keys[j], keys[j]);
  if (!keys.length) sourceSelect.options[0] = new Option("Không có counter", "");
  var controls = ["activityPlayerAdjustButton", "activityPlayerResetButton", "activityPlayerClaimButton", "activityPlayerAdjustScope", "activityPlayerDelta", "activityPlayerResetScope", "activityPlayerSourceKey", "activityPlayerClaimPeriod", "activityPlayerClaimTier", "activityPlayerClaimed"];
  for (var c = 0; c < controls.length; c++) document.getElementById(controls[c]).disabled = !offline;
  ActivityRenderPlayerClaimTiers(); ActivityUpdatePlayerResetFields();
}

function ActivityUpdatePlayerResetFields() {
  var sourceMode = V("activityPlayerResetScope") == "SOURCE";
  document.getElementById("activityPlayerSourceKey").disabled = !sourceMode || !(activitySelectedPlayer && activitySelectedPlayer.onlineState == "OFFLINE");
}

function ActivityRenderPlayerClaimTiers() {
  var select = document.getElementById("activityPlayerClaimTier");
  if (!select) return;
  var period = V("activityPlayerClaimPeriod") || "DAILY";
  var config = activityRuntime ? activityRuntime.config : activityEditingConfig;
  var tiers = config ? (period == "WEEKLY" ? (config.weeklyTiers || []) : (config.dailyTiers || [])) : [];
  select.innerHTML = "";
  for (var i = 0; i < tiers.length; i++) select.options[select.options.length] = new Option(tiers[i].id + " · bit " + tiers[i].claimBit, tiers[i].id);
  if (!tiers.length) select.options[0] = new Option("Chưa có tier", "");
  select.onchange = ActivitySyncPlayerClaimCheckbox;
  ActivitySyncPlayerClaimCheckbox();
}

function ActivitySyncPlayerClaimCheckbox() {
  var player = activitySelectedPlayer;
  if (!player || !activityRuntime) return;
  var period = V("activityPlayerClaimPeriod") || "DAILY", tierId = V("activityPlayerClaimTier"), tiers = period == "WEEKLY" ? (activityRuntime.config.weeklyTiers || []) : (activityRuntime.config.dailyTiers || []);
  for (var i = 0; i < tiers.length; i++) if (tiers[i].id == tierId) {
    var mask = period == "WEEKLY" ? player.state.weeklyClaimMask : player.state.dailyClaimMask;
    ActivitySetChecked("activityPlayerClaimed", ActivityDecimalMaskHas(mask, ActivityNumber(tiers[i].claimBit, 0))); return;
  }
}

function ActivityPlayerMutationPayload(reason) {
  return { playerId: activitySelectedPlayer.id, expectedVersion: activitySelectedPlayer.version, reason: reason };
}

function ActivityCanMutatePlayer() {
  if (!activitySelectedPlayer) { Msg("activityMessage", "Chọn player trước."); return false; }
  if (activitySelectedPlayer.onlineState != "OFFLINE") { Msg("activityMessage", "Player đang online hoặc trạng thái chưa chắc chắn; chỉ được xem."); return false; }
  return true;
}

function ActivityAdjustPlayer() {
  if (!ActivityCanMutatePlayer()) return;
  var reason = ActivityRequireReason("activityPlayerReason"); if (!reason) return;
  var delta = ActivityNumber(V("activityPlayerDelta"), 0); if (!delta) { Msg("activityMessage", "Nhập điểm cộng/trừ khác 0."); return; }
  if (!window.confirm("Điều chỉnh " + delta + " điểm " + V("activityPlayerAdjustScope") + " cho " + activitySelectedPlayer.name + "?")) return;
  var payload = ActivityPlayerMutationPayload(reason); payload.scope = V("activityPlayerAdjustScope"); payload.pointsDelta = delta;
  var response = ActivityRunJson("adjustactivityplayer", { PayloadJson: JSON.stringify(payload) });
  if (!response || response.status != "ok") return;
  ActivityLoadPlayer(activitySelectedPlayer.id); ActivityLoadPlayers(); ActivityLoadLogs(); Msg("activityMessage", "Đã điều chỉnh điểm player.");
}

function ActivityResetPlayer() {
  if (!ActivityCanMutatePlayer()) return;
  var reason = ActivityRequireReason("activityPlayerReason"); if (!reason) return;
  var scope = V("activityPlayerResetScope"), payload = ActivityPlayerMutationPayload(reason); payload.scope = scope; payload.confirm = "RESET_ACTIVITY";
  if (scope == "SOURCE") { payload.sourceKey = V("activityPlayerSourceKey"); if (!payload.sourceKey) { Msg("activityMessage", "Chọn counter source cần reset."); return; } }
  if (!window.confirm("RESET " + scope + " Năng Động của " + activitySelectedPlayer.name + "? Điểm/claim có thể đổi theo phạm vi chọn.")) return;
  var response = ActivityRunJson("resetactivityplayer", { PayloadJson: JSON.stringify(payload) });
  if (!response || response.status != "ok") return;
  ActivityLoadPlayer(activitySelectedPlayer.id); ActivityLoadPlayers(); ActivityLoadLogs(); Msg("activityMessage", "Đã reset state player.");
}

function ActivitySetPlayerClaim() {
  if (!ActivityCanMutatePlayer()) return;
  var reason = ActivityRequireReason("activityPlayerReason"); if (!reason) return;
  var payload = ActivityPlayerMutationPayload(reason); payload.period = V("activityPlayerClaimPeriod"); payload.tierId = V("activityPlayerClaimTier"); payload.claimed = document.getElementById("activityPlayerClaimed").checked; payload.confirm = "SET_ACTIVITY_CLAIM";
  if (!payload.tierId) { Msg("activityMessage", "Chọn tier trước."); return; }
  if (!window.confirm("Đặt " + payload.period + "/" + payload.tierId + " = " + (payload.claimed ? "đã nhận" : "chưa nhận") + "? Bỏ claim đã audit sẽ bị backend chặn.")) return;
  var response = ActivityRunJson("setactivityclaim", { PayloadJson: JSON.stringify(payload) });
  if (!response || response.status != "ok") return;
  ActivityLoadPlayer(activitySelectedPlayer.id); ActivityLoadLogs(); Msg("activityMessage", "Đã cập nhật trạng thái claim.");
}

function ActivityRenderVersions() {
  var html = "<thead><tr><th>Version</th><th>Trạng thái</th><th>Runtime</th><th>Ghi chú</th><th>Publish</th></tr></thead><tbody>";
  for (var i = 0; i < activityVersionRows.length; i++) {
    var row = activityVersionRows[i], active = activitySelectedVersion && activitySelectedVersion.versionNo == row.versionNo ? ' class="activity-row-selected"' : "";
    html += '<tr' + active + ' onclick="ActivityPickVersion(' + row.versionNo + ')"><td>' + Html(row.versionNo) + '</td><td>' + Html(row.status) + '</td><td>' + (row.runtime ? "Đang chạy" : "") + '</td><td>' + Html(row.note || "") + '</td><td>' + Html(FormatDateTime(row.publishedAt || "")) + '</td></tr>';
  }
  if (!activityVersionRows.length) html += '<tr><td colspan="5">Chưa có version.</td></tr>';
  document.getElementById("activityVersionsTable").innerHTML = html + "</tbody>";
}

function ActivityPickVersion(versionNo) {
  var response = ActivityRunJson("getactivityversion", { Id: "" + versionNo });
  if (!response || !response.config) return;
  activitySelectedVersion = response; ActivityRenderVersions();
  document.getElementById("activityVersionEditorTitle").innerText = "v" + response.versionNo + " · " + response.status;
  document.getElementById("activityVersionDetail").innerHTML = '<b>Version ' + Html(response.versionNo) + '</b> · ' + Html(response.status) +
    '<br>Tạo: ' + Html(FormatDateTime(response.createdAt)) + ' · Publish: ' + Html(FormatDateTime(response.publishedAt)) +
    '<br>' + Html(response.note || "Không có ghi chú") +
    '<br>' + Html((response.config.sources || []).length) + ' source · ' + Html((response.config.dailyTiers || []).length) + ' mốc ngày · ' + Html((response.config.weeklyTiers || []).length) + ' mốc tuần';
}

function ActivityUseSelectedVersion() {
  if (!activitySelectedVersion || !activitySelectedVersion.config) { Msg("activityMessage", "Chọn version trước."); return; }
  activityEditingConfig = ActivityClone(activitySelectedVersion.config);
  activityDraftVersionNo = activitySelectedVersion.status == "DRAFT" ? ActivityNumber(activitySelectedVersion.versionNo, 0) : 0;
  ActivityConfigOrigin(activitySelectedVersion.status == "DRAFT" ? ("Draft v" + activityDraftVersionNo) : ("Clone từ v" + activitySelectedVersion.versionNo));
  ActivityShowSection("overview"); ActivityRenderOverview(); ActivityRenderSources(); ActivityRenderTiers();
  Msg("activityMessage", "Đang sửa snapshot từ version " + activitySelectedVersion.versionNo + ". Lưu draft để tạo version mới.");
}

function ActivityRollbackSelectedVersion() {
  if (!activitySelectedVersion || !activityRuntime) { Msg("activityMessage", "Chọn version đã publish/archived trước."); return; }
  if (activitySelectedVersion.status == "DRAFT") { Msg("activityMessage", "Draft không thể rollback trực tiếp; hãy publish draft trước."); return; }
  var reason = ActivityRequireReason("activityReason"); if (!reason) return;
  if (!window.confirm("Rollback runtime về version " + activitySelectedVersion.versionNo + "? Không xóa version mới hơn.")) return;
  var response = ActivityRunJson("rollbackactivityconfig", { PayloadJson: JSON.stringify({ expectedRevision: activityRuntime.revision, versionNo: activitySelectedVersion.versionNo, reason: reason }) });
  if (!response || response.status != "ok") return;
  activityEditingConfig = null; activityDraftVersionNo = 0; ActivityLoadOverview(); ActivityLoadVersions(); ActivityLoadLogs();
  Msg("activityMessage", response.message || "Đã rollback config.");
}

function ActivityShowSection(section) {
  activitySelectedSection = section;
  var sections = ["overview", "sources", "daily", "weekly", "players", "releases"];
  for (var i = 0; i < sections.length; i++) {
    var name = sections[i], button = document.getElementById("activitySection" + name.charAt(0).toUpperCase() + name.substring(1));
    if (button) button.className = name == section ? "activity-section-button active" : "activity-section-button";
  }
  document.getElementById("activityLeftOverview").style.display = section == "overview" ? "block" : "none";
  document.getElementById("activityLeftSources").style.display = section == "sources" ? "block" : "none";
  document.getElementById("activityLeftTiers").style.display = (section == "daily" || section == "weekly") ? "block" : "none";
  document.getElementById("activityLeftPlayers").style.display = section == "players" ? "block" : "none";
  document.getElementById("activityLeftReleases").style.display = section == "releases" ? "block" : "none";
  document.getElementById("activityRightOverview").style.display = section == "overview" ? "block" : "none";
  document.getElementById("activityRightSources").style.display = section == "sources" ? "block" : "none";
  document.getElementById("activityRightTiers").style.display = (section == "daily" || section == "weekly") ? "block" : "none";
  document.getElementById("activityRightPlayers").style.display = section == "players" ? "block" : "none";
  document.getElementById("activityRightReleases").style.display = section == "releases" ? "block" : "none";
  if (section == "daily" || section == "weekly") { activitySelectedTierPeriod = section == "weekly" ? "WEEKLY" : "DAILY"; activitySelectedTierIndex = 0; activitySelectedRewardIndex = -1; ActivityRenderTiers(); }
  if (section == "sources") ActivityRenderSources();
  if (section == "players") { ActivityLoadPlayers(); if (activitySelectedPlayer) ActivityRenderPlayerEditor(); }
  if (section == "releases") { ActivityLoadVersions(); ActivityLoadLogs(); }
  AdjustTableOffsets();
}

function ActivityLoadTab() {
  LoadItemCatalog(false); LoadOptions(false); ActivityLoadOverview(); ActivityLoadDraft(); ActivityLoadVersions(); ActivityLoadPlayers(); ActivityLoadLogs();
}

function ActivityRefreshTab() {
  activityEditingConfig = null; activityDraftVersionNo = 0; activitySelectedVersion = null;
  LoadItemCatalog(true); LoadOptions(true); ActivityLoadOverview(); ActivityLoadDraft(); ActivityLoadVersions(); ActivityLoadPlayers(); ActivityLoadLogs();
}

RegisterTab({
  id: "activity", view: "activity.html", panelId: "panelActivity", navId: "navActivity",
  title: "Cấu hình Năng Động", subtitle: "Nguồn điểm, mốc quà, tiến trình người chơi và phát hành cấu hình",
  onOpen: function () { ActivityLoadTab(); },
  onRefresh: function () { ActivityRefreshTab(); }
});
