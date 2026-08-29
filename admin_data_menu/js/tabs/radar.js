var radarRows = [];
var radarOptions = [];
var radarMobDropRows = [];
var radarMobDrops = [];
var radarBossDropRows = [];
var radarBossDrops = [];
var radarOptionEditIndex = -1;
var radarMobDropEditIndex = -1;
var radarBossDropEditIndex = -1;
var selectedCollectionSection = "radar";
var costumeCollectionAchievementRows = [];
var costumeCollectionFilteredRows = [];
var costumeCollectionSelectedItemIds = [];
var costumeCollectionRewardItemIds = [];
var costumeCollectionRewardQuantities = {};
var costumeCollectionIsNew = false;
var costumeCollectionItemSearchTimer = null;
var costumeCollectionRewardSearchTimer = null;
var fishBookRows = [];
var fishBookFilteredRows = [];

function CollectionSections() {
  return [
    ["radar", "Thẻ radar", "Thẻ sưu tầm, mốc kích hoạt và nguồn rơi", "RADAR", "radar"],
    ["costume", "Cải trang", "Mốc thành tựu và bộ cải trang cần sưu tầm", "THỜI TRANG", "costume"],
    ["fish", "Sổ tay cá", "Thẻ sưu tầm của hệ thống câu cá", "NGƯ PHỦ", "fish"]
  ];
}

function CollectionBadge(text, tone, title) {
  return '<span class="collection-badge ' + HtmlAttr(tone || "off") + '"'
    + (title ? ' title="' + HtmlAttr(title) + '"' : "") + ">" + Html(text) + "</span>";
}

function CollectionRankBadge(value) {
  var rank = parseInt(value, 10);
  if (isNaN(rank) || rank < 0) rank = 0;
  if (rank > 6) rank = 6;
  return CollectionBadge("RANK " + rank, "rank-" + rank);
}

function CollectionRarityTone(value) {
  var rarity = Trim(value).toLowerCase();
  if (rarity.indexOf("huyền thoại") >= 0) return "legendary";
  if (rarity.indexOf("sử thi") >= 0) return "epic";
  if (rarity.indexOf("cực hiếm") >= 0 || rarity.indexOf("siêu hiếm") >= 0) return "mythic";
  if (rarity.indexOf("không phổ thông") >= 0) return "uncommon";
  if (rarity.indexOf("hiếm") >= 0) return "rare";
  return "common";
}

function CollectionRarityBadge(value) {
  return CollectionBadge(value || "Chưa đặt", CollectionRarityTone(value));
}

function CollectionRewardBadges(gold, gem, rewardItems) {
  var html = "";
  var goldValue = NormalizeInteger(gold || "0");
  var gemValue = NormalizeInteger(gem || "0");
  if (/^\d+$/.test(goldValue) && !/^0+$/.test(goldValue)) html += CollectionBadge(FormatNumber(goldValue) + " vàng", "gold");
  if (/^\d+$/.test(gemValue) && !/^0+$/.test(gemValue)) html += CollectionBadge(FormatNumber(gemValue) + " ngọc", "gem");
  if (rewardItems && rewardItems.ids && rewardItems.ids.length) {
    html += CollectionBadge(rewardItems.ids.length + " vật phẩm", "item");
  }
  return html || CollectionBadge("Chưa có thưởng", "off");
}

function RenderCollectionTabs() {
  var sections = CollectionSections();
  var html = "";
  for (var i = 0; i < sections.length; i++) {
    var section = sections[i];
    html += '<button type="button" class="player-config-tab' + (section[0] == selectedCollectionSection ? " active" : "")
      + '" title="' + HtmlAttr(section[2]) + '" onclick="SelectCollectionSection(\'' + section[0] + '\')">'
      + CollectionBadge(section[3], section[4]) + '<span class="collection-tab-label">' + Html(section[1]) + "</span></button>";
  }
  document.getElementById("collectionTabs").innerHTML = html;
}

function SelectCollectionSection(section) {
  var sections = CollectionSections();
  var valid = false;
  for (var i = 0; i < sections.length; i++) if (sections[i][0] == section) valid = true;
  if (!valid) return;
  selectedCollectionSection = section;
  RenderCollectionTabs();
  document.getElementById("collectionRadarContent").className = "collection-content" + (section == "radar" ? " active" : "");
  document.getElementById("collectionCostumeContent").className = "collection-content" + (section == "costume" ? " active" : "");
  document.getElementById("collectionFishContent").className = "collection-content" + (section == "fish" ? " active" : "");
  if (section == "radar") {
    if (!optionRows || optionRows.length < 2) LoadOptions();
    LoadRadarCards();
  } else if (section == "costume") {
    LoadCostumeCollectionAchievements();
  } else if (section == "fish") {
    LoadFishBookEntries();
  }
  QueueAdminSelectDecoration();
  AdjustTableOffsetsLater();
}

function RefreshCollectionSection() {
  if (selectedCollectionSection == "radar") LoadRadarCards();
  else if (selectedCollectionSection == "costume") LoadCostumeCollectionAchievements();
  else if (selectedCollectionSection == "fish") LoadFishBookEntries();
}

function RadarIconSrc(iconId) {
  iconId = Trim(iconId);
  if (!iconId || iconId == "-1") return "";
  return "data/icon/x2/" + iconId + ".png";
}

function UpdateRadarPreview() {
  var target = document.getElementById("radarPreview");
  if (!target) return;
  var iconId = V("radarIconId");
  var src = RadarIconSrc(iconId);
  target.innerHTML = src
    ? '<img src="' + HtmlAttr(src) + '" onerror="this.style.visibility=\'hidden\'">' + CollectionBadge("ICON " + iconId, "radar")
    : CollectionBadge("Chưa có icon", "off");
}

function LoadRadarCards() {
  radarRows = ParseTsv(RunAdmin("listradarcards", {
    Search: V("radarSearch"),
    RadarRank: V("radarRankFilter")
  }));
  RenderRadarCards();
  Msg("radarMessage", radarRows.length > 1 ? "\u0110\u00e3 t\u1ea3i " + (radarRows.length - 1) + " th\u1ebb s\u01b0u t\u1ea7m." : "Kh\u00f4ng c\u00f3 th\u1ebb s\u01b0u t\u1ea7m ph\u00f9 h\u1ee3p.");
  AdjustTableOffsets();
}

function RenderRadarCards() {
  var html = "<thead><tr><th>\u1ea2nh</th><th>ID</th><th>Icon</th><th>H\u1ea1ng</th><th>T\u1ed1i \u0111a</th><th>Ki\u1ec3u</th><th>T\u00ean</th><th>Y\u00eau c\u1ea7u</th><th>Option</th></tr></thead><tbody>";
  for (var i = 1; i < radarRows.length; i++) {
    var r = radarRows[i];
    var iconSrc = RadarIconSrc(r[1] || "");
    html += '<tr onclick="PickRadarCard(' + i + ')">';
    html += '<td class="radar-icon-cell">' + (iconSrc ? '<img class="radar-icon" src="' + HtmlAttr(iconSrc) + '" onerror="this.style.visibility=\'hidden\'">' : "") + "</td>";
    html += "<td>" + CollectionBadge("#" + (r[0] || "?"), "id") + "</td>";
    html += "<td>" + CollectionBadge("ICON " + (r[1] || "-1"), "radar") + "</td>";
    html += "<td>" + CollectionRankBadge(r[2] || "0") + "</td>";
    html += "<td>" + CollectionBadge((r[3] || "0") + " thẻ", "count") + "</td>";
    html += "<td>" + CollectionBadge(r[4] == "1" ? "NHÂN VẬT" : "QUÁI", r[4] == "1" ? "character" : "mob") + "</td>";
    html += "<td>" + Html(r[6] || "") + "</td>";
    html += "<td>" + (!r[8] || r[8] == "-1"
      ? CollectionBadge("KHÔNG YÊU CẦU", "off")
      : CollectionBadge("THẺ " + r[8] + " · MỐC " + (r[9] || "0"), "require")) + "</td>";
    html += "<td>" + CollectionBadge((r[11] || "0") + " option", "option") + "</td>";
    html += "</tr>";
  }
  html += "</tbody>";
  document.getElementById("radarCardsTable").innerHTML = html;
}

