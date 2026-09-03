var npcCreatorRows = [];
var npcCreatorMapList = [];
var npcCreatorFilePaths = {
  fullbody: "",
  head: "",
  body: "",
  leg: "",
  avatar: ""
};

var npcCreatorOrigSizes = {
  fullbody: { w: 0, h: 0 },
  head: { w: 0, h: 0 },
  body: { w: 0, h: 0 },
  leg: { w: 0, h: 0 },
  avatar: { w: 0, h: 0 }
};

var npcCreatorScaleSizes = {
  fullbody: { iconId: 0 },
  head: { iconId: 0 },
  body: { iconId: 0 },
  leg: { iconId: 0 }
};

function BuildNpcScaleSizes(detail, prefix, iconId) {
  return {
    iconId: parseInt(iconId, 10) || 0,
    1: { w: parseInt(detail[prefix + "W1"], 10) || 0, h: parseInt(detail[prefix + "H1"], 10) || 0 },
    2: { w: parseInt(detail[prefix + "W2"], 10) || 0, h: parseInt(detail[prefix + "H2"], 10) || 0 },
    3: { w: parseInt(detail[prefix + "W3"], 10) || 0, h: parseInt(detail[prefix + "H3"], 10) || 0 },
    4: { w: parseInt(detail[prefix + "W"], 10) || 0, h: parseInt(detail[prefix + "H"], 10) || 0 }
  };
}

// Các tọa độ gốc này trùng với frame đứng mà client NRO dùng khi vẽ nhân vật.
// Offset trong bảng part luôn là đơn vị x1; preview nhân theo mức zoom đang chọn.
var NPC_CREATOR_STAND_FRAMES = {
  0: {
    head: { x: -13, y: 34 },
    body: { x: -9, y: 16 },
    leg: { x: -8, y: 10 }
  },
  1: {
    head: { x: -13, y: 35 },
    body: { x: -9, y: 17 },
    leg: { x: -8, y: 10 }
  }
};

// Client gốc dùng Char.ch = 32 và vẽ đáy tên tại cy - ch - 8 khi NPC không
// được focus. NameLift dịch cy của NPC lên; backend bù lại dy của các part để
// hình không đổi chỗ, nhưng tên và bóng vẫn cùng dịch lên theo cy.
var NPC_CREATOR_NATIVE_CH = 32;
var NPC_CREATOR_NATIVE_NAME_MARGIN = 8;

function NormalizeNpcX4Dimension(value) {
  var number = parseInt(value, 10) || 0;
  if (number <= 0) return 0;
  return Math.max(4, Math.round(number / 4) * 4);
}

function GetNpcPreviewZoom() {
  var zoom = parseInt(V("npcCreatorPreviewZoom"), 10) || 4;
  return Math.max(1, Math.min(4, zoom));
}

function GetNpcPreviewDirection() {
  return V("npcCreatorPreviewDirection") === "left" ? -1 : 1;
}

function ChangeNpcPreviewRuntime() {
  UpdateNpcCreatorPreview();
}

function SwitchNpcCreatorMode(mode) {
  var isFullBody = (mode === "fullbody");
  Set("npcCreatorMode", isFullBody ? "fullbody" : "multipart");

  var btnFb = document.getElementById("btnNpcModeFullBody");
  var btnMp = document.getElementById("btnNpcModeMultipart");
  if (btnFb && btnMp) {
    btnFb.className = isFullBody ? "npc-mode-tab active" : "npc-mode-tab";
    btnMp.className = !isFullBody ? "npc-mode-tab active" : "npc-mode-tab";
  }

  var formFb = document.getElementById("npcFullBodyFormWrap");
  var formMp = document.getElementById("npcMultipartFormWrap");
  if (formFb) formFb.style.display = isFullBody ? "block" : "none";
  if (formMp) formMp.style.display = !isFullBody ? "block" : "none";

  var scaleRow = document.getElementById("npcScaleRowWrap");
  var btnAutoSnap = document.getElementById("btnAutoSnap");
  var btnPresetAll = document.getElementById("btnPresetAll");
  var btnAutoCenter = document.getElementById("btnAutoCenter");
  var btnGroundLegs = document.getElementById("btnGroundLegs");
  if (scaleRow) scaleRow.style.display = isFullBody ? "none" : "flex";
  if (btnAutoSnap) btnAutoSnap.style.display = isFullBody ? "none" : "inline-block";
  if (btnPresetAll) btnPresetAll.style.display = isFullBody ? "none" : "inline-block";
  if (btnAutoCenter) btnAutoCenter.style.display = isFullBody ? "none" : "inline-block";
  if (btnGroundLegs) btnGroundLegs.style.display = isFullBody ? "none" : "inline-block";
  var layerOrder = document.getElementById("npcCreatorLayerOrder");
  if (layerOrder) layerOrder.disabled = isFullBody;

  if (!isFullBody) {
    var hW = parseInt(V("npcCreatorHeadW"), 10) || 0;
    var hDx = parseInt(V("npcCreatorHeadDx"), 10) || 0;
    var bDx = parseInt(V("npcCreatorBodyDx"), 10) || 0;
    var lDx = parseInt(V("npcCreatorLegDx"), 10) || 0;
    if (hW > 0 && hDx === 0 && bDx === 0 && lDx === 0) {
      AutoSnapJoints();
      return;
    }
  }

  UpdateNpcCreatorPreview();
}

function SetFullBodyPreset(targetH) {
  targetH = NormalizeNpcX4Dimension(targetH);
  var orig = npcCreatorOrigSizes.fullbody;
  var targetW = 120;
  if (orig.w > 0 && orig.h > 0) {
    targetW = NormalizeNpcX4Dimension(targetH * orig.w / orig.h);
  } else {
    targetW = NormalizeNpcX4Dimension(targetH * 0.7);
  }
  Set("npcCreatorFullBodyW", targetW);
  Set("npcCreatorFullBodyH", targetH);

  var targetH1 = Math.round(targetH / 4);
  var suggestedLift = Math.max(0, targetH1 - 36);
  Set("npcCreatorNameLift", suggestedLift);

  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã gán kích thước ảnh toàn thân: " + targetW + "x" + targetH + "px" + (suggestedLift > 0 ? " • Tự động gợi ý NameLift=" + suggestedLift + "px" : "") + ".");
}

function ResetFullBodyOffset() {
  Set("npcCreatorFullBodyDy", "0");
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã đưa đáy ảnh toàn thân chạm khít mặt đất.");
}

function ResetFullBodyDx() {
  Set("npcCreatorFullBodyDx", "0");
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã đưa tâm ảnh toàn thân về chính giữa bóng.");
}

function LoadNpcCreatorList() {
  EnsureNpcCreatorMaps();
  npcCreatorRows = ParseTsv(RunAdmin("listnpccreator", {}));
  RenderNpcCreatorTable(npcCreatorRows);
  Msg("npcCreatorMessage", npcCreatorRows.length > 1 ? "Đã tải " + (npcCreatorRows.length - 1) + " NPC." : "Không có NPC nào.");
}

function RenderNpcCreatorTable(rows) {
  var search = (document.getElementById("npcCreatorSearch").value || "").toLowerCase();
  var html = "<thead><tr><th>ID</th><th>Tên NPC</th><th>Map</th><th>X, Y</th><th>Shop</th></tr></thead><tbody>";
  var count = 0;
  for (var i = 1; i < rows.length; i++) {
    var r = rows[i];
    var id = r[0];
    var name = r[1];
    var mapId = r[6];
    var mapName = r[7];
    var posX = r[8];
    var posY = r[9];
    var hasShop = r[10];

    if (search.length > 0) {
      var textMatch = (id + " " + name + " " + mapName).toLowerCase();
      if (textMatch.indexOf(search) < 0) continue;
    }

    var mapDisplay = mapId ? (mapId + " - " + mapName) : '<span style="color:#666;">Chưa đặt</span>';
    var posDisplay = posX ? (posX + ", " + posY) : "-";
    var shopDisplay = (hasShop == "1") ? '<span style="color:#4caf50;">Có</span>' : '<span style="color:#888;">Không</span>';

    html += '<tr onclick="PickNpcCreator(' + i + ')" style="cursor:pointer;">' +
      '<td>' + Html(id) + '</td>' +
      '<td><b>' + Html(name) + '</b></td>' +
      '<td>' + mapDisplay + '</td>' +
      '<td>' + posDisplay + '</td>' +
      '<td>' + shopDisplay + '</td>' +
      '</tr>';
    count++;
  }
  document.getElementById("npcCreatorTable").innerHTML = html + "</tbody>";
}

function FilterNpcCreatorList() {
  RenderNpcCreatorTable(npcCreatorRows);
}

function EnsureNpcCreatorMaps() {
  if (npcCreatorMapList.length > 0) return;
  var rows = ParseTsv(RunAdmin("listmapcatalog", {}));
  npcCreatorMapList = rows;
  var sel = document.getElementById("npcCreatorMapSelect");
  if (!sel) return;
  sel.innerHTML = '<option value="-1">-- Không đặt vào Map --</option>';
  for (var i = 1; i < rows.length; i++) {
    var r = rows[i];
    var opt = document.createElement("option");
    opt.value = r[0];
    opt.text = r[0] + " - " + r[1];
    sel.appendChild(opt);
  }
}

