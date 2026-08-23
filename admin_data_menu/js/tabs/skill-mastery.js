var skillMasteryData = null;
var skillMasteryProperties = {};
var skillMasterySelectedId = -1;
var skillMasteryLevelRows = [];
var skillMasteryEditingIndex = -1;

function SkillMasteryFieldValue(field, property) {
  return field[property] != null ? field[property] : field[property.toLowerCase()];
}

function SkillMasteryInputId(scope, key) {
  return "skillMastery_" + scope + "_" + ("" + key).replace(/[^A-Za-z0-9_-]/g, "_");
}

function LoadSkillMastery(selectId, showGeneral) {
  var text = RunAdmin("listskillmastery", {});
  if (IsAdminError(text)) {
    Msg("skillMasteryMessage", StatusText(text));
    return;
  }
  try {
    skillMasteryData = JSON.parse(text.replace(/^\uFEFF/, ""));
  } catch (e) {
    Msg("skillMasteryMessage", "ERROR Không đọc được dữ liệu quản lý Skill: " + e.message);
    return;
  }
  skillMasteryProperties = {};
  var properties = skillMasteryData.properties || [];
  for (var i = 0; i < properties.length; i++) skillMasteryProperties[properties[i].key] = "" + properties[i].value;
  RenderSkillMasteryGeneralFields();
  FilterSkillMasteryCatalog();
  if (!showGeneral && selectId != null && parseInt(selectId, 10) >= 0) PickSkillMastery(parseInt(selectId, 10));
  else ShowSkillMasteryGeneral();
  Msg("skillMasteryMessage", "Đã tải " + ((skillMasteryData.skills || []).length) + " skill. Cấu hình được runtime đọc lại trong tối đa 1 giây.");
  AdjustTableOffsets();
}

function SkillMasteryGetProperty(key, fallback) {
  return skillMasteryProperties[key] != null ? skillMasteryProperties[key] : fallback;
}

function SkillMasteryGeneralDefault(key) {
  var fields = skillMasteryData ? (skillMasteryData.generalFields || []) : [];
  for (var i = 0; i < fields.length; i++) if (SkillMasteryFieldValue(fields[i], "Key") == key) return "" + SkillMasteryFieldValue(fields[i], "Default");
  return "";
}

function SkillMasteryGlobalValue(fieldName) {
  var key = "mastery." + fieldName;
  return SkillMasteryGetProperty(key, SkillMasteryGeneralDefault(key));
}

function SkillMasteryFormulaValue(skillId, fieldName) {
  var own = SkillMasteryGetProperty("skill." + skillId + "." + fieldName, null);
  return own != null && own !== "" ? own : SkillMasteryGlobalValue(fieldName);
}

function SkillMasteryNumber(value, fallback) {
  var number = parseFloat(("" + value).replace(/\s/g, ""));
  return isNaN(number) ? (fallback == null ? 0 : fallback) : number;
}

function SkillMasteryFindSkill(skillId) {
  var skills = skillMasteryData ? (skillMasteryData.skills || []) : [];
  for (var i = 0; i < skills.length; i++) if (parseInt(skills[i].id, 10) == parseInt(skillId, 10)) return skills[i];
  return null;
}

function SkillMasteryPrefixCount(prefix) {
  var count = 0;
  for (var key in skillMasteryProperties) if (key.indexOf(prefix) == 0) count++;
  return count;
}

function SkillMasteryExactLevelCount(skillId) {
  var seen = {};
  var pattern = new RegExp("^skill\\." + skillId + "\\.level\\.(\\d+)\\.");
  for (var key in skillMasteryProperties) {
    var match = pattern.exec(key);
    if (match) seen[match[1]] = true;
  }
  var count = 0;
  for (var level in seen) count++;
  return count;
}

