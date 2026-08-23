package nro.models.services;

import java.util.List;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.skill.SkillMasteryConfig;
import nro.models.utils.SkillUtil;

/** Điều phối tiến trình, tự lên cấp và chỉ số thực của kỹ năng sau cấp 7. */
public final class SkillMasteryService {

    private static final SkillMasteryService INSTANCE = new SkillMasteryService();

    private SkillMasteryService() {
    }

    public static SkillMasteryService gI() {
        return INSTANCE;
    }

    /**
     * Khôi phục state từ JSON cũ/mới. originalPoint được giữ lại để chuyển
     * tuyệt kỹ cấp 8-9 cũ sang visual cấp 7 mà không mất cấp thật.
     */
    public void initializeSkill(Skill skill, int originalPoint, int savedActualLevel,
            long savedProgress, boolean hasStoredMasteryState) {
        if (!isRealSkill(skill)) {
            return;
        }
        int visualLevel = Math.max(0, Math.min(SkillMasteryConfig.getBaseLevel(), skill.point));
        skill.point = visualLevel;
        skill.masteryLevel = Math.max(visualLevel,
                hasStoredMasteryState ? savedActualLevel : originalPoint);
        skill.masteryProgress = Math.max(0L, savedProgress);

        if (!hasStoredMasteryState && originalPoint == SkillMasteryConfig.getBaseLevel()
                && skill.currLevel > 0) {
            long required = SkillMasteryConfig.getRequiredUses(skill.template.id, skill.masteryLevel);
            skill.masteryProgress = Math.min(required,
                    required * Math.min(1_000, skill.currLevel) / 1_000L);
        }
        normalizeProgressWithoutNotification(skill);
        applyConfiguredStats(skill);
        refreshClientProgress(skill);
    }

    /** Khởi tạo state cho skill vừa được học/nâng theo cơ chế cấp 1-7 cũ. */
    public void initializeNewSkill(Skill skill) {
        if (!isRealSkill(skill)) {
            return;
        }
        skill.masteryLevel = skill.point;
        skill.masteryProgress = 0L;
        skill.currLevel = 0;
        skill.lastMasteryGainTime = 0L;
        applyConfiguredStats(skill);
    }

    /**
     * Ghi nhận đúng một lần dùng đã hoàn tất. Với tuyệt kỹ cấp 1-6, vẫn duy
     * trì thanh luyện 0..1000 để tương thích luồng học tại Whis.
     */
    public void recordValidUse(Player player, Skill skill) {
        if (!canGain(player, skill)) {
            return;
        }
        synchronized (skill) {
            long now = System.currentTimeMillis();
            int minInterval = SkillMasteryConfig.getMinGainIntervalMs(skill.template.id);
            if (now - skill.lastMasteryGainTime < minInterval) {
                return;
            }
            skill.lastMasteryGainTime = now;

            if (skill.template.type == 4 && skill.point < SkillMasteryConfig.getBaseLevel()) {
                increaseLegacySpecialProgress(player, skill);
                return;
            }
            ensureState(skill);
            // Đồng bộ cấu hình nóng cho nhân vật đang online. Lần sử dụng hiện
            // tại đã hoàn tất; các chỉ số mới có hiệu lực từ lần kế tiếp.
            applyConfiguredStats(skill);
            if (skill.getActualLevel() < SkillMasteryConfig.getBaseLevel()
                    || skill.getActualLevel() >= SkillMasteryConfig.getMaxLevel(skill.template.id)) {
                return;
            }

            int oldClientProgress = getClientProgress(skill);
            long gain = SkillMasteryConfig.getGainPerUse(skill.template.id, skill.getActualLevel());
            skill.masteryProgress = safeAdd(skill.masteryProgress, gain);
            boolean leveledUp = false;
            while (skill.getActualLevel() < SkillMasteryConfig.getMaxLevel(skill.template.id)) {
                long required = SkillMasteryConfig.getRequiredUses(skill.template.id, skill.getActualLevel());
                if (skill.masteryProgress < required) {
                    break;
                }
                skill.masteryProgress -= required;
                skill.masteryLevel++;
                leveledUp = true;
            }

            if (leveledUp) {
                applyConfiguredStats(skill);
                Service.gI().sendThongBao(player, "Kỹ năng " + skill.template.name
                        + " đã tự động lên cấp " + skill.getActualLevel() + "!");
                Service.gI().sendTimeSkill(player, skill);
            }
            refreshClientProgress(skill);
            int newClientProgress = getClientProgress(skill);
            if (leveledUp || crossedClientStep(oldClientProgress, newClientProgress)) {
                SkillService.gI().sendCurrLevelSpecial(player, skill);
            }
        }
    }

    public void applyConfiguredStats(Skill skill) {
        if (!isRealSkill(skill) || skill.point <= 0) {
            return;
        }
        List<Skill> configuredLevels = SkillUtil.findSkills(skill.template.id);
        if (configuredLevels == null || configuredLevels.isEmpty()) {
            return;
        }
        int visualLevel = Math.min(SkillMasteryConfig.getBaseLevel(), skill.point);
        if (visualLevel < 1 || visualLevel > configuredLevels.size()) {
            return;
        }
        Skill base = configuredLevels.get(visualLevel - 1);
        skill.skillId = base.skillId;
        skill.powRequire = base.powRequire;
        skill.maxFight = SkillMasteryConfig.applyMaxFight(skill, base.maxFight);
        skill.price = base.price;
        skill.moreInfo = base.moreInfo;
        skill.damage = SkillMasteryConfig.applyDamage(skill, base.damage);
        skill.manaUse = SkillMasteryConfig.applyMana(skill, base.manaUse);
        skill.coolDown = SkillMasteryConfig.applyCooldown(skill, base.coolDown);
        skill.dx = SkillMasteryConfig.applyRangeX(skill, base.dx);
        skill.dy = SkillMasteryConfig.applyRangeY(skill, base.dy);
    }