var NPC_MAP_GROUND_DEFAULTS = {
  0: { x: 514, y: 432 },   // Làng Aru
  1: { x: 468, y: 312 },   // Đồi hoa cúc
  2: { x: 400, y: 312 },   // Thung lũng tre
  5: { x: 328, y: 408 },   // Đảo Kamê
  7: { x: 300, y: 432 },   // Làng Mori
  14: { x: 396, y: 408 },  // Làng Kakarot
  19: { x: 1140, y: 360 }, // Thành phố Vegeta
  21: { x: 228, y: 336 },  // Nhà Gôhan
  22: { x: 516, y: 336 },  // Nhà Moori
  23: { x: 228, y: 336 },  // Nhà Broly
  24: { x: 516, y: 336 },  // Trạm tàu vũ trụ Trái Đất
  25: { x: 348, y: 336 },  // Trạm tàu vũ trụ Namek
  26: { x: 348, y: 336 },  // Trạm tàu vũ trụ Xayda
  42: { x: 400, y: 408 },  // Vách núi Aru
  43: { x: 400, y: 408 },  // Vách núi Moori
  44: { x: 400, y: 408 },  // Vách núi Kakarot
  186: { x: 288, y: 648 }, // Làng Chài
  187: { x: 59, y: 360 }   // Vô Hạn Thành 1
};

function SyncNpcCreatorMap() {
  var mapId = parseInt(V("npcCreatorMapSelect"), 10);
  if (NPC_MAP_GROUND_DEFAULTS[mapId]) {
    var def = NPC_MAP_GROUND_DEFAULTS[mapId];
    var curX = V("npcCreatorSpawnX");
    var curY = V("npcCreatorSpawnY");
    // Nếu đang là tọa độ mặc định hoặc chưa nhập, tự cập nhật tọa độ đất chuẩn của map
    if (!curX || curX === "300" || curX === "0") {
      Set("npcCreatorSpawnX", String(def.x));
    }
    if (!curY || curY === "336" || curY === "0") {
      Set("npcCreatorSpawnY", String(def.y));
    }
  }
  ChangeNpcPreviewBg(V("npcCreatorBgSelect") || "auto");
}

function RenderNpcCreatorMapPlacements(placements) {
  var target = document.getElementById("npcCreatorMapPlacements");
  if (!target) return;
  if (!placements) placements = [];
  if (!Array.isArray(placements)) placements = [placements];
  if (placements.length === 0) {
    target.innerHTML = '<span class="npc-placement-empty">Chưa đặt vào map.</span>';
    return;
  }
  var labels = [];
  for (var i = 0; i < placements.length; i++) {
    var placement = placements[i] || {};
    var placementLift = parseInt(placement.NameLift, 10) || 0;
    labels.push(
      '<span class="npc-placement-chip">Map ' + Html(placement.MapId) +
      ' - ' + Html(placement.MapName || "") +
      ' (' + Html(placement.X) + ', ' + Html(placement.Y) + ')' +
      (placementLift > 0 ? ' • dịch mốc +' + placementLift : '') + '</span>'
    );
  }
  target.innerHTML = labels.join("");
}

function PickNpcCreator(index) {
  var r = npcCreatorRows[index];
  if (!r) return;
  var id = r[0];
  var detailRaw = RunAdmin("getnpccreator", { Id: id });
  var detail = null;
  try {
    detail = JSON.parse(detailRaw);
  } catch (e) {
    Msg("npcCreatorMessage", "Không đọc được chi tiết NPC: " + detailRaw);
    return;
  }

  npcCreatorScaleSizes = {
    fullbody: { iconId: 0 },
    head: { iconId: 0 },
    body: { iconId: 0 },
    leg: { iconId: 0 }
  };

  Set("npcCreatorId", detail.Id);
  Set("npcCreatorName", detail.Name);
  Set("npcCreatorHeadPartId", detail.HeadPartId);
  Set("npcCreatorBodyPartId", detail.BodyPartId);
  Set("npcCreatorLegPartId", detail.LegPartId);
  Set("npcCreatorLayerOrder", detail.LayerOrder || "NATIVE");

  var isFullBody = (detail.Mode === "fullbody" || detail.HeadIconId == 25001);
  SwitchNpcCreatorMode(isFullBody ? "fullbody" : "multipart");

  if (isFullBody) {
    Set("npcCreatorFullBodyIcon", detail.FullBodyIconId || detail.BodyIconId || "0");
    Set("npcCreatorFullBodyW", detail.FullBodyW || detail.BodyW || "0");
    Set("npcCreatorFullBodyH", detail.FullBodyH || detail.BodyH || "0");
    Set("npcCreatorFullBodyDx", detail.FullBodyDx || detail.BodyDx || "0");
    Set("npcCreatorFullBodyDy", detail.FullBodyDy || "0");
    npcCreatorOrigSizes.fullbody.w = parseInt(detail.FullBodyW || detail.BodyW, 10) || 0;
    npcCreatorOrigSizes.fullbody.h = parseInt(detail.FullBodyH || detail.BodyH, 10) || 0;
    npcCreatorScaleSizes.fullbody = BuildNpcScaleSizes(detail, "FullBody", detail.FullBodyIconId || detail.BodyIconId);

    Set("npcCreatorFbAvatarIcon", detail.AvatarIconId || "0");
  } else {
    Set("npcCreatorHeadIcon", detail.HeadIconId);
    Set("npcCreatorHeadDx", detail.HeadDx);
    Set("npcCreatorHeadDy", detail.HeadDy);
    Set("npcCreatorHeadW", detail.HeadW || "0");
    Set("npcCreatorHeadH", detail.HeadH || "0");
    npcCreatorOrigSizes.head.w = parseInt(detail.HeadW, 10) || 0;
    npcCreatorOrigSizes.head.h = parseInt(detail.HeadH, 10) || 0;
    npcCreatorScaleSizes.head = BuildNpcScaleSizes(detail, "Head", detail.HeadIconId);

    Set("npcCreatorBodyIcon", detail.BodyIconId);
    Set("npcCreatorBodyDx", detail.BodyDx);
    Set("npcCreatorBodyDy", detail.BodyDy);
    Set("npcCreatorBodyW", detail.BodyW || "0");
    Set("npcCreatorBodyH", detail.BodyH || "0");
    npcCreatorOrigSizes.body.w = parseInt(detail.BodyW, 10) || 0;
    npcCreatorOrigSizes.body.h = parseInt(detail.BodyH, 10) || 0;
    npcCreatorScaleSizes.body = BuildNpcScaleSizes(detail, "Body", detail.BodyIconId);

    Set("npcCreatorLegIcon", detail.LegIconId);
    Set("npcCreatorLegDx", detail.LegDx);
    Set("npcCreatorLegDy", detail.LegDy);
    Set("npcCreatorLegW", detail.LegW || "0");
    Set("npcCreatorLegH", detail.LegH || "0");
    npcCreatorOrigSizes.leg.w = parseInt(detail.LegW, 10) || 0;
    npcCreatorOrigSizes.leg.h = parseInt(detail.LegH, 10) || 0;
    npcCreatorScaleSizes.leg = BuildNpcScaleSizes(detail, "Leg", detail.LegIconId);

    Set("npcCreatorAvatarIcon", detail.AvatarIconId);
    Set("npcCreatorAvatarW", detail.AvatarW || "0");
    Set("npcCreatorAvatarH", detail.AvatarH || "0");
    npcCreatorOrigSizes.avatar.w = parseInt(detail.AvatarW, 10) || 0;
    npcCreatorOrigSizes.avatar.h = parseInt(detail.AvatarH, 10) || 0;
  }

  var mapSel = document.getElementById("npcCreatorMapSelect");
  if (mapSel) {
    mapSel.value = (detail.MapId !== "" && detail.MapId !== null) ? detail.MapId : "-1";
  }
  Set("npcCreatorSpawnX", detail.SpawnX || "300");
  Set("npcCreatorSpawnY", detail.SpawnY || "336");
  Set("npcCreatorNameLift", detail.NameLift || "0");
  var keepOtherMaps = document.getElementById("npcCreatorKeepOtherMaps");
  if (keepOtherMaps) {
    // Kanao là NPC cổng hai chiều nên mặc định luôn bảo toàn bản ở map còn lại,
    // kể cả khi dữ liệu cũ hiện chỉ còn một vị trí.
    keepOtherMaps.checked = (detail.KeepOtherMaps == "1" || String(detail.Id) === "112");
    keepOtherMaps.disabled = (String(detail.Id) === "112");
    keepOtherMaps.title = keepOtherMaps.disabled ? "Kanao cần tồn tại đồng thời ở map 0 và 187." : "";
  }
  RenderNpcCreatorMapPlacements(detail.MapPlacements || []);

  var chkShop = document.getElementById("npcCreatorHasShop");
  if (chkShop) chkShop.checked = (detail.HasShop == "1");
  Set("npcCreatorShopTag", detail.ShopTagName || "SHOP_MUA_BAN");

  // Reset file inputs
  npcCreatorFilePaths.fullbody = "";
  npcCreatorFilePaths.head = "";
  npcCreatorFilePaths.body = "";
  npcCreatorFilePaths.leg = "";
  npcCreatorFilePaths.avatar = "";
  ResetFileInput("npcCreatorFullBodyFile");
  ResetFileInput("npcCreatorFbAvatarFile");
  ResetFileInput("npcCreatorHeadFile");
  ResetFileInput("npcCreatorBodyFile");
  ResetFileInput("npcCreatorLegFile");
  ResetFileInput("npcCreatorAvatarFile");

  SyncNpcCreatorMap();
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đang xem NPC: " + detail.Name + " (ID: " + detail.Id + ")");
}