function RadarTypeLabel(value) {
  if (value == "0") return "0 - qu\u00e1i";
  if (value == "1") return "1 - nh\u00e2n v\u1eadt";
  return value;
}

function RadarRequireLabel(requireId, requireLevel) {
  if (!requireId || requireId == "-1") return "Kh\u00f4ng y\u00eau c\u1ea7u";
  return "Th\u1ebb " + requireId + " / m\u1ed1c " + (requireLevel || "0");
}

function PickRadarCard(index) {
  var id = radarRows[index] ? radarRows[index][0] : "";
  if (!id) return;
  var rows = ParseTsv(RunAdmin("getradarcard", { Id: id }));
  if (rows.length < 2) {
    Msg("radarMessage", "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c th\u00f4ng tin th\u1ebb ID " + id + ".");
    return;
  }
  ApplyRadarDetail(rows[1]);
  Msg("radarMessage", "\u0110ang s\u1eeda th\u1ebb s\u01b0u t\u1ea7m ID " + id + ".");
  AdjustTableOffsets();
}

function ApplyRadarDetail(r) {
  Set("radarId", r[0] || "");
  Set("radarIconId", r[1] || "-1");
  Set("radarRank", r[2] || "0");
  Set("radarMax", r[3] || "120");
  Set("radarType", r[4] || "0");
  Set("radarMobId", r[5] || "-1");
  Set("radarName", r[7] || "");
  Set("radarInfo", r[8] || "");
  Set("radarRequireId", r[10] || "-1");
  Set("radarRequireLevel", r[11] || "0");
  Set("radarAuraId", r[12] || "-1");
  var milestones = ParseRadarMilestonesJson(r[13] || "[]", r[3] || "120");
  Set("radarMilestone0", milestones[0]);
  Set("radarMilestone1", milestones[1]);
  Set("radarMilestone2", milestones[2]);

  var body = ParseRadarBodyJson(r[6] || "");
  Set("radarHead", body.head);
  Set("radarBody", body.body);
  Set("radarLeg", body.leg);
  Set("radarBag", body.bag);

  radarOptions = ParseRadarOptionsJson(r[9] || "[]");
  radarMobDrops = [];
  radarMobDropRows = [];
  radarMobDropEditIndex = -1;
  radarBossDrops = [];
  radarBossDropRows = [];
  radarBossDropEditIndex = -1;
  NewRadarOption();
  RenderRadarOptionsTable();
  UpdateRadarPreview();
  UpdateRadarMetaHints();
  LoadRadarMobDrops();
  LoadRadarBossDrops();
}

function ParseRadarBodyJson(value) {
  var fallback = { head: "-1", body: "-1", leg: "-1", bag: "-1" };
  try {
    var parsed = JSON.parse(value);
    if (parsed && parsed.length) parsed = parsed[0];
    if (!parsed) return fallback;
    return {
      head: parsed.head == null ? "-1" : parsed.head,
      body: parsed.body == null ? "-1" : parsed.body,
      leg: parsed.leg == null ? "-1" : parsed.leg,
      bag: parsed.bag == null ? "-1" : parsed.bag
    };
  } catch (e) {
    return fallback;
  }
}

function ParseRadarOptionsJson(value) {
  var list = [];
  try {
    var parsed = JSON.parse(value || "[]");
    if (!parsed) return list;
    if (Object.prototype.toString.call(parsed) != "[object Array]") parsed = [parsed];
    for (var i = 0; i < parsed.length; i++) {
      var active = parsed[i].activeCard;
      if (active == null) active = parsed[i].active;
      list.push({
        id: "" + (parsed[i].id == null ? "" : parsed[i].id),
        param: "" + (parsed[i].param == null ? "0" : parsed[i].param),
        activeCard: "" + (active == null ? "0" : active)
      });
    }
  } catch (e) {}
  return list;
}

function ParseRadarMilestonesJson(value, fallbackMax) {
  var fallback = parseInt(fallbackMax || "120", 10);
  if (!fallback || fallback < 1) fallback = 120;
  var result = [1, fallback, fallback];
  try {
    var parsed = JSON.parse(value || "[]");
    if (parsed && parsed.length) {
      if (parsed[1] != null) result[1] = parsed[1];
      if (parsed[2] != null) result[2] = parsed[2];
    }
  } catch (e) {}
  return result;
}

function NewRadarCard() {
  Set("radarId", "");
  Set("radarIconId", "-1");
  Set("radarRank", "0");
  Set("radarMax", "120");
  Set("radarName", "");
  Set("radarInfo", "");
  Set("radarType", "0");
  Set("radarMobId", "-1");
  Set("radarAuraId", "-1");
  Set("radarHead", "-1");
  Set("radarBody", "-1");
  Set("radarLeg", "-1");
  Set("radarBag", "-1");
  Set("radarRequireId", "-1");
  Set("radarRequireLevel", "0");
  Set("radarMilestone0", "1");
  Set("radarMilestone1", "120");
  Set("radarMilestone2", "120");
  radarOptions = [];
  radarMobDrops = [];
  radarMobDropRows = [];
  radarMobDropEditIndex = -1;
  radarBossDrops = [];
  radarBossDropRows = [];
  radarBossDropEditIndex = -1;
  NewRadarOption();
  RenderRadarOptionsTable();
  RenderRadarMobDrops();
  RenderRadarBossDrops();
  UpdateRadarPreview();
  UpdateRadarMetaHints();
  Msg("radarMessage", "Nh\u1eadp th\u00f4ng tin th\u1ebb s\u01b0u t\u1ea7m m\u1edbi.");
}

function UpdateRadarMetaHints() {
  var mobHint = document.getElementById("radarMobHint");
  if (mobHint) mobHint.innerText = V("radarMobId") == "-1" ? "Kh\u00f4ng g\u1eafn mob hi\u1ec3n th\u1ecb" : RadarMobDisplayName(V("radarMobId"));
  var auraHint = document.getElementById("radarAuraHint");
  if (auraHint) auraHint.innerText = V("radarAuraId") == "-1" ? "Kh\u00f4ng c\u00f3 h\u00e0o quang" : "H\u00e0o quang " + V("radarAuraId");
  var bagHint = document.getElementById("radarBagHint");
  if (bagHint) bagHint.innerText = V("radarBag") == "-1" ? "Kh\u00f4ng g\u1eafn t\u00fai" : "T\u00fai " + V("radarBag");
  var requireHint = document.getElementById("radarRequireHint");
  if (requireHint) requireHint.innerText = V("radarRequireId") == "-1" ? "Kh\u00f4ng y\u00eau c\u1ea7u th\u1ebb kh\u00e1c" : RadarRequireLabel(V("radarRequireId"), V("radarRequireLevel"));
}

