var costumeCreatorCatalogRows = [];
var costumeCreatorCurrentDraftPath = "";
var costumeCreatorSelectedPart = "head";
var costumeCreatorSelectedIndex = 0;
var costumeCreatorPlaybackTimer = 0;
var costumeCreatorActionFrame = 0;
var costumeCreatorLoading = false;
var costumeCreatorImageMetrics = {};

var costumeCreatorPartCounts = { head: 3, body: 17, leg: 14 };
var costumeCreatorPartNames = { head: "HEAD", body: "BODY", leg: "LEG" };
var costumeCreatorAnchors = {
  head: { x: -13, y: -34 },
  body: { x: -9, y: -16 },
  leg: { x: -8, y: -10 }
};

// Đây là nhóm ghép suy luận từ bộ Luffy chuẩn, không phải state table chính thức của client.
var costumeCreatorActionPresets = {
  stand: [
    { head: 0, body: 1, leg: 1, label: "Đứng yên" }
  ],
  run: [
    { head: 1, body: 3, leg: 2, label: "Chạy 1" },
    { head: 1, body: 4, leg: 3, label: "Chạy 2" },
    { head: 1, body: 5, leg: 5, label: "Chạy 3" },
    { head: 1, body: 6, leg: 6, label: "Chạy 4" }
  ],
  fly: [
    { head: 1, body: 2, leg: 0, label: "Bay ngang" },
    { head: 1, body: 13, leg: 0, label: "Ngã / đánh văng" }
  ],
  guard: [
    { head: 0, body: 0, leg: 7, label: "Đỡ 1" },
    { head: 0, body: 4, leg: 9, label: "Đỡ 2" },
    { head: 0, body: 15, leg: 7, label: "Phòng thủ" }
  ],
  attack: [
    { head: 0, body: 7, leg: 9, label: "Đánh 1" },
    { head: 0, body: 10, leg: 10, label: "Đánh 2" },
    { head: 0, body: 11, leg: 11, label: "Đánh 3" },
    { head: 0, body: 12, leg: 12, label: "Đánh 4" },
    { head: 0, body: 14, leg: 9, label: "Đánh 5" }
  ],
  skill: [
    { head: 0, body: 8, leg: 7, label: "Tụ lực" },
    { head: 0, body: 9, leg: 9, label: "Kỹ năng 1" },
    { head: 0, body: 10, leg: 10, label: "Kỹ năng 2" },
    { head: 0, body: 14, leg: 12, label: "Kỹ năng 3" }
  ],
  dash: [
    { head: 2, body: 16, leg: 13, label: "Motion blur" }
  ]
};

function CreateCostumeCreatorSlot() {
  return {
    imageId: 0,
    dx: 0,
    dy: 0,
    sourcePath: "",
    sourceScale: 4,
    sourceWidth: 0,
    sourceHeight: 0,
    placeholder: false,
    observed: "",
    group: ""
  };
}

function CreateCostumeCreatorPart(count) {
  var slots = [];
  for (var i = 0; i < count; i++) slots.push(CreateCostumeCreatorSlot());
  return slots;
}

function CreateCostumeCreatorState() {
  return {
    schemaVersion: 1,
    metadata: {
      itemId: "",
      name: "",
      description: "",
      gender: 3,
      iconId: 0,
      level: 1,
      headPartId: "",
      bodyPartId: "",
      legPartId: ""
    },
    parts: {
      head: CreateCostumeCreatorPart(costumeCreatorPartCounts.head),
      body: CreateCostumeCreatorPart(costumeCreatorPartCounts.body),
      leg: CreateCostumeCreatorPart(costumeCreatorPartCounts.leg)
    },
    source: "draft",
    sourceItemId: "",
    sourceIssues: []
  };
}

var costumeCreatorState = CreateCostumeCreatorState();

function SetCostumeCreatorMessage(text) {
  var box = document.getElementById("costumeCreatorMessage");
  if (box) box.innerText = text == null ? "" : "" + text;
}

function SetCostumeCreatorBadge(text, state) {
  var badge = document.getElementById("costumeCreatorStatusBadge");
  if (!badge) return;
  badge.innerText = text;
  badge.className = "costume-status-badge " + (state || "draft");
}

function MarkCostumeCreatorDirty() {
  if (costumeCreatorLoading) return;
  costumeCreatorState.source = "draft";
  SetCostumeCreatorBadge("draft", "draft");
}

function NormalizeCostumeCreatorState(raw) {
  var normalized = CreateCostumeCreatorState();
  if (!raw || typeof raw != "object") return normalized;
  if (raw.metadata) {
    for (var key in normalized.metadata) {
      if (raw.metadata[key] != null) normalized.metadata[key] = raw.metadata[key];
    }
  }
  if (raw.source) normalized.source = "" + raw.source;
  if (raw.sourceItemId != null) normalized.sourceItemId = "" + raw.sourceItemId;
  normalized.sourceIssues = raw.sourceIssues && raw.sourceIssues.length ? raw.sourceIssues : [];
  var parts = ["head", "body", "leg"];
  for (var p = 0; p < parts.length; p++) {
    var partName = parts[p];
    var incoming = raw.parts && raw.parts[partName] ? raw.parts[partName] : [];
    for (var i = 0; i < costumeCreatorPartCounts[partName]; i++) {
      if (!incoming[i]) continue;
      var target = normalized.parts[partName][i];
      var source = incoming[i];
      if (source.imageId != null) target.imageId = source.imageId;
      if (source.dx != null) target.dx = source.dx;
      if (source.dy != null) target.dy = source.dy;
      if (source.sourcePath != null) target.sourcePath = "" + source.sourcePath;
      if (source.sourceScale != null) target.sourceScale = parseInt(source.sourceScale, 10) == 1 ? 1 : 4;
      if (source.sourceWidth != null) target.sourceWidth = parseInt(source.sourceWidth, 10) || 0;
      if (source.sourceHeight != null) target.sourceHeight = parseInt(source.sourceHeight, 10) || 0;
      target.placeholder = !!source.placeholder;
      if (source.observed != null) target.observed = "" + source.observed;
      if (source.group != null) target.group = "" + source.group;
    }
  }
  return normalized;
}