function NewNpcCreator() {
  Set("npcCreatorId", "");
  Set("npcCreatorName", "");
  Set("npcCreatorHeadPartId", "-1");
  Set("npcCreatorBodyPartId", "-1");
  Set("npcCreatorLegPartId", "-1");

  Set("npcCreatorFullBodyIcon", "0");
  Set("npcCreatorFullBodyW", "120");
  Set("npcCreatorFullBodyH", "180");
  Set("npcCreatorFullBodyDx", "0");
  Set("npcCreatorFullBodyDy", "0");
  Set("npcCreatorFbAvatarIcon", "0");

  Set("npcCreatorHeadIcon", "0");
  Set("npcCreatorHeadDx", "3");
  Set("npcCreatorHeadDy", "-11");
  Set("npcCreatorHeadW", "80");
  Set("npcCreatorHeadH", "92");

  Set("npcCreatorBodyIcon", "0");
  Set("npcCreatorBodyDx", "-1");
  Set("npcCreatorBodyDy", "-6");
  Set("npcCreatorBodyW", "76");
  Set("npcCreatorBodyH", "48");

  Set("npcCreatorLegIcon", "0");
  Set("npcCreatorLegDx", "1");
  Set("npcCreatorLegDy", "-1");
  Set("npcCreatorLegW", "52");
  Set("npcCreatorLegH", "44");

  Set("npcCreatorAvatarIcon", "0");
  Set("npcCreatorAvatarW", "180");
  Set("npcCreatorAvatarH", "180");

  npcCreatorOrigSizes = {
    fullbody: { w: 0, h: 0 },
    head: { w: 0, h: 0 },
    body: { w: 0, h: 0 },
    leg: { w: 0, h: 0 },
    avatar: { w: 0, h: 0 }
  };
  npcCreatorScaleSizes = {
    fullbody: { iconId: 0 },
    head: { iconId: 0 },
    body: { iconId: 0 },
    leg: { iconId: 0 }
  };

  var mapSel = document.getElementById("npcCreatorMapSelect");
  if (mapSel) mapSel.value = "-1";
  Set("npcCreatorSpawnX", "300");
  Set("npcCreatorSpawnY", "336");
  Set("npcCreatorNameLift", "0");
  Set("npcCreatorLayerOrder", "NATIVE");
  var keepOtherMaps = document.getElementById("npcCreatorKeepOtherMaps");
  if (keepOtherMaps) {
    keepOtherMaps.checked = false;
    keepOtherMaps.disabled = false;
    keepOtherMaps.title = "";
  }
  RenderNpcCreatorMapPlacements([]);

  var chkShop = document.getElementById("npcCreatorHasShop");
  if (chkShop) chkShop.checked = false;
  Set("npcCreatorShopTag", "SHOP_MUA_BAN");

  npcCreatorFilePaths.fullbody = "";
  npcCreatorFilePaths.head = "";
  npcCreatorFilePaths.body = "";
  npcCreatorFilePaths.leg = "";
  npcCreatorFilePaths.avatar = "";
  ResetFileInput("npcCreatorFullBodyFile");
  ResetFileInput("npcCreatorFbAvatarFile");
  ResetFileInput("npcCreatorHeadFile");
  ResetFileInput("npcCreatorBodyFile");
  ResetFileInput("npcCreatorLegFile");
  ResetFileInput("npcCreatorAvatarFile");

  SwitchNpcCreatorMode("fullbody");
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Sẵn sàng tạo NPC mới ở Chế độ 1 Ảnh Toàn Thân.");
}

function ResetFileInput(elementId) {
  var el = document.getElementById(elementId);
  if (el) {
    try {
      el.value = "";
    } catch (e) {}
  }
}

function HandleNpcFileChange(partType, fileInput) {
  if (!fileInput || !fileInput.value) return;
  var filePath = fileInput.value;
  npcCreatorFilePaths[partType] = filePath;

  var tempImg = new Image();
  var rawPath = filePath.replace(/\\/g, "/");
  if (rawPath.charAt(0) != "/" && rawPath.charAt(1) == ":") {
    rawPath = "/" + rawPath;
  }
  var encodedUrl = "file://" + encodeURI(rawPath);

  tempImg.onload = function () {
    var origW = tempImg.width || 0;
    var origH = tempImg.height || 0;
    npcCreatorOrigSizes[partType].w = origW;
    npcCreatorOrigSizes[partType].h = origH;

    if (partType === "fullbody") {
      var targetH = 180;
      var targetW = NormalizeNpcX4Dimension(targetH * origW / origH);
      Set("npcCreatorFullBodyW", targetW);
      Set("npcCreatorFullBodyH", targetH);
      var targetH1 = Math.round(targetH / 4);
      var suggestedLift = Math.max(0, targetH1 - 36);
      Set("npcCreatorNameLift", suggestedLift);
      Msg("npcCreatorMessage", "Ảnh toàn thân gốc (" + origW + "x" + origH + "px) đã được tự động gợi ý co về chuẩn x4: " + targetW + "x" + targetH + "px" + (suggestedLift > 0 ? " • NameLift=" + suggestedLift + "px" : "") + ".");
      UpdateNpcCreatorPreview();
      return;
    }

    var capPart = partType.charAt(0).toUpperCase() + partType.slice(1);
    var targetMaxW = 80;
    if (partType === "body") targetMaxW = 76;
    if (partType === "leg") targetMaxW = 52;
    if (partType === "avatar") targetMaxW = 180;

    if (origW > 120 && origW > 0) {
      var scaledW = NormalizeNpcX4Dimension(targetMaxW);
      var scaledH = NormalizeNpcX4Dimension(targetMaxW * origH / origW);
      Set("npcCreator" + capPart + "W", scaledW);
      Set("npcCreator" + capPart + "H", scaledH);
      Msg("npcCreatorMessage", "Ảnh " + partType + " gốc (" + origW + "x" + origH + "px) đã được tự động gợi ý co về chuẩn x4: " + scaledW + "x" + scaledH + "px.");
    } else {
      Set("npcCreator" + capPart + "W", NormalizeNpcX4Dimension(origW));
      Set("npcCreator" + capPart + "H", NormalizeNpcX4Dimension(origH));
    }
    if (partType === "head" || partType === "body" || partType === "leg") {
      AutoSnapJoints();
    } else {
      UpdateNpcCreatorPreview();
    }
  };
  tempImg.onerror = function () {
    UpdateNpcCreatorPreview();
  };
  tempImg.src = encodedUrl;
  UpdateNpcCreatorPreview();
}

function HandleNpcManualSizeChange(partType, changedAxis) {
  var capPart = (partType === "fullbody") ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
  var lockRatioEl = document.getElementById("npcCreator" + capPart + "LockRatio");
  var isLocked = lockRatioEl ? lockRatioEl.checked : true;

  var orig = npcCreatorOrigSizes[partType];
  var ratio = (orig.w > 0 && orig.h > 0) ? (orig.h / orig.w) : 1.0;

  if (isLocked) {
    if (changedAxis === "w") {
      var curW = parseInt(V("npcCreator" + capPart + "W"), 10) || 0;
      if (curW > 0) {
        var newH = Math.max(1, Math.round(curW * ratio));
        Set("npcCreator" + capPart + "H", newH);
      }
    } else {
      var curH = parseInt(V("npcCreator" + capPart + "H"), 10) || 0;
      if (curH > 0 && ratio > 0) {
        var newW = Math.max(1, Math.round(curH / ratio));
        Set("npcCreator" + capPart + "W", newW);
      }
    }
  }
  UpdateNpcCreatorPreview();
}

function AdjustNpcSize(partType, deltaPercent, deltaPx) {
  var capPart = (partType === "fullbody") ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
  var curW = parseInt(V("npcCreator" + capPart + "W"), 10) || 0;
  var curH = parseInt(V("npcCreator" + capPart + "H"), 10) || 0;

  if (curW <= 0) {
    var orig = npcCreatorOrigSizes[partType];
    curW = orig.w > 0 ? orig.w : (partType === "fullbody" ? 120 : (partType === "head" ? 80 : (partType === "body" ? 76 : (partType === "leg" ? 52 : 180))));
    curH = orig.h > 0 ? orig.h : (partType === "fullbody" ? 180 : (partType === "head" ? 92 : (partType === "body" ? 48 : (partType === "leg" ? 44 : 180))));
  }

  var lockRatioEl = document.getElementById("npcCreator" + capPart + "LockRatio");
  var isLocked = lockRatioEl ? lockRatioEl.checked : true;
  var ratio = (curW > 0 && curH > 0) ? (curH / curW) : 1.0;

  var newW = curW;
  if (deltaPercent !== 0) {
    newW = Math.max(8, Math.round(curW * (1 + deltaPercent)));
  } else if (deltaPx !== 0) {
    newW = Math.max(8, curW + deltaPx);
  }

  Set("npcCreator" + capPart + "W", newW);
  if (isLocked) {
    var newH = Math.max(8, Math.round(newW * ratio));
    Set("npcCreator" + capPart + "H", newH);
  }
  UpdateNpcCreatorPreview();
}