function RadarMobDisplayName(templateId) {
  for (var i = 1; i < radarMobDropRows.length; i++) {
    if (radarMobDropRows[i][1] == templateId) return templateId + " - " + (radarMobDropRows[i][2] || "");
  }
  return "Mob template " + templateId;
}

function RenderRadarOptionsTable() {
  var html = "<thead><tr><th>#</th><th>M\u1ed1c</th><th>Option</th><th>T\u00ean</th><th>Param</th></tr></thead><tbody>";
  for (var i = 0; i < radarOptions.length; i++) {
    var opt = radarOptions[i];
    html += '<tr onclick="PickRadarOption(' + i + ')">';
    html += "<td>" + (i + 1) + "</td>";
    html += "<td>" + Html(RadarActiveLabel(opt.activeCard)) + "</td>";
    html += "<td>" + Html(opt.id) + "</td>";
    html += "<td>" + Html(OptionName(opt.id)) + "</td>";
    html += "<td>" + Html(opt.param) + "</td>";
    html += "</tr>";
  }
  html += "</tbody>";
  document.getElementById("radarOptionsTable").innerHTML = html;
}

function RadarActiveLabel(value) {
  if (value == "0") return "M\u1ed1c 0";
  if (value == "1") return "M\u1ed1c 1";
  if (value == "2") return "M\u1ed1c 2";
  return "M\u1ed1c " + value;
}

function RenderRadarOptionPicker() {
  var target = document.getElementById("radarOptionList");
  if (!target) return;
  if (!optionRows || optionRows.length < 2) {
    target.innerHTML = "Ch\u01b0a t\u1ea3i danh m\u1ee5c option.";
    UpdateRadarOptionSummary();
    return;
  }
  var selectedIds = V("radarOptionId") ? [V("radarOptionId")] : [];
  RenderOptionPicker("radarOptionList", "radarOptionSearch", selectedIds, "ToggleRadarOption", "radarPicker");
  UpdateRadarOptionSummary();
}

function ToggleRadarOption(id, checked) {
  Set("radarOptionId", checked ? id : "");
  RenderRadarOptionPicker();
}

function UpdateRadarOptionSummary() {
  var target = document.getElementById("radarOptionSummary");
  if (!target) return;
  var id = V("radarOptionId");
  target.innerText = id ? ("\u0110ang ch\u1ecdn: " + id + " - " + OptionName(id)) : "Ch\u01b0a ch\u1ecdn option";
}

function NewRadarOption() {
  radarOptionEditIndex = -1;
  Set("radarOptionSearch", "");
  Set("radarOptionId", "");
  Set("radarOptionParam", "0");
  Set("radarOptionActive", "0");
  RenderRadarOptionPicker();
}

function PickRadarOption(index) {
  if (!radarOptions[index]) return;
  radarOptionEditIndex = index;
  Set("radarOptionSearch", "");
  Set("radarOptionId", radarOptions[index].id);
  Set("radarOptionParam", radarOptions[index].param);
  Set("radarOptionActive", radarOptions[index].activeCard);
  RenderRadarOptionPicker();
}

function SaveRadarOption() {
  var id = Trim(V("radarOptionId"));
  var param = Trim(V("radarOptionParam"));
  var active = Trim(V("radarOptionActive"));
  if (!/^\d+$/.test(id)) { Msg("radarMessage", "Option ID ph\u1ea3i l\u00e0 s\u1ed1 kh\u00f4ng \u00e2m."); return; }
  if (!/^-?\d+$/.test(param)) { Msg("radarMessage", "Param ph\u1ea3i l\u00e0 s\u1ed1 nguy\u00ean."); return; }
  if (!/^[0-2]$/.test(active)) { Msg("radarMessage", "M\u1ed1c k\u00edch ho\u1ea1t ch\u1ec9 nh\u1eadn 0, 1 ho\u1eb7c 2."); return; }
  var row = { id: id, param: param, activeCard: active };
  if (radarOptionEditIndex >= 0 && radarOptionEditIndex < radarOptions.length) {
    radarOptions[radarOptionEditIndex] = row;
  } else {
    radarOptions.push(row);
  }
  RenderRadarOptionsTable();
  NewRadarOption();
}

function DeleteRadarOption() {
  if (radarOptionEditIndex < 0 || radarOptionEditIndex >= radarOptions.length) return;
  radarOptions.splice(radarOptionEditIndex, 1);
  RenderRadarOptionsTable();
  NewRadarOption();
}

function CollectRadarOptions() {
  var list = [];
  for (var i = 0; i < radarOptions.length; i++) {
    list.push({
      id: parseInt(radarOptions[i].id, 10),
      param: parseInt(radarOptions[i].param, 10),
      activeCard: parseInt(radarOptions[i].activeCard, 10)
    });
  }
  return list;
}

function CollectRadarMilestones() {
  return [1, parseInt(V("radarMilestone1"), 10), parseInt(V("radarMilestone2"), 10)];
}

function SaveRadarCard() {
  if (!V("radarId")) { Msg("radarMessage", "C\u1ea7n nh\u1eadp ID th\u1ebb s\u01b0u t\u1ea7m."); return; }
  if (!/^\d+$/.test(V("radarMilestone1")) || !/^\d+$/.test(V("radarMilestone2")) ||
      parseInt(V("radarMilestone1"), 10) < 1 || parseInt(V("radarMilestone1"), 10) > 127 ||
      parseInt(V("radarMilestone2"), 10) < 1 || parseInt(V("radarMilestone2"), 10) > 127) {
    Msg("radarMessage", "S\u1ed1 th\u1ebb k\u00edch ho\u1ea1t m\u1ed1c ph\u1ea3i t\u1eeb 1 \u0111\u1ebfn 127.");
    return;
  }
  var text = RunAdmin("saveradarcard", {
    Id: V("radarId"),
    IconId: V("radarIconId"),
    RadarRank: V("radarRank"),
    RadarMax: V("radarMax"),
    RadarType: V("radarType"),
    RadarMobId: V("radarMobId"),
    Name: V("radarName"),
    Description: V("radarInfo"),
    Head: V("radarHead"),
    Body: V("radarBody"),
    Leg: V("radarLeg"),
    Part: V("radarBag"),
    RequireId: V("radarRequireId"),
    RequireLevel: V("radarRequireLevel"),
    AuraId: V("radarAuraId"),
    OptionsJson: JSON.stringify(CollectRadarOptions()),
    MilestonesJson: JSON.stringify(CollectRadarMilestones())
  });
  Msg("radarMessage", StatusText(text));
  if (!IsAdminError(text)) LoadRadarCards();
}

function LoadRadarMobDrops() {
  if (!V("radarId")) {
    radarMobDropRows = [];
    radarMobDrops = [];
    RenderRadarMobDrops();
    return;
  }
  radarMobDropRows = ParseTsv(RunAdmin("listradarmobdrops", { Id: V("radarId"), Search: V("radarMobDropSearch") }));
  radarMobDrops = [];
  for (var i = 1; i < radarMobDropRows.length; i++) {
    if (radarMobDropRows[i][4] == "1") {
      radarMobDrops.push({
        templateId: parseInt(radarMobDropRows[i][1], 10),
        name: radarMobDropRows[i][2] || "",
        quantityMin: parseInt(radarMobDropRows[i][5] || "1", 10),
        quantityMax: parseInt(radarMobDropRows[i][6] || "1", 10),
        dropRate: parseFloat(radarMobDropRows[i][7] || "0")
      });
    }
  }
  radarMobDropEditIndex = -1;
  ClearRadarMobDropEditor();
  RenderRadarMobDrops();
  UpdateRadarMetaHints();
}

