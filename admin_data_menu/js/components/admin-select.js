/*
 * AdminSelect is a shared, fully custom select component. It keeps the native
 * <select> as the data source, while rendering the same cyan menu treatment as
 * the existing .combo component. Inline onchange handlers continue to work.
 */
var adminSelectObserver = null;
var adminSelectDecorationTimer = 0;
var adminSelectSequence = 0;

function AdminSelectHasClass(node, className) {
  return !!node && (" " + (node.className || "") + " ").indexOf(" " + className + " ") >= 0;
}

function AdminSelectShouldSkip(select) {
  return select.getAttribute("data-admin-select-skin") == "native"
    || AdminSelectHasClass(select, "admin-hidden")
    || select.multiple
    || parseInt(select.size, 10) > 1;
}

function AdminSelectKey(select) {
  var key = select.getAttribute("data-admin-select-key");
  if (key) return key;
  key = select.id || "adminSelect_" + (++adminSelectSequence);
  select.setAttribute("data-admin-select-key", key);
  return key;
}

function FindAdminSelect(key) {
  var selects = document.getElementsByTagName("select");
  for (var i = 0; i < selects.length; i++) {
    if (selects[i].getAttribute("data-admin-select-key") == key) return selects[i];
  }
  return null;
}

function AdminSelectLabel(select) {
  var index = select.selectedIndex;
  if (index < 0 || !select.options[index]) return "Chọn option";
  var option = select.options[index];
  return option.text != null ? option.text : option.innerText;
}

function AdminSelectBaseClass(select) {
  var original = select.getAttribute("data-admin-select-class") || "";
  var base = "admin-select-control" + (original ? " " + original : "");
  if (select.getAttribute("data-admin-select-fill") == "1") base += " admin-select-fill";
  if (select.disabled) base += " disabled";
  return base;
}

function AdminSelectControl(select) {
  var parent = select ? select.parentNode : null;
  return parent && AdminSelectHasClass(parent, "admin-select-control") ? parent : null;
}

function AdminSelectMenu(select) {
  var control = AdminSelectControl(select);
  if (!control) return null;
  var nodes = control.getElementsByTagName("div");
  for (var i = 0; i < nodes.length; i++) if (AdminSelectHasClass(nodes[i], "admin-select-menu")) return nodes[i];
  return null;
}

function AdminSelectStopBubble() {
  if (window.event) window.event.cancelBubble = true;
}

function RenderAdminSelectMenu(select) {
  var menu = AdminSelectMenu(select);
  if (!menu) return;
  while (menu.firstChild) menu.removeChild(menu.firstChild);
  var current = select.value;
  for (var i = 0; i < select.options.length; i++) {
    var option = select.options[i];
    var item = document.createElement("div");
    item.className = "admin-select-option" + (option.value == current ? " active" : "") + (option.disabled ? " disabled" : "");
    item.innerText = option.text != null ? option.text : option.innerText;
    item.title = item.innerText;
    item.onclick = (function (index) {
      return function () {
        ChooseAdminSelect(AdminSelectKey(select), index);
        AdminSelectStopBubble();
      };
    })(i);
    menu.appendChild(item);
  }
}

function SyncAdminSelect(select) {
  if (!select || !select.getAttribute("data-admin-select")) return;
  var control = AdminSelectControl(select);
  if (!control) return;
  var isOpen = AdminSelectHasClass(control, "open");
  control.className = AdminSelectBaseClass(select) + (isOpen && !select.disabled ? " open" : "");
  control.tabIndex = select.disabled ? -1 : 0;
  var nodes = control.getElementsByTagName("span");
  for (var i = 0; i < nodes.length; i++) {
    if (AdminSelectHasClass(nodes[i], "admin-select-text")) {
      nodes[i].innerText = AdminSelectLabel(select);
      break;
    }
  }
  RenderAdminSelectMenu(select);
}

function CloseAdminSelects(exceptKey) {
  var selects = document.getElementsByTagName("select");
  for (var i = 0; i < selects.length; i++) {
    var select = selects[i];
    if (!select.getAttribute("data-admin-select")) continue;
    if (exceptKey && AdminSelectKey(select) == exceptKey) continue;
    var control = AdminSelectControl(select);
    if (control) control.className = AdminSelectBaseClass(select);
  }
}