function ApplyNpcPreset(partType) {
  if (partType === "all") {
    ApplyNpcPreset("head");
    ApplyNpcPreset("body");
    ApplyNpcPreset("leg");
    ApplyNpcPreset("avatar");
    AutoAlignNpcParts();
    Msg("npcCreatorMessage", "Đã áp dụng kích thước chuẩn NRO x4 và căn chỉnh khớp tỷ lệ toàn bộ bộ phận.");
    return;
  }

  var capPart = partType.charAt(0).toUpperCase() + partType.slice(1);
  var orig = npcCreatorOrigSizes[partType];
  var ratio = (orig.w > 0 && orig.h > 0) ? (orig.h / orig.w) : 1.0;

  var targetW = 80;
  var targetH = 92;
  if (partType === "head") { targetW = 80; targetH = 92; }
  if (partType === "body") { targetW = 76; targetH = 48; }
  if (partType === "leg") { targetW = 52; targetH = 44; }
  if (partType === "avatar") { targetW = 180; targetH = 180; }

  if (orig.w > 0 && orig.h > 0) {
    targetH = NormalizeNpcX4Dimension(targetW * ratio);
  }

  targetW = NormalizeNpcX4Dimension(targetW);
  targetH = NormalizeNpcX4Dimension(targetH);
  Set("npcCreator" + capPart + "W", targetW);
  Set("npcCreator" + capPart + "H", targetH);
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã gán kích thước chuẩn x4 cho " + partType + ": " + targetW + "x" + targetH + "px.");
}

function AutoCenterNpcMultipart() {
  var headW = parseInt(V("npcCreatorHeadW"), 10) || 80;
  var bodyW = parseInt(V("npcCreatorBodyW"), 10) || 76;
  var legW = parseInt(V("npcCreatorLegW"), 10) || 52;
  var headW1 = Math.round(headW / 4);
  var bodyW1 = Math.round(bodyW / 4);
  var legW1 = Math.round(legW / 4);

  var targetHeadDx = 13 - Math.round(headW1 / 2.0);
  var targetBodyDx = 9 - Math.round(bodyW1 / 2.0);
  var targetLegDx = 8 - Math.round(legW1 / 2.0);

  Set("npcCreatorHeadDx", targetHeadDx);
  Set("npcCreatorBodyDx", targetBodyDx);
  Set("npcCreatorLegDx", targetLegDx);

  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã tự động căn giữa tâm bóng: Đầu (Dx=" + targetHeadDx + "), Thân (Dx=" + targetBodyDx + "), Chân (Dx=" + targetLegDx + ").");
}

function GroundNpcLegs() {
  var legH = parseInt(V("npcCreatorLegH"), 10) || 44;
  var legH1 = Math.round(legH / 4);
  var targetLegDy = 10 - legH1;
  Set("npcCreatorLegDy", targetLegDy);
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã căn đáy chân chạm khít vạch đất (LegDy=" + targetLegDy + ").");
  return targetLegDy;
}

function AutoSnapJoints() {
  var legH = parseInt(V("npcCreatorLegH"), 10) || 44;
  var bodyH = parseInt(V("npcCreatorBodyH"), 10) || 48;
  var headH = parseInt(V("npcCreatorHeadH"), 10) || 92;

  var legH1 = Math.round(legH / 4);
  var bodyH1 = Math.round(bodyH / 4);
  var headH1 = Math.round(headH / 4);

  // 1. Căn chân chạm khít mặt đất
  var targetLegDy = 10 - legH1;
  Set("npcCreatorLegDy", targetLegDy);

  // 2. Ghép eo và cổ khít khao
  var targetBodyDy = 6 + targetLegDy - bodyH1;
  Set("npcCreatorBodyDy", targetBodyDy);

  var targetHeadDy = 18 + targetBodyDy - headH1;
  Set("npcCreatorHeadDy", targetHeadDy);

  // 3. Tự động căn giữa tâm bóng trục X
  var headW = parseInt(V("npcCreatorHeadW"), 10) || 80;
  var bodyW = parseInt(V("npcCreatorBodyW"), 10) || 76;
  var legW = parseInt(V("npcCreatorLegW"), 10) || 52;
  var headW1 = Math.round(headW / 4);
  var bodyW1 = Math.round(bodyW / 4);
  var legW1 = Math.round(legW / 4);

  var targetHeadDx = 13 - Math.round(headW1 / 2.0);
  var targetBodyDx = 9 - Math.round(bodyW1 / 2.0);
  var targetLegDx = 8 - Math.round(legW1 / 2.0);

  Set("npcCreatorHeadDx", targetHeadDx);
  Set("npcCreatorBodyDx", targetBodyDx);
  Set("npcCreatorLegDx", targetLegDx);

  // 4. Gợi ý NameLift nếu nhân vật cao hơn 144px
  var totalHeight1 = 34 - targetHeadDy;
  var suggestedLift = Math.max(0, totalHeight1 - 36);
  var curLift = parseInt(V("npcCreatorNameLift"), 10) || 0;
  if (suggestedLift > 0 && curLift === 0) {
    Set("npcCreatorNameLift", suggestedLift);
  }

  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", "Đã tự động căn chỉnh hoàn hảo: Căn giữa tâm (Dx: " + targetHeadDx + ", " + targetBodyDx + ", " + targetLegDx + "), Chân chạm đất (LegDy=" + targetLegDy + "), Khép eo (BodyDy=" + targetBodyDy + "), Khép cổ (HeadDy=" + targetHeadDy + ")" + (suggestedLift > 0 ? " • Gợi ý NameLift=" + suggestedLift + "px" : "") + ".");
}

function ScaleNpcOverall(ratio) {
  var baseHeadW = 80, baseHeadH = 80;
  var baseBodyW = 76, baseBodyH = 52;
  var baseLegW = 52, baseLegH = 44;
  var baseAvatarW = 180, baseAvatarH = 180;

  var origHead = npcCreatorOrigSizes.head;
  if (origHead && origHead.w > 0 && origHead.h > 0) {
    baseHeadH = Math.round(baseHeadW * origHead.h / origHead.w);
  }
  var origBody = npcCreatorOrigSizes.body;
  if (origBody && origBody.w > 0 && origBody.h > 0) {
    baseBodyH = Math.round(baseBodyW * origBody.h / origBody.w);
  }
  var origLeg = npcCreatorOrigSizes.leg;
  if (origLeg && origLeg.w > 0 && origLeg.h > 0) {
    baseLegH = Math.round(baseLegW * origLeg.h / origLeg.w);
  }

  var newHeadW = Math.round(baseHeadW * ratio);
  var newHeadH = Math.round(baseHeadH * ratio);
  var newBodyW = Math.round(baseBodyW * ratio);
  var newBodyH = Math.round(baseBodyH * ratio);
  var newLegW = Math.round(baseLegW * ratio);
  var newLegH = Math.round(baseLegH * ratio);
  var newAvatarW = Math.round(baseAvatarW * Math.min(1.5, ratio));
  var newAvatarH = Math.round(baseAvatarH * Math.min(1.5, ratio));

  Set("npcCreatorHeadW", newHeadW);
  Set("npcCreatorHeadH", newHeadH);
  Set("npcCreatorBodyW", newBodyW);
  Set("npcCreatorBodyH", newBodyH);
  Set("npcCreatorLegW", newLegW);
  Set("npcCreatorLegH", newLegH);
  Set("npcCreatorAvatarW", newAvatarW);
  Set("npcCreatorAvatarH", newAvatarH);

  AutoSnapJoints();
  Msg("npcCreatorMessage", "Đã đổi quy mô nhân vật sang " + Math.round(ratio * 100) + "% và tự động căn chỉnh khớp nối.");
}

function AutoAlignNpcParts() {
  AutoSnapJoints();
}

function ChangeNpcPreviewBg(bgValue) {
  var box = document.getElementById("npcPreviewBox");
  if (!box) return;

  var currentClass = box.className;
  var hasGrid = currentClass.indexOf("show-grid") >= 0;
  var targetBgClass = bgValue;

  if (!targetBgClass || targetBgClass === "auto") {
    var mapIdStr = V("npcCreatorMapSelect") || "0";
    var mapId = parseInt(mapIdStr, 10);
    if (mapId === 5 || mapId === 6 || mapId === 7) {
      targetBgClass = "npc-bg-kame";
    } else if (mapId === 1 || mapId === 2 || mapId === 3) {
      targetBgClass = "npc-bg-doicuc";
    } else if (mapId === 102 || mapId === 103) {
      targetBgClass = "npc-bg-bunma";
    } else if (mapId >= 21 && mapId <= 23) {
      targetBgClass = "npc-bg-namek";
    } else if (mapId >= 42 && mapId <= 44) {
      targetBgClass = "npc-bg-xayda";
    } else if (mapId < 0) {
      targetBgClass = "npc-bg-studio";
    } else {
      targetBgClass = "npc-bg-aru";
    }
  }

  box.className = "npc-preview-stage " + targetBgClass + (hasGrid ? " show-grid" : "");
}