function FilterSkillMasteryCatalog() {
  if (!skillMasteryData || !document.getElementById("skillMasteryCatalogTable")) return;
  var query = Trim(V("skillMasterySearch")).toLowerCase();
  var classFilter = V("skillMasteryClassFilter");
  var skills = skillMasteryData.skills || [];
  var html = "<thead><tr><th>ID / Skill</th><th>Hành tinh</th><th>Damage cấp 7</th><th>Cấu hình riêng</th></tr></thead><tbody>";
  var shown = 0;
  for (var i = 0; i < skills.length; i++) {
    var skill = skills[i];
    if (classFilter !== "" && ("" + skill.classId) != classFilter) continue;
    if (query && ((skill.id + " " + skill.name + " " + skill.className).toLowerCase().indexOf(query) < 0)) continue;
    var overrideCount = SkillMasteryPrefixCount("skill." + skill.id + ".");
    var levelCount = SkillMasteryExactLevelCount(skill.id);
    var status = overrideCount ? (overrideCount + " dòng" + (levelCount ? ", " + levelCount + " cấp" : "")) : "Kế thừa";
    var active = parseInt(skill.id, 10) == skillMasterySelectedId ? ' class="skill-row-selected"' : "";
    html += '<tr' + active + ' onclick="PickSkillMastery(' + skill.id + ')"><td>' + Html(skill.id + " - " + skill.name) + '</td><td>' + Html(skill.className) + '</td><td>' + Html(FormatNumber(skill.base.damage)) + '</td><td>' + Html(status) + '</td></tr>';
    shown++;
  }
  if (!shown) html += '<tr><td colspan="4">Không tìm thấy skill phù hợp.</td></tr>';
  document.getElementById("skillMasteryCatalogTable").innerHTML = html + "</tbody>";
}

function RenderSkillMasteryFieldGroups(targetId, fields, scope, useDefaults) {
  var target = document.getElementById(targetId);
  if (!target) return;
  var html = "";
  var group = "";
  for (var i = 0; i < fields.length; i++) {
    var field = fields[i];
    var key = SkillMasteryFieldValue(field, "Key");
    var nextGroup = SkillMasteryFieldValue(field, "Group") || "Cấu hình";
    if (nextGroup != group) {
      if (group) html += "</div></div>";
      group = nextGroup;
      html += '<div class="skill-config-group"><div class="skill-config-group-title">' + Html(group) + '</div><div class="skill-config-grid">';
    }
    var id = SkillMasteryInputId(scope, key);
    var kind = SkillMasteryFieldValue(field, "Kind");
    var description = SkillMasteryFieldValue(field, "Description") || "";
    var value;
    var placeholder = "";
    if (useDefaults) {
      value = SkillMasteryGetProperty(key, "" + SkillMasteryFieldValue(field, "Default"));
    } else {
      value = SkillMasteryGetProperty("skill." + skillMasterySelectedId + "." + key, "");
      placeholder = "Kế thừa: " + SkillMasteryGlobalValue(key);
    }
    html += '<div class="skill-config-field" title="' + HtmlAttr(description) + '"><label for="' + id + '">' + Html(SkillMasteryFieldValue(field, "Name")) + '</label>';
    if (kind == "bool" && useDefaults) {
      html += '<label class="option-default-toggle skill-config-toggle"><input type="checkbox" id="' + id + '"' + (IsTrueValue(value) ? ' checked="checked"' : '') + '><span>' + (IsTrueValue(value) ? "Đang bật" : "Đang tắt") + '</span></label>';
    } else {
      html += '<input type="text" id="' + id + '" value="' + HtmlAttr(value) + '" placeholder="' + HtmlAttr(placeholder) + '">';
    }
    html += '<div class="skill-field-hint">' + Html(description) + '</div></div>';
  }
  if (group) html += "</div></div>";
  target.innerHTML = html;
}

function RenderSkillMasteryGeneralFields() {
  if (!skillMasteryData) return;
  RenderSkillMasteryFieldGroups("skillMasteryGeneralFields", skillMasteryData.generalFields || [], "general", true);
}

function ShowSkillMasteryGeneral() {
  skillMasterySelectedId = -1;
  skillMasteryEditingIndex = -1;
  document.getElementById("skillMasteryGeneralPanel").style.display = "block";
  document.getElementById("skillMasterySkillPanel").style.display = "none";
  RenderSkillMasteryGeneralFields();
  FilterSkillMasteryCatalog();
}