function FindRadarMobDrop(templateId) {
  for (var i = 0; i < radarMobDrops.length; i++) {
    if (parseInt(radarMobDrops[i].templateId, 10) == parseInt(templateId, 10)) return i;
  }
  return -1;
}

function RenderRadarMobDrops() {
  var html = "<thead><tr><th>Mob</th><th>Tr\u1ea1ng th\u00e1i</th><th>S\u1ed1 l\u01b0\u1ee3ng</th><th>T\u1ec9 l\u1ec7</th></tr></thead><tbody>";
  for (var i = 1; i < radarMobDropRows.length; i++) {
    var row = radarMobDropRows[i];
    var idx = FindRadarMobDrop(row[1]);
    var drop = idx >= 0 ? radarMobDrops[idx] : null;
    html += '<tr onclick="PickRadarMobDropRow(' + i + ')"><td>' + Html(row[1] + " - " + (row[2] || "")) + '</td>';
    html += '<td>' + (drop ? "\u0110ang r\u01a1i" : "Ch\u01b0a r\u01a1i") + '</td>';
    html += '<td>' + Html(drop ? (drop.quantityMin + "-" + drop.quantityMax) : "") + '</td>';
    html += '<td>' + Html(drop ? (drop.dropRate + "%") : "") + '</td></tr>';
  }
  document.getElementById("radarMobDropsTable").innerHTML = html + "</tbody>";
}

function PickRadarMobDropRow(rowIndex) {
  var row = radarMobDropRows[rowIndex];
  if (!row) return;
  var idx = FindRadarMobDrop(row[1]);
  radarMobDropEditIndex = rowIndex;
  Set("radarDropMobLabel", row[1] + " - " + (row[2] || ""));
  Set("radarDropMin", idx >= 0 ? radarMobDrops[idx].quantityMin : "1");
  Set("radarDropMax", idx >= 0 ? radarMobDrops[idx].quantityMax : "1");
  Set("radarDropRate", idx >= 0 ? radarMobDrops[idx].dropRate : "1");
}

function ClearRadarMobDropEditor() {
  Set("radarDropMobLabel", "");
  Set("radarDropMin", "1");
  Set("radarDropMax", "1");
  Set("radarDropRate", "1");
}

function UpdateRadarMobDrop() {
  if (radarMobDropEditIndex < 1 || !radarMobDropRows[radarMobDropEditIndex]) { Msg("radarMessage", "H\u00e3y ch\u1ecdn m\u1ed9t Mob trong b\u1ea3ng."); return false; }
  if (!/^\d+$/.test(V("radarDropMin")) || !/^\d+$/.test(V("radarDropMax")) || !/^\d+(\.\d+)?$/.test(V("radarDropRate"))) {
    Msg("radarMessage", "S\u1ed1 l\u01b0\u1ee3ng v\u00e0 t\u1ec9 l\u1ec7 r\u01a1i ph\u1ea3i h\u1ee3p l\u1ec7."); return false;
  }
  var min = parseInt(V("radarDropMin"), 10), max = parseInt(V("radarDropMax"), 10), rate = parseFloat(V("radarDropRate"));
  if (min < 1 || min > max || rate < 0 || rate > 100) { Msg("radarMessage", "Min ph\u1ea3i >= 1, kh\u00f4ng l\u1edbn h\u01a1n max; t\u1ec9 l\u1ec7 0-100%."); return false; }
  var row = radarMobDropRows[radarMobDropEditIndex];
  var idx = FindRadarMobDrop(row[1]);
  var drop = { templateId: parseInt(row[1], 10), name: row[2] || "", quantityMin: min, quantityMax: max, dropRate: rate };
  if (idx >= 0) radarMobDrops[idx] = drop; else radarMobDrops.push(drop);
  RenderRadarMobDrops();
  Msg("radarMessage", "\u0110\u00e3 c\u1eadp nh\u1eadt Mob r\u01a1i th\u1ebb; b\u1ea5m L\u01b0u Mob r\u01a1i th\u1ebb \u0111\u1ec3 ghi database.");
  return true;
}

function RemoveRadarMobDrop() {
  if (radarMobDropEditIndex < 1 || !radarMobDropRows[radarMobDropEditIndex]) return;
  var idx = FindRadarMobDrop(radarMobDropRows[radarMobDropEditIndex][1]);
  if (idx >= 0) radarMobDrops.splice(idx, 1);
  ClearRadarMobDropEditor();
  RenderRadarMobDrops();
}

function SaveRadarMobDrops() {
  if (!V("radarId")) { Msg("radarMessage", "C\u1ea7n l\u01b0u/ch\u1ecdn th\u1ebb tr\u01b0\u1edbc."); return; }
  if (radarMobDropEditIndex >= 1 && V("radarDropMobLabel")) {
    if (!UpdateRadarMobDrop()) return;
  }
  var text = RunAdmin("saveradarmobdrops", { Id: V("radarId"), MobDropsJson: JSON.stringify(radarMobDrops) });
  Msg("radarMessage", StatusText(text));
  if (!IsAdminError(text)) LoadRadarMobDrops();
}

function LoadRadarBossDrops() {
  if (!V("radarId")) {
    radarBossDropRows = [];
    radarBossDrops = [];
    RenderRadarBossDrops();
    return;
  }
  radarBossDropRows = ParseTsv(RunAdmin("listradarbossdrops", { Id: V("radarId"), Search: V("radarBossDropSearch") }));
  radarBossDrops = [];
  for (var i = 1; i < radarBossDropRows.length; i++) {
    if (radarBossDropRows[i][5] == "1") {
      radarBossDrops.push({
        bossId: parseInt(radarBossDropRows[i][0], 10),
        key: radarBossDropRows[i][1] || "",
        name: radarBossDropRows[i][2] || "",
        quantityMin: parseInt(radarBossDropRows[i][6] || "1", 10),
        quantityMax: parseInt(radarBossDropRows[i][7] || "1", 10),
        dropRate: parseFloat(radarBossDropRows[i][8] || "0")
      });
    }
  }
  radarBossDropEditIndex = -1;
  ClearRadarBossDropEditor();
  RenderRadarBossDrops();
}

function FindRadarBossDrop(bossId) {
  for (var i = 0; i < radarBossDrops.length; i++) {
    if (parseInt(radarBossDrops[i].bossId, 10) == parseInt(bossId, 10)) return i;
  }
  return -1;
}

function RadarBossLabel(row) {
  if (!row) return "";
  return row[0] + " - " + (row[2] || row[1] || "");
}

