var mapCreatorProjects = [];
var mapCreatorCurrentId = "";
var mapCreatorCurrentDetail = null;
var mapCreatorPreviewMode = "debug";
var mapCreatorPlanDirty = false;
var mapCreatorReleasePlanState = null;
var mapCreatorPlan = null;
var mapEditorState = null;
var mapCanvasMatrix = [];
var mapCanvasTool = "select";
var mapCanvasSelectedAssetId = -1;
var mapCanvasSelection = null;
var mapCanvasDragging = null;
var mapCanvasPointerDown = false;
var mapCanvasWaypointStart = null;
var mapCanvasImageCache = {};
var mapCanvasEventsBound = false;
var mapCanvasDropPreview = null;
var mapCanvasDragGhost = null;
var mapAssetUploadPath = "";
var mapCreatorMode = "canvas";
var mapCreatorJsonChanged = false;
var mapCanvasPointerChanged = false;
var mapCanvasTileImageCache = {};
var mapCreatorUndoStack = [];
var mapCreatorRedoStack = [];
var mapCreatorClipboard = null;
var mapCreatorHistoryLimit = 80;
var mapCreatorAutosaveTimer = null;
var mapCreatorShortcutsBound = false;
var mapCreatorReleases = [];
var mapCreatorSelectedReleaseId = "";
var mapCreatorSelectedReleaseDetail = null;
var mapCreatorAcceptance = null;
var mapCreatorAcceptanceCheckKeys = ["clientLogin", "mapEntered", "visualMatchesPreview", "layersCorrect", "collisionAndGround", "waypoints", "npcs", "mobs", "zoomAssets", "noClientErrors"];
var mapCreatorAcceptanceCheckIds = {
  clientLogin: "mapQaCheckClientLogin",
  mapEntered: "mapQaCheckMapEntered",
  visualMatchesPreview: "mapQaCheckVisualMatchesPreview",
  layersCorrect: "mapQaCheckLayersCorrect",
  collisionAndGround: "mapQaCheckCollisionAndGround",
  waypoints: "mapQaCheckWaypoints",
  npcs: "mapQaCheckNpcs",
  mobs: "mapQaCheckMobs",
  zoomAssets: "mapQaCheckZoomAssets",
  noClientErrors: "mapQaCheckNoClientErrors"
};

function ParseMapCreatorResponse(raw) {
  raw = RepairMojibake(raw || "").replace(/^\uFEFF/, "");
  if (raw.indexOf("ERROR\t") === 0 || raw.indexOf("ERROR ") === 0) {
    var detail = raw.substring(raw.indexOf("\t") >= 0 ? raw.indexOf("\t") + 1 : 6);
    var jsonStart = detail.indexOf("{");
    if (jsonStart >= 0) {
      var parsedMessage = "";
      try {
        var errorObject = JSON.parse(detail.substring(jsonStart));
        if (errorObject.report) {
          var summary = errorObject.report.summary || {};
          var issueLines = [];
          var issues = errorObject.report.issues || [];
          for (var i = 0; i < issues.length && i < 12; i++) {
            issueLines.push(issues[i].code + " @ " + issues[i].path + ": " + issues[i].message);
          }
          parsedMessage = "Compile lỗi " + (summary.errors || 0) + ", cảnh báo " + (summary.warnings || 0) + ".\r\n" + issueLines.join("\r\n");
        }
        if (!parsedMessage && errorObject.message) parsedMessage = errorObject.message;
      } catch (parseError) {}
      if (parsedMessage) throw new Error(parsedMessage);
    }
    throw new Error(detail || raw);
  }
  try {
    return JSON.parse(raw);
  } catch (e) {
    throw new Error("Map Editor trả về JSON không hợp lệ: " + raw.substring(0, 500));
  }
}

function CallMapCreator(action, params) {
  return ParseMapCreatorResponse(RunAdmin(action, params || {}));
}

function SetMapCreatorMessage(text) {
  var target = document.getElementById("mapCreatorMessage");
  if (target) target.innerText = text == null ? "" : "" + text;
}

function CloneMapCanvasMatrix(matrix) {
  var clone = [];
  matrix = matrix || [];
  for (var row = 0; row < matrix.length; row++) clone.push(matrix[row].slice(0));
  return clone;
}

function BuildMapCreatorHistorySnapshot(label) {
  if (!mapCreatorPlan) return null;
  return {
    label: label || "Thay đổi",
    planJson: JSON.stringify(mapCreatorPlan),
    matrix: CloneMapCanvasMatrix(mapCanvasMatrix)
  };
}

function UpdateMapCreatorHistoryControls() {
  var undo = document.getElementById("mapCreatorUndoButton");
  var redo = document.getElementById("mapCreatorRedoButton");
  if (undo) {
    undo.disabled = mapCreatorUndoStack.length <= 1;
    undo.title = undo.disabled ? "Không có thao tác để hoàn tác" : "Hoàn tác: " + mapCreatorUndoStack[mapCreatorUndoStack.length - 1].label;
  }
  if (redo) {
    redo.disabled = mapCreatorRedoStack.length === 0;
    redo.title = redo.disabled ? "Không có thao tác để làm lại" : "Làm lại: " + mapCreatorRedoStack[mapCreatorRedoStack.length - 1].label;
  }
}

function ResetMapCreatorHistory() {
  mapCreatorUndoStack = [];
  mapCreatorRedoStack = [];
  var initial = BuildMapCreatorHistorySnapshot("Mở draft");
  if (initial) mapCreatorUndoStack.push(initial);
  UpdateMapCreatorHistoryControls();
}

function RecordMapCreatorHistory(label) {
  var snapshot = BuildMapCreatorHistorySnapshot(label);
  if (!snapshot) return;
  var previous = mapCreatorUndoStack.length ? mapCreatorUndoStack[mapCreatorUndoStack.length - 1] : null;
  if (previous && previous.planJson == snapshot.planJson && JSON.stringify(previous.matrix) == JSON.stringify(snapshot.matrix)) return;
  mapCreatorUndoStack.push(snapshot);
  if (mapCreatorUndoStack.length > mapCreatorHistoryLimit) mapCreatorUndoStack.shift();
  mapCreatorRedoStack = [];
  UpdateMapCreatorHistoryControls();
}

function RestoreMapCreatorHistorySnapshot(snapshot, message) {
  if (!snapshot) return;
  mapCreatorPlan = JSON.parse(snapshot.planJson);
  mapCanvasMatrix = CloneMapCanvasMatrix(snapshot.matrix);
  mapCanvasSelection = null;
  mapCreatorJsonChanged = false;
  Set("mapCreatorPlanJson", JSON.stringify(mapCreatorPlan, null, 2));
  ConfigureMapCanvas();
  RenderMapTilePalette();
  UpdateMapCanvasSelectionInfo();
  MarkMapCreatorDirty();
  UpdateMapCreatorHistoryControls();
  SetMapCreatorMessage(message);
}

function UndoMapCreatorChange() {
  if (mapCreatorUndoStack.length <= 1) {
    SetMapCreatorMessage("Không có thao tác canvas để hoàn tác.");
    return;
  }
  var current = mapCreatorUndoStack.pop();
  mapCreatorRedoStack.push(current);
  var target = mapCreatorUndoStack[mapCreatorUndoStack.length - 1];
  RestoreMapCreatorHistorySnapshot(target, "Đã hoàn tác: " + current.label + ".");
}

function RedoMapCreatorChange() {
  if (!mapCreatorRedoStack.length) {
    SetMapCreatorMessage("Không có thao tác canvas để làm lại.");
    return;
  }
  var target = mapCreatorRedoStack.pop();
  mapCreatorUndoStack.push(target);
  RestoreMapCreatorHistorySnapshot(target, "Đã làm lại: " + target.label + ".");
}

function SetMapCreatorAutosaveStatus(text, state) {
  var target = document.getElementById("mapCreatorAutosaveStatus");
  if (!target) return;
  target.innerText = text || "";
  target.className = "map-autosave-status " + (state || "");
}

function ScheduleMapCreatorAutosave() {
  if (mapCreatorAutosaveTimer) window.clearTimeout(mapCreatorAutosaveTimer);
  mapCreatorAutosaveTimer = null;
  var toggle = document.getElementById("mapCreatorAutosave");
  if (!toggle || !toggle.checked || !mapCreatorPlanDirty || mapCreatorJsonChanged) return;
  SetMapCreatorAutosaveStatus("Chờ autosave…", "pending");
  mapCreatorAutosaveTimer = window.setTimeout(function () {
    mapCreatorAutosaveTimer = null;
    if (mapCreatorPlanDirty && !mapCreatorJsonChanged && !mapCanvasPointerDown) SaveMapCreatorProject(true, true);
  }, 1800);
}