function PickSkillMastery(skillId) {
  var skill = SkillMasteryFindSkill(skillId);
  if (!skill) return;
  skillMasterySelectedId = parseInt(skillId, 10);
  skillMasteryEditingIndex = -1;
  document.getElementById("skillMasteryGeneralPanel").style.display = "none";
  document.getElementById("skillMasterySkillPanel").style.display = "block";
  document.getElementById("skillMasteryLevelEditor").style.display = "none";
  document.getElementById("skillMasterySkillTitle").innerText = skill.id + " - " + skill.name + " (" + skill.className + ")";
  var base = skill.base;
  document.getElementById("skillMasteryBaseStats").innerHTML =
    SkillMasteryStatChip("Damage", base.damage) + SkillMasteryStatChip("Mana", base.manaUse) +
    SkillMasteryStatChip("Cooldown", base.coolDown + " ms") + SkillMasteryStatChip("Phạm vi", base.dx + " × " + base.dy) +
    SkillMasteryStatChip("Mục tiêu", base.maxFight) + SkillMasteryStatChip("Hiệu ứng", skill.damageInfo || "Theo code");
  RenderSkillMasteryFieldGroups("skillMasterySkillFields", skillMasteryData.skillFields || [], "skill", false);
  LoadSkillMasteryLevelRows();
  RenderSkillMasteryLevelTable();
  var nextLevel = 8;
  for (var i = 0; i < skillMasteryLevelRows.length; i++) nextLevel = Math.max(nextLevel, parseInt(skillMasteryLevelRows[i].level, 10) + 1);
  Set("skillMasteryNewLevel", nextLevel);
  FilterSkillMasteryCatalog();
  Msg("skillMasteryMessage", "Đang chỉnh " + skill.name + ". Chỉ số gốc và animation đều lấy mốc cấp 7.");
}

function SkillMasteryStatChip(label, value) {
  return '<span class="skill-stat-chip"><b>' + Html(label) + '</b> ' + Html(value) + '</span>';
}

function LoadSkillMasteryLevelRows() {
  skillMasteryLevelRows = [];
  var byLevel = {};
  var pattern = new RegExp("^skill\\." + skillMasterySelectedId + "\\.level\\.(\\d+)\\.([A-Za-z0-9]+)$");
  for (var key in skillMasteryProperties) {
    var match = pattern.exec(key);
    if (!match) continue;
    var level = parseInt(match[1], 10);
    if (!byLevel[level]) { byLevel[level] = { level: level }; skillMasteryLevelRows.push(byLevel[level]); }
    byLevel[level][match[2]] = skillMasteryProperties[key];
  }
  skillMasteryLevelRows.sort(function (a, b) { return parseInt(a.level, 10) - parseInt(b.level, 10); });
}

function SkillMasteryIncrease(baseValue, percent, flat, levels) {
  return Math.max(0, Math.floor(baseValue + baseValue * percent * levels / 100 + flat * levels));
}

function SkillMasteryReduction(baseValue, percent, flat, minimumPercent, levels) {
  var reduced = Math.floor(baseValue - baseValue * percent * levels / 100 - flat * levels);
  return Math.max(Math.floor(baseValue * minimumPercent / 100), reduced, 0);
}

function SkillMasteryRequiredUses(skillId, level) {
  var base = SkillMasteryNumber(SkillMasteryFormulaValue(skillId, "requiredUses"), 1000);
  var growth = SkillMasteryNumber(SkillMasteryFormulaValue(skillId, "requiredGrowthPercent"), 20);
  var maximum = SkillMasteryNumber(SkillMasteryFormulaValue(skillId, "requiredUsesMax"), 1000000);
  var steps = Math.max(0, level - 7);
  var required = base;
  for (var i = 0; i < steps && required < maximum && i < 10000; i++) required = Math.min(maximum, required + Math.max(1, Math.floor(required * growth / 100)));
  if (steps > 10000 && required < maximum) required = Math.min(maximum, Math.floor(required * Math.pow(1 + growth / 100, steps - 10000)));
  return Math.max(1, Math.floor(required));
}

function SkillMasteryRowValue(row, key) {
  return row && row[key] != null && Trim("" + row[key]) !== "" ? SkillMasteryNumber(row[key], 0) : null;
}

