var currentTab = "items";

window.onload = function () {
  FitWindowToScreen();
  LoadTabViews();
  InitAdminCheckboxes();
  InitAdminSelects();
  InitCombos();
  LoadStatus();
  ShowTab(currentTab);
  AdjustTableOffsets();
  AdjustTableOffsetsLater();
};

window.onresize = function () {
  CloseCombos("");
  AdjustTableOffsets();
  AdjustTableOffsetsLater();
};

document.onclick = function () {
  CloseCombos("");
  CloseAdminSelects("");
};

function LoadTabViews() {
  var host = document.getElementById("tabViews");
  var html = "";
  for (var i = 0; i < tabOrder.length; i++) {
    var config = tabRegistry[tabOrder[i]];
    var path = rootDir + "\\admin_data_menu\\views\\" + config.view;
    if (!fso.FileExists(path)) throw new Error("Missing admin view: " + path);
    var view = ReadFile(path);
    if (!view) throw new Error("Admin view is empty or unreadable: " + path);
    html += view;
  }
  host.innerHTML = html;
  QueueAdminCheckboxDecoration();
  QueueAdminSelectDecoration();
}


function FitWindowToScreen() {
  try {
    var sw = screen.availWidth || 1320;
    var sh = screen.availHeight || 800;
    var w = Math.min(1320, Math.max(760, sw - 80));
    var h = Math.min(820, Math.max(620, sh - 80));
    window.resizeTo(w, h);
    window.moveTo(Math.max(0, (sw - w) / 2), Math.max(0, (sh - h) / 2));
  } catch (e) {
  }
}


function AdjustTableOffsets() {
  try {
    QueueAdminCheckboxDecoration();
    QueueAdminSelectDecoration();
    var nodes = document.getElementsByTagName("div");
    for (var i = 0; i < nodes.length; i++) {
      var tb = nodes[i];
      if ((" " + tb.className + " ").indexOf(" toolbar ") < 0) continue;
      var next = tb.nextSibling;
      while (next && next.nodeType != 1) next = next.nextSibling;
      if (!next || (" " + next.className + " ").indexOf(" table-wrap ") < 0) continue;
      var h = tb.scrollHeight > tb.offsetHeight ? tb.scrollHeight : tb.offsetHeight;
      if (h < 56) h = 56;
      next.style.top = h + "px";
    }
    for (var t = 0; t < tabOrder.length; t++) {
      var config = tabRegistry[tabOrder[t]];
      if (config.onLayout) config.onLayout();
    }
  } catch (e) {
  }
}


function AdjustTableOffsetsLater() {
  try {
    window.setTimeout(function () { AdjustTableOffsets(); }, 0);
    window.setTimeout(function () { AdjustTableOffsets(); }, 120);
  } catch (e) {
  }
}


function UpdateConfigColumnsOffset(panelId, tabsId) {
  var panel = document.getElementById(panelId);
  var tabs = document.getElementById(tabsId);
  if (!panel || !tabs) return;
  var columns = panel.getElementsByTagName("div");
  for (var i = 0; i < columns.length; i++) {
    if ((" " + columns[i].className + " ").indexOf(" columns ") >= 0) {
      columns[i].style.top = (tabs.offsetHeight + 10) + "px";
      return;
    }
  }
}


function ShowTab(tab) {
  var selected = GetTabConfig(tab) || GetDefaultTabConfig();
  if (!selected) throw new Error("No admin tabs are registered.");
  currentTab = selected.id;
  for (var i = 0; i < tabOrder.length; i++) {
    var config = tabRegistry[tabOrder[i]];
    document.getElementById(config.panelId).className = config.id == currentTab ? "panel active" : "panel";
    document.getElementById(config.navId).className = config.id == currentTab ? "nav-button active" : "nav-button";
  }
  document.getElementById("pageTitle").innerText = selected.title;
  document.getElementById("pageSub").innerText = selected.subtitle;
  if (!selected.isLoaded) {
    if (selected.onOpen) selected.onOpen();
    selected.isLoaded = true;
  }
  AdjustTableOffsets();
  AdjustTableOffsetsLater();
}


function RefreshCurrent() {
  LoadStatus();
  var selected = GetTabConfig(currentTab) || GetDefaultTabConfig();
  if (selected && selected.onRefresh) {
    selected.onRefresh();
    selected.isLoaded = true;
  }
  AdjustTableOffsets();
  AdjustTableOffsetsLater();
}
