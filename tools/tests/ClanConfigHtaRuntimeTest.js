// Loaded only by the isolated MSHTML regression host, never by the admin menu.
var clanHtaCalls = 0;
var clanHtaFailure = false;
var clanHtaChecks = [];
var clanHtaConfirm = true;
RunAdmin = function (action, params) {
  clanHtaCalls++;
  if (clanHtaFailure) { clanHtaFailure = false; return "ERROR\tSimulated bridge failure"; }
  var cmd = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + clanHtaTest.runner + '" -TestRoot "' + clanHtaTest.fixture + '" -Action "' + action + '"';
  if (params.ConfigKey) cmd += ' -ConfigKey "' + encodeURIComponent(params.ConfigKey) + '"';
  if (params.ConfigValue) cmd += ' -ConfigValue "' + encodeURIComponent(params.ConfigValue) + '"';
  shell.Run(cmd, 0, true);
  return ReadFile(clanHtaTest.fixture + "\\bridge.txt");
};
window.confirm = function () { return clanHtaConfirm; };
window.onerror = function (message, url, line) {
  WriteUtf8File(clanHtaTest.report, "FAIL script: " + message + " line " + line);
  window.close();
  return true;
};
function ClanHtaAssert(label, condition) {
  if (!condition) throw new Error(label);
  clanHtaChecks.push(label);
}
function ClanHtaEdit(id, value) {
  Set(id, value);
  var keyEvent = document.createEvent("Event");
  keyEvent.initEvent("keyup", true, true);
  document.getElementById(id).dispatchEvent(keyEvent);
}
function ClanHtaClick(id) { document.getElementById(id).click(); }
function ClanHtaLayout() {
  LayoutClanConfig();
  var editor = document.getElementById("clanConfigDetail").parentNode.parentNode;
  var list = document.getElementById("clanConfigTable").parentNode;
  var groups = document.getElementById("clanConfigTabs");
  var e = editor.getBoundingClientRect();
  var l = list.getBoundingClientRect();
  var g = groups.getBoundingClientRect();
  ClanHtaAssert("panes do not overlap at width " + document.body.clientWidth, g.bottom <= l.top && l.right < e.left && e.right - e.left >= 270);
  var save = document.getElementById("clanConfigSave").getBoundingClientRect();
  var reset = document.getElementById("clanConfigReset").getBoundingClientRect();
  ClanHtaAssert("actions remain inside editor", save.left >= e.left && reset.right <= e.right && reset.bottom <= e.bottom);
}
window.onload = function () {
  try {
    window.resizeTo(1320, 820);
    ClanHtaAssert("MSHTML standards mode", document.documentMode >= 9);
    GetTabConfig("claneconomy").onOpen();
    ClanConfigText("pageTitle", GetTabConfig("claneconomy").title);
    ClanConfigText("pageSub", GetTabConfig("claneconomy").subtitle);
    document.getElementById("navItems").className = "nav-button";
    document.getElementById("navClanEconomy").className = "nav-button active";
    ClanHtaAssert("catalog loaded through PowerShell", clanConfigRows.length == 182);
    ClanHtaAssert("economic report tab removed", !document.getElementById("clanEconomyReportTab"));
    ClanHtaAssert("save disabled before editing", document.getElementById("clanConfigSave").disabled);
    document.getElementById("clanConfigGroup_6").focus();
    ClanHtaClick("clanConfigGroup_6");
    ClanHtaAssert("keyboard focus retained on group switch", document.activeElement.id == "clanConfigGroup_6");
    document.getElementById("clanConfigGroup_19").focus();
    ClanHtaClick("clanConfigGroup_19");
    ClanHtaAssert("switched to chat color group", selectedClanConfigCategory == "Màu chat bang" && filteredClanConfigRows.length == 6);
    document.getElementById("clanConfigRow_1").focus();
    ClanHtaClick("clanConfigRow_1");
    ClanHtaAssert("keyboard focus retained on selection", document.activeElement.id == "clanConfigRow_1");
    ClanHtaAssert("color picker visible for chat color", document.getElementById("clanConfigColorPicker").style.display == "block");
    PickClanPresetColor("0x00E5FF");
    ClanHtaAssert("preset color updates value and badge", V("clanConfigValue") == "0x00E5FF" && document.getElementById("clanColorHexBadge").innerText == "0x00E5FF");
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("chat color saved to file", FindConfigRow(clanConfigRows, selectedClanConfigKey)[3] == "0x00E5FF");
    PickClanConfigByKey("features.tree.enabled");
    ClanHtaClick("clanConfigBoolOff");
    ClanHtaAssert("toggle creates draft", clanConfigDrafts["features.tree.enabled"] == "false");
    ClanHtaClick("clanConfigDiscard");
    ClanHtaAssert("discard restores saved value", ClanConfigDraftCount() == 0);
    PickClanConfigByKey("buff.attack_percent");
    ClanHtaEdit("clanConfigValue", "101");
    var before = clanHtaCalls;
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("invalid input never reaches backend", clanHtaCalls == before && document.getElementById("clanConfigError").innerText.length > 0);
    PickClanConfigByKey("shop.item.2252");
    ClanHtaAssert("shop exposes seven labeled inputs", clanConfigStructuredCount == 7);
    ClanHtaAssert("draft survives selection", clanConfigDrafts["buff.attack_percent"] == "101");
    ClanHtaEdit("clanConfigPart_4", "17");
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("shop save reloads actual file", FindConfigRow(clanConfigRows, "shop.item.2252")[3].split(",")[4] == "17");
    ClanHtaAssert("save message preserved", document.getElementById("clanConfigMessage").innerText.indexOf("shop") >= 0);
    PickClanConfigByKey("tree.upgrade_days_to_levels_2_20");
    ClanHtaAssert("nineteen level controls", clanConfigStructuredCount == 19 && document.getElementById("clanConfigPart_18"));
    ClanHtaEdit("clanConfigPart_18", "14");
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("schedule round-trip", FindConfigRow(clanConfigRows, selectedClanConfigKey)[3].split(",")[18] == "14");
    PickClanConfigByKey("appearance.tier_0_accent_rgb");
    ClanHtaAssert("day and night color samples visible", document.getElementById("clanConfigColorPreview").style.display == "block");
    ClanHtaEdit("clanConfigValue", "0xFFF59D");
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("color save round-trip", FindConfigRow(clanConfigRows, selectedClanConfigKey)[3] == "0xFFF59D");
    PickClanConfigByKey("appearance.resource_prefix");
    ClanHtaEdit("clanConfigValue", "missing_");
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("missing assets rejected by backend with draft retained", clanConfigDrafts[selectedClanConfigKey] == "missing_" && document.getElementById("clanConfigError").innerText.indexOf("ERROR") >= 0);
    clanHtaConfirm = false;
    before = clanHtaCalls;
    ClanHtaClick("clanConfigReset");
    LoadClanConfig();
    ClanHtaAssert("reset and reload cancellation", clanHtaCalls == before && ClanConfigDraftCount() == 2);
    PickClanConfigByKey("buff.attack_percent");
    clanHtaConfirm = true;
    ClanHtaClick("clanConfigReset");
    ClanHtaAssert("reset uses server default", FindConfigRow(clanConfigRows, selectedClanConfigKey)[3] == "10");
    clanHtaFailure = true;
    LoadClanConfig(true);
    ClanHtaAssert("load failure preserves other drafts", clanConfigDrafts["appearance.resource_prefix"] == "missing_");
    PickClanConfigByKey("appearance.tier_0_name");
    ClanHtaEdit("clanConfigValue", 'Mầm & <img src=x>');
    ClanHtaClick("clanConfigSave");
    ClanHtaAssert("Unicode and markup stored as text", document.getElementById("clanConfigSaved").innerText == 'Mầm & <img src=x>' && document.getElementById("clanConfigSaved").getElementsByTagName("img").length == 0);
    selectedClanConfigCategory = "Cửa hàng bang - Vật phẩm buff";
    Set("clanConfigSearch", "");
    PickClanConfigByKey("shop.item.2252");
    ClanHtaLayout();
    document.getElementById("toastContainer").innerHTML = "";
    clanConfigDrafts = {};
    UpdateClanConfigEditState();
    ClanConfigText("clanConfigMessage", "Bản xem bố cục từ bộ thử HTA; dữ liệu là bản sao riêng, không phải server đang chạy.");
    var preview = document.documentElement.outerHTML.replace(/<script\b[\s\S]*?<\/script>/gi, "");
    WriteUtf8File(clanHtaTest.fixture + "\\preview.html", "<!doctype html>" + preview);
    var previousWidth = document.body.clientWidth;
    window.resizeTo(760, 620);
    window.setTimeout(function () {
      try {
        if (document.body.clientWidth != previousWidth) ClanHtaLayout();
        else clanHtaChecks.push("NOTE: hidden HTA ignored resize; narrow viewport needs separate visual/manual verification");
        WriteUtf8File(clanHtaTest.report, "PASS\n" + clanHtaChecks.join("\n"));
      } catch (e) { WriteUtf8File(clanHtaTest.report, "FAIL: " + e.message + "\n" + clanHtaChecks.join("\n")); }
      window.close();
    }, 150);
  } catch (e) {
    WriteUtf8File(clanHtaTest.report, "FAIL: " + e.message + "\n" + clanHtaChecks.join("\n"));
    window.close();
  }
};