function RenderRadarBossDrops() {
  var html = "<thead><tr><th>Boss</th><th>Map</th><th>Tr\u1ea1ng th\u00e1i</th><th>S\u1ed1 l\u01b0\u1ee3ng</th><th>T\u1ec9 l\u1ec7</th></tr></thead><tbody>";
  for (var i = 1; i < radarBossDropRows.length; i++) {
    var row = radarBossDropRows[i];
    var idx = FindRadarBossDrop(row[0]);
    var drop = idx >= 0 ? radarBossDrops[idx] : null;
    html += '<tr onclick="PickRadarBossDropRow(' + i + ')"><td>' + Html(RadarBossLabel(row)) + '</td>';
    html += '<td>' + Html(row[4] || "") + '</td>';
    html += '<td>' + (drop ? "\u0110ang r\u01a1i" : "Ch\u01b0a r\u01a1i") + '</td>';
    html += '<td>' + Html(drop ? (drop.quantityMin + "-" + drop.quantityMax) : "") + '</td>';
    html += '<td>' + Html(drop ? (drop.dropRate + "%") : "") + '</td></tr>';
  }
  document.getElementById("radarBossDropsTable").innerHTML = html + "</tbody>";
}

function PickRadarBossDropRow(rowIndex) {
  var row = radarBossDropRows[rowIndex];
  if (!row) return;
  var idx = FindRadarBossDrop(row[0]);
  radarBossDropEditIndex = rowIndex;
  Set("radarBossDropLabel", RadarBossLabel(row));
  Set("radarBossDropMin", idx >= 0 ? radarBossDrops[idx].quantityMin : "1");
  Set("radarBossDropMax", idx >= 0 ? radarBossDrops[idx].quantityMax : "1");
  Set("radarBossDropRate", idx >= 0 ? radarBossDrops[idx].dropRate : "1");
}

function ClearRadarBossDropEditor() {
  Set("radarBossDropLabel", "");
  Set("radarBossDropMin", "1");
  Set("radarBossDropMax", "1");
  Set("radarBossDropRate", "1");
}

function UpdateRadarBossDrop() {
  if (radarBossDropEditIndex < 1 || !radarBossDropRows[radarBossDropEditIndex]) { Msg("radarMessage", "H\u00e3y ch\u1ecdn m\u1ed9t Boss trong b\u1ea3ng."); return false; }
  if (!/^\d+$/.test(V("radarBossDropMin")) || !/^\d+$/.test(V("radarBossDropMax")) || !/^\d+(\.\d+)?$/.test(V("radarBossDropRate"))) {
    Msg("radarMessage", "S\u1ed1 l\u01b0\u1ee3ng v\u00e0 t\u1ec9 l\u1ec7 r\u01a1i ph\u1ea3i h\u1ee3p l\u1ec7."); return false;
  }
  var min = parseInt(V("radarBossDropMin"), 10), max = parseInt(V("radarBossDropMax"), 10), rate = parseFloat(V("radarBossDropRate"));
  if (min < 1 || min > max || rate < 0 || rate > 100) { Msg("radarMessage", "Min ph\u1ea3i >= 1, kh\u00f4ng l\u1edbn h\u01a1n max; t\u1ec9 l\u1ec7 0-100%."); return false; }
  var row = radarBossDropRows[radarBossDropEditIndex];
  var idx = FindRadarBossDrop(row[0]);
  var drop = { bossId: parseInt(row[0], 10), key: row[1] || "", name: row[2] || "", quantityMin: min, quantityMax: max, dropRate: rate };
  if (idx >= 0) radarBossDrops[idx] = drop; else radarBossDrops.push(drop);
  RenderRadarBossDrops();
  Msg("radarMessage", "\u0110\u00e3 c\u1eadp nh\u1eadt Boss r\u01a1i th\u1ebb; b\u1ea5m L\u01b0u Boss r\u01a1i th\u1ebb \u0111\u1ec3 ghi database.");
  return true;
}

function RemoveRadarBossDrop() {
  if (radarBossDropEditIndex < 1 || !radarBossDropRows[radarBossDropEditIndex]) return;
  var idx = FindRadarBossDrop(radarBossDropRows[radarBossDropEditIndex][0]);
  if (idx >= 0) radarBossDrops.splice(idx, 1);
  ClearRadarBossDropEditor();
  RenderRadarBossDrops();
}

function SaveRadarBossDrops() {
  if (!V("radarId")) { Msg("radarMessage", "C\u1ea7n l\u01b0u/ch\u1ecdn th\u1ebb tr\u01b0\u1edbc."); return; }
  if (radarBossDropEditIndex >= 1 && V("radarBossDropLabel")) {
    if (!UpdateRadarBossDrop()) return;
  }
  var text = RunAdmin("saveradarbossdrops", { Id: V("radarId"), BossDropsJson: JSON.stringify(radarBossDrops) });
  Msg("radarMessage", StatusText(text));
  if (!IsAdminError(text)) LoadRadarBossDrops();
}

function DeleteRadarCard() {
  if (!V("radarId")) return;
  if (!window.confirm("X\u00f3a th\u1ebb s\u01b0u t\u1ea7m ID " + V("radarId") + "?")) return;
  var text = RunAdmin("deleteradarcard", { Id: V("radarId") });
  Msg("radarMessage", StatusText(text));
  if (!IsAdminError(text)) {
    NewRadarCard();
    LoadRadarCards();
  }
}

function CostumeCollectionConditionLabel(value) {
  return value == "1" ? "Bộ cải trang chỉ định" : "Đủ số cải trang";
}

function ParseCostumeCollectionItemIds(value) {
  var result = [];
  var parts = (value || "").split(",");
  for (var i = 0; i < parts.length; i++) {
    var id = Trim(parts[i]);
    if (/^\d+$/.test(id) && !ArrayContains(result, id)) result.push(id);
  }
  return result;
}

function NextCostumeCollectionAchievementId() {
  var maximum = 0;
  for (var i = 1; i < costumeCollectionAchievementRows.length; i++) {
    var id = parseInt(costumeCollectionAchievementRows[i][0], 10);
    if (!isNaN(id) && id > maximum) maximum = id;
  }
  return maximum + 1;
}

function LoadCostumeCollectionAchievements() {
  LoadItemCatalog(false);
  costumeCollectionAchievementRows = ParseTsv(RunAdmin("listcostumecollectionachievements", {}));
  FilterCostumeCollectionAchievements();
  if (costumeCollectionFilteredRows.length > 1) {
    PickCostumeCollectionAchievement(1);
  } else {
    NewCostumeCollectionAchievement();
  }
  Msg("costumeCollectionMessage", costumeCollectionAchievementRows.length > 1
    ? "Đã tải " + (costumeCollectionAchievementRows.length - 1) + " mốc thành tựu cải trang."
    : "Chưa có mốc thành tựu cải trang.");
  AdjustTableOffsets();
}

