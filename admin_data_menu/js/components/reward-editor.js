// Data-only reward helpers shared by configuration tabs. Renderers keep ownership
// of their DOM so each tab can choose its own layout without copying validation shape.
function ActivityRewardEditorDefault(kind) {
  return {
    kind: kind || "ITEM", itemId: 0, quantityMin: 1, quantityMax: 1, gender: 3,
    bindMode: "BOUND", initBaseOptions: false, useDefaultOptions: false,
    options: [], expiryDaysMin: 0, expiryDaysMax: 0
  };
}

function ActivityRewardEditorNormalize(reward) {
  if (!reward) return ActivityRewardEditorDefault("ITEM");
  reward.kind = (reward.kind || "ITEM").toUpperCase();
  reward.itemId = parseInt(reward.itemId == null ? 0 : reward.itemId, 10);
  reward.quantityMin = parseInt(reward.quantityMin == null ? 1 : reward.quantityMin, 10);
  reward.quantityMax = parseInt(reward.quantityMax == null ? reward.quantityMin : reward.quantityMax, 10);
  reward.gender = parseInt(reward.gender == null ? 3 : reward.gender, 10);
  reward.bindMode = (reward.bindMode || "BOUND").toUpperCase();
  reward.initBaseOptions = !!reward.initBaseOptions;
  reward.useDefaultOptions = !!reward.useDefaultOptions;
  reward.expiryDaysMin = parseInt(reward.expiryDaysMin == null ? 0 : reward.expiryDaysMin, 10);
  reward.expiryDaysMax = parseInt(reward.expiryDaysMax == null ? reward.expiryDaysMin : reward.expiryDaysMax, 10);
  reward.options = reward.options || [];
  return reward;
}

function ActivityRewardEditorFindOption(reward, optionId) {
  if (!reward || !reward.options) return null;
  for (var i = 0; i < reward.options.length; i++) {
    if (("" + reward.options[i].id) == ("" + optionId)) return reward.options[i];
  }
  return null;
}

function ActivityRewardEditorIsItem(reward) {
  return reward && (reward.kind || "").toUpperCase() == "ITEM";
}

function ActivityRewardEditorSummary(reward) {
  reward = ActivityRewardEditorNormalize(reward);
  var core = reward.kind == "ITEM" ? ("Item " + reward.itemId) : reward.kind;
  return core + " x" + reward.quantityMin + (reward.quantityMax != reward.quantityMin ? ("–" + reward.quantityMax) : "") +
    (reward.options.length ? (", " + reward.options.length + " option") : "") +
    (reward.expiryDaysMax ? (", HSD " + reward.expiryDaysMin + "–" + reward.expiryDaysMax + " ngày") : "");
}
