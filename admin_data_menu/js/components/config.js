function FindConfigRow(rows, key) {
  for (var i = 1; i < rows.length; i++) if (rows[i][0] == key) return rows[i];
  return null;
}

function IsIntegerConfigKind(kind) {
  return ArrayContains(["int", "positive-int", "long", "positive-long", "milliseconds", "stamina",
    "limit-level", "limit-level-positive", "skill-level", "slot", "percent", "wide-percent", "critical", "item-id"], kind);
}

function IsIntegerListConfigKind(kind) {
  return ArrayContains(["long-list", "long-list-4", "int-list", "int-list-5", "critical-list", "map-id-list"], kind);
}

function ConfigKindLabel(kind) {
  var labels = {
    "bool": "Bật / tắt", "int": "Số nguyên", "positive-int": "Số nguyên dương", "long": "Số nguyên lớn",
    "milliseconds": "Thời lượng", "percent": "Tỉ lệ phần trăm", "wide-percent": "Tỉ lệ phần trăm", "map-id-list": "Danh sách Map ID",
    "long-list-4": "Danh sách 4 mốc", "int-list-5": "Danh sách 5 giá trị", "type-list": "Danh sách loại đệ tử", "weights-3": "Ba trọng số",
    "skill-pool": "Checklist kỹ năng", "skill-level": "Cấp kỹ năng", "item-id": "ID vật phẩm", "option-id": "ID option"
  };
  return labels[kind] || kind;
}

function ConfigScopeLabel(scope) {
  var labels = { "runtime": "Đang chạy", "database": "Cơ sở dữ liệu" };
  return labels[scope] || scope;
}

function FormatPetTypes(value) {
  var names = ["Đệ tử thường", "Mabư", "Uub", "Kid Beer", "Jiren"];
  var parts = ("" + value).split(",");
  for (var i = 0; i < parts.length; i++) {
    var id = parseInt(Trim(parts[i]), 10);
    parts[i] = id >= 0 && id < names.length ? names[id] : Trim(parts[i]);
  }
  return parts.join(", ");
}

function FormatAdminValue(value, kind, key) {
  if (kind == "skill-pool") return FormatPetSkillPool(value);
  if (kind == "bool") return IsTrueValue(("" + value).toLowerCase()) ? "Bật" : "Tắt";
  if (kind == "type-list") return FormatPetTypes(value);
  if (kind == "milliseconds") {
    var duration = FormatDurationMs(value);
    return duration == NormalizeInteger(value) + " ms" ? FormatNumber(value) + " ms" : FormatNumber(value) + " ms · " + duration;
  }
  if (kind == "percent" || kind == "wide-percent") return FormatNumber(value) + "%";
  if (kind == "weights-3") return ("" + value).split(",").join(" : ");
  if (IsIntegerConfigKind(kind)) return FormatNumber(value);
  if (IsIntegerListConfigKind(kind)) return FormatNumberList(value);
  return "" + value;
}

function FormatConfigEditValue(value, kind) {
  if (IsIntegerConfigKind(kind)) return FormatNumber(value);
  if (IsIntegerListConfigKind(kind)) return FormatNumberList(value);
  return "" + value;
}

function NormalizeConfigEditValue(value, kind) {
  if (IsIntegerConfigKind(kind)) return NormalizeInteger(value);
  if (IsIntegerListConfigKind(kind)) return NormalizeNumberList(value);
  return Trim(value);
}
