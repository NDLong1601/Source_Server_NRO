function MoneyLabel(value) {
  var labels = {
    "0": "Vàng",
    "1": "Ngọc",
    "2": "Ngọc khóa (chưa hỗ trợ)",
    "3": "Ruby",
    "4": "Coupon"
  };
  return labels[value] ? labels[value] : value;
}


function ToggleLabel(value) {
  return value == "1" ? "Bật" : "Tắt";
}


function IsAdminError(text) {
  return StatusText(text).indexOf("ERROR") == 0;
}


function ArrayIndexOf(values, target) {
  for (var i = 0; i < values.length; i++) if (values[i] == target) return i;
  return -1;
}


function ArrayContains(values, target) {
  return ArrayIndexOf(values, target) >= 0;
}


function IsTrueValue(value) {
  return value == "true" || value == "1";
}


function NormalizeInteger(value) {
  var text = Trim(value).replace(/\s/g, "");
  if (/^-?[\d.]+$/.test(text)) text = text.replace(/\./g, "");
  return text;
}


function FormatNumber(value) {
  var text = NormalizeInteger(value);
  if (!/^-?\d+$/.test(text)) return "" + value;
  var sign = text.charAt(0) == "-" ? "-" : "";
  var digits = sign ? text.substring(1) : text;
  return sign + digits.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}


function FormatNumberList(value) {
  var parts = ("" + value).split(",");
  for (var i = 0; i < parts.length; i++) parts[i] = FormatNumber(Trim(parts[i]));
  return parts.join(", ");
}


function NormalizeNumberList(value) {
  var parts = ("" + value).split(",");
  for (var i = 0; i < parts.length; i++) parts[i] = NormalizeInteger(parts[i]);
  return parts.join(",");
}


function FormatDurationMs(value) {
  var raw = NormalizeInteger(value);
  if (!/^\d+$/.test(raw)) return "";
  var totalSeconds = Math.floor(parseInt(raw, 10) / 1000);
  if (totalSeconds < 1) return raw + " ms";
  var days = Math.floor(totalSeconds / 86400); totalSeconds %= 86400;
  var hours = Math.floor(totalSeconds / 3600); totalSeconds %= 3600;
  var minutes = Math.floor(totalSeconds / 60); var seconds = totalSeconds % 60;
  var parts = [];
  if (days) parts.push(days + " ngày");
  if (hours) parts.push(hours + " giờ");
  if (minutes) parts.push(minutes + " phút");
  if (seconds && parts.length < 2) parts.push(seconds + " giây");
  return parts.join(" ");
}


function FormatDateTime(value) {
  var text = Trim(value);
  var match = /^(\d{4})-(\d{2})-(\d{2})(?:[T\s]+(\d{2}):(\d{2})(?::(\d{2}))?)?/.exec(text);
  if (!match) return text;
  return match[3] + "/" + match[2] + "/" + match[1] + (match[4] ? " " + match[4] + ":" + match[5] + ":" + (match[6] || "00") : "");
}


function StatusText(text) {
  var rows = ParseTsv(text);
  if (rows.length > 0 && (rows[0][0] == "OK" || rows[0][0] == "ERROR")) return rows[0].join(" ");
  return text;
}
