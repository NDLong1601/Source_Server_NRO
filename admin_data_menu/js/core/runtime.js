var shell = new ActiveXObject("WScript.Shell");
var fso = new ActiveXObject("Scripting.FileSystemObject");
var rootDir = GetRootDir();
var adminRequestId = 0;

function GetRootDir() {
  var path = unescape(document.location.pathname).replace(/\//g, "\\");
  if (path.charAt(0) == "\\" && path.charAt(2) == ":") path = path.substring(1);
  return fso.GetParentFolderName(path);
}


function RunAdmin(action, params) {
  adminRequestId++;
  var requestPath = rootDir + "\\logs\\admin_data_result_" + (new Date().getTime()) + "_" + adminRequestId + ".txt";
  var cmd = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + rootDir + '\\tools\\admin_data.ps1" -Action "' + SafeArg(action) + '" -Output "' + requestPath + '" -Encoded "1"';
  for (var key in params) {
    cmd += " -" + key + ' "' + SafeArg(EncodeArg(params[key])) + '"';
  }
  shell.Run(cmd, 0, true);
  var text = ReadFile(requestPath);
  try {
    if (fso.FileExists(requestPath)) fso.DeleteFile(requestPath, true);
  } catch (e) {
  }
  return text;
}


function RunBat(action) {
  shell.Run('cmd /c ""' + rootDir + '\\run.bat" ' + action + '"', 0, false);
}


function RestartServer() {
  if (window.confirm("Khởi động lại server để áp dụng dữ liệu mới?")) {
    RunAdmin("recordaudit", { Name: "restartserver", Description: "Yêu cầu Restart server từ NRO Admin Data" });
    RunBat("restart");
    setTimeout(function () {
      if (typeof UpdateItemVersionBadge === "function") UpdateItemVersionBadge();
      if (typeof UpdateDefaultOptionVersionBadge === "function") UpdateDefaultOptionVersionBadge();
    }, 4000);
  }
}


function SafeArg(value) {
  if (value == null) return "";
  return ("" + value).replace(/"/g, "'");
}


function EncodeArg(value) {
  if (value == null) return "";
  return encodeURIComponent("" + value);
}


function ReadFile(path) {
  try {
    if (!fso.FileExists(path)) return "";
    var stream = new ActiveXObject("ADODB.Stream");
    stream.Type = 2;
    stream.Charset = "utf-8";
    stream.Open();
    stream.LoadFromFile(path);
    var text = stream.ReadText();
    stream.Close();
    return text;
  } catch (e) {
    return "";
  }
}


function WriteUtf8File(path, text) {
  var stream = new ActiveXObject("ADODB.Stream");
  stream.Type = 2;
  stream.Charset = "utf-8";
  stream.Open();
  stream.WriteText(text == null ? "" : "" + text);
  stream.SaveToFile(path, 2);
  stream.Close();
}


function NewAdminJsonRequestPath(prefix) {
  adminRequestId++;
  var safePrefix = (prefix || "admin_request").replace(/[^A-Za-z0-9_-]/g, "_");
  return rootDir + "\\logs\\" + safePrefix + "_" + (new Date().getTime()) + "_" + adminRequestId + ".json";
}


function ParseTsv(text) {
  text = RepairMojibake(text);
  text = text.replace(/^\uFEFF/, "");
  var lines = text.split(/\r?\n/);
  var rows = [];
  for (var i = 0; i < lines.length; i++) {
    if (Trim(lines[i]).length > 0) rows.push(lines[i].split("\t"));
  }
  return rows;
}


function RepairMojibake(text) {
  if (!/[\u2500-\u257F]/.test(text)) {
    return text;
  }

  var cp437 = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";
  var bytes = [];
  for (var i = 0; i < text.length; i++) {
    var code = text.charCodeAt(i);
    if (code < 128) {
      bytes.push(code);
      continue;
    }
    var idx = cp437.indexOf(text.charAt(i));
    if (idx >= 0) {
      bytes.push(idx + 128);
    } else {
      return text;
    }
  }
  return Utf8BytesToString(bytes);
}


function Utf8BytesToString(bytes) {
  var out = "";
  for (var i = 0; i < bytes.length;) {
    var b = bytes[i++];
    if (b < 128) {
      out += String.fromCharCode(b);
    } else if ((b & 224) == 192 && i < bytes.length) {
      out += String.fromCharCode(((b & 31) << 6) | (bytes[i++] & 63));
    } else if ((b & 240) == 224 && i + 1 < bytes.length) {
      out += String.fromCharCode(((b & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63));
    } else if ((b & 248) == 240 && i + 2 < bytes.length) {
      var cp = ((b & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63);
      cp -= 65536;
      out += String.fromCharCode(55296 + (cp >> 10), 56320 + (cp & 1023));
    }
  }
  return out;
}