function FilterCostumeCollectionAchievements() {
  var query = Trim(V("costumeCollectionSearch")).toLowerCase();
  costumeCollectionFilteredRows = [costumeCollectionAchievementRows[0] || []];
  var html = "<thead><tr><th>ID</th><th>Tên mốc</th><th>Điều kiện</th><th>Tiến độ yêu cầu</th><th>Phần thưởng</th></tr></thead><tbody>";
  for (var i = 1; i < costumeCollectionAchievementRows.length; i++) {
    var row = costumeCollectionAchievementRows[i];
    if (query && row.join(" ").toLowerCase().indexOf(query) < 0) continue;
    costumeCollectionFilteredRows.push(row);
    var condition = row[3] == "1"
      ? CollectionBadge("THEO BỘ", "theme", CostumeCollectionConditionLabel(row[3]))
      : CollectionBadge("SỐ LƯỢNG", "count", CostumeCollectionConditionLabel(row[3]));
    var requiredCount = row[3] == "1" ? ParseCostumeCollectionItemIds(row[5]).length : (row[4] || "0");
    var required = CollectionBadge(requiredCount + " cải trang", row[3] == "1" ? "costume" : "count");
    var rewardItems = ParseCostumeCollectionRewardItems(row[8] || "");
    var reward = CollectionRewardBadges(row[6], row[7], rewardItems);
    html += '<tr onclick="PickCostumeCollectionAchievement(' + (costumeCollectionFilteredRows.length - 1) + ')">';
    html += "<td>" + CollectionBadge("#" + row[0], "id") + "</td><td>" + Html(row[1]) + "</td><td>" + condition + "</td>";
    html += "<td>" + required + "</td><td class=\"collection-badge-list\">" + reward + "</td></tr>";
  }
  document.getElementById("costumeCollectionAchievementsTable").innerHTML = html + "</tbody>";
}

function PickCostumeCollectionAchievement(index) {
  var row = costumeCollectionFilteredRows[index];
  if (!row) return;
  costumeCollectionIsNew = false;
  Set("costumeCollectionId", row[0] || "");
  Set("costumeCollectionName", row[1] || "");
  Set("costumeCollectionDescription", row[2] || "");
  Set("costumeCollectionCondition", row[3] || "0");
  Set("costumeCollectionTarget", row[4] || "0");
  Set("costumeCollectionRewardGold", row[6] || "0");
  Set("costumeCollectionRewardGem", row[7] || "0");
  costumeCollectionSelectedItemIds = ParseCostumeCollectionItemIds(row[5] || "");
  ApplyCostumeCollectionRewardItems(row[8] || "", false);
  UpdateCostumeCollectionCondition();
}

function NewCostumeCollectionAchievement() {
  costumeCollectionIsNew = true;
  Set("costumeCollectionId", NextCostumeCollectionAchievementId());
  Set("costumeCollectionName", "");
  Set("costumeCollectionDescription", "");
  Set("costumeCollectionCondition", "0");
  Set("costumeCollectionTarget", "10");
  Set("costumeCollectionRewardGold", "0");
  Set("costumeCollectionRewardGem", "0");
  Set("costumeCollectionItemSearch", "");
  Set("costumeCollectionRewardSearch", "");
  costumeCollectionSelectedItemIds = [];
  costumeCollectionRewardItemIds = [];
  costumeCollectionRewardQuantities = {};
  UpdateCostumeCollectionCondition();
}

function RenderCostumeCollectionEditorSummary() {
  var summary = document.getElementById("costumeCollectionSummary");
  if (!summary) return;
  var isTheme = V("costumeCollectionCondition") == "1";
  var target = isTheme ? costumeCollectionSelectedItemIds.length : (V("costumeCollectionTarget") || "0");
  var html = CollectionBadge(costumeCollectionIsNew ? "MỐC MỚI" : "MỐC #" + V("costumeCollectionId"), costumeCollectionIsNew ? "new" : "id");
  html += CollectionBadge(isTheme ? "THEO BỘ" : "SỐ LƯỢNG", isTheme ? "theme" : "count");
  html += CollectionBadge(target + " cải trang", "costume");
  html += CollectionRewardBadges(V("costumeCollectionRewardGold"), V("costumeCollectionRewardGem"), {
    ids: costumeCollectionRewardItemIds
  });
  summary.innerHTML = html;
}

function CostumeCollectionSelectedItemMap() {
  var result = {};
  for (var i = 0; i < costumeCollectionSelectedItemIds.length; i++) result[costumeCollectionSelectedItemIds[i]] = true;
  return result;
}

function CostumeCollectionCostumeRows() {
  var rows = [itemCatalogRows[0] || ["id", "name", "description", "type", "gender", "icon"]];
  for (var i = 1; i < itemCatalogRows.length; i++) {
    if (itemCatalogRows[i][3] == "5") rows.push(itemCatalogRows[i]);
  }
  return rows;
}

function RenderCostumeCollectionItemPicker() {
  var selected = CostumeCollectionSelectedItemMap();
  var query = V("costumeCollectionItemSearch");
  RenderItemPicker({
    listId: "costumeCollectionItemList",
    rows: CostumeCollectionCostumeRows(),
    selected: selected,
    selectedOrder: costumeCollectionSelectedItemIds,
    query: query,
    toggleFunction: "ToggleCostumeCollectionItem",
    idPrefix: "costumeCollectionItem",
    itemClass: "costume-collection-item",
    lightweightCheckbox: true,
    limit: Trim(query) ? 60 : 24
  });
  UpdateCostumeCollectionItemSummary();
}

function UpdateCostumeCollectionItemSummary() {
  var summary = document.getElementById("costumeCollectionItemSummary");
  if (!costumeCollectionSelectedItemIds.length) {
    summary.innerHTML = CollectionBadge("CHƯA CHỌN", "off") + " Nhập ID hoặc tên để thêm cải trang vào bộ.";
  } else {
    var html = CollectionBadge(costumeCollectionSelectedItemIds.length + " CẢI TRANG", "costume");
    var visibleCount = Math.min(6, costumeCollectionSelectedItemIds.length);
    for (var i = 0; i < visibleCount; i++) {
      var itemId = costumeCollectionSelectedItemIds[i];
      html += CollectionBadge("#" + itemId, "id", ItemCatalogName(itemId));
    }
    if (costumeCollectionSelectedItemIds.length > visibleCount) {
      html += CollectionBadge("+" + (costumeCollectionSelectedItemIds.length - visibleCount), "more");
    }
    summary.innerHTML = html;
  }
}

function ToggleCostumeCollectionItem(itemId, checked) {
  var index = ArrayIndexOf(costumeCollectionSelectedItemIds, itemId);
  if (checked && index < 0) costumeCollectionSelectedItemIds.push(itemId);
  if (!checked && index >= 0) costumeCollectionSelectedItemIds.splice(index, 1);
  var checkbox = document.getElementById("costumeCollectionItem_" + itemId);
  if (checkbox && checkbox.parentNode) {
    checkbox.parentNode.className = "option-picker-item costume-collection-item"
      + (checked ? " checklist-item-selected" : "");
  }
  UpdateCostumeCollectionCondition(false);
  UpdateCostumeCollectionItemSummary();
}

function ScheduleCostumeCollectionItemPicker() {
  if (costumeCollectionItemSearchTimer) window.clearTimeout(costumeCollectionItemSearchTimer);
  costumeCollectionItemSearchTimer = window.setTimeout(function () {
    costumeCollectionItemSearchTimer = null;
    RenderCostumeCollectionItemPicker();
  }, 120);
}

function ParseCostumeCollectionRewardItems(value) {
  var result = { ids: [], quantities: {} };
  var parts = (value || "").split(",");
  for (var i = 0; i < parts.length; i++) {
    var match = /^\s*(\d+)\s*:\s*(\d+)\s*$/.exec(parts[i]);
    if (!match || ArrayContains(result.ids, match[1])) continue;
    result.ids.push(match[1]);
    result.quantities[match[1]] = match[2];
  }
  return result;
}

function ApplyCostumeCollectionRewardItems(value, renderNow) {
  var parsed = ParseCostumeCollectionRewardItems(value);
  costumeCollectionRewardItemIds = parsed.ids;
  costumeCollectionRewardQuantities = parsed.quantities;
  if (renderNow !== false) RenderCostumeCollectionRewardPicker();
}

