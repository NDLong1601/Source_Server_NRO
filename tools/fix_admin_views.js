const fs = require("fs");
const path = require("path");

const viewsDir = path.resolve(__dirname, "..", "admin_data_menu", "views");
const checkOnly = process.argv.includes("--check");
const viewFiles = fs.readdirSync(viewsDir).filter((name) => name.endsWith(".html"));
const changedFiles = [];

function normalizeView(source) {
  let output = source.replace(/<button\b(?![^>]*\btype=)/gi, '<button type="button"');
  output = output.replace(/<input\b[^>]*>/gi, (tag) => {
    let normalized = tag;
    if (!/\btype\s*=/.test(normalized)) {
      normalized = normalized.replace(/^<input\b/i, '<input type="text"');
    }
    return normalized.replace(/\s*\/>$/, ">");
  });
  output = output.replace(
    /\s(readonly|disabled|checked|multiple|required|autofocus|selected)="\1"/gi,
    " $1"
  );
  output = output.replace(
    /<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\b([^>]*)\s*\/>/gi,
    "<$1$2>"
  );
  return output;
}

for (const filename of viewFiles) {
  const fullPath = path.join(viewsDir, filename);
  const source = fs.readFileSync(fullPath, "utf8");
  const normalized = normalizeView(source);
  if (/\sstyle\s*=/i.test(normalized)) {
    throw new Error(`Inline style must be moved to admin_data_menu/css: ${filename}`);
  }
  if (normalized === source) continue;
  changedFiles.push(filename);
  if (!checkOnly) fs.writeFileSync(fullPath, normalized, "utf8");
}

if (checkOnly && changedFiles.length) {
  throw new Error(`Admin views need normalization: ${changedFiles.join(", ")}`);
}

console.log(
  changedFiles.length
    ? `${checkOnly ? "Invalid" : "Normalized"}: ${changedFiles.join(", ")}`
    : `OK: ${viewFiles.length} admin views are normalized.`
);