function AdjustNpcOffset(partType, axis, delta) {
  var capPart = (partType === "fullbody") ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
  var inputId = "npcCreator" + capPart + (axis.toUpperCase() == "X" ? "Dx" : "Dy");
  var currentVal = parseInt(V(inputId), 10) || 0;
  var newVal = currentVal + delta;
  Set(inputId, newVal);
  UpdateNpcCreatorPreview();
}

function ResetNpcOffsets() {
  var isFullBody = (V("npcCreatorMode") === "fullbody");
  if (isFullBody) {
    Set("npcCreatorFullBodyDx", "0");
    Set("npcCreatorFullBodyDy", "0");
    UpdateNpcCreatorPreview();
    Msg("npcCreatorMessage", "Đã reset offset ảnh toàn thân về tâm bóng và chạm đất.");
  } else {
    AutoSnapJoints();
    Msg("npcCreatorMessage", "Đã reset và tự động căn chuẩn tâm bóng, khớp nối và chạm đất cho 3 phần.");
  }
}

function ToggleNpcPreviewGrid() {
  var box = document.getElementById("npcPreviewBox");
  if (box) {
    if (box.className.indexOf("show-grid") >= 0) {
      box.className = box.className.replace(" show-grid", "");
    } else {
      box.className += " show-grid";
    }
  }
}

function NpcCreatorFileUrl(filePath) {
  var rawPath = (filePath || "").replace(/\\/g, "/");
  if (rawPath.charAt(0) != "/" && rawPath.charAt(1) == ":") {
    rawPath = "/" + rawPath;
  }
  return rawPath ? ("file://" + encodeURI(rawPath)) : "";
}

function FetchAndApplyIconDimensions(partType, iconId) {
  var idNum = parseInt(iconId, 10) || 0;
  if (idNum <= 0) return;
  try {
    var res = RunAdmin("getnpciconinfo", { IconId: idNum });
    if (res && res.indexOf("IconId") >= 0) {
      var info = JSON.parse(res);
      if (info && info.Width4 > 0 && info.Height4 > 0) {
        var capPart = (partType === "fullbody") ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
        Set("npcCreator" + capPart + "W", info.Width4);
        Set("npcCreator" + capPart + "H", info.Height4);
        if (!npcCreatorOrigSizes[partType]) npcCreatorOrigSizes[partType] = {};
        npcCreatorOrigSizes[partType].w = info.Width4;
        npcCreatorOrigSizes[partType].h = info.Height4;
        if (partType === "fullbody") {
          var suggestedLift = Math.max(0, info.Height1 - 36);
          var curLift = parseInt(V("npcCreatorNameLift"), 10) || 0;
          if (suggestedLift > 0 && curLift === 0) {
            Set("npcCreatorNameLift", suggestedLift);
          }
        } else if (partType === "head" || partType === "body" || partType === "leg") {
          AutoSnapJoints();
        }
        UpdateNpcCreatorPreview();
        Msg("npcCreatorMessage", "Đã nhận diện kích thước thật của Icon " + idNum + " (" + info.Width4 + "x" + info.Height4 + "px).");
      }
    }
  } catch (e) {}
}

function OnNpcIconInputChange(partType, iconVal) {
  var idNum = parseInt(iconVal, 10) || 0;
  if (idNum > 0) {
    FetchAndApplyIconDimensions(partType, idNum);
  } else {
    UpdateNpcCreatorPreview();
  }
}

function GetImageSourceForPart(partType, overrideIconId, forcedZoom) {
  if (npcCreatorFilePaths[partType] && npcCreatorFilePaths[partType].length > 0) {
    return NpcCreatorFileUrl(npcCreatorFilePaths[partType]);
  }
  var iconInputId = "npcCreator" + (partType === "fullbody" ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1))) + "Icon";
  var iconId = parseInt(overrideIconId, 10) || parseInt(V(iconInputId), 10) || 0;
  if (iconId > 0) {
    var zoom = forcedZoom || GetNpcPreviewZoom();
    var windowsPath = rootDir + "\\data\\icon\\x" + zoom + "\\" + iconId + ".png";
    try {
      if (!fso.FileExists(windowsPath)) {
        windowsPath = rootDir + "\\data\\icon\\x4\\" + iconId + ".png";
      }
    } catch (e) {
    }
    return NpcCreatorFileUrl(windowsPath);
  }
  return "";
}

function GetNpcPreviewSize(partType, zoom) {
  var capPart = partType === "fullbody" ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
  var fallbackW = partType === "fullbody" ? 120 : (partType === "head" ? 80 : (partType === "body" ? 76 : 52));
  var fallbackH = partType === "fullbody" ? 180 : (partType === "head" ? 92 : (partType === "body" ? 52 : 44));
  var width4 = parseInt(V("npcCreator" + capPart + "W"), 10) || fallbackW;
  var height4 = parseInt(V("npcCreator" + capPart + "H"), 10) || fallbackH;
  var currentIconId = parseInt(V("npcCreator" + capPart + "Icon"), 10) || 0;
  var savedSizes = npcCreatorScaleSizes[partType];
  var savedX4 = savedSizes ? savedSizes[4] : null;
  var canUseSavedSize = !npcCreatorFilePaths[partType] && savedSizes && savedSizes.iconId === currentIconId &&
    savedX4 && savedX4.w === width4 && savedX4.h === height4;
  var actualAtZoom = canUseSavedSize ? savedSizes[zoom] : null;
  var actualAtX1 = canUseSavedSize ? savedSizes[1] : null;
  var actualAtX4 = canUseSavedSize ? savedSizes[4] : null;
  if (actualAtX4 && actualAtX4.w > 0 && actualAtX4.h > 0) {
    width4 = actualAtX4.w;
    height4 = actualAtX4.h;
  }
  return {
    width4: width4,
    height4: height4,
    width: actualAtZoom && actualAtZoom.w > 0 ? actualAtZoom.w : Math.max(1, Math.round(width4 * zoom / 4)),
    height: actualAtZoom && actualAtZoom.h > 0 ? actualAtZoom.h : Math.max(1, Math.round(height4 * zoom / 4)),
    width1: actualAtX1 && actualAtX1.w > 0 ? actualAtX1.w : Math.max(1, Math.round(width4 / 4)),
    height1: actualAtX1 && actualAtX1.h > 0 ? actualAtX1.h : Math.max(1, Math.round(height4 / 4))
  };
}

function PositionNpcPreviewLayer(img, src, size, logicalX, logicalY, zoom, direction) {
  if (!img) return;
  if (!src) {
    img.style.display = "none";
    return;
  }

  var anchorX = logicalX * zoom;
  var left = direction > 0 ? anchorX : (-anchorX - size.width);
  img.src = src;
  img.style.display = "block";
  img.style.left = "calc(50% + " + left + "px)";
  img.style.top = "calc(100% - 42px + " + (logicalY * zoom) + "px)";
  img.style.right = "auto";
  img.style.bottom = "auto";
  img.style.marginLeft = "0";
  img.style.width = size.width + "px";
  img.style.height = size.height + "px";
  img.style.transform = direction > 0 ? "none" : "scaleX(-1)";
}

function GetNpcPreviewPartRect(src, size, logicalX, logicalY, zoom, direction) {
  if (!src || !size || size.width <= 0 || size.height <= 0) return null;
  var anchorX = logicalX * zoom;
  var left = direction > 0 ? anchorX : (-anchorX - size.width);
  var top = logicalY * zoom;
  return {
    left: left,
    top: top,
    right: left + size.width,
    bottom: top + size.height
  };
}

function ApplyNpcPreviewNativeAnchors(nameEl, shadowEl, nameLift, zoom) {
  var nativeNameY = -(NPC_CREATOR_NATIVE_CH + NPC_CREATOR_NATIVE_NAME_MARGIN + nameLift);
  var nameAnchor = nativeNameY * zoom;
  if (nameEl) {
    // CSS translateY(-100%) khiến tọa độ top này chính là cạnh dưới của tên.
    nameEl.style.top = "calc(100% - 42px + " + nameAnchor + "px)";
  }

  if (shadowEl) {
    var shadowHeight = parseFloat(shadowEl.style.height) || Math.max(5, Math.round(4.5 * zoom));
    shadowEl.style.left = "50%";
    // Client gốc neo bóng tại (cx, cy); NameLift vì thế cũng nâng bóng lên.
    shadowEl.style.bottom = (42 + nameLift * zoom - shadowHeight / 2) + "px";
  }

  return {
    nameAnchor: nativeNameY,
    nameAnchorPx: nameAnchor,
    shadowX: 0,
    shadowY: -nameLift
  };
}

