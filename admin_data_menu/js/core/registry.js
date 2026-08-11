var tabRegistry = {};
var tabOrder = [];

function RegisterTab(config) {
  if (!config || !config.id) throw new Error("Tab registration requires an id.");
  if (!config.view || !config.panelId || !config.navId) {
    throw new Error("Tab registration is incomplete: " + config.id);
  }
  if (tabRegistry[config.id]) throw new Error("Duplicate tab registration: " + config.id);
  tabRegistry[config.id] = config;
  tabOrder.push(config.id);
}

function GetTabConfig(tabId) {
  return tabRegistry[tabId] || null;
}

function GetDefaultTabConfig() {
  return GetTabConfig("items") || (tabOrder.length ? tabRegistry[tabOrder[0]] : null);
}