function SkillMasteryPreview(level, row) {
  var skill = SkillMasteryFindSkill(skillMasterySelectedId);
  if (!skill) return null;
  var base = skill.base;
  var levels = Math.max(0, parseInt(level, 10) - 7);
  var rangePercentExact = SkillMasteryRowValue(row, "rangePercent");
  var damage = SkillMasteryRowValue(row, "damage");
  var mana = SkillMasteryRowValue(row, "manaUse");
  var cooldown = SkillMasteryRowValue(row, "coolDown");
  var dx = SkillMasteryRowValue(row, "dx");
  var dy = SkillMasteryRowValue(row, "dy");
  var maxFight = SkillMasteryRowValue(row, "maxFight");
  var required = SkillMasteryRowValue(row, "requiredUses");
  var gain = SkillMasteryRowValue(row, "gainPerUse");
  if (damage == null) damage = SkillMasteryIncrease(base.damage, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "damagePercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "damageFlatPerLevel"), 0), levels);
  if (mana == null) mana = SkillMasteryReduction(base.manaUse, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "manaReductionPercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "manaReductionFlatPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "minimumManaPercent"), 50), levels);
  if (cooldown == null) cooldown = SkillMasteryReduction(base.coolDown, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "cooldownReductionPercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "cooldownReductionMsPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "minimumCooldownPercent"), 50), levels);
  if (dx == null) dx = rangePercentExact != null ? Math.floor(base.dx * rangePercentExact / 100) : SkillMasteryIncrease(base.dx, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "rangePercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "rangeFlatPerLevel"), 0), levels);
  if (dy == null) dy = rangePercentExact != null ? Math.floor(base.dy * rangePercentExact / 100) : SkillMasteryIncrease(base.dy, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "rangePercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "rangeFlatPerLevel"), 0), levels);
  if (maxFight == null) maxFight = Math.max(1, SkillMasteryIncrease(base.maxFight, SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "maxFightPercentPerLevel"), 0), SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "maxFightFlatPerLevel"), 0), levels));
  if (required == null) required = SkillMasteryRequiredUses(skill.id, level);
  if (gain == null) gain = SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "gainPerUse"), 1);
  var effectExact = SkillMasteryRowValue(row, "effectPercent");
  var effectText = effectExact != null ? effectExact + "% cấp 7" : "+" + (SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "effectPercentPerLevel"), 0) * levels) + "%" + (SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "effectFlatPerLevel"), 0) ? " + " + (SkillMasteryNumber(SkillMasteryFormulaValue(skill.id, "effectFlatPerLevel"), 0) * levels) : "");
  var damagePercentExact = SkillMasteryRowValue(row, "damagePercent");
  var damageDynamicText = damagePercentExact != null ? damagePercentExact + "% cấp 7" : "theo công thức damage";
  return { requiredUses: required, gainPerUse: gain, damage: damage, manaUse: mana, coolDown: cooldown, dx: dx, dy: dy, maxFight: maxFight, effectText: effectText, damageDynamicText: damageDynamicText };
}

function RenderSkillMasteryLevelTable() {
  var target = document.getElementById("skillMasteryLevelTable");
  if (!target) return;
  var html = "<thead><tr><th>Cấp</th><th>Điểm cần</th><th>Damage</th><th>Mana</th><th>Cooldown</th><th>Phạm vi</th><th>Mục tiêu</th><th>Hiệu ứng</th><th></th></tr></thead><tbody>";
  for (var i = 0; i < skillMasteryLevelRows.length; i++) {
    var row = skillMasteryLevelRows[i];
    var preview = SkillMasteryPreview(parseInt(row.level, 10), row);
    var active = i == skillMasteryEditingIndex ? ' class="skill-level-selected"' : "";
    html += '<tr' + active + ' onclick="PickSkillMasteryLevel(' + i + ')"><td>' + row.level + '</td><td>' + Html(FormatNumber(preview.requiredUses)) + '</td><td>' + Html(FormatNumber(preview.damage)) + '</td><td>' + Html(FormatNumber(preview.manaUse)) + '</td><td>' + Html(FormatNumber(preview.coolDown)) + ' ms</td><td>' + preview.dx + ' × ' + preview.dy + '</td><td>' + preview.maxFight + '</td><td>' + Html(preview.effectText) + '</td><td><button class="danger" onclick="RemoveSkillMasteryLevel(' + i + '); event.cancelBubble=true">Xóa</button></td></tr>';
  }
  if (!skillMasteryLevelRows.length) html += '<tr><td colspan="9">Chưa có dòng cấp chính xác; skill đang tăng hoàn toàn theo công thức.</td></tr>';
  target.innerHTML = html + "</tbody>";
}