function AppendNpcPreviewNameGap(statusText, referenceRect, anchors, zoom) {
  if (!referenceRect || !anchors) return { text: statusText, warning: false };
  var gap = Math.round((referenceRect.top - anchors.nameAnchorPx) / zoom);
  if (gap < 0) {
    return { text: statusText + " • tên đang chạm ảnh " + Math.abs(gap) + "px", warning: true };
  }
  return { text: statusText + " • tên cách ảnh " + gap + "px", warning: false };
}

function GetNpcPreviewTopRect(rects) {
  var topRect = null;
  for (var i = 0; i < rects.length; i++) {
    if (rects[i] && (!topRect || rects[i].top < topRect.top)) topRect = rects[i];
  }
  return topRect;
}

function SetNpcPreviewStatus(text, isWarning) {
  var status = document.getElementById("npcPreviewStatus");
  if (!status) return;
  status.className = isWarning ? "npc-preview-status warning" : "npc-preview-status";
  status.innerText = text;
}

function UpdateNpcCreatorPreview() {
  var mode = V("npcCreatorMode") || "fullbody";
  var isFullBody = (mode === "fullbody");
  var zoom = GetNpcPreviewZoom();
  var direction = GetNpcPreviewDirection();
  var frameIndex = parseInt(V("npcCreatorPreviewFrame"), 10) || 0;
  var frame = NPC_CREATOR_STAND_FRAMES[frameIndex] || NPC_CREATOR_STAND_FRAMES[0];
  var nameLift = parseInt(V("npcCreatorNameLift"), 10) || 0;
  nameLift = Math.max(0, Math.min(64, nameLift));

  var imgFb = document.getElementById("npcPrevFullBody");
  var imgHead = document.getElementById("npcPrevHead");
  var imgBody = document.getElementById("npcPrevBody");
  var imgLeg = document.getElementById("npcPrevLeg");
  var nameEl = document.getElementById("npcPreviewName");

  if (nameEl) {
    var npcId = Trim(V("npcCreatorId") || "");
    var npcName = Trim(V("npcCreatorName") || "") || "NPC";
    nameEl.innerText = (npcId ? "[" + npcId + "] " : "") + npcName;
  }

  var shadowEl = document.querySelector(".npc-preview-shadow");
  var directionLabel = direction > 0 ? "phải" : "trái";
  var statusText = "x" + zoom + " • hướng " + directionLabel + " • frame đứng " + frameIndex;
  var statusWarning = false;
  if (shadowEl) {
    shadowEl.style.left = "50%";
    shadowEl.style.bottom = "34px";
  }
  statusText += " • NameLift=" + nameLift + "px";

  if (isFullBody) {
    if (imgHead) imgHead.style.display = "none";
    if (imgBody) imgBody.style.display = "none";
    if (imgLeg) imgLeg.style.display = "none";

    var fbSrc = GetImageSourceForPart("fullbody");
    var fbDx = parseInt(V("npcCreatorFullBodyDx"), 10) || 0;
    var fbDy = parseInt(V("npcCreatorFullBodyDy"), 10) || 0;
    var fbSize = GetNpcPreviewSize("fullbody", zoom);

    // Đây chính là phép chuyển đổi sẽ được backend ghi vào body part.
    var encodedBodyDx = 9 - Math.floor(fbSize.width1 / 2) + fbDx;
    var encodedBodyDy = 16 - fbSize.height1 + fbDy;
    var logicalFbX = frame.body.x + encodedBodyDx;
    var logicalFbY = -frame.body.y + encodedBodyDy;
    var footError = (logicalFbY * zoom) + fbSize.height;
    var fbRect = GetNpcPreviewPartRect(fbSrc, fbSize, logicalFbX, logicalFbY, zoom, direction);

    if (imgFb) {
      PositionNpcPreviewLayer(imgFb, fbSrc, fbSize, logicalFbX, logicalFbY, zoom, direction);
      if (fbSrc && shadowEl) {
        var sW = Math.max(18, Math.min(130, Math.round(fbSize.width * 0.9)));
        shadowEl.style.width = sW + "px";
        shadowEl.style.marginLeft = (-sW / 2) + "px";
        shadowEl.style.height = Math.max(5, Math.round(4.5 * zoom)) + "px";
      }
    }

    var fullBodyAnchors = ApplyNpcPreviewNativeAnchors(nameEl, shadowEl, nameLift, zoom);
    statusText += " • tên=cy-" + (NPC_CREATOR_NATIVE_CH + NPC_CREATOR_NATIVE_NAME_MARGIN + nameLift) +
      " • bóng=(0,-" + nameLift + ")";
    var fullBodyGap = AppendNpcPreviewNameGap(statusText, fbRect, fullBodyAnchors, zoom);
    statusText = fullBodyGap.text;
    statusWarning = statusWarning || fullBodyGap.warning;

    statusText += " • body DATA=[" + (parseInt(V("npcCreatorFullBodyIcon"), 10) || "icon mới") + "," + encodedBodyDx + "," + encodedBodyDy + "]";
    if ((fbSize.width4 % 4) !== 0 || (fbSize.height4 % 4) !== 0) {
      statusWarning = true;
      statusText += " • W/H x4 chưa chia hết cho 4";
    }
    if (footError !== 0) {
      statusWarning = true;
      statusText += " • chân lệch " + footError + "px tại x" + zoom;
    }
    var imgAvatar = document.getElementById("npcPrevAvatar");
    var txtAvatar = document.getElementById("npcPrevAvatarText");
    var fbAvatarFile = npcCreatorFilePaths.avatar;
    var fbAvatarIcon = parseInt(V("npcCreatorFbAvatarIcon"), 10) || 0;
    var fbAvatarSrc = "";
    if (fbAvatarFile) {
      fbAvatarSrc = NpcCreatorFileUrl(fbAvatarFile);
    } else if (fbAvatarIcon > 0) {
      fbAvatarSrc = GetImageSourceForPart("avatar", fbAvatarIcon, 4);
    } else {
      fbAvatarSrc = fbSrc;
    }

    if (imgAvatar && txtAvatar) {
      if (fbAvatarSrc) {
        imgAvatar.src = fbAvatarSrc;
        imgAvatar.style.display = "inline-block";
        txtAvatar.style.display = "none";
      } else {
        imgAvatar.style.display = "none";
        txtAvatar.style.display = "inline-block";
      }
    }
  } else {
    if (imgFb) imgFb.style.display = "none";

    var headSrc = GetImageSourceForPart("head");
    var bodySrc = GetImageSourceForPart("body");
    var legSrc = GetImageSourceForPart("leg");
    var avatarSrc = GetImageSourceForPart("avatar", 0, 4);

    var headDx = parseInt(V("npcCreatorHeadDx"), 10) || 0;
    var headDy = parseInt(V("npcCreatorHeadDy"), 10) || 0;
    var bodyDx = parseInt(V("npcCreatorBodyDx"), 10) || 0;
    var bodyDy = parseInt(V("npcCreatorBodyDy"), 10) || 0;
    var legDx = parseInt(V("npcCreatorLegDx"), 10) || 0;
    var legDy = parseInt(V("npcCreatorLegDy"), 10) || 0;

    var headSize = GetNpcPreviewSize("head", zoom);
    var bodySize = GetNpcPreviewSize("body", zoom);
    var legSize = GetNpcPreviewSize("leg", zoom);
    var layerOrder = V("npcCreatorLayerOrder") || "NATIVE";
    var headOnTop = (layerOrder === "LEG_BODY_HEAD");
    if (imgHead) imgHead.style.zIndex = headOnTop ? "7" : "4";
    if (imgBody) imgBody.style.zIndex = "6";
    if (imgLeg) imgLeg.style.zIndex = "5";
    statusText += headOnTop ? " • lớp Leg→Body→Head" : " • lớp Head→Leg→Body";

    if (shadowEl) {
      var sW = Math.max(18, Math.min(110, Math.round(legSize.width * 1.3)));
      shadowEl.style.width = sW + "px";
      shadowEl.style.marginLeft = (-sW / 2) + "px";
      shadowEl.style.height = Math.max(5, Math.round(4.5 * zoom)) + "px";
    }

    // LEG_BODY_HEAD đổi part sang slot client khác để đổi z-order. Vì slot có
    // anchor frame riêng, preview phải dùng đúng anchor vật lý mà backend lưu.
    var previewHeadX = frame.head.x + headDx;
    var previewHeadY = -frame.head.y + headDy;
    var previewBodyX = headOnTop ? frame.leg.x - 1 + bodyDx : frame.body.x + bodyDx;
    var previewBodyY = headOnTop ? -frame.leg.y - 6 + bodyDy : -frame.body.y + bodyDy;
    var previewLegX = headOnTop ? frame.head.x + 5 + legDx : frame.leg.x + legDx;
    var previewLegY = headOnTop ? -frame.head.y + 24 + legDy : -frame.leg.y + legDy;

    PositionNpcPreviewLayer(imgHead, headSrc, headSize, previewHeadX, previewHeadY, zoom, direction);
    PositionNpcPreviewLayer(imgLeg, legSrc, legSize, previewLegX, previewLegY, zoom, direction);
    PositionNpcPreviewLayer(imgBody, bodySrc, bodySize, previewBodyX, previewBodyY, zoom, direction);

    var headRect = GetNpcPreviewPartRect(headSrc, headSize, previewHeadX, previewHeadY, zoom, direction);
    var legRect = GetNpcPreviewPartRect(legSrc, legSize, previewLegX, previewLegY, zoom, direction);
    var bodyRect = GetNpcPreviewPartRect(bodySrc, bodySize, previewBodyX, previewBodyY, zoom, direction);
    var multipartAnchors = ApplyNpcPreviewNativeAnchors(nameEl, shadowEl, nameLift, zoom);
    statusText += " • tên=cy-" + (NPC_CREATOR_NATIVE_CH + NPC_CREATOR_NATIVE_NAME_MARGIN + nameLift) +
      " • bóng=(0,-" + nameLift + ")";
    var multipartTopRect = GetNpcPreviewTopRect([headRect, bodyRect, legRect]);
    var multipartGap = AppendNpcPreviewNameGap(statusText, multipartTopRect, multipartAnchors, zoom);
    statusText = multipartGap.text;
    statusWarning = statusWarning || multipartGap.warning;

    var multipartSizes = [headSize, bodySize, legSize];
    var multipartDimensionWarning = false;
    for (var sizeIndex = 0; sizeIndex < multipartSizes.length; sizeIndex++) {
      if ((multipartSizes[sizeIndex].width4 % 4) !== 0 || (multipartSizes[sizeIndex].height4 % 4) !== 0) {
        statusWarning = true;
        multipartDimensionWarning = true;
      }
    }
    statusText += " • offset part ở đơn vị x1";
    if (multipartDimensionWarning) statusText += " • có W/H x4 chưa chia hết cho 4";

    var imgAvatar2 = document.getElementById("npcPrevAvatar");
    var txtAvatar2 = document.getElementById("npcPrevAvatarText");
    var finalAvatarSrc = avatarSrc || headSrc;
    if (imgAvatar2 && txtAvatar2) {
      if (finalAvatarSrc) {
        imgAvatar2.src = finalAvatarSrc;
        imgAvatar2.style.display = "inline-block";
        txtAvatar2.style.display = "none";
      } else {
        imgAvatar2.style.display = "none";
        txtAvatar2.style.display = "inline-block";
      }
    }
  }

  SetNpcPreviewStatus(statusText, statusWarning);
}