    public int applyEffectStat(Skill skill, int baseValue) {
        return SkillMasteryConfig.applyEffect(skill, baseValue);
    }

    public int applyDamageStat(Skill skill, int baseValue) {
        return SkillMasteryConfig.applyDamageStat(skill, baseValue);
    }

    public int applyRangeStat(Skill skill, int baseValue) {
        return SkillMasteryConfig.applyRange(skill, baseValue);
    }

    public int getClientProgress(Skill skill) {
        if (!isRealSkill(skill)) {
            return 0;
        }
        if (skill.template.type == 4 && skill.point < SkillMasteryConfig.getBaseLevel()) {
            return Math.max(0, Math.min(1_000, skill.currLevel));
        }
        ensureState(skill);
        if (skill.getActualLevel() < SkillMasteryConfig.getBaseLevel()) {
            return 0;
        }
        if (skill.getActualLevel() >= SkillMasteryConfig.getMaxLevel(skill.template.id)) {
            return 1_000;
        }
        long required = SkillMasteryConfig.getRequiredUses(skill.template.id, skill.getActualLevel());
        return (int) Math.max(0L, Math.min(1_000L, skill.masteryProgress * 1_000L / required));
    }

    public long getRequiredProgress(Skill skill) {
        return isRealSkill(skill)
                ? SkillMasteryConfig.getRequiredUses(skill.template.id, Math.max(SkillMasteryConfig.getBaseLevel(), skill.getActualLevel()))
                : 0L;
    }

    public boolean shouldSendProgressToClient(Skill skill) {
        if (!isRealSkill(skill) || skill.point <= 0) {
            return false;
        }
        if (skill.template.type == 4) {
            return skill.currLevel > 0 || skill.point >= SkillMasteryConfig.getBaseLevel();
        }
        return SkillMasteryConfig.sendProgressForAllSkills()
                && skill.getActualLevel() >= SkillMasteryConfig.getBaseLevel();
    }

    public void sendMasterySummary(Player player) {
        if (player == null || player.playerSkill == null) {
            return;
        }
        StringBuilder info = new StringBuilder("|1|THÀNH THẠO KỸ NĂNG");
        for (Skill skill : player.playerSkill.skills) {
            if (!isRealSkill(skill) || skill.point <= 0) {
                continue;
            }
            info.append("\n|2|").append(skill.template.name)
                    .append(": cấp ").append(skill.getActualLevel());
            if (skill.getActualLevel() >= SkillMasteryConfig.getBaseLevel()) {
                info.append(" - ").append(skill.masteryProgress).append("/")
                        .append(getRequiredProgress(skill));
            } else if (skill.template.type == 4) {
                info.append(" - luyện ").append(Math.max(0, skill.currLevel)).append("/1000");
            }
        }
        info.append("\n|7|Gõ 'thành thạo' để xem lại.");
        Service.gI().sendThongBaoOK(player, info.toString());
    }

    public void refreshClientProgress(Skill skill) {
        if (isRealSkill(skill) && !(skill.template.type == 4 && skill.point < SkillMasteryConfig.getBaseLevel())) {
            skill.currLevel = (short) getClientProgress(skill);
        }
    }

    private void increaseLegacySpecialProgress(Player player, Skill skill) {
        int oldProgress = Math.max(0, Math.min(1_000, skill.currLevel));
        if (oldProgress >= 1_000) {
            return;
        }
        skill.currLevel = (short) Math.min(1_000, oldProgress + 1);
        if (crossedClientStep(oldProgress, skill.currLevel)) {
            SkillService.gI().sendCurrLevelSpecial(player, skill);
        }
    }

    private boolean canGain(Player player, Skill skill) {
        return SkillMasteryConfig.isEnabled()
                && player != null && player.isPl() && player.zone != null
                && !SkillMasteryConfig.isMapExcluded(player.zone.map.mapId)
                && isRealSkill(skill) && skill.point > 0;
    }

    private boolean crossedClientStep(int oldProgress, int newProgress) {
        int step = SkillMasteryConfig.getClientProgressStep();
        return oldProgress / step != newProgress / step;
    }

    private void ensureState(Skill skill) {
        if (skill.masteryLevel <= 0) {
            skill.masteryLevel = skill.point;
        }
        if (skill.masteryProgress < 0) {
            skill.masteryProgress = 0;
        }
    }

    private void normalizeProgressWithoutNotification(Skill skill) {
        ensureState(skill);
        while (skill.getActualLevel() >= SkillMasteryConfig.getBaseLevel()
                && skill.getActualLevel() < SkillMasteryConfig.getMaxLevel(skill.template.id)) {
            long required = SkillMasteryConfig.getRequiredUses(skill.template.id, skill.getActualLevel());
            if (skill.masteryProgress < required) {
                break;
            }
            skill.masteryProgress -= required;
            skill.masteryLevel++;
        }
    }

    private boolean isRealSkill(Skill skill) {
        return skill != null && skill.skillId != -1 && skill.template != null;
    }

    private long safeAdd(long first, long second) {
        return second > 0 && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