function AddSkillMasteryLevel() {
  if (skillMasterySelectedId < 0) return;
  var raw = NormalizeInteger(V("skillMasteryNewLevel"));
  if (!/^\d+$/.test(raw) || parseInt(raw, 10) < 8 || parseInt(raw, 10) > 1000000) {
    Msg("skillMasteryMessage", "Cấp mới phải là số nguyên từ 8 đến 1000000.");
    return;
  }
  var level = parseInt(raw, 10);
  for (var i = 0; i < skillMasteryLevelRows.length; i++) {
    if (parseInt(skillMasteryLevelRows[i].level, 10) == level) { PickSkillMasteryLevel(i); return; }
  }
  var preview = SkillMasteryPreview(level, { level: level });
  var row = {
    level: level, requiredUses: "" + preview.requiredUses, gainPerUse: "" + preview.gainPerUse,
    damage: "" + preview.damage, damagePercent: "", manaUse: "" + preview.manaUse, coolDown: "" + preview.coolDown,
    dx: "" + preview.dx, dy: "" + preview.dy, maxFight: "" + preview.maxFight,
    effectPercent: "", rangePercent: ""
  };
  skillMasteryLevelRows.push(row);
  skillMasteryLevelRows.sort(function (a, b) { return parseInt(a.level, 10) - parseInt(b.level, 10); });
  for (var index = 0; index < skillMasteryLevelRows.length; index++) if (skillMasteryLevelRows[index] === row) { PickSkillMasteryLevel(index); break; }
  Set("skillMasteryNewLevel", level + 1);
  Msg("skillMasteryMessage", "Đã tạo dòng cấp " + level + " từ chỉ số xem trước. Bấm Lưu skill để ghi cấu hình.");
}

function PickSkillMasteryLevel(index) {
  if (index < 0 || index >= skillMasteryLevelRows.length) return;
  skillMasteryEditingIndex = index;
  var row = skillMasteryLevelRows[index];
  Set("skillMasteryLevelIndex", index);
  document.getElementById("skillMasteryEditingLevel").innerText = row.level;
  var fields = skillMasteryData.levelFields || [];
  var html = '<div class="skill-config-grid">';
  for (var i = 0; i < fields.length; i++) {
    var field = fields[i];
    var key = SkillMasteryFieldValue(field, "Key");
    var id = SkillMasteryInputId("level", key);
    var value = row[key] == null ? "" : row[key];
    html += '<div class="skill-config-field" title="' + HtmlAttr(SkillMasteryFieldValue(field, "Description")) + '"><label for="' + id + '">' + Html(SkillMasteryFieldValue(field, "Name")) + '</label><input type="text" id="' + id + '" value="' + HtmlAttr(value) + '" placeholder="Theo công thức" onkeyup="PreviewEditingSkillMasteryLevel()"><div class="skill-field-hint">' + Html(SkillMasteryFieldValue(field, "Description")) + '</div></div>';
  }
  document.getElementById("skillMasteryLevelFields").innerHTML = html + "</div>";
  document.getElementById("skillMasteryLevelEditor").style.display = "block";
  PreviewEditingSkillMasteryLevel();
  RenderSkillMasteryLevelTable();
}

function CaptureEditingSkillMasteryLevel() {
  if (skillMasteryEditingIndex < 0 || skillMasteryEditingIndex >= skillMasteryLevelRows.length) return null;
  var row = skillMasteryLevelRows[skillMasteryEditingIndex];
  var fields = skillMasteryData.levelFields || [];
  for (var i = 0; i < fields.length; i++) {
    var key = SkillMasteryFieldValue(fields[i], "Key");
    row[key] = Trim(V(SkillMasteryInputId("level", key)));
  }
  return row;
}

function PreviewEditingSkillMasteryLevel() {
  var row = CaptureEditingSkillMasteryLevel();
  if (!row) return;
  var preview = SkillMasteryPreview(parseInt(row.level, 10), row);
  document.getElementById("skillMasteryLevelPreview").innerHTML =
    '<b>Xem trước hiệu lực:</b> cần ' + Html(FormatNumber(preview.requiredUses)) + ' điểm, +' + Html(FormatNumber(preview.gainPerUse)) + '/lượt; ' +
    'damage ' + Html(FormatNumber(preview.damage)) + ' (damage động: ' + Html(preview.damageDynamicText) + '), mana ' + Html(FormatNumber(preview.manaUse)) + ', cooldown ' + Html(FormatNumber(preview.coolDown)) + ' ms, phạm vi ' + preview.dx + ' × ' + preview.dy + ', mục tiêu ' + preview.maxFight + ', hiệu ứng ' + Html(preview.effectText) + '.';
}

function UpdateSkillMasteryLevel() {
  var row = CaptureEditingSkillMasteryLevel();
  if (!row) return;
  RenderSkillMasteryLevelTable();
  Msg("skillMasteryMessage", "Đã cập nhật dòng cấp " + row.level + " trong bản nháp. Bấm Lưu skill để ghi file.");
}