function ValidateNpcOffset(value, label) {
  var text = Trim(value == null ? "" : String(value));
  if (!/^-?\d+$/.test(text)) return label + " phải là số nguyên.";
  var number = parseInt(text, 10);
  if (number < -128 || number > 127) return label + " phải nằm trong khoảng -128..127 (giới hạn byte của client).";
  return "";
}

function NormalizeNpcUploadDimensions(partType, forceExistingIcon) {
  var capPart = partType === "fullbody" ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
  var widthId = "npcCreator" + capPart + "W";
  var heightId = "npcCreator" + capPart + "H";
  var oldW = parseInt(V(widthId), 10) || 0;
  var oldH = parseInt(V(heightId), 10) || 0;
  var currentIconId = parseInt(V("npcCreator" + capPart + "Icon"), 10) || 0;
  var savedSizes = npcCreatorScaleSizes[partType];
  var savedX4 = savedSizes ? savedSizes[4] : null;
  var usesUnchangedExistingIcon = !npcCreatorFilePaths[partType] && savedSizes && savedSizes.iconId === currentIconId &&
    savedX4 && savedX4.w === oldW && savedX4.h === oldH;
  if (usesUnchangedExistingIcon && !forceExistingIcon) return false;
  var newW = NormalizeNpcX4Dimension(oldW);
  var newH = NormalizeNpcX4Dimension(oldH);
  if (newW > 0) Set(widthId, newW);
  if (newH > 0) Set(heightId, newH);
  return oldW !== newW || oldH !== newH;
}

function NormalizeNpcActiveDimensions() {
  var isFullBody = (V("npcCreatorMode") || "fullbody") === "fullbody";
  var parts = isFullBody ? ["fullbody"] : ["head", "body", "leg", "avatar"];
  var changed = false;
  for (var i = 0; i < parts.length; i++) {
    changed = NormalizeNpcUploadDimensions(parts[i], true) || changed;
  }
  UpdateNpcCreatorPreview();
  Msg("npcCreatorMessage", changed ?
    "Đã chuẩn hóa W/H x4 theo bội số 4. Khi lưu, icon có sẵn sẽ được clone sang ID mới để bảo vệ dữ liệu dùng chung." :
    "Kích thước hiện tại đã đồng nhất theo chuẩn x1-x4.");
}

function ValidateNpcCreatorForm(isFullBody) {
  var nameLiftText = Trim(V("npcCreatorNameLift"));
  if (!/^\d+$/.test(nameLiftText)) return "Nâng bảng tên phải là số nguyên trong khoảng 0..64.";
  var nameLift = parseInt(nameLiftText, 10);
  if (nameLift < 0 || nameLift > 64) return "Nâng bảng tên phải nằm trong khoảng 0..64 px logic.";
  var layerOrder = V("npcCreatorLayerOrder") || "NATIVE";
  if (layerOrder !== "LEG_BODY_HEAD" && layerOrder !== "NATIVE") return "Thứ tự xếp lớp NPC không hợp lệ.";

  var requiredParts = isFullBody ? ["fullbody"] : ["head", "body", "leg"];
  for (var i = 0; i < requiredParts.length; i++) {
    var partType = requiredParts[i];
    var capPart = partType === "fullbody" ? "FullBody" : (partType.charAt(0).toUpperCase() + partType.slice(1));
    var iconText = Trim(V("npcCreator" + capPart + "Icon"));
    var iconId = parseInt(iconText, 10) || 0;
    if (!npcCreatorFilePaths[partType] && iconId <= 0) {
      return "Thiếu ảnh hoặc Icon ID cho phần " + partType + ".";
    }
    if (!npcCreatorFilePaths[partType] && !/^\d+$/.test(iconText)) {
      return "Icon ID của " + partType + " phải là số nguyên dương.";
    }
    var widthText = Trim(V("npcCreator" + capPart + "W"));
    var heightText = Trim(V("npcCreator" + capPart + "H"));
    if (!/^\d+$/.test(widthText) || !/^\d+$/.test(heightText)) {
      return "Kích thước " + partType + " phải là số nguyên dương.";
    }
    var width = parseInt(widthText, 10) || 0;
    var height = parseInt(heightText, 10) || 0;
    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
      return "Kích thước " + partType + " phải nằm trong khoảng 1..4096px.";
    }
  }

  if (isFullBody) {
    var visualDxError = ValidateNpcOffset(V("npcCreatorFullBodyDx"), "FullBody dx");
    if (visualDxError) return visualDxError;
    var visualDyError = ValidateNpcOffset(V("npcCreatorFullBodyDy"), "FullBody dy");
    if (visualDyError) return visualDyError;
    var size = GetNpcPreviewSize("fullbody", 1);
    var encodedDx = 9 - Math.floor(size.width1 / 2) + (parseInt(V("npcCreatorFullBodyDx"), 10) || 0);
    var encodedDy = 16 - size.height1 + (parseInt(V("npcCreatorFullBodyDy"), 10) || 0) + nameLift;
    var encodedDxError = ValidateNpcOffset(String(encodedDx), "FullBody DATA dx");
    if (encodedDxError) return encodedDxError;
    var encodedDyError = ValidateNpcOffset(String(encodedDy), "FullBody DATA dy");
    if (encodedDyError) return encodedDyError;
  } else {
    var offsetFields = [
      ["npcCreatorHeadDx", "Head dx"], ["npcCreatorHeadDy", "Head dy"],
      ["npcCreatorBodyDx", "Body dx"], ["npcCreatorBodyDy", "Body dy"],
      ["npcCreatorLegDx", "Leg dx"], ["npcCreatorLegDy", "Leg dy"]
    ];
    for (var offsetIndex = 0; offsetIndex < offsetFields.length; offsetIndex++) {
      var offsetError = ValidateNpcOffset(V(offsetFields[offsetIndex][0]), offsetFields[offsetIndex][1]);
      if (offsetError) return offsetError;
      if (/Dy$/.test(offsetFields[offsetIndex][0])) {
        var compensatedError = ValidateNpcOffset(String((parseInt(V(offsetFields[offsetIndex][0]), 10) || 0) + nameLift), offsetFields[offsetIndex][1] + " sau khi bù bảng tên");
        if (compensatedError) return compensatedError;
      }
    }
  }
  return "";
}

function PickNpcCreatorById(npcId) {
  for (var i = 1; i < npcCreatorRows.length; i++) {
    if (String(npcCreatorRows[i][0]) === String(npcId)) {
      PickNpcCreator(i);
      return true;
    }
  }
  return false;
}