function ToggleAdminSelect(key) {
  var select = FindAdminSelect(key);
  if (!select || select.disabled) return;
  var control = AdminSelectControl(select);
  if (!control) return;
  var isOpen = AdminSelectHasClass(control, "open");
  CloseCombos("");
  CloseAdminSelects(key);
  SyncAdminSelect(select);
  if (!isOpen) control.className = AdminSelectBaseClass(select) + " open";
  AdminSelectStopBubble();
}

function NotifyAdminSelectChange(select) {
  if (select.fireEvent) {
    select.fireEvent("onchange");
    return;
  }
  if (document.createEvent) {
    var event = document.createEvent("HTMLEvents");
    event.initEvent("change", true, false);
    select.dispatchEvent(event);
    return;
  }
  if (select.onchange) select.onchange();
}

function ChooseAdminSelect(key, index) {
  var select = FindAdminSelect(key);
  if (!select || !select.options[index] || select.options[index].disabled) return;
  select.selectedIndex = index;
  NotifyAdminSelectChange(select);
  SyncAdminSelect(select);
  CloseAdminSelects("");
}

function AdminSelectBind(select, control) {
  control.onclick = function () { ToggleAdminSelect(AdminSelectKey(select)); };
  control.onkeydown = function () {
    var code = window.event ? window.event.keyCode : 0;
    if (code == 13 || code == 32) {
      ToggleAdminSelect(AdminSelectKey(select));
      if (window.event) window.event.returnValue = false;
    } else if (code == 27) {
      CloseAdminSelects("");
    }
  };
  if (select.attachEvent) {
    select.attachEvent("onpropertychange", function () {
      if (window.event && (window.event.propertyName == "value" || window.event.propertyName == "selectedIndex" || window.event.propertyName == "disabled")) SyncAdminSelect(select);
    });
  }
}

function DecorateAdminSelects(root) {
  root = root || document;
  var selects = root.getElementsByTagName("select");
  var pending = [];
  for (var i = 0; i < selects.length; i++) pending.push(selects[i]);
  for (var s = 0; s < pending.length; s++) {
    var select = pending[s];
    if (select.getAttribute("data-admin-select") || AdminSelectShouldSkip(select)) continue;
    var parent = select.parentNode;
    if (!parent) continue;
    var control = document.createElement("div");
    var fill = AdminSelectHasClass(parent, "cell") || ("" + parent.tagName).toLowerCase() == "label";
    select.setAttribute("data-admin-select-class", select.className || "");
    if (fill) select.setAttribute("data-admin-select-fill", "1");
    control.className = AdminSelectBaseClass(select);
    control.setAttribute("data-admin-select-id", select.id || "");
    control.title = select.title || "Chọn option";
    parent.insertBefore(control, select);

    var text = document.createElement("span");
    text.className = "admin-select-text";
    control.appendChild(text);
    var button = document.createElement("span");
    button.className = "admin-select-button";
    var caret = document.createElement("span");
    caret.className = "admin-select-caret";
    button.appendChild(caret);
    control.appendChild(button);
    var menu = document.createElement("div");
    menu.className = "admin-select-menu";
    control.appendChild(menu);
    control.appendChild(select);
    select.setAttribute("data-admin-select", "1");
    AdminSelectKey(select);
    AdminSelectBind(select, control);
    SyncAdminSelect(select);
  }
}

function QueueAdminSelectDecoration() {
  if (adminSelectDecorationTimer) return;
  adminSelectDecorationTimer = window.setTimeout(function () {
    adminSelectDecorationTimer = 0;
    DecorateAdminSelects();
  }, 0);
}

function InitAdminSelects() {
  DecorateAdminSelects();
  try {
    if (!window.MutationObserver || adminSelectObserver) return;
    adminSelectObserver = new window.MutationObserver(function (records) {
      var needsDecoration = false;
      for (var i = 0; i < records.length; i++) {
        var target = records[i].target;
        if (("" + target.tagName).toLowerCase() == "select" && target.getAttribute("data-admin-select")) SyncAdminSelect(target);
        if (records[i].addedNodes && records[i].addedNodes.length) needsDecoration = true;
      }
      if (needsDecoration) QueueAdminSelectDecoration();
    });
    adminSelectObserver.observe(document.getElementById("tabViews") || document.body, { childList: true, subtree: true });
  } catch (e) {
  }
}

var AdminSelect = {
  refresh: DecorateAdminSelects,
  sync: SyncAdminSelect,
  closeAll: CloseAdminSelects,
  initialize: InitAdminSelects
};