function ApplyCostumeCreatorState(raw, sourceLabel) {
  StopCostumeCreatorPlayback();
  costumeCreatorLoading = true;
  costumeCreatorState = NormalizeCostumeCreatorState(raw);
  costumeCreatorSelectedPart = "head";
  costumeCreatorSelectedIndex = 0;
  costumeCreatorActionFrame = 0;
  ApplyCostumeCreatorMetadataToForm();
  PopulateCostumeCreatorFrameSelects();
  RenderCostumeCreatorSlots();
  LoadCostumeCreatorSelectedSlot();
  Set("costumeCreatorAction", "stand");
  UpdateCostumeCreatorPreview();
  costumeCreatorLoading = false;
  var heading = document.getElementById("costumeCreatorHeading");
  if (heading) heading.innerText = costumeCreatorState.metadata.name || "Draft cải trang mới";
  if (sourceLabel == "database") SetCostumeCreatorBadge("database", "database");
  else if (sourceLabel == "sample") SetCostumeCreatorBadge("mẫu", "database");
  else SetCostumeCreatorBadge("draft", "draft");
}

function ApplyCostumeCreatorMetadataToForm() {
  var meta = costumeCreatorState.metadata;
  Set("costumeCreatorItemId", meta.itemId);
  Set("costumeCreatorName", meta.name);
  Set("costumeCreatorDescription", meta.description);
  Set("costumeCreatorGender", meta.gender);
  Set("costumeCreatorIconId", meta.iconId);
  Set("costumeCreatorHeadPartId", meta.headPartId);
  Set("costumeCreatorBodyPartId", meta.bodyPartId);
  Set("costumeCreatorLegPartId", meta.legPartId);
}

function SyncCostumeCreatorMetadata() {
  if (!costumeCreatorState || !costumeCreatorState.metadata) return;
  var meta = costumeCreatorState.metadata;
  meta.itemId = Trim(V("costumeCreatorItemId"));
  meta.name = V("costumeCreatorName");
  meta.description = V("costumeCreatorDescription");
  meta.gender = parseInt(V("costumeCreatorGender"), 10);
  meta.iconId = Trim(V("costumeCreatorIconId"));
  meta.headPartId = Trim(V("costumeCreatorHeadPartId"));
  meta.bodyPartId = Trim(V("costumeCreatorBodyPartId"));
  meta.legPartId = Trim(V("costumeCreatorLegPartId"));
  var heading = document.getElementById("costumeCreatorHeading");
  if (heading) heading.innerText = Trim(meta.name) || "Draft cải trang mới";
  MarkCostumeCreatorDirty();
  UpdateCostumeCreatorPreview();
}

function NewCostumeCreatorDraft() {
  costumeCreatorCurrentDraftPath = "";
  ApplyCostumeCreatorState(CreateCostumeCreatorState(), "draft");
  SetCostumeCreatorMessage("Đã tạo draft rỗng với đủ HEAD 3 / BODY 17 / LEG 14 slot. Slot chưa có ảnh được giữ nguyên để bổ sung asset sau.");
}

function ParseCostumeCreatorJson(raw) {
  var text = RepairMojibake(raw || "").replace(/^\uFEFF/, "");
  if (/^ERROR\t/i.test(text)) throw new Error(StatusText(text));
  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error("Backend trả về JSON không hợp lệ: " + e.message);
  }
}

function LoadCostumeCreatorCatalog() {
  var host = document.getElementById("costumeCreatorCatalog");
  if (host) host.innerHTML = '<div class="costume-empty">Đang đọc database...</div>';
  try {
    costumeCreatorCatalogRows = ParseTsv(RunAdmin("listcostumecreator", {}));
    RenderCostumeCreatorCatalog(costumeCreatorCatalogRows);
  } catch (e) {
    if (host) host.innerHTML = '<div class="costume-empty">' + Html(e.message) + '</div>';
  }
}

function FilterCostumeCreatorCatalog() {
  var query = Trim(V("costumeCreatorSearch")).toLowerCase();
  if (!query) {
    RenderCostumeCreatorCatalog(costumeCreatorCatalogRows);
    return;
  }
  var rows = [];
  if (costumeCreatorCatalogRows.length) rows.push(costumeCreatorCatalogRows[0]);
  for (var i = 1; i < costumeCreatorCatalogRows.length; i++) {
    var row = costumeCreatorCatalogRows[i];
    if ((row[0] + " " + row[1]).toLowerCase().indexOf(query) >= 0) rows.push(row);
  }
  RenderCostumeCreatorCatalog(rows);
}

function RenderCostumeCreatorCatalog(rows) {
  var host = document.getElementById("costumeCreatorCatalog");
  if (!host) return;
  if (!rows || rows.length <= 1) {
    host.innerHTML = '<div class="costume-empty">Không tìm thấy cải trang.</div>';
    return;
  }
  var html = "";
  var currentId = "" + (costumeCreatorState.sourceItemId || "");
  for (var i = 1; i < rows.length; i++) {
    var row = rows[i];
    var full = row[6] == "1";
    var active = currentId && currentId == row[0] ? " active" : "";
    html += '<button type="button" class="costume-catalog-item' + active + '" onclick="LoadCostumeCreatorItem(' + parseInt(row[0], 10) + ')">';
    html += '<strong>#' + Html(row[0]) + ' · ' + Html(row[1]) + '</strong>';
    html += '<span>Icon ' + Html(row[2]) + ' · P ' + Html(row[3]) + '/' + Html(row[4]) + '/' + Html(row[5]) + '</span>';
    html += '<b class="costume-catalog-state' + (full ? " full" : "") + '">' + (full ? "3 PART" : "THIẾU") + '</b></button>';
  }
  host.innerHTML = html;
}