function SaveNpcCreator() {
  var name = V("npcCreatorName");
  if (!name || Trim(name).length === 0) {
    Msg("npcCreatorMessage", "Vui lòng nhập Tên NPC.");
    return;
  }

  var mode = V("npcCreatorMode") || "fullbody";
  var isFullBody = (mode === "fullbody");

  var normalized = false;
  if (isFullBody) {
    normalized = NormalizeNpcUploadDimensions("fullbody") || normalized;
  } else {
    normalized = NormalizeNpcUploadDimensions("head") || normalized;
    normalized = NormalizeNpcUploadDimensions("body") || normalized;
    normalized = NormalizeNpcUploadDimensions("leg") || normalized;
    if (npcCreatorFilePaths.avatar) normalized = NormalizeNpcUploadDimensions("avatar") || normalized;
  }
  UpdateNpcCreatorPreview();

  var validationError = ValidateNpcCreatorForm(isFullBody);
  if (validationError) {
    Msg("npcCreatorMessage", validationError);
    return;
  }

  var mapId = V("npcCreatorMapSelect");
  var spawnX = V("npcCreatorSpawnX") || "300";
  var spawnY = V("npcCreatorSpawnY") || "336";
  var hasShop = CheckedValue("npcCreatorHasShop");
  var shopTag = V("npcCreatorShopTag") || "SHOP_MUA_BAN";

  Msg("npcCreatorMessage", "Đang xử lý kích thước ảnh, tạo Part và lưu dữ liệu NPC...");

  var payload = {
    Id: V("npcCreatorId"),
    Name: name,
    Mode: mode,
    MapId: mapId,
    SpawnX: spawnX,
    SpawnY: spawnY,
    NameLift: V("npcCreatorNameLift") || "0",
    LayerOrder: V("npcCreatorLayerOrder") || "NATIVE",
    KeepOtherMaps: CheckedValue("npcCreatorKeepOtherMaps"),
    HasShop: hasShop,
    TagName: shopTag
  };

  if (isFullBody) {
    payload.FullBodyPath = npcCreatorFilePaths.fullbody;
    payload.FullBodyIconId = V("npcCreatorFullBodyIcon");
    payload.FullBodyW = V("npcCreatorFullBodyW");
    payload.FullBodyH = V("npcCreatorFullBodyH");
    payload.FullBodyDx = V("npcCreatorFullBodyDx");
    payload.FullBodyDy = V("npcCreatorFullBodyDy");
    payload.AvatarPath = npcCreatorFilePaths.avatar;
    payload.AvatarIconId = V("npcCreatorFbAvatarIcon");
    payload.AvatarW = "180";
    payload.AvatarH = "180";
    payload.HeadPartId = V("npcCreatorHeadPartId");
    payload.BodyPartId = V("npcCreatorBodyPartId");
    payload.LegPartId = V("npcCreatorLegPartId");
  } else {
    payload.HeadPath = npcCreatorFilePaths.head;
    payload.BodyPath = npcCreatorFilePaths.body;
    payload.LegPath = npcCreatorFilePaths.leg;
    payload.AvatarPath = npcCreatorFilePaths.avatar;
    payload.HeadIconId = V("npcCreatorHeadIcon");
    payload.HeadDx = V("npcCreatorHeadDx");
    payload.HeadDy = V("npcCreatorHeadDy");
    payload.HeadW = V("npcCreatorHeadW");
    payload.HeadH = V("npcCreatorHeadH");
    payload.BodyIconId = V("npcCreatorBodyIcon");
    payload.BodyDx = V("npcCreatorBodyDx");
    payload.BodyDy = V("npcCreatorBodyDy");
    payload.BodyW = V("npcCreatorBodyW");
    payload.BodyH = V("npcCreatorBodyH");
    payload.LegIconId = V("npcCreatorLegIcon");
    payload.LegDx = V("npcCreatorLegDx");
    payload.LegDy = V("npcCreatorLegDy");
    payload.LegW = V("npcCreatorLegW");
    payload.LegH = V("npcCreatorLegH");
    payload.AvatarIconId = V("npcCreatorAvatarIcon");
    payload.AvatarW = V("npcCreatorAvatarW");
    payload.AvatarH = V("npcCreatorAvatarH");
    payload.HeadPartId = V("npcCreatorHeadPartId");
    payload.BodyPartId = V("npcCreatorBodyPartId");
    payload.LegPartId = V("npcCreatorLegPartId");
  }

  var text = RunAdmin("savenpccreator", payload);

  var messageText = StatusText(text).replace(/\s+NPC_ID=\d+/g, "");
  if (text.indexOf("OK\t") === 0 || text.indexOf("OK ") === 0) {
    var savedIdMatch = /NPC_ID=(\d+)/.exec(text);
    LoadNpcCreatorList();
    if (savedIdMatch) PickNpcCreatorById(savedIdMatch[1]);
    Msg("npcCreatorMessage", messageText + (normalized ? "\r\nKích thước ảnh đã được chuẩn hóa theo bội số 4 để x1-x4 khớp nhau." : "") + "\r\nPreview đã nạp lại từ Part/Icon vừa lưu. Restart server để áp dụng dữ liệu NPC và Map mới.");
  } else {
    Msg("npcCreatorMessage", messageText);
  }
}

function DeleteNpcCreator() {
  var id = V("npcCreatorId");
  if (!id) {
    Msg("npcCreatorMessage", "Hãy chọn một NPC trong danh sách để xóa.");
    return;
  }
  if (!window.confirm("Bạn có chắc chắn muốn xóa NPC ID " + id + " (" + V("npcCreatorName") + ") khỏi Map và Database?")) {
    return;
  }

  var text = RunAdmin("deletenpccreator", { Id: id });
  Msg("npcCreatorMessage", StatusText(text) + "\r\nRestart server để đồng bộ map.");
  NewNpcCreator();
  LoadNpcCreatorList();
}

var npcCreatorSplitterInitialized = false;
var npcCreatorCachedSplitWidth = 360;

function GetSavedNpcSplitWidth() {
  try {
    if (typeof window !== "undefined" && window.localStorage) {
      var val = window.localStorage.getItem("npcCreatorSplitWidth");
      if (val) {
        var num = parseInt(val, 10);
        if (num > 100) return num;
      }
    }
  } catch (e) {}
  return npcCreatorCachedSplitWidth || 360;
}

function SaveNpcSplitWidth(val) {
  npcCreatorCachedSplitWidth = val;
  try {
    if (typeof window !== "undefined" && window.localStorage) {
      window.localStorage.setItem("npcCreatorSplitWidth", String(val));
    }
  } catch (e) {}
}

function ApplyNpcCreatorSplit(leftW) {
  var leftPane = document.getElementById("npcCreatorLeftPane");
  var splitter = document.getElementById("npcCreatorSplitter");
  var rightPane = document.getElementById("npcCreatorRightPane");
  if (!leftPane || !splitter || !rightPane) return;

  var winW = window.innerWidth || (document.documentElement ? document.documentElement.clientWidth : 1000) || 1000;
  var clampedW = Math.max(220, Math.min(winW - 400, leftW));
  leftPane.style.width = clampedW + "px";
  splitter.style.left = clampedW + "px";
  rightPane.style.left = (clampedW + 8) + "px";
  rightPane.style.width = "calc(100% - " + (clampedW + 8) + "px)";
}

function InitNpcCreatorSplitter() {
  var splitter = document.getElementById("npcCreatorSplitter");
  if (!splitter) return;

  var savedW = GetSavedNpcSplitWidth();
  ApplyNpcCreatorSplit(savedW);

  if (npcCreatorSplitterInitialized) return;
  npcCreatorSplitterInitialized = true;

  var isDragging = false;
  var startX = 0;
  var startLeftW = 360;

  splitter.onmousedown = function (e) {
    e = e || window.event;
    isDragging = true;
    startX = e.clientX;
    var leftPane = document.getElementById("npcCreatorLeftPane");
    startLeftW = leftPane ? leftPane.offsetWidth : 360;
    splitter.className = "npc-splitter-handle dragging";
    document.body.style.cursor = "col-resize";
    if (e.preventDefault) e.preventDefault();
    return false;
  };

  var prevMouseMove = document.onmousemove;
  document.onmousemove = function (e) {
    if (typeof prevMouseMove === "function") prevMouseMove(e);
    if (!isDragging) return;
    e = e || window.event;
    var deltaX = e.clientX - startX;
    var newW = startLeftW + deltaX;
    ApplyNpcCreatorSplit(newW);
  };

  var prevMouseUp = document.onmouseup;
  document.onmouseup = function (e) {
    if (typeof prevMouseUp === "function") prevMouseUp(e);
    if (!isDragging) return;
    isDragging = false;
    splitter.className = "npc-splitter-handle";
    document.body.style.cursor = "";

    var leftPane = document.getElementById("npcCreatorLeftPane");
    if (leftPane) {
      SaveNpcSplitWidth(leftPane.offsetWidth);
    }
  };
}

RegisterTab({
  id: "npccreator",
  view: "npc-creator.html",
  panelId: "panelNpcCreator",
  navId: "navNpcCreator",
  title: "Tạo & Quản lý NPC",
  subtitle: "Tạo NPC trực quan, upload 1 ảnh toàn thân hoặc ghép 3 phần, điều chỉnh kích thước và đặt map",
  onOpen: function () {
    InitNpcCreatorSplitter();
    EnsureNpcCreatorMaps();
    LoadNpcCreatorList();
    UpdateNpcCreatorPreview();
  },
  onRefresh: function () {
    LoadNpcCreatorList();
  }
});