function CostumeCollectionRewardItemMap() {
  var result = {};
  for (var i = 0; i < costumeCollectionRewardItemIds.length; i++) result[costumeCollectionRewardItemIds[i]] = true;
  return result;
}

function RenderCostumeCollectionRewardPicker() {
  var selected = CostumeCollectionRewardItemMap();
  var query = V("costumeCollectionRewardSearch");
  RenderItemPicker({
    listId: "costumeCollectionRewardList",
    selected: selected,
    selectedOrder: costumeCollectionRewardItemIds,
    paramValues: costumeCollectionRewardQuantities,
    query: query,
    toggleFunction: "ToggleCostumeCollectionRewardItem",
    paramFunction: "SetCostumeCollectionRewardQuantity",
    idPrefix: "costumeCollectionReward",
    itemClass: "costume-collection-reward-item",
    showParam: true,
    lightweightCheckbox: true,
    limit: Trim(query) ? 60 : 24,
    emptyText: "Không tìm thấy vật phẩm phù hợp."
  });
  UpdateCostumeCollectionRewardSummary();
}

function UpdateCostumeCollectionRewardSummary() {
  var summary = document.getElementById("costumeCollectionRewardSummary");
  if (!costumeCollectionRewardItemIds.length) {
    summary.innerHTML = CollectionBadge("CHƯA CHỌN", "off") + " Nhập ID hoặc tên để thêm vật phẩm thưởng.";
    RenderCostumeCollectionEditorSummary();
    return;
  }
  var html = CollectionBadge(costumeCollectionRewardItemIds.length + " VẬT PHẨM", "item");
  for (var i = 0; i < costumeCollectionRewardItemIds.length && i < 3; i++) {
    var id = costumeCollectionRewardItemIds[i];
    html += CollectionBadge("x" + (costumeCollectionRewardQuantities[id] || "1"), "quantity");
    html += '<span class="collection-summary-item">' + Html(id + " - " + ItemCatalogName(id)) + "</span>";
  }
  if (costumeCollectionRewardItemIds.length > 3) {
    html += CollectionBadge("+" + (costumeCollectionRewardItemIds.length - 3), "more");
  }
  summary.innerHTML = html;
  RenderCostumeCollectionEditorSummary();
}

function ToggleCostumeCollectionRewardItem(itemId, checked) {
  var index = ArrayIndexOf(costumeCollectionRewardItemIds, itemId);
  if (checked && index < 0) {
    if (costumeCollectionRewardItemIds.length >= 50) {
      var overLimitCheckbox = document.getElementById("costumeCollectionReward_" + itemId);
      if (overLimitCheckbox) overLimitCheckbox.checked = false;
      Msg("costumeCollectionMessage", "Chỉ được chọn tối đa 50 vật phẩm thưởng.");
      return;
    }
    costumeCollectionRewardItemIds.push(itemId);
    costumeCollectionRewardQuantities[itemId] = "1";
  }
  if (!checked && index >= 0) {
    costumeCollectionRewardItemIds.splice(index, 1);
    delete costumeCollectionRewardQuantities[itemId];
  }
  SyncCostumeCollectionRewardItem(itemId, checked);
  UpdateCostumeCollectionRewardSummary();
}

function SetCostumeCollectionRewardQuantity(itemId, quantity) {
  costumeCollectionRewardQuantities[itemId] = Trim(quantity);
  UpdateCostumeCollectionRewardSummary();
}

function SyncCostumeCollectionRewardItem(itemId, checked) {
  var checkbox = document.getElementById("costumeCollectionReward_" + itemId);
  if (!checkbox || !checkbox.parentNode) return;
  var row = checkbox.parentNode;
  row.className = "option-picker-item costume-collection-reward-item"
    + (checked ? " checklist-item-selected checklist-item-with-param" : "");
  var inputs = row.getElementsByTagName("input");
  for (var index = inputs.length - 1; index >= 0; index--) {
    if (inputs[index] != checkbox) row.removeChild(inputs[index]);
  }
  if (!checked) return;
  var quantityInput = document.createElement("input");
  quantityInput.className = "checklist-param-input";
  quantityInput.value = costumeCollectionRewardQuantities[itemId] || "1";
  quantityInput.title = "Số lượng";
  quantityInput.onchange = function () { SetCostumeCollectionRewardQuantity(itemId, this.value); };
  row.appendChild(quantityInput);
}

function ScheduleCostumeCollectionRewardPicker() {
  if (costumeCollectionRewardSearchTimer) window.clearTimeout(costumeCollectionRewardSearchTimer);
  costumeCollectionRewardSearchTimer = window.setTimeout(function () {
    costumeCollectionRewardSearchTimer = null;
    RenderCostumeCollectionRewardPicker();
  }, 120);
}

function CollectCostumeCollectionRewardItems() {
  var rewards = [];
  for (var i = 0; i < costumeCollectionRewardItemIds.length; i++) {
    var itemId = costumeCollectionRewardItemIds[i];
    var quantity = NormalizeInteger(costumeCollectionRewardQuantities[itemId] || "");
    if (!/^\d+$/.test(quantity) || parseInt(quantity, 10) < 1 || parseInt(quantity, 10) > 100000000) {
      Msg("costumeCollectionMessage", "Số lượng thưởng của vật phẩm " + itemId + " phải từ 1 đến 100.000.000.");
      return null;
    }
    rewards.push({ itemId: parseInt(itemId, 10), quantity: parseInt(quantity, 10) });
  }
  return rewards;
}

function UpdateCostumeCollectionCondition(renderPickers) {
  var isTheme = V("costumeCollectionCondition") == "1";
  var themeEditor = document.getElementById("costumeCollectionThemeEditor");
  themeEditor.className = isTheme ? "option-picker-box" : "option-picker-box admin-hidden";
  document.getElementById("costumeCollectionTargetLabel").innerText = isTheme ? "Số cải trang trong bộ" : "Số cải trang cần có";
  document.getElementById("costumeCollectionTarget").disabled = isTheme;
  if (isTheme) Set("costumeCollectionTarget", costumeCollectionSelectedItemIds.length);
  if (renderPickers !== false) {
    RenderCostumeCollectionItemPicker();
    RenderCostumeCollectionRewardPicker();
    QueueAdminSelectDecoration();
  }
  RenderCostumeCollectionEditorSummary();
}