function LoadCostumeCreatorItem(itemId) {
  SetCostumeCreatorMessage("Đang đọc item " + itemId + " và ba Part từ database...");
  try {
    var detail = ParseCostumeCreatorJson(RunAdmin("getcostumecreator", { Id: itemId }));
    costumeCreatorCurrentDraftPath = "";
    ApplyCostumeCreatorState(detail, "database");
    RenderCostumeCreatorCatalog(costumeCreatorCatalogRows);
    var issues = detail.sourceIssues || [];
    SetCostumeCreatorMessage("Đã nạp cải trang #" + itemId + "." + (issues.length ? "\r\nCảnh báo nguồn: " + issues.join("; ") : " Dữ liệu đã sẵn sàng để xem hoặc clone thành draft."));
  } catch (e) {
    SetCostumeCreatorMessage(e.message);
  }
}

function EnsureCostumeCreatorDraftFolder() {
  var toolDir = rootDir + "\\costume-tools";
  var projectDir = toolDir + "\\projects";
  if (!fso.FolderExists(toolDir)) fso.CreateFolder(toolDir);
  if (!fso.FolderExists(projectDir)) fso.CreateFolder(projectDir);
  return projectDir;
}

function SanitizeCostumeCreatorDraftName(value) {
  var text = (value || "costume-draft").toLowerCase();
  text = text.replace(/[^a-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "");
  if (!text) text = "costume-draft";
  var itemId = Trim(V("costumeCreatorItemId"));
  if (itemId) text += "-" + itemId;
  return text.substring(0, 72);
}

function LoadCostumeCreatorDraftList() {
  if (!document.getElementById("costumeCreatorDraftSelect")) return;
  var folderPath = EnsureCostumeCreatorDraftFolder();
  var items = [];
  var files = new Enumerator(fso.GetFolder(folderPath).Files);
  for (; !files.atEnd(); files.moveNext()) {
    var file = files.item();
    if (/\.json$/i.test(file.Name)) items.push({ name: file.Name, path: file.Path });
  }
  items.sort(function (a, b) { return a.name.toLowerCase() < b.name.toLowerCase() ? -1 : 1; });
  var comboItems = [["", items.length ? "-- Chọn draft --" : "-- Chưa có draft --"]];
  for (var i = 0; i < items.length; i++) comboItems.push([items[i].path, items[i].name]);
  SetComboItems("costumeCreatorDraftSelect", comboItems);
  Set("costumeCreatorDraftSelect", costumeCreatorCurrentDraftPath || "");
}

function SaveCostumeCreatorDraft() {
  SyncCostumeCreatorMetadata();
  UpdateCostumeCreatorSelectedSlot();
  var folderPath = EnsureCostumeCreatorDraftFolder();
  var targetPath = costumeCreatorCurrentDraftPath;
  if (!targetPath) {
    targetPath = folderPath + "\\" + SanitizeCostumeCreatorDraftName(costumeCreatorState.metadata.name) + ".json";
    if (fso.FileExists(targetPath) && !window.confirm("Draft đã tồn tại. Ghi đè file này?\r\n" + targetPath)) return;
  }
  costumeCreatorState.savedAt = new Date().toISOString ? new Date().toISOString() : "" + new Date();
  WriteUtf8File(targetPath, JSON.stringify(costumeCreatorState, null, 2));
  costumeCreatorCurrentDraftPath = targetPath;
  costumeCreatorState.source = "draft";
  LoadCostumeCreatorDraftList();
  SetCostumeCreatorBadge("draft đã lưu", "draft");
  SetCostumeCreatorMessage("Đã lưu draft cục bộ:\r\n" + targetPath + "\r\nChưa ghi database, data/icon hoặc cache version.");
}

function LoadSelectedCostumeCreatorDraft() {
  var path = V("costumeCreatorDraftSelect");
  if (!path) {
    SetCostumeCreatorMessage("Hãy chọn một draft JSON để mở.");
    return;
  }
  try {
    var raw = JSON.parse(ReadFile(path));
    costumeCreatorCurrentDraftPath = path;
    ApplyCostumeCreatorState(raw, "draft");
    SetCostumeCreatorMessage("Đã mở draft:\r\n" + path);
  } catch (e) {
    SetCostumeCreatorMessage("Không mở được draft: " + e.message);
  }
}

function LoadCostumeCreatorLuffySample() {
  var state = CreateCostumeCreatorState();
  state.metadata.itemId = "2001";
  state.metadata.name = "Cải trang Luffy Mũ rơm";
  state.metadata.description = "Cải trang thành Luffy Mũ rơm";
  state.metadata.gender = 3;
  state.metadata.iconId = 5432;
  state.metadata.headPartId = "582";
  state.metadata.bodyPartId = "583";
  state.metadata.legPartId = "584";
  state.source = "sample";
  state.sourceItemId = "2001";

  ApplyCostumeCreatorTupleList(state.parts.head, [[5432,2,2],[5433,2,2],[3000,0,0]], ["Mặt chính diện","Mặt nghiêng / chúi","Motion blur đầu"], ["Đứng / icon","Chạy / bay","Dash"]);
  ApplyCostumeCreatorTupleList(state.parts.body, [[5437,0,0],[5434,-2,0],[5435,-1,-2],[5436,0,-1],[5438,2,-1],[5446,1,-1],[5445,2,-1],[5444,0,0],[5442,0,1],[5441,0,-1],[5439,0,1],[5440,0,0],[5443,1,-2],[5462,-1,-1],[5447,0,0],[5448,0,0],[3001,0,0]], ["Đỡ / trúng đòn","Đứng yên","Nằm ngang","Chúi trước","Che trước","Hai tay trước","Co người","Đấm chéo","Hai tay mở","Đấm lên","Tay kéo ngang","Đấm ngang","Đấm co chéo","Nằm ngang 2","Đấm lớn","Khoanh tay","Motion blur thân"], ["Đỡ","Đứng","Bay / ngã","Chạy","Đỡ","Chạy","Chạy","Đánh","Kỹ năng","Kỹ năng","Đánh","Đánh","Đánh","Bay / ngã","Đánh","Phòng thủ","Dash"]);
  ApplyCostumeCreatorTupleList(state.parts.leg, [[5461,2,1],[5449,0,1],[5452,0,0],[5451,1,0],[5450,0,0],[5453,0,0],[5454,0,0],[5459,0,0],[5460,0,3],[5458,0,0],[5457,0,0],[5456,0,0],[5455,0,0],[3002,0,0]], ["Chân co nằm ngang","Đứng yên","Bước chuyển","Chạy / bật","Hạ trọng tâm","Sải chân","Co gối","Hai chân co","Nâng gối","Đứng tấn","Chân chéo","Bay / đá","Chân kéo dài","Motion blur chân"], ["Bay","Đứng","Chạy","Chạy","Chạy","Chạy","Chạy","Đỡ / kỹ năng","Kỹ năng","Đánh / đỡ","Đánh","Đánh","Đánh","Dash"]);

  costumeCreatorCurrentDraftPath = "";
  ApplyCostumeCreatorState(state, "sample");
  SetCostumeCreatorMessage("Đã nạp bộ Luffy chuẩn để tham khảo. Các tên nhóm hành động là suy luận thị giác; DATA 3/17/14 là dữ liệu thật.");
}

function ApplyCostumeCreatorTupleList(target, tuples, observed, groups) {
  for (var i = 0; i < target.length; i++) {
    var tuple = tuples[i] || [0, 0, 0];
    target[i].imageId = tuple[0];
    target[i].dx = tuple[1];
    target[i].dy = tuple[2];
    target[i].placeholder = tuple[0] === 0;
    target[i].observed = observed && observed[i] ? observed[i] : "";
    target[i].group = groups && groups[i] ? groups[i] : "";
  }
}

function GetCostumeCreatorSlot(partName, index) {
  if (!costumeCreatorState.parts[partName]) return null;
  return costumeCreatorState.parts[partName][index] || null;
}

function GetSelectedCostumeCreatorSlot() {
  return GetCostumeCreatorSlot(costumeCreatorSelectedPart, costumeCreatorSelectedIndex);
}

function GetCostumeCreatorSlotClass(slot) {
  if (!slot) return "";
  if (slot.placeholder) return "placeholder";
  if (Trim(slot.sourcePath || "")) return "upload";
  var imageId = parseInt(slot.imageId, 10) || 0;
  return imageId > 0 ? "ready" : "";
}

function RenderCostumeCreatorSlots() {
  var parts = ["head", "body", "leg"];
  var assetCount = 0;
  var placeholderCount = 0;
  for (var p = 0; p < parts.length; p++) {
    var partName = parts[p];
    var target = document.getElementById("costumeCreator" + (partName == "head" ? "Head" : partName == "body" ? "Body" : "Leg") + "Slots");
    if (!target) continue;
    var html = "";
    var slots = costumeCreatorState.parts[partName];
    for (var i = 0; i < slots.length; i++) {
      var slot = slots[i];
      var stateClass = GetCostumeCreatorSlotClass(slot);
      if (slot.placeholder) placeholderCount++;
      else if (parseInt(slot.imageId, 10) > 0 || Trim(slot.sourcePath || "")) assetCount++;
      var active = partName == costumeCreatorSelectedPart && i == costumeCreatorSelectedIndex ? " active" : "";
      var sourceLabel = slot.placeholder ? "placeholder" : (parseInt(slot.imageId, 10) > 0 ? "ID " + slot.imageId : (slot.sourcePath ? "PNG mới" : "CHƯA CÓ ẢNH"));
      html += '<button type="button" class="costume-slot-button ' + stateClass + active + '" onclick="SelectCostumeCreatorSlot(\'' + partName + '\',' + i + ')">';
      html += '<b>' + costumeCreatorPartNames[partName] + '[' + i + ']</b><span>' + Html(sourceLabel) + '</span><span>[' + Html(slot.dx) + ',' + Html(slot.dy) + ']</span></button>';
    }
    target.innerHTML = html;
  }
  var progress = document.getElementById("costumeCreatorSlotProgress");
  if (progress) progress.innerText = assetCount + " ảnh · " + placeholderCount + " placeholder · " + (34 - assetCount - placeholderCount) + " thiếu";
}

function SelectCostumeCreatorSlot(partName, index) {
  UpdateCostumeCreatorSelectedSlot();
  costumeCreatorSelectedPart = partName;
  costumeCreatorSelectedIndex = parseInt(index, 10) || 0;
  RenderCostumeCreatorSlots();
  LoadCostumeCreatorSelectedSlot();
}

function LoadCostumeCreatorSelectedSlot() {
  var slot = GetSelectedCostumeCreatorSlot();
  if (!slot) return;
  var label = costumeCreatorPartNames[costumeCreatorSelectedPart] + "[" + costumeCreatorSelectedIndex + "]";
  var labelNode = document.getElementById("costumeCreatorSelectedLabel");
  if (labelNode) labelNode.innerText = label;
  Set("costumeCreatorSlotImageId", slot.imageId);
  Set("costumeCreatorSlotDx", slot.dx);
  Set("costumeCreatorSlotDy", slot.dy);
  Set("costumeCreatorSourceScale", slot.sourceScale == 1 ? "1" : "4");
  Set("costumeCreatorSlotObserved", slot.observed);
  Set("costumeCreatorSlotGroup", slot.group);
  var placeholder = document.getElementById("costumeCreatorSlotPlaceholder");
  if (placeholder) placeholder.checked = !!slot.placeholder;
  var fileInput = document.getElementById("costumeCreatorSlotFile");
  if (fileInput) {
    try { fileInput.value = ""; } catch (e) {}
  }
  var pathNode = document.getElementById("costumeCreatorSlotPath");
  if (pathNode) pathNode.innerText = slot.sourcePath || "Chưa chọn file.";
  UpdateCostumeCreatorSelectedState();
}

function UpdateCostumeCreatorSelectedSlot() {
  var slot = GetSelectedCostumeCreatorSlot();
  if (!slot) return;
  slot.imageId = Trim(V("costumeCreatorSlotImageId"));
  slot.dx = Trim(V("costumeCreatorSlotDx"));
  slot.dy = Trim(V("costumeCreatorSlotDy"));
  slot.sourceScale = parseInt(V("costumeCreatorSourceScale"), 10) == 1 ? 1 : 4;
  slot.observed = V("costumeCreatorSlotObserved");
  slot.group = V("costumeCreatorSlotGroup");
  var placeholder = document.getElementById("costumeCreatorSlotPlaceholder");
  slot.placeholder = placeholder ? placeholder.checked : false;
  MarkCostumeCreatorDirty();
  RenderCostumeCreatorSlots();
  UpdateCostumeCreatorSelectedState();
  UpdateCostumeCreatorPreview();
}

function UpdateCostumeCreatorSelectedState() {
  var slot = GetSelectedCostumeCreatorSlot();
  var stateNode = document.getElementById("costumeCreatorSelectedState");
  if (!slot || !stateNode) return;
  if (slot.placeholder) stateNode.innerText = "PLACEHOLDER";
  else if (Trim(slot.sourcePath || "")) stateNode.innerText = "PNG NGUỒN";
  else if (parseInt(slot.imageId, 10) > 0) stateNode.innerText = "IMAGE ID " + slot.imageId;
  else stateNode.innerText = "CHƯA CÓ ẢNH";
}

function HandleCostumeCreatorSlotFile(fileInput) {
  if (!fileInput || !fileInput.value) return;
  var slot = GetSelectedCostumeCreatorSlot();
  if (!slot) return;
  slot.sourcePath = fileInput.value;
  slot.sourceScale = parseInt(V("costumeCreatorSourceScale"), 10) == 1 ? 1 : 4;
  slot.placeholder = false;
  var image = new Image();
  image.onload = function () {
    slot.sourceWidth = image.naturalWidth || image.width || 0;
    slot.sourceHeight = image.naturalHeight || image.height || 0;
    costumeCreatorImageMetrics[CostumeCreatorFileUrl(slot.sourcePath)] = { width: slot.sourceWidth, height: slot.sourceHeight };
    var pathNode = document.getElementById("costumeCreatorSlotPath");
    if (pathNode) pathNode.innerText = slot.sourcePath + " · " + slot.sourceWidth + "x" + slot.sourceHeight + "px";
    RenderCostumeCreatorSlots();
    UpdateCostumeCreatorPreview();
  };
  image.onerror = function () {
    SetCostumeCreatorMessage("Không đọc được PNG nguồn: " + slot.sourcePath);
  };
  image.src = CostumeCreatorFileUrl(slot.sourcePath);
  var pathNodeNow = document.getElementById("costumeCreatorSlotPath");
  if (pathNodeNow) pathNodeNow.innerText = slot.sourcePath;
  MarkCostumeCreatorDirty();
  RenderCostumeCreatorSlots();
  LoadCostumeCreatorSelectedSlot();
  UpdateCostumeCreatorPreview();
}

function ClearCostumeCreatorSelectedAsset() {
  var slot = GetSelectedCostumeCreatorSlot();
  if (!slot) return;
  slot.imageId = 0;
  slot.sourcePath = "";
  slot.sourceWidth = 0;
  slot.sourceHeight = 0;
  slot.placeholder = false;
  MarkCostumeCreatorDirty();
  RenderCostumeCreatorSlots();
  LoadCostumeCreatorSelectedSlot();
  UpdateCostumeCreatorPreview();
}

function NudgeCostumeCreatorOffset(axis, delta) {
  var inputId = axis == "x" ? "costumeCreatorSlotDx" : "costumeCreatorSlotDy";
  var value = parseInt(V(inputId), 10) || 0;
  value = Math.max(-128, Math.min(127, value + delta));
  Set(inputId, value);
  UpdateCostumeCreatorSelectedSlot();
}

function PopulateCostumeCreatorFrameSelects() {
  var specs = [
    { id: "costumeCreatorPreviewHead", name: "HEAD", count: 3 },
    { id: "costumeCreatorPreviewBody", name: "BODY", count: 17 },
    { id: "costumeCreatorPreviewLeg", name: "LEG", count: 14 }
  ];
  for (var s = 0; s < specs.length; s++) {
    if (!document.getElementById(specs[s].id)) continue;
    var items = [];
    for (var i = 0; i < specs[s].count; i++) items.push(["" + i, "" + i]);
    SetComboItems(specs[s].id, items);
  }
  Set("costumeCreatorPreviewHead", "0");
  Set("costumeCreatorPreviewBody", "1");
  Set("costumeCreatorPreviewLeg", "1");
}

function GetCostumeCreatorPreviewFrame() {
  var action = V("costumeCreatorAction") || "stand";
  if (action == "custom") {
    return {
      head: parseInt(V("costumeCreatorPreviewHead"), 10) || 0,
      body: parseInt(V("costumeCreatorPreviewBody"), 10) || 0,
      leg: parseInt(V("costumeCreatorPreviewLeg"), 10) || 0,
      label: "Tự chọn"
    };
  }
  var frames = costumeCreatorActionPresets[action] || costumeCreatorActionPresets.stand;
  if (costumeCreatorActionFrame >= frames.length) costumeCreatorActionFrame = 0;
  return frames[costumeCreatorActionFrame];
}

function ChangeCostumeCreatorAction() {
  StopCostumeCreatorPlayback();
  costumeCreatorActionFrame = 0;
  UpdateCostumeCreatorPreview();
}

function ChangeCostumeCreatorCustomFrame() {
  StopCostumeCreatorPlayback();
  Set("costumeCreatorAction", "custom");
  costumeCreatorActionFrame = 0;
  UpdateCostumeCreatorPreview();
}

function ToggleCostumeCreatorPlayback() {
  if (costumeCreatorPlaybackTimer) {
    StopCostumeCreatorPlayback();
    return;
  }
  var action = V("costumeCreatorAction") || "stand";
  var frames = costumeCreatorActionPresets[action] || [];
  if (frames.length <= 1) {
    SetCostumeCreatorMessage("Hành động này chỉ có một frame preview.");
    return;
  }
  var button = document.getElementById("costumeCreatorPlayButton");
  if (button) button.innerText = "Dừng";
  costumeCreatorPlaybackTimer = window.setInterval(function () {
    if (currentTab != "costumecreator") {
      StopCostumeCreatorPlayback();
      return;
    }
    costumeCreatorActionFrame = (costumeCreatorActionFrame + 1) % frames.length;
    UpdateCostumeCreatorPreview();
  }, 360);
}

function StopCostumeCreatorPlayback() {
  if (costumeCreatorPlaybackTimer) window.clearInterval(costumeCreatorPlaybackTimer);
  costumeCreatorPlaybackTimer = 0;
  var button = document.getElementById("costumeCreatorPlayButton");
  if (button) button.innerText = "Phát";
}

function CostumeCreatorFileUrl(path) {
  if (!path) return "";
  var normalized = ("" + path).replace(/\\/g, "/");
  if (/^[A-Za-z]:\//.test(normalized)) normalized = "/" + normalized;
  return encodeURI("file://" + normalized);
}

function GetCostumeCreatorImageSource(slot, zoom) {
  if (!slot || slot.placeholder) return null;
  if (Trim(slot.sourcePath || "")) {
    return { url: CostumeCreatorFileUrl(slot.sourcePath), sourceScale: slot.sourceScale == 1 ? 1 : 4 };
  }
  var imageId = parseInt(slot.imageId, 10) || 0;
  if (imageId <= 0) return null;
  var path = rootDir + "\\data\\icon\\x" + zoom + "\\" + imageId + ".png";
  var sourceScale = zoom;
  try {
    if (!fso.FileExists(path)) {
      path = rootDir + "\\data\\icon\\x4\\" + imageId + ".png";
      sourceScale = 4;
    }
  } catch (e) {}
  return { url: CostumeCreatorFileUrl(path), sourceScale: sourceScale };
}

function PositionCostumeCreatorLayer(partName, slot, image, source, zoom, direction) {
  if (!image || !source) return;
  var metric = costumeCreatorImageMetrics[source.url];
  var naturalWidth = metric ? metric.width : (image.naturalWidth || image.width || slot.sourceWidth || 0);
  var naturalHeight = metric ? metric.height : (image.naturalHeight || image.height || slot.sourceHeight || 0);
  if (!naturalWidth || !naturalHeight) return;
  costumeCreatorImageMetrics[source.url] = { width: naturalWidth, height: naturalHeight };
  var displayWidth = Math.max(1, Math.round(naturalWidth * zoom / source.sourceScale));
  var displayHeight = Math.max(1, Math.round(naturalHeight * zoom / source.sourceScale));
  var dx = parseInt(slot.dx, 10) || 0;
  var dy = parseInt(slot.dy, 10) || 0;
  var anchor = costumeCreatorAnchors[partName];
  var logicalX = anchor.x + dx;
  var logicalY = anchor.y + dy;
  var left = direction > 0 ? logicalX * zoom : (-logicalX * zoom - displayWidth);
  image.style.display = "block";
  image.style.left = "calc(50% + " + left + "px)";
  image.style.top = "calc(100% - 45px + " + (logicalY * zoom) + "px)";
  image.style.width = displayWidth + "px";
  image.style.height = displayHeight + "px";
  image.style.transform = direction > 0 ? "none" : "scaleX(-1)";
}

function RenderCostumeCreatorPreviewLayer(partName, slotIndex, imageId, zoom, direction) {
  var slot = GetCostumeCreatorSlot(partName, slotIndex);
  var image = document.getElementById(imageId);
  if (!image || !slot) return false;
  var source = GetCostumeCreatorImageSource(slot, zoom);
  if (!source) {
    image.style.display = "none";
    image.removeAttribute("src");
    image.costumeSourceKey = "";
    return false;
  }
  var key = source.url + "|" + source.sourceScale;
  var position = function () { PositionCostumeCreatorLayer(partName, slot, image, source, zoom, direction); };
  image.onload = function () {
    var width = image.naturalWidth || image.width || 0;
    var height = image.naturalHeight || image.height || 0;
    if (width && height) costumeCreatorImageMetrics[source.url] = { width: width, height: height };
    position();
  };
  image.onerror = function () { image.style.display = "none"; };
  if (image.costumeSourceKey != key) {
    image.costumeSourceKey = key;
    image.style.width = "auto";
    image.style.height = "auto";
    image.src = source.url;
  } else if (image.complete) {
    position();
  }
  return true;
}

function UpdateCostumeCreatorPreview() {
  if (!costumeCreatorState || !document.getElementById("costumeCreatorPreviewStage")) return;
  var frame = GetCostumeCreatorPreviewFrame();
  var zoom = parseInt(V("costumeCreatorZoom"), 10) || 4;
  zoom = Math.max(1, Math.min(4, zoom));
  var direction = V("costumeCreatorDirection") == "left" ? -1 : 1;
  Set("costumeCreatorPreviewHead", frame.head);
  Set("costumeCreatorPreviewBody", frame.body);
  Set("costumeCreatorPreviewLeg", frame.leg);
  var hasHead = RenderCostumeCreatorPreviewLayer("head", frame.head, "costumeCreatorPreviewHeadImage", zoom, direction);
  var hasLeg = RenderCostumeCreatorPreviewLayer("leg", frame.leg, "costumeCreatorPreviewLegImage", zoom, direction);
  var hasBody = RenderCostumeCreatorPreviewLayer("body", frame.body, "costumeCreatorPreviewBodyImage", zoom, direction);
  var empty = document.getElementById("costumeCreatorPreviewEmpty");
  if (empty) empty.style.display = hasHead || hasBody || hasLeg ? "none" : "block";
  var previewName = document.getElementById("costumeCreatorPreviewName");
  if (previewName) previewName.innerText = Trim(costumeCreatorState.metadata.name) || "Cải trang mới";
  var action = V("costumeCreatorAction") || "stand";
  var frames = action == "custom" ? [frame] : (costumeCreatorActionPresets[action] || [frame]);
  var frameLabel = document.getElementById("costumeCreatorFrameLabel");
  if (frameLabel) frameLabel.innerText = "Frame " + (costumeCreatorActionFrame + 1) + "/" + frames.length + " · " + frame.label + " · mapping suy luận";
  var status = document.getElementById("costumeCreatorPreviewStatus");
  if (status) status.innerText = "x" + zoom + " · hướng " + (direction > 0 ? "phải" : "trái") + " · HEAD[" + frame.head + "] + BODY[" + frame.body + "] + LEG[" + frame.leg + "] · offset dùng đơn vị x1";
}

function IsCostumeCreatorInteger(value) {
  return /^-?\d+$/.test(Trim(value == null ? "" : "" + value));
}

function ValidateCostumeCreator(quiet) {
  SyncCostumeCreatorMetadata();
  UpdateCostumeCreatorSelectedSlot();
  var errors = [];
  var warnings = [];
  var meta = costumeCreatorState.metadata;
  if (!Trim(meta.name)) errors.push("Thiếu tên cải trang.");
  if (meta.itemId && (!IsCostumeCreatorInteger(meta.itemId) || parseInt(meta.itemId, 10) < 0 || parseInt(meta.itemId, 10) > 32767)) errors.push("Item ID phải nằm trong 0..32767.");
  if (!IsCostumeCreatorInteger(meta.iconId) || parseInt(meta.iconId, 10) < 0 || parseInt(meta.iconId, 10) > 32767) errors.push("Icon ID phải nằm trong 0..32767.");
  else if (parseInt(meta.iconId, 10) === 0) warnings.push("Chưa chọn icon hành trang.");

  var partIdValues = [meta.headPartId, meta.bodyPartId, meta.legPartId];
  for (var pid = 0; pid < partIdValues.length; pid++) {
    if (partIdValues[pid] && (!IsCostumeCreatorInteger(partIdValues[pid]) || parseInt(partIdValues[pid], 10) < 0 || parseInt(partIdValues[pid], 10) > 32767)) errors.push("Part ID phải nằm trong 0..32767 hoặc để trống.");
  }
  if (!meta.headPartId || !meta.bodyPartId || !meta.legPartId) warnings.push("Part ID sẽ phải được cấp liên tục khi Publish.");

  var parts = ["head", "body", "leg"];
  for (var p = 0; p < parts.length; p++) {
    var partName = parts[p];
    var slots = costumeCreatorState.parts[partName];
    if (slots.length != costumeCreatorPartCounts[partName]) errors.push(costumeCreatorPartNames[partName] + " sai số slot.");
    for (var i = 0; i < slots.length; i++) {
      var slot = slots[i];
      var label = costumeCreatorPartNames[partName] + "[" + i + "]";
      var imageId = IsCostumeCreatorInteger(slot.imageId) ? parseInt(slot.imageId, 10) : -1;
      if (!IsCostumeCreatorInteger(slot.dx) || parseInt(slot.dx, 10) < -128 || parseInt(slot.dx, 10) > 127) errors.push(label + " dx ngoài -128..127.");
      if (!IsCostumeCreatorInteger(slot.dy) || parseInt(slot.dy, 10) < -128 || parseInt(slot.dy, 10) > 127) errors.push(label + " dy ngoài -128..127.");
      if (slot.placeholder) {
        if (imageId > 0 || Trim(slot.sourcePath || "")) warnings.push(label + " vừa có asset vừa đánh dấu Placeholder.");
        continue;
      }
      if (imageId < 0 || imageId > 32767) errors.push(label + " Image ID phải nằm trong 0..32767.");
      if (!Trim(slot.sourcePath || "") && imageId === 0) {
        errors.push(label + " CHƯA CÓ ẢNH.");
        continue;
      }
      if (Trim(slot.sourcePath || "")) {
        try {
          if (!fso.FileExists(slot.sourcePath)) errors.push(label + " không tìm thấy PNG nguồn.");
        } catch (e) { errors.push(label + " đường dẫn PNG không hợp lệ."); }
        if (imageId <= 0) errors.push(label + " có PNG nguồn nhưng chưa cấp Image ID.");
        else warnings.push(label + " là PNG nguồn; cần compile nearest-neighbor thành x1-x4.");
      } else if (imageId > 0) {
        var missing = [];
        for (var scale = 1; scale <= 4; scale++) {
          var iconPath = rootDir + "\\data\\icon\\x" + scale + "\\" + imageId + ".png";
          if (!fso.FileExists(iconPath)) missing.push("x" + scale);
        }
        if (missing.length) errors.push(label + " thiếu ảnh " + missing.join(", ") + " cho ID " + imageId + ".");
      }
    }
  }

  for (var si = 0; si < costumeCreatorState.sourceIssues.length; si++) warnings.push("Nguồn DB: " + costumeCreatorState.sourceIssues[si]);
  var report = { errors: errors, warnings: warnings };
  if (!quiet) {
    var lines = ["Kết quả: " + errors.length + " lỗi, " + warnings.length + " cảnh báo."];
    var limit = 12;
    for (var eIndex = 0; eIndex < errors.length && eIndex < limit; eIndex++) lines.push("LỖI: " + errors[eIndex]);
    for (var wIndex = 0; wIndex < warnings.length && lines.length <= limit + 1; wIndex++) lines.push("CẢNH BÁO: " + warnings[wIndex]);
    if (errors.length + warnings.length > limit) lines.push("... còn " + (errors.length + warnings.length - limit) + " mục; xuất DATA để xem toàn bộ.");
    SetCostumeCreatorMessage(lines.join("\r\n"));
    SetCostumeCreatorBadge(errors.length ? "chưa hợp lệ" : "đã kiểm tra", errors.length ? "invalid" : "database");
  }
  return report;
}

function GetCostumeCreatorPartTuples(partName) {
  var result = [];
  var slots = costumeCreatorState.parts[partName];
  for (var i = 0; i < slots.length; i++) {
    result.push([
      parseInt(slots[i].imageId, 10) || 0,
      parseInt(slots[i].dx, 10) || 0,
      parseInt(slots[i].dy, 10) || 0
    ]);
  }
  return result;
}

function CostumeCreatorSqlString(value) {
  return "'" + (value == null ? "" : "" + value).replace(/'/g, "''") + "'";
}

function GenerateCostumeCreatorData() {
  var report = ValidateCostumeCreator(true);
  var meta = costumeCreatorState.metadata;
  var head = GetCostumeCreatorPartTuples("head");
  var body = GetCostumeCreatorPartTuples("body");
  var leg = GetCostumeCreatorPartTuples("leg");
  var itemId = meta.itemId && IsCostumeCreatorInteger(meta.itemId) ? meta.itemId : ":item_id";
  var headPart = meta.headPartId && IsCostumeCreatorInteger(meta.headPartId) ? meta.headPartId : ":head_part_id";
  var bodyPart = meta.bodyPartId && IsCostumeCreatorInteger(meta.bodyPartId) ? meta.bodyPartId : ":body_part_id";
  var legPart = meta.legPartId && IsCostumeCreatorInteger(meta.legPartId) ? meta.legPartId : ":leg_part_id";
  var output = [];
  output.push("# KẾT QUẢ KIỂM TRA");
  output.push("errors=" + report.errors.length + " warnings=" + report.warnings.length);
  for (var i = 0; i < report.errors.length; i++) output.push("ERROR: " + report.errors[i]);
  for (var w = 0; w < report.warnings.length; w++) output.push("WARN: " + report.warnings[w]);
  output.push("");
  output.push("# PART DATA");
  output.push("HEAD = " + JSON.stringify(head));
  output.push("BODY = " + JSON.stringify(body));
  output.push("LEG  = " + JSON.stringify(leg));
  output.push("");
  output.push("# SQL PREVIEW — KHÔNG TỰ THỰC THI");
  output.push("START TRANSACTION;");
  output.push("");
  output.push("INSERT INTO part (id, `TYPE`, `DATA`) VALUES");
  output.push("  (" + headPart + ", 0, '" + JSON.stringify(head) + "'),");
  output.push("  (" + bodyPart + ", 1, '" + JSON.stringify(body) + "'),");
  output.push("  (" + legPart + ", 2, '" + JSON.stringify(leg) + "');");
  output.push("");
  output.push("INSERT INTO item_template");
  output.push("  (id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)");
  output.push("VALUES");
  output.push("  (" + itemId + ", 5, " + (parseInt(meta.gender, 10) || 0) + ", " + CostumeCreatorSqlString(meta.name) + ", " + CostumeCreatorSqlString(meta.description) + ", 1, " + (parseInt(meta.iconId, 10) || 0) + ", -1, 0, 0, 0, 0, " + headPart + ", " + bodyPart + ", " + legPart + ");");
  output.push("");
  output.push("COMMIT;");
  output.push("");
  output.push("# LƯU Ý");
  output.push("- Mapping hành động trong preview là suy luận, không phải state table client.");
  output.push("- Publish phải audit ID liên tục/duy nhất, tạo ảnh x1-x4, tăng vsData/vsItem và restart có kiểm soát.");
  Set("costumeCreatorDataOutput", output.join("\r\n"));
  document.getElementById("costumeCreatorOutputWrap").className = "costume-output-wrap active";
  SetCostumeCreatorMessage("Đã tạo DATA/SQL preview: " + report.errors.length + " lỗi, " + report.warnings.length + " cảnh báo. Nội dung chưa được thực thi.");
}

function CloseCostumeCreatorOutput() {
  var wrap = document.getElementById("costumeCreatorOutputWrap");
  if (wrap) wrap.className = "costume-output-wrap";
}

RegisterTab({
  id: "costumecreator",
  view: "costume-creator.html",
  panelId: "panelCostumeCreator",
  navId: "navCostumeCreator",
  title: "Tạo Cải Trang",
  subtitle: "Draft HEAD/BODY/LEG 3/17/14, live preview và kiểm tra asset",
  onOpen: function () {
    PopulateCostumeCreatorFrameSelects();
    ApplyCostumeCreatorState(costumeCreatorState, costumeCreatorState.source == "database" ? "database" : costumeCreatorState.source == "sample" ? "sample" : "draft");
    LoadCostumeCreatorCatalog();
    LoadCostumeCreatorDraftList();
  },
  onRefresh: function () {
    LoadCostumeCreatorCatalog();
    LoadCostumeCreatorDraftList();
    RenderCostumeCreatorSlots();
    UpdateCostumeCreatorPreview();
  }
});