function RemoveSkillMasteryLevel(index) {
  if (index < 0 || index >= skillMasteryLevelRows.length) return;
  var level = skillMasteryLevelRows[index].level;
  skillMasteryLevelRows.splice(index, 1);
  skillMasteryEditingIndex = -1;
  document.getElementById("skillMasteryLevelEditor").style.display = "none";
  RenderSkillMasteryLevelTable();
  Msg("skillMasteryMessage", "Đã xóa dòng cấp " + level + " khỏi bản nháp. Bấm Lưu skill để áp dụng.");
}

function RemoveEditingSkillMasteryLevel() {
  RemoveSkillMasteryLevel(skillMasteryEditingIndex);
}

function SkillMasteryRunJsonRequest(action, payload, params) {
  var requestPath = NewAdminJsonRequestPath("admin_skill_mastery");
  try {
    WriteUtf8File(requestPath, JSON.stringify(payload, null, 2));
    params = params || {};
    params.RequestPath = requestPath;
    return RunAdmin(action, params);
  } finally {
    try { if (fso.FileExists(requestPath)) fso.DeleteFile(requestPath, true); } catch (e) {}
  }
}

function SaveSkillMasteryGeneral() {
  if (!skillMasteryData) return;
  var values = {};
  var fields = skillMasteryData.generalFields || [];
  for (var i = 0; i < fields.length; i++) {
    var field = fields[i];
    var key = SkillMasteryFieldValue(field, "Key");
    var id = SkillMasteryInputId("general", key);
    values[key] = SkillMasteryFieldValue(field, "Kind") == "bool" ? (document.getElementById(id).checked ? "true" : "false") : Trim(V(id));
  }
  var result = SkillMasteryRunJsonRequest("saveskillmasterygeneral", { values: values }, {});
  Msg("skillMasteryMessage", StatusText(result));
  if (!IsAdminError(result)) LoadSkillMastery(null, true);
}

function ResetSkillMasteryGeneral() {
  if (!window.confirm("Đưa toàn bộ công thức thành thạo chung về mặc định trong server? Cấu hình riêng từng skill vẫn được giữ.")) return;
  var result = RunAdmin("resetskillmasterygeneral", {});
  Msg("skillMasteryMessage", StatusText(result));
  if (!IsAdminError(result)) LoadSkillMastery(null, true);
}

function SaveSkillMasterySkill() {
  if (skillMasterySelectedId < 0 || !skillMasteryData) return;
  CaptureEditingSkillMasteryLevel();
  var values = {};
  var fields = skillMasteryData.skillFields || [];
  for (var i = 0; i < fields.length; i++) {
    var key = SkillMasteryFieldValue(fields[i], "Key");
    values[key] = Trim(V(SkillMasteryInputId("skill", key)));
  }
  var result = SkillMasteryRunJsonRequest("saveskillmasteryskill", {
    skillId: skillMasterySelectedId, values: values, levels: skillMasteryLevelRows
  }, {});
  Msg("skillMasteryMessage", StatusText(result));
  if (!IsAdminError(result)) LoadSkillMastery(skillMasterySelectedId, false);
}

function ResetSkillMasterySkill() {
  if (skillMasterySelectedId < 0) return;
  var skill = SkillMasteryFindSkill(skillMasterySelectedId);
  if (!window.confirm("Xóa toàn bộ công thức và cấp riêng của " + (skill ? skill.name : ("skill " + skillMasterySelectedId)) + "? Skill sẽ kế thừa cấu hình toàn server.")) return;
  var id = skillMasterySelectedId;
  var result = RunAdmin("resetskillmasteryskill", { Id: id });
  Msg("skillMasteryMessage", StatusText(result));
  if (!IsAdminError(result)) LoadSkillMastery(id, false);
}

RegisterTab({
  id: "skillmastery", view: "skill-mastery.html", panelId: "panelSkillMastery", navId: "navSkillMastery",
  title: "Quản lý Skill", subtitle: "Thành thạo sau cấp 7, công thức tăng trưởng và chỉ số chính xác từng cấp",
  onOpen: function () { LoadSkillMastery(null, true); },
  onRefresh: function () { LoadSkillMastery(skillMasterySelectedId, skillMasterySelectedId < 0); }
});