function SaveCostumeCollectionAchievement() {
  var id = Trim(V("costumeCollectionId"));
  var name = Trim(V("costumeCollectionName"));
  var condition = V("costumeCollectionCondition");
  var target = Trim(V("costumeCollectionTarget"));
  var gold = NormalizeInteger(V("costumeCollectionRewardGold"));
  var gem = NormalizeInteger(V("costumeCollectionRewardGem"));
  if (!/^\d+$/.test(id) || parseInt(id, 10) < 1 || parseInt(id, 10) > 32767) {
    Msg("costumeCollectionMessage", "ID mốc phải là số từ 1 đến 32767."); return;
  }
  if (!name) { Msg("costumeCollectionMessage", "Cần nhập tên thành tựu."); return; }
  if (condition != "0" && condition != "1") { Msg("costumeCollectionMessage", "Điều kiện thành tựu không hợp lệ."); return; }
  if (condition == "0" && (!/^\d+$/.test(target) || parseInt(target, 10) < 1)) {
    Msg("costumeCollectionMessage", "Số cải trang cần có phải là số nguyên dương."); return;
  }
  if (condition == "1" && !costumeCollectionSelectedItemIds.length) {
    Msg("costumeCollectionMessage", "Hãy chọn ít nhất một cải trang cho bộ sưu tầm."); return;
  }
  if (!/^\d+$/.test(gold) || !/^\d+$/.test(gem)) {
    Msg("costumeCollectionMessage", "Thưởng vàng và ngọc phải là số không âm."); return;
  }
  var rewardItems = CollectCostumeCollectionRewardItems();
  if (rewardItems == null) return;
  var text = RunAdmin("savecostumecollectionachievement", {
    Id: id,
    Name: V("costumeCollectionName"),
    Description: V("costumeCollectionDescription"),
    CollectionCondition: condition,
    RequiredCount: condition == "1" ? costumeCollectionSelectedItemIds.length : target,
    RequiredTemplateIdsJson: JSON.stringify(costumeCollectionSelectedItemIds),
    Gold: gold,
    Gem: gem,
    RewardItemsJson: JSON.stringify(rewardItems)
  });
  Msg("costumeCollectionMessage", StatusText(text));
  if (!IsAdminError(text)) LoadCostumeCollectionAchievements();
}

function DeleteCostumeCollectionAchievement() {
  var id = Trim(V("costumeCollectionId"));
  if (!/^\d+$/.test(id)) return;
  if (!window.confirm("Xóa mốc thành tựu cải trang ID " + id + "?")) return;
  var text = RunAdmin("deletecostumecollectionachievement", { Id: id });
  Msg("costumeCollectionMessage", StatusText(text));
  if (!IsAdminError(text)) LoadCostumeCollectionAchievements();
}

function FishBookRankLabel(value) {
  return "Rank " + (value || "0");
}

function LoadFishBookEntries() {
  fishBookRows = ParseTsv(RunAdmin("listfishbookentries", {}));
  FilterFishBookEntries();
  if (fishBookFilteredRows.length > 1) PickFishBookEntry(1);
  else ClearFishBookEntry();
  Msg("fishBookMessage", fishBookRows.length > 1
    ? "Đã tải " + (fishBookRows.length - 1) + " thẻ Sổ Tay Ngư Phủ."
    : "Chưa tải được cấu hình Sổ Tay Ngư Phủ.");
  AdjustTableOffsets();
}

function FilterFishBookEntries() {
  var query = Trim(V("fishBookSearch")).toLowerCase();
  fishBookFilteredRows = [fishBookRows[0] || []];
  var html = "<thead><tr><th>Ảnh</th><th>ID</th><th>Cấp</th><th>Tên hiển thị</th><th>Độ hiếm</th><th>Hạng</th><th>Mob</th></tr></thead><tbody>";
  for (var i = 1; i < fishBookRows.length; i++) {
    var row = fishBookRows[i];
    if (query && row.join(" ").toLowerCase().indexOf(query) < 0) continue;
    fishBookFilteredRows.push(row);
    var iconSrc = RadarIconSrc(row[2] || "");
    var tier = parseInt(row[0], 10) - 2124 + 1;
    html += '<tr onclick="PickFishBookEntry(' + (fishBookFilteredRows.length - 1) + ')">';
    html += '<td class="radar-icon-cell">' + (iconSrc ? '<img class="radar-icon" src="' + HtmlAttr(iconSrc) + '" onerror="this.style.visibility=\'hidden\'">' : "") + "</td>";
    html += "<td>" + CollectionBadge("#" + row[0], "id") + "</td><td>" + CollectionBadge("CẤP " + tier, "fish") + "</td><td>" + Html(row[4]) + "</td>";
    html += "<td>" + CollectionRarityBadge(row[5]) + "</td><td>" + CollectionRankBadge(row[7]) + "</td><td>" + CollectionBadge("MOB " + row[6], "mob") + "</td></tr>";
  }
  document.getElementById("fishBookEntriesTable").innerHTML = html + "</tbody>";
}

function PickFishBookEntry(index) {
  var row = fishBookFilteredRows[index];
  if (!row) return;
  var tier = parseInt(row[0], 10) - 2124 + 1;
  Set("fishBookItemId", row[0] || "");
  Set("fishBookTier", tier);
  Set("fishBookItemName", row[1] || "");
  Set("fishBookName", row[4] || "");
  Set("fishBookRarity", row[5] || "");
  Set("fishBookMobId", row[6] || "-1");
  Set("fishBookRank", row[7] || "0");
  var iconSrc = RadarIconSrc(row[2] || "");
  document.getElementById("fishBookPreview").innerHTML = (iconSrc ? '<img class="radar-icon" src="' + HtmlAttr(iconSrc) + '" onerror="this.style.visibility=\'hidden\'"> ' : "")
    + CollectionBadge("CẤP " + tier, "fish") + CollectionRarityBadge(row[5]) + CollectionRankBadge(row[7])
    + '<span class="collection-preview-name">' + Html(row[4] || "") + "</span>";
  QueueAdminSelectDecoration();
}

function ClearFishBookEntry() {
  Set("fishBookItemId", ""); Set("fishBookTier", ""); Set("fishBookItemName", "");
  Set("fishBookName", ""); Set("fishBookRarity", ""); Set("fishBookMobId", "-1"); Set("fishBookRank", "0");
  document.getElementById("fishBookPreview").innerHTML = CollectionBadge("CHƯA CHỌN", "off") + " Chọn một loài cá để cấu hình thẻ sưu tầm.";
}

function SaveFishBookEntry() {
  var itemId = Trim(V("fishBookItemId"));
  var name = Trim(V("fishBookName"));
  var rarity = Trim(V("fishBookRarity"));
  var mobId = Trim(V("fishBookMobId"));
  var rank = V("fishBookRank");
  if (!/^\d+$/.test(itemId) || parseInt(itemId, 10) < 2124 || parseInt(itemId, 10) > 2138) {
    Msg("fishBookMessage", "Hãy chọn một thẻ cá hợp lệ."); return;
  }
  if (!name || !rarity) { Msg("fishBookMessage", "Tên hiển thị và độ hiếm không được để trống."); return; }
  if (!/^-?\d+$/.test(mobId) || parseInt(mobId, 10) < -1 || parseInt(mobId, 10) > 32767) {
    Msg("fishBookMessage", "Mob template phải từ -1 đến 32767."); return;
  }
  if (!/^[0-6]$/.test(rank)) { Msg("fishBookMessage", "Hạng thẻ phải từ 0 đến 6."); return; }
  var text = RunAdmin("savefishbookentry", {
    Id: itemId,
    FishBookName: name,
    FishBookRarity: rarity,
    FishBookMobId: mobId,
    FishBookRank: rank
  });
  Msg("fishBookMessage", StatusText(text));
  if (!IsAdminError(text)) LoadFishBookEntries();
}

RegisterTab({
  id: "radar", view: "radar.html", panelId: "panelRadar", navId: "navRadar",
  title: "Sổ sưu tầm", subtitle: "Cấu hình thẻ radar, thành tựu cải trang và Sổ Tay Ngư Phủ",
  onOpen: function () {
    RenderCollectionTabs();
    SelectCollectionSection(selectedCollectionSection);
  },
  onRefresh: function () { RefreshCollectionSection(); }
});