function LoadMapCreatorProjects() {
  SetMapCreatorMessage("Đang tải danh sách project map...");
  try {
    var response = CallMapCreator("listmapprojects", {});
    mapCreatorProjects = response.projects || [];
    RenderMapCreatorProjects();
    SetMapCreatorMessage("Đã tải " + mapCreatorProjects.length + " project map.");
    if (mapCreatorCurrentId) {
      SelectMapCreatorProject(mapCreatorCurrentId);
    } else if (mapCreatorProjects.length > 0) {
      SelectMapCreatorProject(mapCreatorProjects[0].projectId);
    }
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function RenderMapCreatorProjects() {
  var host = document.getElementById("mapCreatorProjectList");
  if (!host) return;
  if (!mapCreatorProjects.length) {
    host.innerHTML = '<div class="map-empty-projects">Chưa có project. Tạo draft mới hoặc import map runtime.</div>';
    return;
  }
  var html = "";
  for (var i = 0; i < mapCreatorProjects.length; i++) {
    var project = mapCreatorProjects[i];
    var active = project.projectId == mapCreatorCurrentId ? " active" : "";
    html += '<button type="button" class="map-project-item' + active + '" onclick="SelectMapCreatorProject(\'' + EscapeJs(project.projectId) + '\')">' +
      '<strong>' + Html(project.mapId + " - " + project.displayName) + '</strong>' +
      '<span>' + Html(project.projectId + " | " + project.status + " | rev " + project.revision) + '</span>' +
      '</button>';
  }
  host.innerHTML = html;
}

function CreateMapCreatorProject() {
  SetMapCreatorMessage("Đang tạo draft map mới...");
  try {
    var response = CallMapCreator("createmapproject", {
      MapId: V("mapCreatorNewId"),
      Name: V("mapCreatorNewName"),
      WidthTiles: V("mapCreatorNewWidth"),
      HeightTiles: V("mapCreatorNewHeight"),
      TileSetId: V("mapCreatorNewTileSet")
    });
    ApplyMapCreatorDetail(response);
    LoadMapCreatorProjects();
    SetMapCreatorMessage("Đã tạo project " + response.project.projectId + ". Draft chưa ghi runtime/database.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function ImportMapCreatorProject() {
  var mapId = Trim(V("mapCreatorImportId"));
  if (!mapId) {
    SetMapCreatorMessage("Nhập Map ID cần import.");
    return;
  }
  SetMapCreatorMessage("Đang import tile, background và map_template ID " + mapId + "...");
  try {
    var response = CallMapCreator("importmapproject", { MapId: mapId });
    ApplyMapCreatorDetail(response);
    LoadMapCreatorProjects();
    SetMapCreatorMessage("Import hoàn tất. Hãy kiểm tra playerSpawn suy ra và Compile & Preview trước khi publish.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function SelectMapCreatorProject(projectId) {
  if (!projectId) return;
  if (mapCreatorPlanDirty && mapCreatorCurrentId && projectId != mapCreatorCurrentId) {
    if (!window.confirm("Draft hiện tại chưa lưu. Chuyển project và bỏ thay đổi trên màn hình?")) return;
  }
  SetMapCreatorMessage("Đang mở " + projectId + "...");
  try {
    ApplyMapCreatorDetail(CallMapCreator("getmapproject", { ProjectId: projectId }));
    RenderMapCreatorProjects();
    SetMapCreatorMessage("Đã mở " + projectId + ".");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function ApplyMapCreatorDetail(detail) {
  var projectChanged = mapCreatorCurrentId && mapCreatorCurrentId != detail.project.projectId;
  mapCreatorCurrentDetail = detail;
  mapCreatorCurrentId = detail.project.projectId;
  mapCreatorPlanDirty = false;
  mapCreatorReleasePlanState = null;
  mapCreatorAcceptance = null;
  mapCreatorJsonChanged = false;
  mapCreatorPlan = detail.plan;
  if (projectChanged) {
    mapCreatorReleases = [];
    mapCreatorSelectedReleaseId = "";
    mapCreatorSelectedReleaseDetail = null;
  }
  mapCanvasTileImageCache = {};
  Set("mapCreatorPlanJson", JSON.stringify(mapCreatorPlan, null, 2));
  document.getElementById("mapCreatorCurrentName").innerText = detail.project.mapId + " - " + detail.project.displayName;
  var badge = document.getElementById("mapCreatorStatusBadge");
  badge.innerText = detail.project.status;
  badge.className = "map-status-badge " + detail.project.status;
  document.getElementById("mapCreatorRevision").innerText = "Revision " + detail.project.revision;
  document.getElementById("mapCreatorReleasePlan").innerText = "Chưa kiểm tra publish cho revision này.";
  UpdateMapCreatorPreview();
  LoadMapCanvasState();
  ResetMapCreatorHistory();
  SetMapCreatorAutosaveStatus("Đã đồng bộ draft", "saved");
}

function MarkMapCreatorDirty() {
  if (!mapCreatorCurrentId) return;
  mapCreatorPlanDirty = true;
  mapCreatorReleasePlanState = null;
  document.getElementById("mapCreatorRevision").innerText = "Có thay đổi chưa lưu";
  document.getElementById("mapCreatorReleasePlan").innerText = "Draft đã thay đổi; cần lưu và compile lại.";
  ScheduleMapCreatorAutosave();
}

function MarkMapCreatorJsonDirty() {
  mapCreatorJsonChanged = true;
  MarkMapCreatorDirty();
}

function SaveMapCreatorProject(silent, fromAutosave) {
  if (!mapCreatorCurrentId) {
    SetMapCreatorMessage("Chưa chọn project để lưu.");
    return false;
  }
  var plan = null;
  try {
    plan = JSON.parse(V("mapCreatorPlanJson"));
  } catch (e) {
    SetMapCreatorMessage("JSON chưa hợp lệ: " + e.message);
    return false;
  }
  var wasJsonChanged = mapCreatorJsonChanged;
  mapCreatorPlan = plan;
  if (!mapCreatorPlanDirty) {
    if (!silent) SetMapCreatorMessage("Draft không có thay đổi mới.");
    return true;
  }
  var requestPath = NewAdminJsonRequestPath("admin_map_plan");
  try {
    WriteUtf8File(requestPath, JSON.stringify(plan, null, 2));
    var response = CallMapCreator("savemapproject", {
      ProjectId: mapCreatorCurrentId,
      RequestPath: requestPath
    });
    mapCreatorCurrentDetail = response;
    mapCreatorCurrentId = response.project.projectId;
    mapCreatorPlan = response.plan;
    mapCreatorPlanDirty = false;
    mapCreatorReleasePlanState = null;
    mapCreatorJsonChanged = false;
    Set("mapCreatorPlanJson", JSON.stringify(mapCreatorPlan, null, 2));
    document.getElementById("mapCreatorCurrentName").innerText = response.project.mapId + " - " + response.project.displayName;
    var badge = document.getElementById("mapCreatorStatusBadge");
    badge.innerText = response.project.status;
    badge.className = "map-status-badge " + response.project.status;
    document.getElementById("mapCreatorRevision").innerText = "Revision " + response.project.revision;
    document.getElementById("mapCreatorReleasePlan").innerText = "Chưa kiểm tra publish cho revision này.";
    if (wasJsonChanged) {
      mapCanvasTileImageCache = {};
      LoadMapCanvasState();
      RecordMapCreatorHistory("Sửa JSON");
    }
    UpdateMapCreatorPreview();
    RenderMapCreatorProjects();
    SetMapCreatorAutosaveStatus("Đã lưu revision " + response.project.revision, "saved");
    if (!silent) SetMapCreatorMessage("Đã lưu draft revision " + response.project.revision + ".");
    else if (fromAutosave) SetMapCreatorMessage("Autosave hoàn tất revision " + response.project.revision + ".");
    return true;
  } catch (e2) {
    SetMapCreatorMessage(e2.message);
    return false;
  }
}

function CompileMapCreatorProject() {
  if (!mapCreatorCurrentId) {
    SetMapCreatorMessage("Chưa chọn project để compile.");
    return;
  }
  if (!SaveMapCreatorProject(true)) return;
  SetMapCreatorMessage("Đang validate, compile binary, manifest và render preview...");
  try {
    var response = CallMapCreator("compilemapproject", { ProjectId: mapCreatorCurrentId });
    ApplyMapCreatorDetail(response);
    ShowMapCreatorMode("preview");
    RenderMapCreatorProjects();
    var validation = response.validation || { summary: { errors: 0, warnings: 0 }, issues: [] };
    var summary = validation.summary || {};
    var text = "Compile thành công: " + (summary.errors || 0) + " lỗi, " + (summary.warnings || 0) + " cảnh báo.";
    var issues = validation.issues || [];
    for (var i = 0; i < issues.length && i < 8; i++) {
      text += "\r\n" + issues[i].severity.toUpperCase() + " " + issues[i].code + " @ " + issues[i].path + ": " + issues[i].message;
    }
    SetMapCreatorMessage(text);
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function MapCreatorFileUrl(path) {
  if (!path) return "";
  var normalized = ("" + path).replace(/\\/g, "/");
  if (/^[A-Za-z]:\//.test(normalized)) normalized = "/" + normalized;
  return encodeURI("file://" + normalized);
}

function UpdateMapCreatorPreview() {
  var image = document.getElementById("mapCreatorPreviewImage");
  var empty = document.getElementById("mapCreatorPreviewEmpty");
  var build = mapCreatorCurrentDetail ? mapCreatorCurrentDetail.build : null;
  var path = "";
  if (build) path = mapCreatorPreviewMode == "clean" ? build.previewCleanPath : build.previewDebugPath;
  if (path) {
    image.style.display = "none";
    image.onload = function () {
      image.style.display = "block";
      empty.style.display = "none";
    };
    image.onerror = function () {
      image.style.display = "none";
      empty.style.display = "block";
      empty.innerText = "Không tải được preview: " + path;
    };
    image.src = MapCreatorFileUrl(path) + "?v=" + (new Date().getTime());
  } else {
    image.removeAttribute("src");
    image.style.display = "none";
    empty.style.display = "block";
    empty.innerText = "Compile project để tạo preview 24px/tile.";
  }
}

function SetMapCreatorPreviewMode(mode) {
  mapCreatorPreviewMode = mode == "clean" ? "clean" : "debug";
  document.getElementById("mapCreatorCleanButton").className = mapCreatorPreviewMode == "clean" ? "compact primary" : "compact";
  document.getElementById("mapCreatorDebugButton").className = mapCreatorPreviewMode == "debug" ? "compact primary" : "compact";
  UpdateMapCreatorPreview();
}

function ShowMapCreatorMode(mode) {
  mode = mode == "json" || mode == "preview" || mode == "releases" || mode == "acceptance" || mode == "guide" ? mode : "canvas";
  if (mode == "canvas" && mapCreatorJsonChanged) {
    if (!SaveMapCreatorProject(true)) return;
  }
  mapCreatorMode = mode;
  document.getElementById("mapCanvasMode").className = mode == "canvas" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapJsonMode").className = mode == "json" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapPreviewMode").className = mode == "preview" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapReleaseMode").className = mode == "releases" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapAcceptanceMode").className = mode == "acceptance" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapGuideMode").className = mode == "guide" ? "map-editor-mode active" : "map-editor-mode";
  document.getElementById("mapModeCanvasButton").className = mode == "canvas" ? "compact primary" : "compact";
  document.getElementById("mapModeJsonButton").className = mode == "json" ? "compact primary" : "compact";
  document.getElementById("mapModePreviewButton").className = mode == "preview" ? "compact primary" : "compact";
  document.getElementById("mapModeReleasesButton").className = mode == "releases" ? "compact primary" : "compact";
  document.getElementById("mapModeAcceptanceButton").className = mode == "acceptance" ? "compact primary" : "compact";
  document.getElementById("mapModeGuideButton").className = mode == "guide" ? "compact primary" : "compact";
  if (mode == "canvas") {
    ResizeMapCanvasCss();
    RenderMapCanvas();
  }
  if (mode == "preview") UpdateMapCreatorPreview();
  if (mode == "releases") LoadMapCreatorReleases();
  if (mode == "acceptance") {
    LoadMapCreatorReleases();
    LoadMapCreatorAcceptance();
  }
}

function LoadMapCanvasState() {
  if (!mapCreatorCurrentId) return;
  try {
    mapEditorState = CallMapCreator("getmapeditorstate", { ProjectId: mapCreatorCurrentId });
    mapCanvasMatrix = [];
    for (var row = 0; row < mapEditorState.matrix.length; row++) mapCanvasMatrix.push(mapEditorState.matrix[row].slice(0));
    mapCanvasSelection = null;
    mapCanvasImageCache = {};
    mapCanvasTileImageCache = {};
    InitMapCanvasEvents();
    ConfigureMapCanvas();
    RenderMapTilePalette();
    RenderMapAssetPalette();
    var visualStatus = document.getElementById("mapTileVisualStatus");
    if (visualStatus) visualStatus.innerText = mapEditorState.tilePalette.visualSource == "client-resource" ? "tile client thật x2" : "màu fallback (thiếu PNG client)";
  } catch (e) {
    mapEditorState = null;
    SetMapCreatorMessage("Không mở được canvas: " + e.message + ". Bạn vẫn có thể sửa JSON.");
  }
}

function ConfigureMapCanvas() {
  if (!mapCreatorPlan || !mapCreatorPlan.map) return;
  var canvas = document.getElementById("mapCreatorCanvas");
  canvas.width = mapCreatorPlan.map.widthTiles * 24;
  canvas.height = mapCreatorPlan.map.heightTiles * 24;
  canvas.style.display = "block";
  document.getElementById("mapCanvasEmpty").style.display = "none";
  Set("mapResizeWidth", mapCreatorPlan.map.widthTiles);
  Set("mapResizeHeight", mapCreatorPlan.map.heightTiles);
  ResizeMapCanvasCss();
  RenderMapCanvas();
}

function ResizeMapCanvasCss() {
  var canvas = document.getElementById("mapCreatorCanvas");
  if (!canvas || !mapCreatorPlan) return;
  var zoom = parseFloat(V("mapCanvasZoom")) || 0.5;
  canvas.style.width = Math.max(1, Math.round(canvas.width * zoom)) + "px";
  canvas.style.height = Math.max(1, Math.round(canvas.height * zoom)) + "px";
}

function MapTileColor(tileId) {
  if (tileId === 0) return "rgba(0,0,0,0)";
  return "hsl(" + ((tileId * 47) % 360) + ",62%,48%)";
}

function GetMapTileMeta(tileId) {
  var tiles = mapEditorState && mapEditorState.tilePalette ? mapEditorState.tilePalette.tiles || [] : [];
  for (var i = 0; i < tiles.length; i++) if (tiles[i].tileId == tileId) return tiles[i];
  return null;
}

function GetMapTileImage(tileId) {
  var tile = GetMapTileMeta(tileId);
  if (!tile || !tile.previewPath) return null;
  var key = (mapEditorState.tilePalette.tileSetId || 0) + ":" + tileId;
  if (!mapCanvasTileImageCache[key]) {
    var image = new Image();
    image.onload = function () { RenderMapCanvas(); };
    image.src = MapCreatorFileUrl(tile.previewPath);
    mapCanvasTileImageCache[key] = image;
  }
  return mapCanvasTileImageCache[key];
}

function GetMapAsset(templateId) {
  var assets = mapEditorState ? mapEditorState.assets || [] : [];
  for (var i = 0; i < assets.length; i++) if (assets[i].templateId == templateId) return assets[i];
  return null;
}

function GetMapAssetImage(asset) {
  if (!asset) return null;
  var key = "" + asset.templateId;
  if (!mapCanvasImageCache[key]) {
    var image = new Image();
    image.onload = function () { RenderMapCanvas(); };
    image.src = MapCreatorFileUrl(asset.previewPath);
    mapCanvasImageCache[key] = image;
  }
  return mapCanvasImageCache[key];
}

function RegisterMapAssetPaletteImage(image, templateId) {
  if (!image) return;
  mapCanvasImageCache["" + templateId] = image;
}

function GetMapImageWidth(image) {
  return image ? image.naturalWidth || image.width || 0 : 0;
}

function GetMapImageHeight(image) {
  return image ? image.naturalHeight || image.height || 0 : 0;
}

function IsMapLayerVisible(layer) {
  var checkbox = document.getElementById("mapLayer" + layer + "Visible");
  return !checkbox || checkbox.checked;
}

function DrawMapAssetLayer(ctx, layer) {
  if (!mapCreatorPlan || !IsMapLayerVisible(layer)) return;
  var items = mapCreatorPlan.bgItems || [];
  for (var i = 0; i < items.length; i++) {
    var asset = GetMapAsset(items[i].templateId);
    if (!asset || asset.layer != layer) continue;
    var image = GetMapAssetImage(asset);
    var x = GetMapAssetPixelX(items[i], asset);
    var y = GetMapAssetPixelY(items[i], asset);
    if (image && image.complete && GetMapImageWidth(image)) {
      ctx.drawImage(image, x, y);
    } else {
      ctx.fillStyle = "rgba(255,0,255,0.5)";
      ctx.fillRect(x, y, 24, 24);
    }
    if (mapCanvasSelection && mapCanvasSelection.kind == "asset" && mapCanvasSelection.index == i) {
      ctx.strokeStyle = "#facc15";
      ctx.lineWidth = 2;
      ctx.strokeRect(x - 2, y - 2, (GetMapImageWidth(image) || 24) + 4, (GetMapImageHeight(image) || 24) + 4);
    }
  }
  if (mapCanvasDropPreview) {
    var previewAsset = GetMapAsset(mapCanvasDropPreview.templateId);
    if (previewAsset && previewAsset.layer == layer) {
      var previewImage = GetMapAssetImage(previewAsset);
      var previewWidth = GetMapImageWidth(previewImage) || 24;
      var previewHeight = GetMapImageHeight(previewImage) || 24;
      ctx.save();
      ctx.globalAlpha = 0.72;
      if (previewImage && previewImage.complete && GetMapImageWidth(previewImage)) ctx.drawImage(previewImage, mapCanvasDropPreview.x, mapCanvasDropPreview.y);
      else { ctx.fillStyle = "rgba(255,0,255,0.5)"; ctx.fillRect(mapCanvasDropPreview.x, mapCanvasDropPreview.y, previewWidth, previewHeight); }
      ctx.globalAlpha = 1;
      ctx.strokeStyle = "#38bdf8";
      ctx.lineWidth = 2;
      if (ctx.setLineDash) ctx.setLineDash([6, 4]);
      ctx.strokeRect(mapCanvasDropPreview.x - 1, mapCanvasDropPreview.y - 1, previewWidth + 2, previewHeight + 2);
      ctx.restore();
    }
  }
}

function DrawMapEntities(ctx) {
  if (!mapCreatorPlan || !document.getElementById("mapEntityLayerVisible").checked) return;
  var gameplay = mapCreatorPlan.gameplay || {};
  var waypoints = gameplay.waypoints || [];
  for (var w = 0; w < waypoints.length; w++) {
    var wp = waypoints[w];
    ctx.fillStyle = "rgba(168,85,247,0.22)";
    ctx.fillRect(wp.minX, wp.minY, wp.maxX - wp.minX, wp.maxY - wp.minY);
    ctx.strokeStyle = mapCanvasSelection && mapCanvasSelection.kind == "waypoint" && mapCanvasSelection.index == w ? "#facc15" : "#c084fc";
    ctx.lineWidth = 2;
    ctx.strokeRect(wp.minX, wp.minY, wp.maxX - wp.minX, wp.maxY - wp.minY);
  }
  var player = gameplay.playerSpawn;
  if (player) {
    ctx.fillStyle = "#22d3ee";
    ctx.beginPath(); ctx.arc(player.x, player.y - 10, 7, 0, Math.PI * 2, false); ctx.fill();
    if (mapCanvasSelection && mapCanvasSelection.kind == "player") { ctx.strokeStyle = "#facc15"; ctx.strokeRect(player.x - 10, player.y - 23, 20, 25); }
  }
  var mobs = gameplay.mobs || [];
  for (var m = 0; m < mobs.length; m++) {
    ctx.fillStyle = "#ef4444";
    ctx.beginPath(); ctx.moveTo(mobs[m].mobX, mobs[m].mobY - 20); ctx.lineTo(mobs[m].mobX - 9, mobs[m].mobY); ctx.lineTo(mobs[m].mobX + 9, mobs[m].mobY); ctx.closePath(); ctx.fill();
    if (mapCanvasSelection && mapCanvasSelection.kind == "mob" && mapCanvasSelection.index == m) { ctx.strokeStyle = "#facc15"; ctx.strokeRect(mobs[m].mobX - 12, mobs[m].mobY - 24, 24, 26); }
  }
  var npcs = gameplay.npcs || [];
  for (var n = 0; n < npcs.length; n++) {
    ctx.fillStyle = "#facc15";
    ctx.fillRect(npcs[n].npcX - 7, npcs[n].npcY - 19, 14, 19);
    if (mapCanvasSelection && mapCanvasSelection.kind == "npc" && mapCanvasSelection.index == n) { ctx.strokeStyle = "#ffffff"; ctx.strokeRect(npcs[n].npcX - 10, npcs[n].npcY - 23, 20, 25); }
  }
  if (mapCanvasWaypointStart && mapCanvasDragging && mapCanvasDragging.kind == "new-waypoint") {
    var end = mapCanvasDragging.current;
    ctx.strokeStyle = "#f0abfc"; ctx.lineWidth = 2;
    ctx.strokeRect(Math.min(mapCanvasWaypointStart.x, end.x), Math.min(mapCanvasWaypointStart.y, end.y), Math.abs(end.x - mapCanvasWaypointStart.x), Math.abs(end.y - mapCanvasWaypointStart.y));
  }
}

function RenderMapCanvas() {
  var canvas = document.getElementById("mapCreatorCanvas");
  if (!canvas || !mapCreatorPlan || !mapCanvasMatrix.length) return;
  var ctx = canvas.getContext("2d");
  var gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
  gradient.addColorStop(0, "#334155"); gradient.addColorStop(1, "#0f172a");
  ctx.fillStyle = gradient; ctx.fillRect(0, 0, canvas.width, canvas.height);
  DrawMapAssetLayer(ctx, 1); DrawMapAssetLayer(ctx, 2); DrawMapAssetLayer(ctx, 3);
  if (document.getElementById("mapTileLayerVisible").checked) {
    for (var y = 0; y < mapCanvasMatrix.length; y++) {
      for (var x = 0; x < mapCanvasMatrix[y].length; x++) {
        var tile = mapCanvasMatrix[y][x];
        if (!tile) continue;
        var tileImage = GetMapTileImage(tile);
        if (tileImage && tileImage.complete && tileImage.width) {
          ctx.drawImage(tileImage, x * 24, y * 24, 24, 24);
        } else {
          ctx.fillStyle = MapTileColor(tile); ctx.fillRect(x * 24, y * 24, 24, 24);
          ctx.fillStyle = "rgba(255,255,255,0.18)"; ctx.fillRect(x * 24, y * 24, 24, 2);
        }
      }
    }
  }
  DrawMapEntities(ctx);
  DrawMapAssetLayer(ctx, 4);
  if (document.getElementById("mapCanvasGridVisible").checked) {
    ctx.strokeStyle = "rgba(255,255,255,0.18)"; ctx.lineWidth = 1;
    for (var gx = 0; gx <= canvas.width; gx += 24) { ctx.beginPath(); ctx.moveTo(gx + 0.5, 0); ctx.lineTo(gx + 0.5, canvas.height); ctx.stroke(); }
    for (var gy = 0; gy <= canvas.height; gy += 24) { ctx.beginPath(); ctx.moveTo(0, gy + 0.5); ctx.lineTo(canvas.width, gy + 0.5); ctx.stroke(); }
  }
}

function RenderMapTilePalette() {
  var host = document.getElementById("mapTilePalette");
  if (!host || !mapEditorState) return;
  var palette = mapEditorState.tilePalette || {};
  var mode = V("mapTilePaletteMode") || "frequent";
  var tiles = mode == "all" ? palette.available || palette.all || [] : palette.frequent || [];
  var html = "";
  for (var i = 0; i < tiles.length; i++) {
    var meta = GetMapTileMeta(tiles[i]);
    var active = MapCanvasInt("mapCanvasTileId", 0) == tiles[i] ? " active" : "";
    html += '<button type="button" class="map-tile-swatch' + active + '" style="background:' + MapTileColor(tiles[i]) + '" onclick="PickMapCanvasTile(' + tiles[i] + ')" title="Tile ' + tiles[i] + '">' +
      (meta ? '<img src="' + HtmlAttr(MapCreatorFileUrl(meta.previewPath)) + '" alt="Tile ' + tiles[i] + '">' : '') + '<span>' + tiles[i] + '</span></button>';
  }
  if (!html) html = '<div class="map-help">Tileset này chưa có PNG client; canvas dùng màu fallback.</div>';
  host.innerHTML = html;
}

function PickMapCanvasTile(tileId) {
  Set("mapCanvasTileId", tileId);
  SetMapCanvasTool("tile");
  RenderMapTilePalette();
}

function RenderMapAssetPalette() {
  var host = document.getElementById("mapAssetPalette");
  if (!host || !mapEditorState) return;
  var layer = V("mapAssetLayerFilter") || "all";
  var search = Trim(V("mapAssetSearch")).toLowerCase();
  var assets = mapEditorState.assets || [];
  var html = "";
  var shown = 0;
  for (var i = 0; i < assets.length && shown < 120; i++) {
    var asset = assets[i];
    if (layer != "all" && "" + asset.layer != layer) continue;
    var haystack = (asset.templateId + " " + asset.imageId + " " + asset.name).toLowerCase();
    if (search && haystack.indexOf(search) < 0) continue;
    var active = asset.templateId == mapCanvasSelectedAssetId ? " active" : "";
    html += '<button type="button" draggable="true" class="map-asset-card' + active + '" onclick="PickMapCanvasAsset(' + asset.templateId + ')" ondragstart="BeginMapAssetDrag(event,' + asset.templateId + ')" ondragend="ClearMapCanvasDropPreview()">' +
      '<img src="' + HtmlAttr(MapCreatorFileUrl(asset.previewPath)) + '" alt="BG ' + asset.templateId + '" onload="RegisterMapAssetPaletteImage(this,' + asset.templateId + ')">' +
      '<span>' + Html("T" + asset.templateId + " / I" + asset.imageId + " / L" + asset.layer) + '</span></button>';
    shown++;
  }
  if (!shown) html = '<div class="map-empty-projects">Không có asset phù hợp.</div>';
  host.innerHTML = html;
}

function PickMapCanvasAsset(templateId) {
  mapCanvasSelectedAssetId = templateId;
  SetMapCanvasTool("asset");
  RenderMapAssetPalette();
}

function BeginMapAssetDrag(eventObject, templateId) {
  mapCanvasSelectedAssetId = templateId;
  var asset = GetMapAsset(templateId);
  var image = GetMapAssetImage(asset);
  var dragSource = eventObject ? eventObject.currentTarget || eventObject.srcElement : null;
  if (dragSource && dragSource.tagName && dragSource.tagName.toUpperCase() == "IMG") image = dragSource;
  else if (dragSource && dragSource.getElementsByTagName) {
    var dragImages = dragSource.getElementsByTagName("img");
    if (dragImages.length) image = dragImages[0];
  }
  if (eventObject && eventObject.dataTransfer) {
    eventObject.dataTransfer.setData("Text", "" + templateId);
    eventObject.dataTransfer.effectAllowed = "copy";
    if (image && image.complete && GetMapImageWidth(image) && eventObject.dataTransfer.setDragImage) {
      var zoom = parseFloat(V("mapCanvasZoom")) || 0.5;
      var ghost = document.createElement("canvas");
      ghost.width = Math.max(1, Math.round(GetMapImageWidth(image) * zoom));
      ghost.height = Math.max(1, Math.round(GetMapImageHeight(image) * zoom));
      ghost.style.position = "absolute";
      ghost.style.left = "-10000px";
      ghost.style.top = "-10000px";
      ghost.style.pointerEvents = "none";
      var ghostContext = ghost.getContext("2d");
      ghostContext.globalAlpha = 0.78;
      ghostContext.drawImage(image, 0, 0, ghost.width, ghost.height);
      document.body.appendChild(ghost);
      mapCanvasDragGhost = ghost;
      eventObject.dataTransfer.setDragImage(ghost, 0, 0);
    }
  }
}

function AllowMapCanvasDrop(eventObject) {
  if (eventObject && eventObject.preventDefault) eventObject.preventDefault();
  if (eventObject && eventObject.dataTransfer) eventObject.dataTransfer.dropEffect = "copy";
  if (mapCanvasSelectedAssetId >= 0 && mapCreatorPlan) {
    var point = GetMapCanvasPoint(eventObject);
    mapCanvasDropPreview = { templateId: mapCanvasSelectedAssetId, x: point.x, y: point.y };
    RenderMapCanvas();
  }
  return false;
}

function ClearMapCanvasDropPreview() {
  mapCanvasDropPreview = null;
  if (mapCanvasDragGhost && mapCanvasDragGhost.parentNode) mapCanvasDragGhost.parentNode.removeChild(mapCanvasDragGhost);
  mapCanvasDragGhost = null;
  RenderMapCanvas();
}

function LeaveMapCanvasDropPreview(eventObject) {
  var stage = document.getElementById("mapCanvasStage");
  var related = eventObject ? eventObject.relatedTarget || eventObject.toElement : null;
  if (stage && related && (related == stage || (stage.contains && stage.contains(related)))) return false;
  mapCanvasDropPreview = null;
  RenderMapCanvas();
  return false;
}

function DropMapCanvasAsset(eventObject) {
  if (eventObject && eventObject.preventDefault) eventObject.preventDefault();
  var templateId = mapCanvasSelectedAssetId;
  if (eventObject && eventObject.dataTransfer) {
    var transferred = parseInt(eventObject.dataTransfer.getData("Text"), 10);
    if (!isNaN(transferred)) templateId = transferred;
  }
  if (templateId < 0 || !mapCreatorPlan) return false;
  var point = GetMapCanvasPoint(eventObject);
  mapCanvasDropPreview = null;
  if (mapCanvasDragGhost && mapCanvasDragGhost.parentNode) mapCanvasDragGhost.parentNode.removeChild(mapCanvasDragGhost);
  mapCanvasDragGhost = null;
  AddMapCanvasAsset(templateId, point.x, point.y);
  return false;
}

function SetMapCanvasTool(tool) {
  mapCanvasTool = tool;
  var canvas = document.getElementById("mapCreatorCanvas");
  if (canvas) canvas.style.cursor = tool == "select" ? "move" : tool == "asset" ? "copy" : "crosshair";
  var tools = ["select", "tile", "erase", "asset", "player", "npc", "mob", "waypoint"];
  for (var i = 0; i < tools.length; i++) {
    var id = "mapTool" + tools[i].charAt(0).toUpperCase() + tools[i].substring(1);
    var button = document.getElementById(id);
    if (button) button.className = tools[i] == tool ? "compact primary" : "compact";
  }
}

function GetMapCanvasPoint(eventObject) {
  var canvas = document.getElementById("mapCreatorCanvas");
  var rect = canvas.getBoundingClientRect();
  var clientX = eventObject.clientX == null ? rect.left : eventObject.clientX;
  var clientY = eventObject.clientY == null ? rect.top : eventObject.clientY;
  return {
    x: Math.max(0, Math.min(canvas.width, Math.round((clientX - rect.left) * canvas.width / Math.max(1, rect.right - rect.left)))),
    y: Math.max(0, Math.min(canvas.height, Math.round((clientY - rect.top) * canvas.height / Math.max(1, rect.bottom - rect.top))))
  };
}

function MapCanvasInt(id, fallback) {
  var value = parseInt(V(id), 10);
  return isNaN(value) ? fallback : value;
}

function GetMapAssetPixelX(placement, asset) {
  return placement.tileX * 24 + asset.dx + (parseInt(placement.offsetX, 10) || 0);
}

function GetMapAssetPixelY(placement, asset) {
  return placement.tileY * 24 + asset.dy + (parseInt(placement.offsetY, 10) || 0);
}

function SetMapAssetPixelPosition(placement, asset, pixelX, pixelY) {
  pixelX = Math.round(pixelX);
  pixelY = Math.round(pixelY);
  var tileX = Math.floor((pixelX - asset.dx) / 24);
  var tileY = Math.floor((pixelY - asset.dy) / 24);
  var offsetX = pixelX - (tileX * 24 + asset.dx);
  var offsetY = pixelY - (tileY * 24 + asset.dy);
  placement.tileX = tileX;
  placement.tileY = tileY;
  if (offsetX) placement.offsetX = offsetX; else delete placement.offsetX;
  if (offsetY) placement.offsetY = offsetY; else delete placement.offsetY;
}

function PaintMapCanvasTile(point, erase) {
  var tileX = Math.min(mapCanvasMatrix[0].length - 1, Math.floor(point.x / 24));
  var tileY = Math.min(mapCanvasMatrix.length - 1, Math.floor(point.y / 24));
  var tileId = erase ? 0 : MapCanvasInt("mapCanvasTileId", 0);
  if (tileId < 0 || tileId > 127) { SetMapCreatorMessage("Tile ID phải nằm trong 0..127."); return; }
  if (mapCanvasMatrix[tileY][tileX] != tileId) {
    mapCanvasMatrix[tileY][tileX] = tileId;
    mapCanvasPointerChanged = true;
    RenderMapCanvas();
  }
}

function AddMapCanvasAsset(templateId, pixelX, pixelY) {
  var asset = GetMapAsset(templateId);
  if (!asset) { SetMapCreatorMessage("Không tìm thấy asset template " + templateId + "."); return; }
  if (!mapCreatorPlan.bgItems) mapCreatorPlan.bgItems = [];
  var placement = { templateId: templateId, tileX: 0, tileY: 0 };
  SetMapAssetPixelPosition(placement, asset, pixelX, pixelY);
  mapCreatorPlan.bgItems.push(placement);
  mapCanvasSelection = { kind: "asset", index: mapCreatorPlan.bgItems.length - 1 };
  CommitMapCanvasChange("Đã đặt asset T" + templateId + " vào Layer " + asset.layer + " tại pixel (" + pixelX + ", " + pixelY + ").");
}

function AddMapCanvasEntity(tool, point) {
  var x = Math.round(point.x);
  var y = Math.round(point.y / 24) * 24;
  var gameplay = mapCreatorPlan.gameplay;
  if (tool == "player") {
    gameplay.playerSpawn = { x: x, y: y };
    mapCanvasSelection = { kind: "player", index: -1 };
  } else if (tool == "npc") {
    if (!gameplay.npcs) gameplay.npcs = [];
    gameplay.npcs.push({ npcId: MapCanvasInt("mapCanvasNpcId", 6), npcX: x, npcY: y });
    mapCanvasSelection = { kind: "npc", index: gameplay.npcs.length - 1 };
  } else if (tool == "mob") {
    if (!gameplay.mobs) gameplay.mobs = [];
    gameplay.mobs.push({ mobTemp: MapCanvasInt("mapCanvasMobId", 1), mobLevel: MapCanvasInt("mapCanvasMobLevel", 1), mobHp: Math.max(1, MapCanvasInt("mapCanvasMobHp", 100)), mobX: x, mobY: y });
    mapCanvasSelection = { kind: "mob", index: gameplay.mobs.length - 1 };
  }
  CommitMapCanvasChange("Đã đặt " + tool + " tại pixel (" + x + ", " + y + ").");
}

function FindMapCanvasSelection(point) {
  var gameplay = mapCreatorPlan.gameplay;
  var items = mapCreatorPlan.bgItems || [];
  for (var i = items.length - 1; i >= 0; i--) {
    var asset = GetMapAsset(items[i].templateId);
    if (!asset) continue;
    var image = GetMapAssetImage(asset);
    var left = GetMapAssetPixelX(items[i], asset);
    var top = GetMapAssetPixelY(items[i], asset);
    var width = GetMapImageWidth(image) || 24;
    var height = GetMapImageHeight(image) || 24;
    if (point.x >= left && point.x <= left + width && point.y >= top && point.y <= top + height) return { kind: "asset", index: i, offsetX: point.x - left, offsetY: point.y - top };
  }
  var waypoints = gameplay.waypoints || [];
  for (var w = waypoints.length - 1; w >= 0; w--) if (point.x >= waypoints[w].minX && point.x <= waypoints[w].maxX && point.y >= waypoints[w].minY && point.y <= waypoints[w].maxY) return { kind: "waypoint", index: w, offsetX: point.x - waypoints[w].minX, offsetY: point.y - waypoints[w].minY };
  var npcs = gameplay.npcs || [];
  for (var n = npcs.length - 1; n >= 0; n--) if (Math.abs(point.x - npcs[n].npcX) <= 14 && point.y >= npcs[n].npcY - 28 && point.y <= npcs[n].npcY + 5) return { kind: "npc", index: n, offsetX: point.x - npcs[n].npcX, offsetY: point.y - npcs[n].npcY };
  var mobs = gameplay.mobs || [];
  for (var m = mobs.length - 1; m >= 0; m--) if (Math.abs(point.x - mobs[m].mobX) <= 15 && point.y >= mobs[m].mobY - 28 && point.y <= mobs[m].mobY + 5) return { kind: "mob", index: m, offsetX: point.x - mobs[m].mobX, offsetY: point.y - mobs[m].mobY };
  var player = gameplay.playerSpawn;
  if (player && Math.abs(point.x - player.x) <= 15 && point.y >= player.y - 28 && point.y <= player.y + 5) return { kind: "player", index: -1, offsetX: point.x - player.x, offsetY: point.y - player.y };
  return null;
}

function MoveMapCanvasSelection(selection, point) {
  var gameplay = mapCreatorPlan.gameplay;
  var canvas = document.getElementById("mapCreatorCanvas");
  if (selection.kind == "asset") {
    var placement = mapCreatorPlan.bgItems[selection.index];
    var asset = GetMapAsset(placement.templateId);
    SetMapAssetPixelPosition(placement, asset, point.x - selection.offsetX, point.y - selection.offsetY);
  } else if (selection.kind == "npc") {
    gameplay.npcs[selection.index].npcX = Math.max(0, Math.min(canvas.width, Math.round(point.x - selection.offsetX)));
    gameplay.npcs[selection.index].npcY = Math.max(0, Math.min(canvas.height, Math.round((point.y - selection.offsetY) / 24) * 24));
  } else if (selection.kind == "mob") {
    gameplay.mobs[selection.index].mobX = Math.max(0, Math.min(canvas.width, Math.round(point.x - selection.offsetX)));
    gameplay.mobs[selection.index].mobY = Math.max(0, Math.min(canvas.height, Math.round((point.y - selection.offsetY) / 24) * 24));
  } else if (selection.kind == "player") {
    gameplay.playerSpawn.x = Math.max(0, Math.min(canvas.width, Math.round(point.x - selection.offsetX)));
    gameplay.playerSpawn.y = Math.max(0, Math.min(canvas.height, Math.round((point.y - selection.offsetY) / 24) * 24));
  } else if (selection.kind == "waypoint") {
    var wp = gameplay.waypoints[selection.index];
    var width = wp.maxX - wp.minX;
    var height = wp.maxY - wp.minY;
    wp.minX = Math.max(0, Math.min(canvas.width - width, Math.round(point.x - selection.offsetX)));
    wp.minY = Math.max(0, Math.min(canvas.height - height, Math.round(point.y - selection.offsetY)));
    wp.maxX = wp.minX + width; wp.maxY = wp.minY + height;
  }
  RenderMapCanvas();
  mapCanvasPointerChanged = true;
  UpdateMapCanvasSelectionInfo();
}

function InitMapCanvasEvents() {
  if (mapCanvasEventsBound) return;
  var canvas = document.getElementById("mapCreatorCanvas");
  canvas.onmousedown = function (eventObject) {
    if (!mapCreatorPlan) return;
    var point = GetMapCanvasPoint(eventObject);
    mapCanvasPointerDown = true;
    mapCanvasPointerChanged = false;
    if (mapCanvasTool == "tile" || mapCanvasTool == "erase") PaintMapCanvasTile(point, mapCanvasTool == "erase");
    else if (mapCanvasTool == "asset") { AddMapCanvasAsset(mapCanvasSelectedAssetId, point.x, point.y); mapCanvasPointerDown = false; }
    else if (mapCanvasTool == "player" || mapCanvasTool == "npc" || mapCanvasTool == "mob") { AddMapCanvasEntity(mapCanvasTool, point); mapCanvasPointerDown = false; }
    else if (mapCanvasTool == "waypoint") { mapCanvasWaypointStart = point; mapCanvasDragging = { kind: "new-waypoint", current: point }; }
    else {
      mapCanvasSelection = FindMapCanvasSelection(point);
      mapCanvasDragging = mapCanvasSelection;
      UpdateMapCanvasSelectionInfo(); RenderMapCanvas();
    }
  };
  canvas.onmousemove = function (eventObject) {
    if (!mapCreatorPlan) return;
    var point = GetMapCanvasPoint(eventObject);
    document.getElementById("mapCanvasPointer").innerText = "x=" + point.x + ", y=" + point.y + " | tile " + Math.floor(point.x / 24) + "," + Math.floor(point.y / 24);
    if (!mapCanvasPointerDown) return;
    if (mapCanvasTool == "tile" || mapCanvasTool == "erase") PaintMapCanvasTile(point, mapCanvasTool == "erase");
    else if (mapCanvasDragging && mapCanvasDragging.kind == "new-waypoint") { mapCanvasDragging.current = point; RenderMapCanvas(); }
    else if (mapCanvasDragging) MoveMapCanvasSelection(mapCanvasDragging, point);
  };
  canvas.onmouseup = function (eventObject) {
    if (!mapCanvasPointerDown) return;
    var point = GetMapCanvasPoint(eventObject);
    if (mapCanvasDragging && mapCanvasDragging.kind == "new-waypoint" && mapCanvasWaypointStart) {
      var minX = Math.min(mapCanvasWaypointStart.x, point.x), minY = Math.min(mapCanvasWaypointStart.y, point.y);
      var maxX = Math.max(mapCanvasWaypointStart.x, point.x), maxY = Math.max(mapCanvasWaypointStart.y, point.y);
      if (maxX - minX >= 4 && maxY - minY >= 4) {
        if (!mapCreatorPlan.gameplay.waypoints) mapCreatorPlan.gameplay.waypoints = [];
        mapCreatorPlan.gameplay.waypoints.push({ name: V("mapCanvasWaypointName") || "Cổng mới", minX: minX, minY: minY, maxX: maxX, maxY: maxY, isEnter: false, isOffline: false, goMap: MapCanvasInt("mapCanvasGoMap", 0), goX: MapCanvasInt("mapCanvasGoX", 60), goY: MapCanvasInt("mapCanvasGoY", 432) });
        mapCanvasSelection = { kind: "waypoint", index: mapCreatorPlan.gameplay.waypoints.length - 1 };
        mapCanvasPointerChanged = true;
      }
    }
    mapCanvasPointerDown = false; mapCanvasWaypointStart = null; mapCanvasDragging = null;
    if (mapCanvasPointerChanged) CommitMapCanvasChange("Đã cập nhật canvas.");
    else RenderMapCanvas();
  };
  canvas.onmouseleave = function (eventObject) { if (mapCanvasPointerDown) canvas.onmouseup(eventObject); };
  mapCanvasEventsBound = true;
}

function CommitMapCanvasChange(message) {
  if (!mapCreatorPlan || !mapCanvasMatrix.length) return;
  var matrix = [];
  for (var row = 0; row < mapCanvasMatrix.length; row++) matrix.push(mapCanvasMatrix[row].slice(0));
  mapCreatorPlan.terrain = { matrix: matrix };
  Set("mapCreatorPlanJson", JSON.stringify(mapCreatorPlan, null, 2));
  mapCreatorJsonChanged = false;
  RecordMapCreatorHistory(message || "Cập nhật canvas");
  MarkMapCreatorDirty();
  UpdateMapCanvasSelectionInfo();
  RenderMapCanvas();
  if (message) SetMapCreatorMessage(message);
}

function UpdateMapCanvasSelectionInfo() {
  var target = document.getElementById("mapCanvasSelectionInfo");
  if (!target) return;
  if (!mapCanvasSelection) { target.innerText = "Chưa chọn đối tượng."; return; }
  var kind = mapCanvasSelection.kind;
  var object = null;
  if (kind == "asset") object = mapCreatorPlan.bgItems[mapCanvasSelection.index];
  else if (kind == "npc") object = mapCreatorPlan.gameplay.npcs[mapCanvasSelection.index];
  else if (kind == "mob") object = mapCreatorPlan.gameplay.mobs[mapCanvasSelection.index];
  else if (kind == "waypoint") object = mapCreatorPlan.gameplay.waypoints[mapCanvasSelection.index];
  else object = mapCreatorPlan.gameplay.playerSpawn;
  var positionText = "";
  if (kind == "asset" && object) {
    var asset = GetMapAsset(object.templateId);
    if (asset) positionText = "\r\nPixel X/Y: " + GetMapAssetPixelX(object, asset) + " / " + GetMapAssetPixelY(object, asset) + " (fine " + (object.offsetX || 0) + " / " + (object.offsetY || 0) + ")";
  }
  target.innerText = kind.toUpperCase() + (mapCanvasSelection.index >= 0 ? " #" + mapCanvasSelection.index : "") + positionText + "\r\n" + JSON.stringify(object);
}

function DeleteMapCanvasSelection() {
  if (!mapCanvasSelection || !mapCreatorPlan) { SetMapCreatorMessage("Chưa chọn asset/entity để xóa."); return; }
  var kind = mapCanvasSelection.kind;
  if (kind == "player") { SetMapCreatorMessage("Player spawn là bắt buộc; hãy kéo sang vị trí khác."); return; }
  if (kind == "asset") mapCreatorPlan.bgItems.splice(mapCanvasSelection.index, 1);
  else if (kind == "npc") mapCreatorPlan.gameplay.npcs.splice(mapCanvasSelection.index, 1);
  else if (kind == "mob") mapCreatorPlan.gameplay.mobs.splice(mapCanvasSelection.index, 1);
  else if (kind == "waypoint") mapCreatorPlan.gameplay.waypoints.splice(mapCanvasSelection.index, 1);
  mapCanvasSelection = null;
  CommitMapCanvasChange("Đã xóa đối tượng khỏi draft.");
}

function CopyMapCanvasSelection() {
  if (!mapCanvasSelection || !mapCreatorPlan) {
    SetMapCreatorMessage("Chưa chọn asset/entity để sao chép.");
    return;
  }
  var kind = mapCanvasSelection.kind;
  var object = null;
  if (kind == "asset") object = mapCreatorPlan.bgItems[mapCanvasSelection.index];
  else if (kind == "npc") object = mapCreatorPlan.gameplay.npcs[mapCanvasSelection.index];
  else if (kind == "mob") object = mapCreatorPlan.gameplay.mobs[mapCanvasSelection.index];
  else if (kind == "waypoint") object = mapCreatorPlan.gameplay.waypoints[mapCanvasSelection.index];
  if (!object || kind == "player") {
    SetMapCreatorMessage("Player spawn không được sao chép; hãy kéo player tới vị trí mới.");
    return;
  }
  mapCreatorClipboard = { kind: kind, object: JSON.parse(JSON.stringify(object)) };
  SetMapCreatorMessage("Đã sao chép " + kind + ". Nhấn Ctrl+V để dán lệch 1 ô.");
}

function PasteMapCanvasSelection() {
  if (!mapCreatorClipboard || !mapCreatorPlan) {
    SetMapCreatorMessage("Clipboard map đang trống.");
    return;
  }
  var kind = mapCreatorClipboard.kind;
  var object = JSON.parse(JSON.stringify(mapCreatorClipboard.object));
  var widthPixels = mapCreatorPlan.map.widthTiles * 24;
  var heightPixels = mapCreatorPlan.map.heightTiles * 24;
  if (kind == "asset") {
    if (!GetMapAsset(object.templateId)) { SetMapCreatorMessage("Project này không có asset T" + object.templateId + "."); return; }
    object.tileX += 1; object.tileY += 1;
    if (!mapCreatorPlan.bgItems) mapCreatorPlan.bgItems = [];
    mapCreatorPlan.bgItems.push(object);
    mapCanvasSelection = { kind: kind, index: mapCreatorPlan.bgItems.length - 1 };
  } else if (kind == "npc") {
    object.npcX = Math.min(widthPixels, object.npcX + 24); object.npcY = Math.min(heightPixels, object.npcY + 24);
    if (!mapCreatorPlan.gameplay.npcs) mapCreatorPlan.gameplay.npcs = [];
    mapCreatorPlan.gameplay.npcs.push(object);
    mapCanvasSelection = { kind: kind, index: mapCreatorPlan.gameplay.npcs.length - 1 };
  } else if (kind == "mob") {
    object.mobX = Math.min(widthPixels, object.mobX + 24); object.mobY = Math.min(heightPixels, object.mobY + 24);
    if (!mapCreatorPlan.gameplay.mobs) mapCreatorPlan.gameplay.mobs = [];
    mapCreatorPlan.gameplay.mobs.push(object);
    mapCanvasSelection = { kind: kind, index: mapCreatorPlan.gameplay.mobs.length - 1 };
  } else if (kind == "waypoint") {
    var wpWidth = object.maxX - object.minX, wpHeight = object.maxY - object.minY;
    object.minX = Math.min(Math.max(0, widthPixels - wpWidth), object.minX + 24);
    object.minY = Math.min(Math.max(0, heightPixels - wpHeight), object.minY + 24);
    object.maxX = object.minX + wpWidth; object.maxY = object.minY + wpHeight;
    if (!mapCreatorPlan.gameplay.waypoints) mapCreatorPlan.gameplay.waypoints = [];
    mapCreatorPlan.gameplay.waypoints.push(object);
    mapCanvasSelection = { kind: kind, index: mapCreatorPlan.gameplay.waypoints.length - 1 };
  } else {
    SetMapCreatorMessage("Loại clipboard không được hỗ trợ.");
    return;
  }
  mapCreatorClipboard.object = JSON.parse(JSON.stringify(object));
  CommitMapCanvasChange("Đã dán " + kind + " lệch 1 ô.");
}

function ResizeMapCreatorPlan() {
  if (!mapCreatorCurrentId || !mapCreatorPlan) { SetMapCreatorMessage("Chưa chọn project."); return; }
  var width = MapCanvasInt("mapResizeWidth", 0), height = MapCanvasInt("mapResizeHeight", 0);
  if (width < 1 || width > 127 || height < 1 || height > 127) {
    SetMapCreatorMessage("Kích thước map phải nằm trong 1..127 tile.");
    return;
  }
  if (width == mapCreatorPlan.map.widthTiles && height == mapCreatorPlan.map.heightTiles) {
    SetMapCreatorMessage("Kích thước map không thay đổi.");
    return;
  }
  var requestPath = NewAdminJsonRequestPath("admin_map_resize");
  try {
    WriteUtf8File(requestPath, JSON.stringify(mapCreatorPlan, null, 2));
    var response = CallMapCreator("resizemapprojectplan", {
      RequestPath: requestPath,
      WidthTiles: width,
      HeightTiles: height,
      Anchor: V("mapResizeAnchor") || "top-left"
    });
    var report = response.report || {};
    var warning = "Resize " + report.oldDimensions.widthTiles + "x" + report.oldDimensions.heightTiles + " → " + width + "x" + height +
      " | offset tile (" + report.offsetTiles.x + ", " + report.offsetTiles.y + ")" +
      "\r\nTile đặc bị cắt: " + (report.croppedNonEmptyTiles || 0) +
      " | entity bị clamp: " + (report.clampedEntities || []).length +
      " | asset bị clamp: " + (report.clampedBgItems || 0) +
      "\r\nÁp dụng vào draft trên màn hình? Bạn có thể Ctrl+Z để hoàn tác.";
    if (!window.confirm(warning)) return;
    mapCreatorPlan = response.plan;
    mapCanvasMatrix = CloneMapCanvasMatrix(response.matrix);
    mapCanvasSelection = null;
    ConfigureMapCanvas();
    CommitMapCanvasChange("Resize map thành " + width + "x" + height + " theo anchor " + report.anchor + ".");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function ToggleMapCreatorAutosave() {
  var toggle = document.getElementById("mapCreatorAutosave");
  if (!toggle || !toggle.checked) {
    if (mapCreatorAutosaveTimer) window.clearTimeout(mapCreatorAutosaveTimer);
    mapCreatorAutosaveTimer = null;
    SetMapCreatorAutosaveStatus("Autosave tắt", "");
    return;
  }
  SetMapCreatorAutosaveStatus("Autosave bật", "saved");
  ScheduleMapCreatorAutosave();
}

function HandleMapCreatorShortcut(eventObject) {
  eventObject = eventObject || window.event;
  var panel = document.getElementById("panelMapCreator");
  if (!panel || panel.className.indexOf("active") < 0 || !mapCreatorCurrentId) return;
  var target = eventObject.target || eventObject.srcElement;
  var tag = target && target.tagName ? target.tagName.toUpperCase() : "";
  var editing = tag == "INPUT" || tag == "TEXTAREA" || tag == "SELECT";
  var key = eventObject.keyCode || eventObject.which;
  if (eventObject.ctrlKey && key == 83) {
    SaveMapCreatorProject(false);
  } else if (mapCreatorMode == "canvas" && !editing && eventObject.ctrlKey && key == 90 && !eventObject.shiftKey) {
    UndoMapCreatorChange();
  } else if (mapCreatorMode == "canvas" && !editing && eventObject.ctrlKey && (key == 89 || (key == 90 && eventObject.shiftKey))) {
    RedoMapCreatorChange();
  } else if (mapCreatorMode == "canvas" && !editing && eventObject.ctrlKey && key == 67) {
    CopyMapCanvasSelection();
  } else if (mapCreatorMode == "canvas" && !editing && eventObject.ctrlKey && key == 86) {
    PasteMapCanvasSelection();
  } else if (mapCreatorMode == "canvas" && !editing && (key == 46 || key == 8)) {
    DeleteMapCanvasSelection();
  } else if (mapCreatorMode == "canvas" && !editing && mapCanvasSelection && mapCanvasSelection.kind == "asset" && key >= 37 && key <= 40) {
    var step = eventObject.shiftKey ? 24 : 1;
    NudgeMapCanvasAssetSelection(key == 37 ? -step : key == 39 ? step : 0, key == 38 ? -step : key == 40 ? step : 0);
  } else {
    return;
  }
  eventObject.returnValue = false;
  if (eventObject.preventDefault) eventObject.preventDefault();
}

function NudgeMapCanvasAssetSelection(deltaX, deltaY) {
  if (!mapCanvasSelection || mapCanvasSelection.kind != "asset") return;
  var placement = mapCreatorPlan.bgItems[mapCanvasSelection.index];
  var asset = placement ? GetMapAsset(placement.templateId) : null;
  if (!placement || !asset) return;
  SetMapAssetPixelPosition(placement, asset, GetMapAssetPixelX(placement, asset) + deltaX, GetMapAssetPixelY(placement, asset) + deltaY);
  CommitMapCanvasChange("Đã dịch asset " + deltaX + "px / " + deltaY + "px.");
}

function InitMapCreatorShortcuts() {
  if (mapCreatorShortcutsBound) return;
  if (document.addEventListener) document.addEventListener("keydown", HandleMapCreatorShortcut, false);
  else if (document.attachEvent) document.attachEvent("onkeydown", HandleMapCreatorShortcut);
  mapCreatorShortcutsBound = true;
}

function InspectMapAssetFile(fileInput) {
  if (!fileInput || !fileInput.value) return;
  mapAssetUploadPath = fileInput.value;
  var image = new Image();
  image.onload = function () {
    document.getElementById("mapAssetInspect").innerText = "PNG nguồn " + image.width + "x" + image.height + "px. Đây sẽ là chuẩn x4 nếu W/H để 0.";
  };
  image.onerror = function () { document.getElementById("mapAssetInspect").innerText = "Không đọc được preview PNG đã chọn."; };
  image.src = MapCreatorFileUrl(mapAssetUploadPath);
}

function UploadMapCreatorAsset() {
  if (!mapCreatorCurrentId) { SetMapCreatorMessage("Chưa chọn project."); return; }
  if (!mapAssetUploadPath) { SetMapCreatorMessage("Hãy chọn PNG chuẩn x4."); return; }
  SetMapCreatorMessage("Đang validate PNG và sinh tự động x4, x3, x2, x1 trong project staging...");
  try {
    var state = CallMapCreator("uploadmapprojectasset", {
      ProjectId: mapCreatorCurrentId,
      AssetPath: mapAssetUploadPath,
      Name: V("mapAssetName"),
      Layer: V("mapAssetLayer"),
      Dx: V("mapAssetDx"),
      Dy: V("mapAssetDy"),
      AssetWidth: V("mapAssetWidth") || "0",
      AssetHeight: V("mapAssetHeight") || "0"
    });
    mapEditorState = state;
    mapCanvasSelectedAssetId = state.uploadedAsset.templateId;
    mapCanvasImageCache = {};
    RenderMapAssetPalette(); RenderMapCanvas(); SetMapCanvasTool("asset");
    mapCreatorReleasePlanState = null;
    document.getElementById("mapCreatorStatusBadge").innerText = "draft";
    document.getElementById("mapCreatorStatusBadge").className = "map-status-badge draft";
    SetMapCreatorMessage("Đã tạo asset T" + state.uploadedAsset.templateId + " / image " + state.uploadedAsset.imageId + " đủ x1-x4. Kéo asset từ palette vào canvas.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

function LoadMapCreatorReleases() {
  var host = document.getElementById("mapCreatorReleaseList");
  if (!mapCreatorCurrentId) {
    if (host) host.innerHTML = '<div class="map-empty-projects">Chưa chọn project.</div>';
    return;
  }
  try {
    var response = CallMapCreator("listmapprojectreleases", { ProjectId: mapCreatorCurrentId });
    mapCreatorReleases = response.releases || [];
    RenderMapCreatorReleases();
    if (mapCreatorSelectedReleaseId) {
      var stillExists = false;
      for (var i = 0; i < mapCreatorReleases.length; i++) if (mapCreatorReleases[i].releaseId == mapCreatorSelectedReleaseId) stillExists = true;
      if (stillExists) SelectMapCreatorRelease(mapCreatorSelectedReleaseId);
      else mapCreatorSelectedReleaseId = "";
    }
    if (!mapCreatorSelectedReleaseId && mapCreatorReleases.length) SelectMapCreatorRelease(mapCreatorReleases[0].releaseId);
  } catch (e) {
    if (host) host.innerHTML = '<div class="map-empty-projects">' + Html(e.message) + '</div>';
    SetMapCreatorMessage(e.message);
  }
}

function RenderMapCreatorReleases() {
  var host = document.getElementById("mapCreatorReleaseList");
  if (!host) return;
  if (!mapCreatorReleases.length) {
    host.innerHTML = '<div class="map-empty-projects">Project chưa có release.</div>';
    document.getElementById("mapCreatorReleaseHeading").innerText = "Chưa có release";
    document.getElementById("mapCreatorReleaseVerification").innerText = "Compile và Publish để tạo release đầu tiên.";
    RenderMapCreatorQaReleaseOptions();
    return;
  }
  var html = "";
  for (var i = 0; i < mapCreatorReleases.length; i++) {
    var release = mapCreatorReleases[i];
    var active = release.releaseId == mapCreatorSelectedReleaseId ? " active" : "";
    html += '<button type="button" class="map-release-history-item' + active + '" onclick="SelectMapCreatorRelease(\'' + EscapeJs(release.releaseId) + '\')">' +
      '<strong>' + Html(release.releaseId) + '</strong><span class="map-release-state ' + HtmlAttr(release.status) + '">' + Html(release.status) + '</span>' +
      '<small>Map ' + Html(release.mapId) + ' | version ' + Html(release.mapVersion == null ? "-" : release.mapVersion) + ' | assets ' + Html(release.assetImageCount || 0) + ' | QA ' + Html(release.acceptanceStatus || "not-started") + '</small></button>';
  }
  host.innerHTML = html;
  RenderMapCreatorQaReleaseOptions();
}

function RenderMapCreatorQaReleaseOptions() {
  var select = document.getElementById("mapQaReleaseSelect");
  if (!select) return;
  var html = '<option value="">Chọn release</option>';
  for (var i = 0; i < mapCreatorReleases.length; i++) {
    var release = mapCreatorReleases[i];
    html += '<option value="' + HtmlAttr(release.releaseId) + '">' + Html("Map " + release.mapId + " | " + release.status + " | " + release.releaseId) + '</option>';
  }
  select.innerHTML = html;
  if (mapCreatorSelectedReleaseId) select.value = mapCreatorSelectedReleaseId;
}

function SelectMapCreatorRelease(releaseId) {
  if (!releaseId || !mapCreatorCurrentId) return;
  mapCreatorSelectedReleaseId = releaseId;
  RenderMapCreatorReleases();
  try {
    var response = CallMapCreator("getmapprojectrelease", { ProjectId: mapCreatorCurrentId, ReleaseId: releaseId });
    mapCreatorSelectedReleaseDetail = response;
    var release = response.release;
    document.getElementById("mapCreatorReleaseHeading").innerText = "Release " + release.releaseId + " — " + release.status;
    var lines = [
      "Map ID: " + release.mapId,
      "Tạo: " + (release.createdAt || "-"),
      "Publish: " + (release.publishedAt || "-"),
      "Rollback: " + (release.runtimeRolledBackAt || "-"),
      "Manifest: " + release.manifestSha256,
      "Map version publish: " + (release.mapVersion && release.mapVersion.publishedValue != null ? release.mapVersion.publishedValue : "-"),
      "Custom template: " + (release.customTemplateIds || []).join(", "),
      "Asset image: " + (release.assetImageIds || []).join(", "),
      "Nghiệm thu client: " + (response.summary.acceptanceStatus || "not-started"),
      "\r\nNhấn Verify Runtime + DB sau khi restart server để phát hiện drift."
    ];
    document.getElementById("mapCreatorReleaseVerification").innerText = lines.join("\r\n");
  } catch (e) {
    document.getElementById("mapCreatorReleaseVerification").innerText = e.message;
    SetMapCreatorMessage(e.message);
  }
}

function FormatMapCreatorReleaseVerification(result) {
  var lines = [
    "KẾT QUẢ: " + (result.verified ? "KHỚP RELEASE" : "PHÁT HIỆN DRIFT"),
    "Release: " + result.releaseId + " | trạng thái " + result.releaseStatus + " | server " + (result.serverRunning ? "đang chạy" : "đã dừng"),
    "Runtime: " + (result.runtime.verified ? "OK" : "DRIFT") + " | Database: " + (result.database.verified ? "OK" : "DRIFT"),
    "Map version: expected=" + result.runtime.mapVersion.expectedValue + " current=" + result.runtime.mapVersion.currentValue
  ];
  var files = result.runtime.files || [];
  for (var i = 0; i < files.length; i++) lines.push((files[i].matched ? "OK   " : "DRIFT") + " " + files[i].label + " -> " + files[i].target);
  var releaseFiles = result.runtime.releaseFiles || [];
  for (var p = 0; p < releaseFiles.length; p++) lines.push((releaseFiles[p].matched ? "OK   " : "DRIFT") + " package/" + releaseFiles[p].label);
  var runtimeIssues = result.runtime.issues || [];
  for (var r = 0; r < runtimeIssues.length; r++) lines.push("RUNTIME " + runtimeIssues[r].code + ": " + runtimeIssues[r].message);
  var databaseIssues = result.database.issues || [];
  for (var d = 0; d < databaseIssues.length; d++) lines.push("DATABASE " + databaseIssues[d].code + (databaseIssues[d].field ? " [" + databaseIssues[d].field + "]" : "") + ": " + databaseIssues[d].message);
  return lines.join("\r\n");
}

function VerifySelectedMapCreatorRelease() {
  if (!mapCreatorSelectedReleaseId) { SetMapCreatorMessage("Chưa chọn release để verify."); return; }
  SetMapCreatorMessage("Đang đối chiếu checksum runtime, map version và database...");
  try {
    var result = CallMapCreator("verifymapprojectrelease", { ProjectId: mapCreatorCurrentId, ReleaseId: mapCreatorSelectedReleaseId });
    document.getElementById("mapCreatorReleaseVerification").innerText = FormatMapCreatorReleaseVerification(result);
    SetMapCreatorMessage(result.verified ? "Runtime và database khớp release." : "Phát hiện drift; xem chi tiết và không rollback mù.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
    document.getElementById("mapCreatorReleaseVerification").innerText = e.message;
  }
}

function RollbackSelectedMapCreatorRelease() {
  if (!mapCreatorSelectedReleaseId || !mapCreatorSelectedReleaseDetail) { SetMapCreatorMessage("Chưa chọn release."); return; }
  var release = mapCreatorSelectedReleaseDetail.release;
  if (release.status != "published") { SetMapCreatorMessage("Chỉ rollback được release published mới nhất."); return; }
  var message = "Rollback release " + release.releaseId + " của Map " + release.mapId + "?\r\n\r\n" +
    "Server phải dừng. Hệ thống sẽ verify chống drift, rollback database và runtime/version về snapshot trước publish.";
  if (!window.confirm(message)) return;
  SetMapCreatorMessage("Đang verify và rollback release có compensation...");
  try {
    var result = CallMapCreator("rollbackmapprojectrelease", { ProjectId: mapCreatorCurrentId, ReleaseId: mapCreatorSelectedReleaseId });
    ApplyMapCreatorDetail(CallMapCreator("getmapproject", { ProjectId: mapCreatorCurrentId }));
    LoadMapCreatorReleases();
    document.getElementById("mapCreatorReleaseVerification").innerText = FormatMapCreatorReleaseVerification(result.verification);
    SetMapCreatorMessage("Rollback hoàn tất và đã verify trạng thái runtime/database trước publish.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
    document.getElementById("mapCreatorReleaseVerification").innerText = e.message;
  }
}

function SelectMapCreatorQaRelease(releaseId) {
  if (!releaseId) {
    mapCreatorSelectedReleaseId = "";
    mapCreatorAcceptance = null;
    RenderMapCreatorAcceptanceEmpty("Chưa chọn release để nghiệm thu.");
    return;
  }
  SelectMapCreatorRelease(releaseId);
  LoadMapCreatorAcceptance();
}

function RenderMapCreatorAcceptanceEmpty(message) {
  document.getElementById("mapQaHeading").innerText = "Chưa có dữ liệu nghiệm thu";
  var badge = document.getElementById("mapQaReadyBadge");
  badge.innerText = "CHƯA SẴN SÀNG";
  badge.className = "map-qa-ready pending";
  document.getElementById("mapQaAutomaticStatus").innerText = "Chưa chạy";
  document.getElementById("mapQaManualStatus").innerText = "Chưa kiểm tra";
  document.getElementById("mapQaAutomaticChecks").innerHTML = '<div class="map-help">' + Html(message || "Chọn release để kiểm tra.") + '</div>';
}

function LoadMapCreatorAcceptance() {
  if (!mapCreatorCurrentId) {
    RenderMapCreatorAcceptanceEmpty("Chưa chọn project.");
    return;
  }
  if (!mapCreatorReleases.length) LoadMapCreatorReleases();
  if (!mapCreatorSelectedReleaseId) {
    for (var i = 0; i < mapCreatorReleases.length; i++) {
      if (mapCreatorReleases[i].status == "published") { mapCreatorSelectedReleaseId = mapCreatorReleases[i].releaseId; break; }
    }
    if (!mapCreatorSelectedReleaseId && mapCreatorReleases.length) mapCreatorSelectedReleaseId = mapCreatorReleases[0].releaseId;
  }
  if (!mapCreatorSelectedReleaseId) {
    RenderMapCreatorAcceptanceEmpty("Project chưa có release. Hãy Compile và Publish trước.");
    return;
  }
  RenderMapCreatorQaReleaseOptions();
  SetMapCreatorMessage("Đang kiểm tra server, cổng, release, runtime và database...");
  try {
    mapCreatorAcceptance = CallMapCreator("getmapprojectacceptance", { ProjectId: mapCreatorCurrentId, ReleaseId: mapCreatorSelectedReleaseId });
    RenderMapCreatorAcceptance(mapCreatorAcceptance);
    SetMapCreatorMessage(mapCreatorAcceptance.ready ? "Release đã sẵn sàng nghiệm thu production." : "Nghiệm thu chưa hoàn tất; xem các mục còn thiếu.");
  } catch (e) {
    mapCreatorAcceptance = null;
    RenderMapCreatorAcceptanceEmpty(e.message);
    SetMapCreatorMessage(e.message);
  }
}

function RenderMapCreatorAcceptance(result) {
  if (!result) return;
  document.getElementById("mapQaHeading").innerText = "Map " + result.mapId + " — " + result.releaseId;
  var badge = document.getElementById("mapQaReadyBadge");
  badge.innerText = result.ready ? "SẴN SÀNG NGHIỆM THU" : "CHƯA SẴN SÀNG";
  badge.className = result.ready ? "map-qa-ready ready" : "map-qa-ready pending";
  document.getElementById("mapQaAutomaticStatus").innerText = result.automaticPassed ? "ĐẠT" : "CHƯA ĐẠT";
  document.getElementById("mapQaManualStatus").innerText = result.manualComplete ? "ĐÃ XÁC NHẬN" : "CHƯA ĐỦ";
  var checksHtml = "";
  var automaticChecks = result.automaticChecks || [];
  for (var i = 0; i < automaticChecks.length; i++) {
    var automatic = automaticChecks[i];
    checksHtml += '<div class="map-qa-automatic-item' + (automatic.passed ? " ok" : "") + '"><b>' + (automatic.passed ? "OK" : "!") + '</b><span>' + Html(automatic.code + " — " + automatic.message) + '</span></div>';
  }
  document.getElementById("mapQaAutomaticChecks").innerHTML = checksHtml || '<div class="map-help">Không có kết quả tự động.</div>';
  var required = result.requiredChecks || [];
  var savedChecks = result.acceptance && result.acceptance.checks ? result.acceptance.checks : {};
  for (var c = 0; c < mapCreatorAcceptanceCheckKeys.length; c++) {
    var key = mapCreatorAcceptanceCheckKeys[c];
    var checkbox = document.getElementById(mapCreatorAcceptanceCheckIds[key]);
    if (checkbox) checkbox.checked = savedChecks[key] === true;
    var labels = document.getElementsByTagName("label");
    for (var l = 0; l < labels.length; l++) {
      if (labels[l].getAttribute("data-qa-key") == key) {
        var isRequired = required.indexOf(key) >= 0;
        labels[l].className = isRequired ? "map-qa-check" : "map-qa-check optional";
        var markers = labels[l].getElementsByTagName("b");
        if (markers.length) markers[0].innerText = isRequired ? "*" : "";
        break;
      }
    }
  }
  Set("mapQaTester", result.acceptance ? result.acceptance.tester || "" : "");
  Set("mapQaNotes", result.acceptance ? result.acceptance.notes || "" : "");
  var conditional = [];
  if (required.indexOf("waypoints") >= 0) conditional.push("Điểm chạm");
  if (required.indexOf("npcs") >= 0) conditional.push("NPC");
  if (required.indexOf("mobs") >= 0) conditional.push("Mob");
  document.getElementById("mapQaRequiredHint").innerText = "Mục có dấu * là bắt buộc" + (conditional.length ? "; release này có " + conditional.join(", ") + "." : "; release này không có Điểm chạm/NPC/Mob cần kiểm tra.");
}

function SaveMapCreatorAcceptance() {
  if (!mapCreatorCurrentId || !mapCreatorSelectedReleaseId) {
    SetMapCreatorMessage("Chưa chọn release để lưu nghiệm thu.");
    return;
  }
  var checks = {};
  for (var i = 0; i < mapCreatorAcceptanceCheckKeys.length; i++) {
    var key = mapCreatorAcceptanceCheckKeys[i];
    var checkbox = document.getElementById(mapCreatorAcceptanceCheckIds[key]);
    checks[key] = checkbox ? checkbox.checked === true : false;
  }
  var payload = { tester: Trim(V("mapQaTester")), notes: V("mapQaNotes"), checks: checks };
  var requestPath = NewAdminJsonRequestPath("admin_map_acceptance");
  SetMapCreatorMessage("Đang lưu checklist và tính lại trạng thái nghiệm thu...");
  try {
    WriteUtf8File(requestPath, JSON.stringify(payload));
    mapCreatorAcceptance = CallMapCreator("savemapprojectacceptance", {
      ProjectId: mapCreatorCurrentId,
      ReleaseId: mapCreatorSelectedReleaseId,
      RequestPath: requestPath
    });
    RenderMapCreatorAcceptance(mapCreatorAcceptance);
    LoadMapCreatorReleases();
    SetMapCreatorMessage(mapCreatorAcceptance.ready ? "Đã lưu: release sẵn sàng nghiệm thu production." : "Đã lưu checklist; vẫn còn kiểm tra tự động hoặc mục bắt buộc chưa đạt.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  } finally {
    try { if (fso.FileExists(requestPath)) fso.DeleteFile(requestPath, true); } catch (deleteError) {}
  }
}

function FormatMapCreatorReleasePlan(plan) {
  var text = "Publish ready: " + (plan.canPublish ? "CÓ" : "KHÔNG") +
    " | manifest: " + (plan.verified ? "verified" : "invalid") +
    " | server: " + (plan.serverRunning ? "đang chạy" : "đã dừng");
  var blockers = plan.blockers || [];
  for (var i = 0; i < blockers.length; i++) {
    text += "\r\nBLOCK " + blockers[i].code + ": " + blockers[i].message;
  }
  var operations = plan.operations || [];
  for (var j = 0; j < operations.length; j++) {
    text += "\r\n" + (j + 1) + ". " + operations[j].kind + " -> " + operations[j].target;
  }
  if (plan.assetScaling) text += "\r\nAssets: " + plan.assetScaling.status + " | templates=" + (plan.assetScaling.customTemplateCount || 0) + " | images=" + (plan.assetScaling.imageCount || 0);
  if (plan.dependencyChecks) text += "\r\nDependencies: " + (plan.dependencyChecks.verified ? "verified" : "missing") +
    " | maps=" + (plan.dependencyChecks.missingMapIds || []).join(",") +
    " | compiled-projects=" + (plan.dependencyChecks.compiledProjectMapIds || []).join(",") +
    " | npcs=" + (plan.dependencyChecks.missingNpcIds || []).join(",") + " | mobs=" + (plan.dependencyChecks.missingMobTemplateIds || []).join(",");
  text += "\r\nRollback: " + plan.rollback.strategy;
  return text;
}

function PlanMapCreatorRelease() {
  if (!mapCreatorCurrentId) {
    SetMapCreatorMessage("Chưa chọn project.");
    return false;
  }
  if (mapCreatorPlanDirty) {
    SetMapCreatorMessage("Draft có thay đổi chưa lưu. Hãy Compile & Preview trước khi kiểm tra publish.");
    return false;
  }
  SetMapCreatorMessage("Đang kiểm tra manifest, runtime target và trạng thái server...");
  try {
    mapCreatorReleasePlanState = CallMapCreator("releasemapprojectplan", { ProjectId: mapCreatorCurrentId });
    document.getElementById("mapCreatorReleasePlan").innerText = FormatMapCreatorReleasePlan(mapCreatorReleasePlanState);
    SetMapCreatorMessage(mapCreatorReleasePlanState.canPublish ? "Bundle hợp lệ và server đã dừng: có thể publish." : "Publish đang bị khóa; xem BLOCK trong khung kiểm tra.");
    return mapCreatorReleasePlanState.canPublish;
  } catch (e) {
    SetMapCreatorMessage(e.message);
    return false;
  }
}

function PublishMapCreatorProject() {
  if (!mapCreatorReleasePlanState || !mapCreatorReleasePlanState.canPublish) {
    if (!PlanMapCreatorRelease()) return;
  }
  var message = "Publish Map " + mapCreatorReleasePlanState.mapId + "?\r\n\r\n" +
    "Thao tác sẽ snapshot dữ liệu cũ, ghi tile/background + asset x1-x4, UPSERT map_template/bg_item_template và tăng data/map/version.txt. Server phải đang dừng.";
  if (!window.confirm(message)) return;
  SetMapCreatorMessage("Đang publish transaction map và tạo rollback metadata...");
  try {
    var result = CallMapCreator("publishmapproject", { ProjectId: mapCreatorCurrentId });
    mapCreatorReleasePlanState = null;
    SelectMapCreatorProject(mapCreatorCurrentId);
    LoadMapCreatorProjects();
    LoadMapCreatorReleases();
    var release = result.release || {};
    SetMapCreatorMessage("Publish thành công. Release " + (release.releaseId || "-") + ". Restart server để nạp map đúng như preview.");
  } catch (e) {
    SetMapCreatorMessage(e.message);
  }
}

RegisterTab({
  id: "mapcreator",
  view: "map-creator.html",
  panelId: "panelMapCreator",
  navId: "navMapCreator",
  title: "Tạo Map",
  subtitle: "Nghiệm thu sau restart, checklist trong client và hướng dẫn thao tác đầy đủ",
  onOpen: function () {
    InitMapCreatorShortcuts();
    LoadMapCreatorProjects();
  },
  onRefresh: function () {
    LoadMapCreatorProjects();
  }
});
