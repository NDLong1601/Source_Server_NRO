/*
 * AdminCheckbox is the shared checkbox component for the HTA admin menu.
 * Any normal <input type="checkbox"> is decorated automatically. Call
 * AdminCheckbox.refresh() after rendering HTML dynamically when needed.
 * Add data-admin-checkbox-skin="native" to keep a browser/native checkbox,
 * or wrap a bespoke checkbox in .aura-check-control/.aura-toggle to opt out.
 */
var adminCheckboxObserver = null;
var adminCheckboxDecorationTimer = 0;

function AdminCheckboxHasClass(node, className) {
  return !!node && (" " + (node.className || "") + " ").indexOf(" " + className + " ") >= 0;
}

function AdminCheckboxUsesOwnSkin(input) {
  var parent = input ? input.parentNode : null;
  return input.getAttribute("data-admin-checkbox-skin") == "native"
    || AdminCheckboxHasClass(parent, "aura-check-control")
    || AdminCheckboxHasClass(parent, "aura-toggle");
}

function DecorateAdminCheckboxes(root) {
  root = root || document;
  var inputs = root.getElementsByTagName("input");
  var checkboxes = [];
  for (var i = 0; i < inputs.length; i++) {
    if (("" + inputs[i].type).toLowerCase() == "checkbox") checkboxes.push(inputs[i]);
  }
  for (var c = 0; c < checkboxes.length; c++) {
    var input = checkboxes[c];
    if (input.getAttribute("data-admin-checkbox") || AdminCheckboxUsesOwnSkin(input)) continue;
    var parent = input.parentNode;
    if (!parent) continue;
    var control = document.createElement("span");
    control.className = "admin-checkbox-control";
    control.title = input.title || "Bật / tắt";
    parent.insertBefore(control, input);
    control.appendChild(input);
    var mark = document.createElement("span");
    mark.className = "admin-checkbox-mark";
    control.appendChild(mark);
    input.setAttribute("data-admin-checkbox", "1");
  }
}

function QueueAdminCheckboxDecoration() {
  if (adminCheckboxDecorationTimer) return;
  adminCheckboxDecorationTimer = window.setTimeout(function () {
    adminCheckboxDecorationTimer = 0;
    DecorateAdminCheckboxes();
  }, 0);
}

function InitAdminCheckboxes() {
  DecorateAdminCheckboxes();
  try {
    if (!window.MutationObserver || adminCheckboxObserver) return;
    adminCheckboxObserver = new window.MutationObserver(function () {
      QueueAdminCheckboxDecoration();
    });
    adminCheckboxObserver.observe(document.getElementById("tabViews") || document.body, { childList: true, subtree: true });
  } catch (e) {
  }
}

var AdminCheckbox = {
  refresh: DecorateAdminCheckboxes,
  queueRefresh: QueueAdminCheckboxDecoration,
  initialize: InitAdminCheckboxes
};
