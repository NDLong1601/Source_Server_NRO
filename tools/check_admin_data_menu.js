const fs = require("fs");
const path = require("path");
const vm = require("vm");
const { execFileSync } = require("child_process");

const rootDir = path.resolve(__dirname, "..");
const htaPath = path.join(rootDir, "admin_data_menu.hta");
const hta = fs.readFileSync(htaPath, "utf8");
const scriptSources = [];
const functionOwners = new Map();
const variableOwners = new Map();
const viewSources = [];

execFileSync(process.execPath, [path.join(__dirname, "fix_admin_views.js"), "--check"], {
  stdio: "inherit"
});

function resolveAsset(relativePath) {
  return path.resolve(rootDir, relativePath.replace(/\//g, path.sep));
}

function checkJavaScript(source, filename) {
  new vm.Script(source, { filename });
  scriptSources.push({ source, filename });
}

for (const match of hta.matchAll(/<link\b[^>]*\bhref="([^"]+)"[^>]*>/gi)) {
  const assetPath = resolveAsset(match[1]);
  if (!fs.existsSync(assetPath)) {
    throw new Error(`Missing stylesheet: ${match[1]}`);
  }
}

for (const match of hta.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
  const attributes = match[1];
  const srcMatch = /\bsrc="([^"]+)"/i.exec(attributes);
  if (srcMatch) {
    const assetPath = resolveAsset(srcMatch[1]);
    if (!fs.existsSync(assetPath)) {
      throw new Error(`Missing script: ${srcMatch[1]}`);
    }
    checkJavaScript(fs.readFileSync(assetPath, "utf8"), assetPath);
  } else if (match[2].trim()) {
    checkJavaScript(match[2], `${htaPath}:inline`);
  }
}

for (const entry of scriptSources) {
  for (const match of entry.source.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) {
    const name = match[1];
    if (functionOwners.has(name)) {
      throw new Error(`Duplicate function ${name}: ${functionOwners.get(name)} and ${entry.filename}`);
    }
    functionOwners.set(name, entry.filename);
  }
  for (const match of entry.source.matchAll(/^var\s+([A-Za-z_$][\w$]*)\s*=/gm)) {
    const name = match[1];
    if (variableOwners.has(name)) {
      throw new Error(`Duplicate global ${name}: ${variableOwners.get(name)} and ${entry.filename}`);
    }
    variableOwners.set(name, entry.filename);
  }
}

const tabRegistrations = new Map();
for (const entry of scriptSources) {
  for (const match of entry.source.matchAll(/RegisterTab\s*\(\s*\{([\s\S]*?)\}\s*\);/g)) {
    const body = match[1];
    const value = (name) => {
      const property = new RegExp(`\\b${name}\\s*:\\s*"([^"]+)"`).exec(body);
      return property ? property[1] : "";
    };
    const registration = {
      id: value("id"),
      view: value("view"),
      panelId: value("panelId"),
      navId: value("navId"),
      title: value("title"),
      subtitle: value("subtitle"),
      filename: entry.filename
    };
    for (const property of ["id", "view", "panelId", "navId", "title", "subtitle"]) {
      if (!registration[property]) throw new Error(`Tab registration missing ${property}: ${entry.filename}`);
    }
    if (!/\bonOpen\s*:\s*function\b/.test(body) || !/\bonRefresh\s*:\s*function\b/.test(body)) {
      throw new Error(`Tab registration missing lifecycle callback: ${registration.id}`);
    }
    if (tabRegistrations.has(registration.id)) throw new Error(`Duplicate tab id: ${registration.id}`);
    tabRegistrations.set(registration.id, registration);
  }
}
if (!tabRegistrations.size) throw new Error("No tabs are registered");

for (const registration of tabRegistrations.values()) {
  const viewPath = path.join(rootDir, "admin_data_menu", "views", registration.view);
  if (!fs.existsSync(viewPath)) {
    throw new Error(`Missing tab view: ${registration.view}`);
  }
  const source = fs.readFileSync(viewPath, "utf8");
  const invalidEntity = /&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-f]+);)/i.exec(source);
  if (invalidEntity) throw new Error(`Unescaped ampersand in ${registration.view}`);

  const voidElements = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]);
  const stack = [];
  for (const match of source.matchAll(/<\/?([a-z][a-z0-9:-]*)\b[^>]*>/gi)) {
    const token = match[0];
    const tag = match[1].toLowerCase();
    if (voidElements.has(tag) || /\/>$/.test(token)) continue;
    if (!/^<\//.test(token)) {
      stack.push(tag);
      continue;
    }
    const openTag = stack.pop();
    if (openTag !== tag) {
      throw new Error(`Mismatched tag in ${registration.view}: expected </${openTag}> but found </${tag}>`);
    }
  }
  if (stack.length) throw new Error(`Unclosed tag in ${registration.view}: <${stack[stack.length - 1]}>`);
  const rootPanel = /^\s*<div\s+id="([^"]+)"\s+class="panel(?: active)?">/i.exec(source);
  if (!rootPanel || rootPanel[1] !== registration.panelId) {
    throw new Error(`Tab panel does not match registry: ${registration.id}`);
  }
  viewSources.push({ source, filename: viewPath, registration });
}

const allMarkup = hta + viewSources.map((entry) => entry.source).join("\n");
const idOwners = new Map();
for (const match of allMarkup.matchAll(/\bid="([^"]+)"/gi)) {
  if (idOwners.has(match[1])) throw new Error(`Duplicate DOM id: ${match[1]}`);
  idOwners.set(match[1], true);
}
for (const match of allMarkup.matchAll(/\bonclick="([^"]+)"/gi)) {
  for (const call of match[1].matchAll(/(?:^|[;\s])([A-Za-z_$][\w$]*)\s*\(/g)) {
    if (!functionOwners.has(call[1])) throw new Error(`Unknown onclick function: ${call[1]}`);
  }
}
for (const registration of tabRegistrations.values()) {
  if (!idOwners.has(registration.navId)) throw new Error(`Missing nav element: ${registration.navId}`);
  if (!idOwners.has(registration.panelId)) throw new Error(`Missing panel element: ${registration.panelId}`);
  const navPattern = new RegExp(`id="${registration.navId}"[^>]*onclick="ShowTab\\('${registration.id}'\\)"`, "i");
  if (!navPattern.test(hta)) throw new Error(`Navigation does not match registry: ${registration.id}`);
}

console.log(
  `OK: ${scriptSources.length} scripts, ${tabRegistrations.size} registered tabs/views, ` +
  `${functionOwners.size} functions, ${variableOwners.size} globals and ${idOwners.size} DOM ids.`
);
