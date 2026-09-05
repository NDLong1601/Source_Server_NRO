package nro.models.player;

import nro.models.radar.Card;
import nro.models.radar.OptionCard;
import nro.models.consts.ConstItem;
import nro.models.consts.ConstPlayer;
import nro.models.consts.ConstRatio;
import nro.models.intrinsic.Intrinsic;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.skill.Skill;
import nro.models.server.Manager;
import nro.models.services.EffectSkillService;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillMasteryService;
import nro.models.services.TaskService;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import lombok.Setter;
import nro.models.mob.Mob;
import nro.models.player_badges.BagesTemplate;
import static nro.models.player_badges.BagesTemplate.sendListItemOption;
import nro.models.utils.TimeUtil;
import nro.models.clan.ClanProgressionService;

/**
 *
 * @author By Mr Blue
 *
 */
public class NPoint {

    // Keep this table in sync with Panel.t_tiemnang in the Unity client.
    // Index is the current base critical level before one point is added.
    private static final long[] CRITICAL_POTENTIAL_COSTS = {
        50_000_000L, 250_000_000L, 1_250_000_000L, 5_000_000_000L,
        15_000_000_000L, 30_000_000_000L, 45_000_000_000L,
        60_000_000_000L, 75_000_000_000L, 90_000_000_000L,
        110_000_000_000L, 130_000_000_000L, 150_000_000_000L,
        170_000_000_000L
    };

    public static final byte MAX_LIMIT = 9;

    @Setter
    private Player player;
    long timeXinbatoBuff;

    public NPoint(Player player) {
        this.player = player;
        this.tlHp = new ArrayList<>();
        this.tlMp = new ArrayList<>();
        this.tlDef = new ArrayList<>();
        this.tlDame = new ArrayList<>();
        this.tlDameAttMob = new ArrayList<>();
        this.tlTNSM = new ArrayList<>();
        this.tlDameCrit = new ArrayList<>();
    }

    public boolean isCrit;
    public boolean isCrit100;
    public boolean isCritTele;

    private Intrinsic intrinsic;
    private int percentDameIntrinsic;
    public int dameAfter;

    /*-----------------------Chỉ số cơ bản------------------------------------*/
    public byte numAttack;
    public short stamina, maxStamina;

    public byte limitPower;
    public long power;
    public long tiemNang;

    public int hp, hpMax, hpg;
    public int mp, mpMax, mpg;
    public int dame, dameg;
    public int def, defg;
    public int crit, critg, critdragon;
    public byte speed = 8;

    public boolean teleport;

    public boolean khangTDHS;

    /**
     * Chỉ số cộng thêm
     */
    public int hpAdd, mpAdd, dameAdd, defAdd, critAdd, hpHoiAdd, mpHoiAdd;

    /**
     * //+#% sức đánh chí mạng
     */
    public List<Integer> tlDameCrit;
    public int tlSDCM;

    /**
     * Tỉ lệ hp, mp cộng thêm
     */
    public List<Integer> tlHp, tlMp;

    /**
     * Tỉ lệ giáp cộng thêm
     */
    public List<Integer> tlDef;

    /**
     * Tỉ lệ sức đánh/ sức đánh khi đánh quái
     */
    public List<Integer> tlDame, tlDameAttMob;

    /**
     * Lượng hp, mp hồi mỗi 30s, mp hồi cho người khác
     */
    public int hpHoi, mpHoi, mpHoiCute;

    /**
     * Tỉ lệ hp, mp hồi cộng thêm
     */
    public short tlHpHoi, tlMpHoi;

    /**
     * Tỉ lệ hp, mp hồi bản thân và đồng đội cộng thêm
     */
    public short tlHpHoiBanThanVaDongDoi, tlMpHoiBanThanVaDongDoi;

    /**
     * Tỉ lệ hút hp, mp khi đánh, hp khi đánh quái
     */
    public short tlHutHp, tlHutMp, tlHutHpMob;

    /**
     * Tỉ lệ hút hp, mp xung quanh mỗi 5s
     */
    public short tlHutHpMpXQ;

    /**
     * Tỉ lệ phản sát thương
     */
    public short tlPST;

    /**
     * Các option chiến đấu đặc biệt: hồi KI khi bị đánh, sát thương chuẩn và
     * phản đòn cận chiến cố định.
     */
    public int tlHoiKiKhiBiDanh;
    public int tlSatThuongChuan;
    public int phanDonCanChien;
    public int congDucTriThuong;
    public int tlDameQuaiBay;
    public int tlDameQuaiKhi;
    public int tlDameQuaiMatDat;
    public int tlDameTocNamec;
    public int tlDameTocTraiDat;
    /** Options 183, 188-191 and 197-204. */
    public int shieldCooldownReductionPercent;
    public int blindDurationReductionSeconds;
    public int petKamejokoDamageBonus;
    public int petAntomicDamageBonus;
    public int petMasenkoDamageBonus;
    public int criticalEvasionPercent;
    public int tlDameTocXayda;
    public int damageReductionFromTraiDat;
    public int damageReductionFromNamec;
    public int damageReductionFromXayda;
    public int bossTrueDamagePercent;
    /** Options 221, 222, 225, 227 and 229. */
    public int clanRecoveryPercent;
    public int rageDamagePercent;
    public int maPhongBaReductionSeconds;
    public int shieldControlReductionPercent;
    public int coldAuraDrainPercent;

    /**
     * Tỉ lệ tiềm năng sức mạnh
     */
    public List<Integer> tlTNSM;

    /**
     * Option 62 restores this percentage of max stamina for every full hour
     * the equipment remains worn. Option 79 is granted from master to pet.
     * Option 78 is a final attack-damage amplifier.
     */
    public int tlHoiTheLuc;
    public int tlDameDeTu;
    public int tlSucHuyDiet;

    /**
     * Tỉ lệ vàng cộng thêm
     */
    public short tlGold;

    /**
     * Tỉ lệ né đòn
     */
    public short tlNeDon;
    public short tlNeDonXinbato;

    public short tlBom;

    public short tlGiap;

    public short tlxgcc;

    public short tlxgc;

    public short tlchinhxac;

    public short tlTNSMPet;
    public short xChuong;

    /** Equipment options 151-180 that feed runtime combat and recovery logic. */
    public int option156MaxPunchBonus;
    public int lowKiDamageReduction;
    public int dailyGemFromMob;
    public int cuteMpRecoveryPercent;
    public int flyingMobDamagePercent;
    public int tdhsDurationReductionPercent;
    public int mpRecovery10SecondsPercent;
    public boolean canFindEventItemBare;
    public boolean isBienPumpkin;
    public boolean hasFireStrike;
    public boolean hasColdAura;
    public boolean hasAbsorbBurst;
    public boolean hasPoisonStrike;

    public short setTinhAn;
    public short setNhatAn;
    public short setNguyetAn;

    /**
     * Tỉ lệ sức đánh đẹp cộng thêm cho bản thân và người xung quanh
     */
    public int tlSexyDame;

    /**
     * Tỉ lệ giảm sức đánh
     */
    public short tlSubSD;

    public int voHieuChuong;

    /*------------------------Effect skin-------------------------------------*/
    public Item trainArmor;
    public boolean wearingTrainArmor;

    public boolean wearingVoHinh;
    public boolean isKhongLanh;
    public boolean isTinhAn;
    public boolean isNhatAn;
    public boolean isNguyetAn;
    public boolean isTanHinh;
    public boolean isHoaDa;
    public boolean khangHoaDa;
    public boolean isLamCham;
    public boolean isBienSocola;
    public boolean bienSocola30Seconds;
    public boolean isBienCarrot;
    public boolean isDoSPL;
    public boolean isThoBulma;
    public boolean hasVipEquipment;
    public boolean khongBiQuaiChuDongTanCong;
    public boolean flyWithoutKi;
    public boolean flyAndRecoverKi;
    public boolean flyAndRecoverHpKi;

    public short tlHpGiamODo;

    public boolean isGogeta;

    public int tlSpeed;

    public int levelBT;

    public int tlNeDonBuffXinbato = 0;

    /*-------------------------------------------------------------------------*/
    /**
     * Tính toán mọi chỉ số sau khi có thay đổi
     */
    public void calPoint() {
        if (this.player.pet != null) {
            this.player.pet.nPoint.setPointWhenWearClothes();
        }
        this.setPointWhenWearClothes();
        if (this.player.pet != null) {
            this.player.pet.nPoint.applyMasterDiscipleDamageBonus();
        }
        if (this.player.isPet) {
            applyMasterDiscipleDamageBonus();
        }
    }

    public int getRealTlNeDon() {
        int total = this.tlNeDon;
        if (tlNeDonBuffXinbato > 0) {
            total += tlNeDonBuffXinbato;
        }
        return Math.min(total, 90);
    }

    /** Applies options 42-44 after the target mob has been identified. */
    public long applyDamageBonusAgainst(Mob mob, long damage) {
        if (mob == null || damage <= 0) {
            return damage;
        }
        if (this.player.effectSkin != null && this.player.effectSkin.applyingEquipmentOptionDamage) {
            return damage;
        }
        int bonusPercent = 0;
        if (mob.type == 4) {
            bonusPercent += tlDameQuaiBay;
        }
        if (mob.type == 1) {
            bonusPercent += tlDameQuaiMatDat;
        }
        if (isMonkeyMob(mob)) {
            bonusPercent += tlDameQuaiKhi;
        }
        if (this.player.isFly) {
            bonusPercent += flyingMobDamagePercent;
        }
        long result = addTargetDamageBonus(damage, bonusPercent);
        return applyDestructionDamage(result);
    }

    /** Applies options 45-46 and 197 against the matching player race. */
    public long applyDamageBonusAgainst(Player target, long damage) {
        if (target == null || damage <= 0) {
            return damage;
        }
        if (this.player.effectSkin != null && this.player.effectSkin.applyingEquipmentOptionDamage) {
            return damage;
        }
        int bonusPercent = 0;
        if (target.gender == ConstPlayer.NAMEC) {
            bonusPercent += tlDameTocNamec;
        } else if (target.gender == ConstPlayer.TRAI_DAT) {
            bonusPercent += tlDameTocTraiDat;
        } else if (target.gender == ConstPlayer.XAYDA) {
            bonusPercent += tlDameTocXayda;
        }
        long result = addTargetDamageBonus(damage, bonusPercent);
        return applyDestructionDamage(result);
    }

    /** Final damage reduction against a player whose race is known. */
    public int getRaceDamageReductionFrom(Player attacker) {
        if (attacker == null) {
            return 0;
        }
        return switch (attacker.gender) {
            case ConstPlayer.TRAI_DAT -> Math.min(100, Math.max(0, damageReductionFromTraiDat));
            case ConstPlayer.NAMEC -> Math.min(100, Math.max(0, damageReductionFromNamec));
            case ConstPlayer.XAYDA -> Math.min(100, Math.max(0, damageReductionFromXayda));
            default -> 0;
        };
    }

    /**
     * Option 78 is deliberately applied after skill/critical damage has been
     * calculated by getDameAttack.  It does not replace a normal stat bonus.
     */
    private long applyDestructionDamage(long damage) {
        if (damage <= 0 || tlSucHuyDiet <= 0) {
            return damage;
        }
        long result = damage + damage * tlSucHuyDiet / 100L;
        return Math.min(result, Integer.MAX_VALUE);
    }

    /** Applies option 79 after the master's equipped options are recalculated. */
    private void applyMasterDiscipleDamageBonus() {
        if (!this.player.isPet || !(this.player instanceof Pet)) {
            return;
        }
        Pet pet = (Pet) this.player;
        if (pet.master == null || pet.master.nPoint == null || pet.master.nPoint.tlDameDeTu <= 0) {
            return;
        }
        long boostedDamage = (long) this.dame
                + (long) this.dame * pet.master.nPoint.tlDameDeTu / 100L;
        this.dame = (int) Math.min(Integer.MAX_VALUE, boostedDamage);
    }

    /** Applies the master's option 188-190 bonus to the matching pet skill. */
    private long applyMasterPetSkillDamageBonus(Skill skill, long damage) {
        if (!this.player.isPet || !(this.player instanceof Pet) || skill == null
                || skill.template == null || damage <= 0) {
            return damage;
        }
        Pet pet = (Pet) this.player;
        if (pet.master == null || pet.master.nPoint == null) {
            return damage;
        }
        int bonus = switch (skill.template.id) {
            case Skill.KAMEJOKO -> pet.master.nPoint.petKamejokoDamageBonus;
            case Skill.ANTOMIC -> pet.master.nPoint.petAntomicDamageBonus;
            case Skill.MASENKO -> pet.master.nPoint.petMasenkoDamageBonus;
            default -> 0;
        };
        return addTargetDamageBonus(damage, Math.max(0, bonus));
    }

    private long addTargetDamageBonus(long damage, int bonusPercent) {
        if (bonusPercent <= 0) {
            return damage;
        }
        long result = damage + damage * bonusPercent / 100L;
        return Math.min(result, Integer.MAX_VALUE);
    }

    private boolean isMonkeyMob(Mob mob) {
        nro.models.player_system.Template.MobTemplate template = Manager.getMobTemplateByTemp(mob.tempId);
        return template != null && template.name != null
                && template.name.toLowerCase(Locale.ROOT).contains("khỉ");
    }

    private void setPointWhenWearClothes() {
        resetPoint();
        if (this.player.rewardBlackBall.timeOutOfDateReward[2] > System.currentTimeMillis()) {
            tlHutHp += RewardBlackBall.R3S_1;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[3] > System.currentTimeMillis()) {
            tlPST += RewardBlackBall.R4S_2;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[4] > System.currentTimeMillis()) {
            tlDameCrit.add(RewardBlackBall.R5S_1);
            tlSDCM += RewardBlackBall.R5S_1;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[6] > System.currentTimeMillis()) {
            tlNeDon += RewardBlackBall.R7S_1;
        }
        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 108) {
                tlNeDon += (tlNeDon * opt.param / 100L);
            }
        }

        Card card = player.Cards.stream().filter(r -> r != null && r.Used == 1).findFirst().orElse(null);
        if (card != null) {
            for (OptionCard io : card.Options) {
                if (io.active == card.Level || (card.Level == -1 && io.active == 0)) {
                    switch (io.id) {
                        case 0: //Tấn công +#
                            this.dameAdd += io.param;
                            break;
                        case 2: //HP, KI+#000
                            this.hpAdd += io.param * 1000;
                            this.mpAdd += io.param * 1000;
                            break;
                        case 3:// vô hiệu chưởng
                            this.voHieuChuong += io.param;
                            break;
                        case 5: //+#% sức đánh chí mạng
                            this.tlDameCrit.add(io.param);
                            this.tlSDCM += io.param;
                            break;
                        case 6: //HP+#
                            this.hpAdd += io.param;
                            break;
                        case 7: //KI+#
                            this.mpAdd += io.param;
                            break;
                        case 8: //Hút #% HP, KI xung quanh mỗi 5 giây
                            this.tlHutHpMpXQ += io.param;
                            break;
                        case 14: //Chí mạng+#%
                            this.critAdd += io.param;
                            break;
                        case 16: // Speed
                        case 114:
                        case 148:
                            this.tlSpeed += io.param;
                            break;
                        case 18: //Chinh xac
                            this.tlchinhxac += io.param;
                            break;
                        case 19: //Tấn công+#% khi đánh quái
                            this.tlDameAttMob.add(io.param);
                            break;
                        case 22: //HP+#K
                            this.hpAdd += io.param * 1000;
                            break;
                        case 23: //MP+#K
                            this.mpAdd += io.param * 1000;
                            break;
                        case 27: //+# HP/30s
                            this.hpHoiAdd += io.param;
                            break;
                        case 28: //+# KI/30s
                            this.mpHoiAdd += io.param;
                            break;
                        case 32: //Không bị hóa xương/hóa đá
                            this.khangHoaDa = true;
                            break;
                        case 33: //dịch chuyển tức thời
                            this.teleport = true;
                            break;
                        case 34:
                            this.setTinhAn += 1;
                            break;
                        case 35:
                            this.setNguyetAn += 1;
                            break;
                        case 36:
                            this.setNhatAn += 1;
                            break;
                        case 42: //Tấn công +#% lên quái bay
                            this.tlDameQuaiBay += io.param;
                            break;
                        case 43: //Tấn công +#% lên quái khỉ
                            this.tlDameQuaiKhi += io.param;
                            break;
                        case 44: //Tấn công +#% lên quái mặt đất
                            this.tlDameQuaiMatDat += io.param;
                            break;
                        case 45: //Tấn công +#% lên tộc Namếc
                            this.tlDameTocNamec += io.param;
                            break;
                        case 46: //Tấn công +#% lên tộc Trái đất
                            this.tlDameTocTraiDat += io.param;
                            break;
                        case 47: //Giáp+#
                            this.defAdd += io.param;
                            break;
                        case 48: //HP/KI+#
                            this.hpAdd += io.param;
                            this.mpAdd += io.param;
                            break;
                        case 49: //Tấn công+#%
                        case 50: //Sức đánh+#%
                            this.tlDame.add(io.param);
                            break;
                        case 62: //Phục hồi thể lực #% mỗi giờ khi còn mặc
                            this.tlHoiTheLuc += Math.max(0, io.param);
                            break;
                        case 76: //VIP: cộng 10% chỉ số cơ bản cho nhân vật
                            this.hasVipEquipment = true;
                            break;
                        case 77: //HP+#%
                            this.tlHp.add(io.param);
                            break;
                        case 78: //Sức hủy diệt +#% sát thương cuối
                            this.tlSucHuyDiet += Math.max(0, io.param);
                            break;
                        case 79: //Đệ tử +#% sức đánh
                            this.tlDameDeTu += Math.max(0, io.param);
                            break;
                        case 80: //HP+#%/30s
                            this.tlHpHoi += io.param;
                            break;
                        case 81: //MP+#%/30s
                            this.tlMpHoi += io.param;
                            break;
                        case 82: //Quái không chủ động đánh, giảm 20% sát thương quái
                            this.khongBiQuaiChuDongTanCong = true;
                            break;
                        case 83: //Tăng SM, TN khi đánh quái
                            this.tlTNSM.add(io.param);
                            break;
                        case 84: //Bay không tốn KI
                            this.flyWithoutKi = true;
                            break;
                        case 85: //Bay và phục hồi KI
                            this.flyAndRecoverKi = true;
                            break;
                        case 88: //Cộng #% exp khi đánh quái
                            this.tlTNSM.add(io.param);
                            break;
                        case 89: //Bay và phục hồi HP, KI
                            this.flyAndRecoverHpKi = true;
                            break;
                        case 94: //Giáp #%
                            this.tlGiap += io.param;
                            break;
                        case 95: //Biến #% tấn công thành HP
                            this.tlHutHp += io.param;
                            break;
                        case 96: //Biến #% tấn công thành MP
                            this.tlHutMp += io.param;
                            break;
                        case 97: //Phản #% sát thương
                            this.tlPST += io.param;
                            break;
                        case 98: //Xuyen giap chuong
                            this.tlxgc += io.param;
                            break;
                        case 99: //Xuyen giap can chien
                            this.tlxgcc += io.param;
                            break;
                        case 100: //+#% vàng từ quái
                            this.tlGold += io.param;
                            break;
                        case 101: //+#% TN,SM
                            this.tlTNSM.add(io.param);
                            break;
                        case 103: //KI +#%
                            this.tlMp.add(io.param);
                            break;
                        case 104: //Biến #% tấn công quái thành HP
                            this.tlHutHpMob += io.param;
                            break;
                        case 105: //Vô hình khi không đánh quái và boss
                            this.wearingVoHinh = true;
                            break;
                        case 106: //Không ảnh hưởng bởi cái lạnh
                            this.isKhongLanh = true;
                            break;
                        case 111:
                        case 108: //#% Né đòn
                            this.tlNeDon += io.param;
                            break;
                        case 109: //Hôi, giảm #% HP
                            this.tlHpGiamODo += io.param;
                            break;
                        case 116: //Kháng thái dương hạ san
                            this.khangTDHS = true;
                            break;
                        case 226:
                        case 117: //Đẹp +#% SĐ cho mình và người xung quanh
                            if (io.param > this.tlSexyDame) {
                                this.tlSexyDame = io.param;
                            }
                            break;
                        case 147: //+#% sức đánh
                            this.tlDame.add(io.param);
                            break;
                        case 155: //Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng từ quái
                            this.tlSubSD += 50;
                            this.tlHp.add(-50);
                            this.tlMp.add(-50);
                            this.tlTNSM.add(io.param);
                            this.tlGold += io.param;
                            break;
                        case 156: //Cộng dồn sức đánh khi chỉ dùng chiêu đấm
                            this.option156MaxPunchBonus += Math.max(0, io.param);
                            break;
                        case 157: //Giảm sát thương khi KI dưới 20%
                            this.lowKiDamageReduction += Math.max(0, io.param);
                            break;
                        case 158: //Ở trần có cơ hội tìm thấy vật phẩm sự kiện
                            this.canFindEventItemBare = true;
                            break;
                        case 160: //TN, SM cho đệ tử
                            this.tlTNSMPet += io.param;
                            break;
                        case 161: //Ngọc mỗi ngày từ lần hạ quái đầu tiên
                            this.dailyGemFromMob += Math.max(0, io.param);
                            break;
                        case 162: //Cute hồi #% KI/s bản thân và xung quanh
                            this.cuteMpRecoveryPercent += Math.max(0, io.param);
                            break;
                        case 163: //Biến người xung quanh thành bí ngô
                            this.isBienPumpkin = true;
                            break;
                        case 165: //Đòn lửa khi cận chiến
                            this.hasFireStrike = true;
                            break;
                        case 166: //Tăng sức đánh quái khi bay với Tàu Pảy Pảy
                        case 170: //Tăng sức đánh quái khi bay với Cải Trang nhà Fide
                            this.flyingMobDamagePercent += Math.max(0, io.param);
                            break;
                        case 167: //Tạo không khí lạnh
                            this.hasColdAura = true;
                            break;
                        case 168: //Hấp thụ sức mạnh rồi bộc phá
                            this.hasAbsorbBurst = true;
                            break;
                        case 169: //Cơ hội hạ độc đối thủ
                            this.hasPoisonStrike = true;
                            break;
                        case 175: //Giảm thời gian Thái Dương Hạ San
                            this.tdhsDurationReductionPercent += Math.max(0, io.param);
                            break;
                        case 178: //KI +#%/10s
                            this.mpRecovery10SecondsPercent += Math.max(0, io.param);
                            break;
                        case 221: //Hồi HP, KI cho bản thân và đồng bang mỗi 30 giây
                            this.clanRecoveryPercent += Math.max(0, io.param);
                            break;
                        case 222: //Kích nộ sức đánh trong 30 giây
                            this.rageDamagePercent += Math.max(0, io.param);
                            break;
                        case 225: //Giảm thời gian bị Ma Phong Ba
                            this.maPhongBaReductionSeconds += Math.max(0, io.param);
                            break;
                        case 227: //Giảm khống chế khi đang dùng khiên năng lượng
                            this.shieldControlReductionPercent += Math.max(0, io.param);
                            break;
                        case 229: //Aura lạnh giảm HP, KI xung quanh mỗi 30 giây
                            this.coldAuraDrainPercent += Math.max(0, io.param);
                            break;
                        case 153: //% phát nổ sau khi chết
                            this.tlBom += io.param;
                            break;
                    }
                }
            }
        }

        // Bông tai cấp 2
        if (this.player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            this.player.inventory.itemsBag.stream()
                    .filter(it -> it.isNotNullItem() && it.template.id == 921)
                    .findFirst()
                    .ifPresent(btc2 -> {
                        for (ItemOption io : btc2.itemOptions) {
                            addOption(io);
                            if (io.optionTemplate.id == 72) {
                                this.levelBT = io.param;
                            }
                        }
                    });
        }
        if (this.player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
            this.player.inventory.itemsBag.stream()
                    .filter(it -> it.isNotNullItem() && it.template.id == 1819)
                    .findFirst()
                    .ifPresent(btc3 -> {
                        for (ItemOption io : btc3.itemOptions) {
                            addOption(io);
                            if (io.optionTemplate.id == 72) {
                                this.levelBT = io.param;
                            }
                        }
                    });
        }

        this.player.setClothes.worldcup = 0;
        for (Item item : this.player.inventory.itemsBody) {
            if (item.isNotNullItem()) {
                switch (item.template.id) {
                    case 966:
                    case 982:
                    case 983:
                    case 883:
                    case 904:
                        player.setClothes.worldcup++;
                }
                if (item.template.id >= 592 && item.template.id <= 594) {
                    teleport = true;
                }
                if (!requiresFusion(item)) {
                    boolean requiresAndroidPartner = hasOption(item, 150);
                    for (ItemOption io : item.itemOptions) {
                        if (requiresAndroidPartner && !isAndroidProximityActive()
                                && (io.optionTemplate.id == 50 || io.optionTemplate.id == 77 || io.optionTemplate.id == 94)) {
                            continue;
                        }
                        addOption(io);
                    }
                }
            }
        }
        if (this.player.effectSkin != null && this.player.effectSkin.drSlumDamageBonus > 0) {
            this.tlDame.add(this.player.effectSkin.drSlumDamageBonus);
            this.tlSpeed += this.player.effectSkin.drSlumSpeedBonus;
        }
        if (this.player.effectSkin != null) {
            this.tlDame.add(this.player.effectSkin.groupCostumeDamageBonus);
            this.tlSpeed += this.player.effectSkin.groupCostumeSpeedBonus;
            this.tlHp.add(this.player.effectSkin.groupCostumeAllStatBonus);
            this.tlMp.add(this.player.effectSkin.groupCostumeAllStatBonus);
            this.tlDef.add(this.player.effectSkin.groupCostumeAllStatBonus);
            this.tlDame.add(this.player.effectSkin.groupCostumeAllStatBonus);
            this.tlDame.add(this.player.effectSkin.clanProximityDamageBonus);
            this.tlHp.add(this.player.effectSkin.clanProximityHpBonus);
            this.tlMp.add(this.player.effectSkin.clanProximityKiBonus);
            this.tlHp.add(this.player.effectSkin.darkHpAuraBonus);
            if (this.player.effectSkin.isOption222Active()) {
                this.tlDame.add(this.rageDamagePercent);
            }
            this.player.effectSkin.syncEquipmentOptionState(this.hasAbsorbBurst,
                    this.option156MaxPunchBonus);
        }
        if (this.player.setClothes != null && this.player.setClothes.gohan >= 5) {
            this.tlGold += 30;
        }
        // Legacy Thỏ Đại Ca costumes were issued before default item options
        // existed. Keep those copies compatible while option 115 remains the
        // normal configuration path for every other item.
        if (this.player.inventory.itemsBody.size() > 5
                && this.player.inventory.itemsBody.get(5).isNotNullItem()
                && this.player.inventory.itemsBody.get(5).template.id == ConstItem.CAI_TRANG_THO_DAI_CA) {
            this.isBienCarrot = true;
        }
        setDameTrainArmor();
        setBasePoint();
        setOutfitFusion();
        setSpeed();
    }

    private void addOption(ItemOption io) {
        switch (io.optionTemplate.id) {
            case 0: //Tấn công +#
                this.dameAdd += io.param;
                break;
            case 2: //HP, KI+#000
                this.hpAdd += io.param * 1000;
                this.mpAdd += io.param * 1000;
                break;
            case 3:// vô hiệu chưởng
                this.voHieuChuong += io.param;
                break;
            case 4: //Hồi phục #% KI khi bị đánh
                this.tlHoiKiKhiBiDanh += io.param;
                break;
            case 5: //+#% sức đánh chí mạng
                this.tlDameCrit.add(io.param);
                this.tlSDCM += io.param;
                break;
            case 6: //HP+#
                this.hpAdd += io.param;
                break;
            case 7: //KI+#
                this.mpAdd += io.param;
                break;
            case 8: //Hút #% HP, KI xung quanh mỗi 5 giây
                this.tlHutHpMpXQ += io.param;
                break;
            case 10: //Sát thương chuẩn #%
                this.tlSatThuongChuan += io.param;
                break;
            case 11: //Công đức +# (chỉ tộc Namec)
                if (this.player.gender == ConstPlayer.NAMEC) {
                    this.congDucTriThuong += io.param;
                }
                break;
            case 14: //Chí mạng+#%
                this.critAdd += io.param;
                break;
            case 15: //Phản đòn cận chiến +#
                this.phanDonCanChien += io.param;
                break;
            case 16: // Speed
            case 114:
            case 148:
                this.tlSpeed += io.param;
                break;
            case 18: //Chinh xac
                this.tlchinhxac += io.param;
                break;
            case 19: //Tấn công+#% khi đánh quái
                this.tlDameAttMob.add(io.param);
                break;
            case 22: //HP+#K
                this.hpAdd += io.param * 1000;
                break;
            case 23: //MP+#K
                this.mpAdd += io.param * 1000;
                break;
            case 24: //Làm chậm
                this.isLamCham = true;
                break;
            case 25: //Tàn hình
                this.isTanHinh = true;
                break;
            case 26: //Hóa đá
                this.isHoaDa = true;
                break;
            case 27: //+# HP/30s
                this.hpHoiAdd += io.param;
                break;
            case 28: //+# KI/30s
                this.mpHoiAdd += io.param;
                break;
            case 29: //Biến Sôcôla mọi người xung quanh mỗi 30 giây
                this.isBienSocola = true;
                break;
            case 186: //Biến kẹo mọi người xung quanh mỗi 30 giây, tồn tại 30 giây
                this.isBienSocola = true;
                this.bienSocola30Seconds = true;
                break;
            case 115: //Biến cà rốt khi chạm người chơi khác
                this.isBienCarrot = true;
                break;
            case 32: //Không bị hóa xương/hóa đá
                this.khangHoaDa = true;
                break;
            case 33: //dịch chuyển tức thời
                this.teleport = true;
                break;
            case 34:
                this.setTinhAn += 1;
                break;
            case 35:
                this.setNguyetAn += 1;
                break;
            case 36:
                this.setNhatAn += 1;
                break;
            case 42: //Tấn công +#% lên quái bay
                this.tlDameQuaiBay += io.param;
                break;
            case 43: //Tấn công +#% lên quái khỉ
                this.tlDameQuaiKhi += io.param;
                break;
            case 44: //Tấn công +#% lên quái mặt đất
                this.tlDameQuaiMatDat += io.param;
                break;
            case 45: //Tấn công +#% lên tộc Namếc
                this.tlDameTocNamec += io.param;
                break;
            case 46: //Tấn công +#% lên tộc Trái đất
                this.tlDameTocTraiDat += io.param;
                break;
            case 47: //Giáp+#
                this.defAdd += io.param;
                break;
            case 48: //HP/KI+#
                this.hpAdd += io.param;
                this.mpAdd += io.param;
                break;
            case 49: //Tấn công+#%
            case 50: //Sức đánh+#%
                this.tlDame.add(io.param);
                break;
            case 62: //Phục hồi thể lực #% mỗi giờ khi còn mặc
                this.tlHoiTheLuc += Math.max(0, io.param);
                break;
            case 76: //VIP: cộng 10% chỉ số cơ bản cho nhân vật
                this.hasVipEquipment = true;
                break;
            case 77: //HP+#%
                this.tlHp.add(io.param);
                break;
            case 78: //Sức hủy diệt +#% sát thương cuối
                this.tlSucHuyDiet += Math.max(0, io.param);
                break;
            case 79: //Đệ tử +#% sức đánh
                this.tlDameDeTu += Math.max(0, io.param);
                break;
            case 80: //HP+#%/30s
                this.tlHpHoi += io.param;
                break;
            case 81: //MP+#%/30s
                this.tlMpHoi += io.param;
                break;
            case 82: //Quái không chủ động đánh, giảm 20% sát thương quái
                this.khongBiQuaiChuDongTanCong = true;
                break;
            case 83: //Tăng SM, TN khi đánh quái
                this.tlTNSM.add(io.param);
                break;
            case 84: //Bay không tốn KI
                this.flyWithoutKi = true;
                break;
            case 85: //Bay và phục hồi KI
                this.flyAndRecoverKi = true;
                break;
            case 88: //Cộng #% exp khi đánh quái
                this.tlTNSM.add(io.param);
                break;
            case 89: //Bay và phục hồi HP, KI
                this.flyAndRecoverHpKi = true;
                break;
            case 94: //Giáp #%
                this.tlGiap += io.param;
                break;
            case 95: //Biến #% tấn công thành HP
                this.tlHutHp += io.param;
                break;
            case 96: //Biến #% tấn công thành MP
                this.tlHutMp += io.param;
                break;
            case 97: //Phản #% sát thương
                this.tlPST += io.param;
                break;
            case 98: //Xuyen giap chuong
                this.tlxgc += io.param;
                break;
            case 99: //Xuyen giap can chien
                this.tlxgcc += io.param;
                break;
            case 100: //+#% vàng từ quái
                this.tlGold += io.param;
                break;
            case 101: //+#% TN,SM
                this.tlTNSM.add(io.param);
                break;
            case 103: //KI +#%
                this.tlMp.add(io.param);
                break;
            case 104: //Biến #% tấn công quái thành HP
                this.tlHutHpMob += io.param;
                break;
            case 105: //Vô hình khi không đánh quái và boss
                this.wearingVoHinh = true;
                break;
            case 106: //Không ảnh hưởng bởi cái lạnh
                this.isKhongLanh = true;
                break;
            case 111:
            case 108: //#% Né đòn
                this.tlNeDon += io.param;
                break;
            case 109: //Hôi, giảm #% HP
                this.tlHpGiamODo += io.param;
                break;
            case 110: //Do spl
                this.isDoSPL = true;
                break;
            case 116: //Kháng thái dương hạ san
                this.khangTDHS = true;
                break;
            case 226:
            case 117: //Đẹp +#% SĐ cho mình và người xung quanh
                if (io.param > this.tlSexyDame) {
                    this.tlSexyDame = io.param;
                }
                break;
            case 147: //+#% sức đánh
                this.tlDame.add(io.param);
                break;
            case 183: //Giảm #% thời gian hồi Khiên
                this.shieldCooldownReductionPercent += Math.max(0, io.param);
                break;
            case 187: //Giảm # giây thời gian bị mù
                this.blindDurationReductionSeconds += Math.max(0, io.param);
                break;
            case 188: //Đệ tử chưởng Kamejoko +#% sát thương
                this.petKamejokoDamageBonus += Math.max(0, io.param);
                break;
            case 189: //Đệ tử chưởng Antomic +#% sát thương
                this.petAntomicDamageBonus += Math.max(0, io.param);
                break;
            case 190: //Đệ tử chưởng Masenko +#% sát thương
                this.petMasenkoDamageBonus += Math.max(0, io.param);
                break;
            case 191: //Né chí mạng +#%
                this.criticalEvasionPercent += Math.max(0, io.param);
                break;
            case 197: //Tấn công +#% lên tộc Xayda
                this.tlDameTocXayda += Math.max(0, io.param);
                break;
            case 198: //Giảm sát thương từ tộc Trái Đất
                this.damageReductionFromTraiDat += Math.max(0, io.param);
                break;
            case 199: //Giảm sát thương từ tộc Namếc
                this.damageReductionFromNamec += Math.max(0, io.param);
                break;
            case 200: //Giảm sát thương từ tộc Xayda
                this.damageReductionFromXayda += Math.max(0, io.param);
                break;
            case 204: //Sát thương chuẩn chỉ lên Boss
                this.bossTrueDamagePercent += Math.max(0, io.param);
                break;
            case 155: //Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng từ quái
                this.tlSubSD += 50;
                this.tlHp.add(-50);
                this.tlMp.add(-50);
                this.tlTNSM.add(io.param);
                this.tlGold += io.param;
                break;
            case 156: //Cộng dồn sức đánh khi chỉ dùng chiêu đấm
                this.option156MaxPunchBonus += Math.max(0, io.param);
                break;
            case 157: //Giảm sát thương khi KI dưới 20%
                this.lowKiDamageReduction += Math.max(0, io.param);
                break;
            case 158: //Ở trần có cơ hội tìm thấy vật phẩm sự kiện
                this.canFindEventItemBare = true;
                break;
            case 162: //Cute hồi #% KI/s bản thân và xung quanh
                this.cuteMpRecoveryPercent += Math.max(0, io.param);
                break;
            case 159: // x chưởng
                this.xChuong = (short) io.param;
                break;
            case 160: // TNSM PET;
                this.tlTNSMPet += io.param;
                break;
            case 161: //Ngọc mỗi ngày từ lần hạ quái đầu tiên
                this.dailyGemFromMob += Math.max(0, io.param);
                break;
            case 163: //Biến người xung quanh thành bí ngô
                this.isBienPumpkin = true;
                break;
            case 165: //Đòn lửa khi cận chiến
                this.hasFireStrike = true;
                break;
            case 166: //Tăng sức đánh quái khi bay với Tàu Pảy Pảy
            case 170: //Tăng sức đánh quái khi bay với Cải Trang nhà Fide
                this.flyingMobDamagePercent += Math.max(0, io.param);
                break;
            case 167: //Tạo không khí lạnh
                this.hasColdAura = true;
                break;
            case 168: //Hấp thụ sức mạnh rồi bộc phá
                this.hasAbsorbBurst = true;
                break;
            case 169: //Cơ hội hạ độc đối thủ
                this.hasPoisonStrike = true;
                break;
            case 175: //Giảm thời gian Thái Dương Hạ San
                this.tdhsDurationReductionPercent += Math.max(0, io.param);
                break;
            case 178: //KI +#%/10s
                this.mpRecovery10SecondsPercent += Math.max(0, io.param);
                break;
            case 221: //Hồi HP, KI cho bản thân và đồng bang mỗi 30 giây
                this.clanRecoveryPercent += Math.max(0, io.param);
                break;
            case 222: //Kích nộ sức đánh trong 30 giây
                this.rageDamagePercent += Math.max(0, io.param);
                break;
            case 225: //Giảm thời gian bị Ma Phong Ba
                this.maPhongBaReductionSeconds += Math.max(0, io.param);
                break;
            case 227: //Giảm khống chế khi đang dùng khiên năng lượng
                this.shieldControlReductionPercent += Math.max(0, io.param);
                break;
            case 229: //Aura lạnh giảm HP, KI xung quanh mỗi 30 giây
                this.coldAuraDrainPercent += Math.max(0, io.param);
                break;
            case 153: //% phát nổ sau khi chết
                this.tlBom += io.param;
                break;
        }
    }

    private boolean hasOption(Item item, int optionId) {
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == optionId) {
                return true;
            }
        }
        return false;
    }

    private boolean isAndroidProximityActive() {
        return this.player.effectSkin != null && this.player.effectSkin.androidProximityActive;
    }

    private void setSpeed() {
        if (player.isPl()) {
            long speedWithBonus = 8L + (8L * tlSpeed / 100L);
            if (player.effectSkill != null && player.effectSkill.isLamCham) {
                int slowPercent = Math.max(0, Math.min(100, player.effectSkill.slowPercent));
                speed = (byte) Math.max(1L, speedWithBonus * (100L - slowPercent) / 100L);
                return;
            }
            speed = (byte) Math.max(1L, Math.min(Byte.MAX_VALUE, speedWithBonus));
        }
    }

    /** Option 38 suppresses every option on this equipment outside fusion. */
    private boolean requiresFusion(Item item) {
        for (ItemOption option : item.itemOptions) {
            if (option.optionTemplate.id == 38) {
                return this.player.fusion.typeFusion == ConstPlayer.NON_FUSION;
            }
        }
        return false;
    }

    /** Refreshes movement speed when a temporary slow starts or ends. */
    public void refreshSpeed() {
        setSpeed();
    }

    private void setOutfitFusion() {
        if (this.player.inventory.itemsBody.size() < 6 || this.player.pet == null || this.player.pet.inventory.itemsBody.size() < 6) {
            return;
        }
        Item skin = this.player.inventory.itemsBody.get(5);
        Item pskin = this.player.pet.inventory.itemsBody.get(5);
        if (skin.isNotNullItem() && pskin.isNotNullItem()) {
            this.isGogeta = skin.template.id == 2133 && pskin.template.id == 2134 || skin.template.id == 2134 && pskin.template.id == 2133;
        } else {
            this.isGogeta = false;
        }
    }

    private void setDameTrainArmor() {
        if (!this.player.isPet && !this.player.isBot && !this.player.isBoss) {
            if (this.player.inventory.itemsBody.size() < 7) {
                return;
            }
            try {
                Item gtl = this.player.inventory.itemsBody.get(6);
                if (gtl.isNotNullItem()) {
                    ItemService.gI().ensureTrainArmorTimeOption(gtl);
                    this.wearingTrainArmor = true;
                    this.player.inventory.trainArmor = gtl;
                    this.tlSubSD += ItemService.gI().getPercentTrainArmor(gtl);
                } else {
                    if (this.player.inventory.trainArmor == null) {
                        gtl = this.player.inventory.itemsBag.stream().filter(item -> item.isNotNullItem() && item.template.type == 32 && item.itemOptions != null
                                && item.itemOptions.stream().filter(io -> io.optionTemplate.id == 9 && io.param > 0).findFirst().orElse(null) != null).findFirst().orElse(null);
                        if (gtl == null) {
                            return;
                        }
                        this.player.inventory.trainArmor = gtl;
                    }
                    this.wearingTrainArmor = false;
                    for (Item.ItemOption io : this.player.inventory.trainArmor.itemOptions) {
                        if (io.optionTemplate.id == 9 && io.param > 0) {
                            this.tlDame.add(ItemService.gI().getPercentTrainArmor(this.player.inventory.trainArmor));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Lỗi get giáp tập luyện " + this.player.name + "\n" + e + "\n");
            }
        }
    }

    public void setBasePoint() {
        setHpMax();
        setHp();
        setMpMax();
        setMp();
        setDame();
        setDef();
        setCrit();
        setHpHoi();
        setMpHoi();
        setThoBulma();
        setTinhNhatNguyetAn();
    }

    private void setThoBulma() {
        this.isThoBulma = (this.player.inventory != null && this.player.inventory.itemsBody != null
                && this.player.inventory.itemsBody.size() >= 5 && this.player.inventory.itemsBody.get(5).isNotNullItem()
                && this.player.inventory.itemsBody.get(5).template.id == 584);
    }

    private void setTinhNhatNguyetAn() {
        this.isTinhAn = this.setTinhAn >= 5;
        this.isNhatAn = this.setNhatAn >= 5;
        this.isNguyetAn = this.setNguyetAn >= 5;
    }

    private void setHpHoi() {
        this.hpHoi = this.hpMax / 100;
        this.hpHoi += this.hpHoiAdd;

        if (this.tlHpHoi > 100) {
            this.tlHpHoi = 100;
        } else if (this.tlHpHoi < 0) {
            this.tlHpHoi = 0;
        }

        this.hpHoi += ((long) this.hpMax * this.tlHpHoi / 100);

        if (this.tlHpHoiBanThanVaDongDoi > 100) {
            this.tlHpHoiBanThanVaDongDoi = 100;
        } else if (this.tlHpHoiBanThanVaDongDoi < 0) {
            this.tlHpHoiBanThanVaDongDoi = 0;
        }

        this.hpHoi += ((long) this.hpMax * this.tlHpHoiBanThanVaDongDoi / 100);
    }

    private void setMpHoi() {
        this.mpHoi = this.mpMax / 100;
        this.mpHoi += this.mpHoiAdd;

        if (this.tlMpHoi > 100) {
            this.tlMpHoi = 100;
        } else if (this.tlMpHoi < 0) {
            this.tlMpHoi = 0;
        }

        this.mpHoi += ((long) this.mpMax * this.tlMpHoi / 100);

        if (this.tlMpHoiBanThanVaDongDoi > 100) {
            this.tlMpHoiBanThanVaDongDoi = 100;
        } else if (this.tlMpHoiBanThanVaDongDoi < 0) {
            this.tlMpHoiBanThanVaDongDoi = 0;
        }

        this.mpHoi += (((long) this.mpMax * this.tlMpHoiBanThanVaDongDoi) / 100);
    }

    private boolean isPetMasterUsingPorata(Pet pet) {
        if (pet == null || pet.master == null || pet.master.fusion == null) {
            return false;
        }
        byte fusionType = pet.master.fusion.typeFusion;
        return fusionType == ConstPlayer.HOP_THE_PORATA
                || fusionType == ConstPlayer.HOP_THE_PORATA2
                || fusionType == ConstPlayer.HOP_THE_PORATA3;
    }

    private void setHpMax() {
        long hpMax = this.hpg + this.hpAdd;

        if (this.tlHp != null) {
            for (Integer tl : new ArrayList<>(this.tlHp)) {
                if (tl != null) {
                    hpMax += (hpMax * tl / 100L);
                }
            }
        }

        // Xử lý set nappa
        if (this.player.setClothes.nappa == 5) {
            hpMax += (hpMax * 80L / 100L);
        }

        if (this.player.setClothes.cadicM >= 2) {
            hpMax += (hpMax * 20L / 100L);

        }
        // Xử lý set worldcup
        if (this.player.setClothes.worldcup == 2) {
            hpMax += (hpMax * 10 / 100L);
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            hpMax += (hpMax * 10L / 100L);
        }

        // Xử lý set nhật ấn
        if (this.isNhatAn) {
            hpMax += (hpMax * 15L / 100L);
        }

        // Xử lý ngọc rồng đen 2 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[1] > System.currentTimeMillis()) {
            hpMax += (hpMax * RewardBlackBall.R2S_1 / 100L);
        }

        // Xử lý khỉ
        if (this.player.effectSkill.isMonkey) {
            if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
                Skill monkeySkill = SkillUtil.getSkillbyId(player, Skill.BIEN_KHI);
                int percent = monkeySkill != null
                        ? SkillUtil.getPercentHpMonkey(monkeySkill)
                        : SkillUtil.getPercentHpMonkey(player.effectSkill.levelMonkey);
                hpMax += (hpMax * percent / 100L);
            }
        }
        if (this.player.isPet && isPetMasterUsingPorata((Pet) this.player)) {
            hpMax += hpMax * PetConfig.getFusionTypeBonus(((Pet) this.player).typePet, "hp") / 100L;
        }

        // Xử lý phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            hpMax *= this.player.effectSkin.xHPKI;
        }

        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8062) {
            hpMax += (hpMax * 5 / 100L);
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            hpMax += (hpMax * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            hpMax += 1_000_000;
        }

        // Xử lý +hp đệ
        if (this.player.fusion.typeFusion != ConstPlayer.NON_FUSION && this.player.pet != null) {
            hpMax += this.player.pet.nPoint.hpMax * PetConfig.getFusionContributionPercent() / 100L;
        }

        // Xử lý bổ huyết
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet && !this.player.itemTime.isUseBoHuyet2) {
            hpMax *= 2;
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia2) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia1) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        // Xử lý item sieu cap
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet2) {
            hpMax *= 2.2;
        }

        // Xử lý huýt sáo
        if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
            if (this.player.effectSkill.tiLeHPHuytSao != 0) {
                hpMax += (hpMax * this.player.effectSkill.tiLeHPHuytSao / 100L);
            }
        }

        // Xử lý chibi
        if (this.player.effectSkill != null && this.player.effectSkill.isChibi && this.player.typeChibi == 3) {
            hpMax *= 2;
        }

        // Xử lý map lạnh
        if (this.player.zone != null
                && (MapService.gI().isMapCold(this.player.zone.map)
                || this.player.effectSkin != null && this.player.effectSkin.coldAuraAffected)
                && !this.isKhongLanh) {
            hpMax /= 2;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 77) {
                hpMax += (hpMax * opt.param / 100L);
            }
        }

        hpMax = applyVipEquipmentStatBonus(hpMax);

        int clanHpBonus = ClanProgressionService.gI().statBasisPoints(player,
                ClanProgressionService.Branch.HP, ClanProgressionService.gI().isPvpMode(player));
        hpMax += hpMax * clanHpBonus / 10_000L;

        if (hpMax > 2_147_483_647) {
            hpMax = 2_147_483_647;
        }

        this.hpMax = (int) hpMax;
    }

    private void setHp() {
        this.hp = Math.min(this.hp, this.hpMax);
    }

    private void setMpMax() {
        // Tính toán giới hạn mpMax
        long mpMax = this.mpg + this.mpAdd;

        if (this.tlMp != null) {
            for (Integer tl : new ArrayList<>(this.tlMp)) {
                if (tl != null) {
                    mpMax += (mpMax * tl / 100L);
                }
            }
        }
        if (this.isNguyetAn) {
            mpMax += (mpMax * 15L / 100L);
        }

        // Xử lý ngọc rồng đen 6 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[5] > System.currentTimeMillis()) {
            mpMax += (mpMax * RewardBlackBall.R6S_1 / 100L);
        }

        // Xử lý set worldcup
        if (this.player.setClothes.worldcup
                == 2) {
            mpMax += (this.mpMax * 10 / 100L);
        }
        if (this.player.isPet && isPetMasterUsingPorata((Pet) this.player)) {
            mpMax += mpMax * PetConfig.getFusionTypeBonus(((Pet) this.player).typePet, "mp") / 100L;
        }

        // Xử lý phù
        if (this.player.zone
                != null && MapService.gI()
                        .isMapBlackBallWar(this.player.zone.map.mapId)) {
            mpMax *= this.player.effectSkin.xHPKI;
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            mpMax += (mpMax * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            mpMax += 1_000_000;
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            mpMax += (mpMax * 10L / 100L);
        }

        // Xử lý hợp thể
        if (this.player.fusion.typeFusion != 0 && this.player.pet != null) {
            mpMax += this.player.pet.nPoint.mpMax * PetConfig.getFusionContributionPercent() / 100L;
        }

        // Xử lý bổ khí
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi && !this.player.itemTime.isUseBoKhi2) {
            mpMax *= 2;
        }

        // Xử lý item sieu cap
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi2) {
            mpMax *= 2.2;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 103) {
                mpMax += (mpMax * opt.param / 100L);
            }
        }

        mpMax = applyVipEquipmentStatBonus(mpMax);

        int clanKiBonus = ClanProgressionService.gI().statBasisPoints(player,
                ClanProgressionService.Branch.KI, ClanProgressionService.gI().isPvpMode(player));
        mpMax += mpMax * clanKiBonus / 10_000L;

        if (mpMax
                > 2_147_483_647) {
            mpMax = 2_147_483_647;
        }

        this.mpMax = (int) mpMax;
    }

    private void setMp() {
        this.mp = Math.min(this.mp, this.mpMax);
    }

    public int getHP() {
        return Math.min(this.hp, this.hpMax);
    }

    public void setHP(long hp) {
        if (hp > 0) {
            this.hp = (int) (hp <= this.hpMax ? hp : this.hpMax);
        } else {
            player.setDie();
        }
    }

    public int getMP() {
        return Math.min(this.mp, this.mpMax);
    }

    public void setMP(long mp) {
        if (mp > 0) {
            this.mp = (int) (mp <= this.mpMax ? mp : this.mpMax);
        } else {
            this.mp = 0;
        }
    }

    private void setDame() {
        long dame = this.dameg + this.dameAdd;

        if (this.tlDame != null) {
            for (Integer tl : new ArrayList<>(this.tlDame)) {
                if (tl != null) {
                    dame += (dame * tl / 100L);
                }
            }
        }
        if (this.player.isPet && isPetMasterUsingPorata((Pet) this.player)) {
            dame += dame * PetConfig.getFusionTypeBonus(((Pet) this.player).typePet, "damage") / 100L;
        }

        // Xử lý set tinh ấn
        if (this.isTinhAn) {
            dame += (dame * 15L / 100L);
        }

        // Xử lý thức ăn
        if (!this.player.isPet && this.player.itemTime != null && this.player.itemTime.isEatMeal || this.player.isPet && this.player.itemTime != null && ((Pet) this.player).master.itemTime.isEatMeal) {
            dame += (dame * 10 / 100L);
        }

        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8060) {
            dame += (dame * 5 / 100L);
        }

        if (this.player.setClothes.nail >= 2) {
            this.tlDameCrit.add(10);
        }
        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8061) {
            this.tlDameCrit.add(5);
            this.tlSDCM += 5;
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            this.tlDameCrit.add(10);
            this.tlSDCM += 10;
        }

        // Xử lý cuồng nộ
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo && !this.player.itemTime.isUseCuongNo2) {
            dame *= 2;
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            dame += dame / 10; // tăng 10%
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo2) {
            dame *= 2.2;
        }

        // Xử lý ngọc rồng đen 1 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[0] > System.currentTimeMillis()) {
            dame += (dame * RewardBlackBall.R1S_2 / 100L);
        }

        // Xử lý set worldcup
        if (this.player.setClothes.worldcup == 2) {
            dame += (dame * 10 / 100L);
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            dame += (dame * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            dame += 10_000;
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            dame += (dame * 10L / 100L);
        }

        // Xử lý phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            dame *= this.player.effectSkin.xDame;
        }

        // Xử lý hợp thể
        if (this.player.fusion.typeFusion != 0 && this.player.pet != null) {
            dame += this.player.pet.nPoint.dame * PetConfig.getFusionContributionPercent() / 100L;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 50) {
                dame += (dame * opt.param / 100L);
            }
        }

        // Xử lý khỉ
        if (this.player.effectSkill.isMonkey) {
            if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
                Skill monkeySkill = SkillUtil.getSkillbyId(player, Skill.BIEN_KHI);
                int percent = monkeySkill != null
                        ? SkillUtil.getPercentDameMonkey(monkeySkill)
                        : SkillUtil.getPercentDameMonkey(player.effectSkill.levelMonkey);
                dame += (dame * percent / 100L);
            }
        }
        int totalPercent = 0;
        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 117) {
                tlSexyDame += opt.param;
            }
        }

        dame += (dame * tlSexyDame / 100L);

        // Xử lý giảm dame
        dame -= (dame * tlSubSD / 100L);

        // Xử lý map cold
        if (this.player.zone != null
                && (MapService.gI().isMapCold(this.player.zone.map)
                || this.player.effectSkin != null && this.player.effectSkin.coldAuraAffected)
                && !this.isKhongLanh) {
            dame /= 2;
        }

        dame = applyVipEquipmentStatBonus(dame);

        // Clan option 50 is part of the character's real attack stat so both
        // cDamFull and every damage path use the same value immediately.
        int clanAttackBonus = ClanProgressionService.gI().statBasisPoints(player,
                ClanProgressionService.Branch.ATTACK, ClanProgressionService.gI().isPvpMode(player));
        dame += dame * clanAttackBonus / 10_000L;

        if (this.player.effectSkill != null && this.player.effectSkill.isWeakAfterSleep) {
            dame -= dame * this.player.effectSkill.weakAfterSleepPercent / 100L;
        }
        if (this.player.effectSkill != null && this.player.effectSkill.isSocola) {
            dame -= dame * this.player.effectSkill.socolaDamageReductionPercent / 100L;
        }

        // Carrot is a real damage reduction, not merely a visual costume.
        if (this.player.effectSkill != null && this.player.effectSkill.isCarrot) {
            dame -= dame * 10L / 100L;
        }
        if (this.player.effectSkill != null && this.player.effectSkill.isPumpkin) {
            dame -= dame * 20L / 100L;
        }

        if (dame > 2_147_483_647) {
            dame = 2_147_483_647;
        }

        this.dame = (int) dame;
    }

    public void setDame(long dame) {
        if (dame > 0) {
            this.dame = (int) (dame <= this.dame ? dame : this.dame);
        } else {
            this.dame = 0;
        }
    }

    private void setDef() {
        this.def = this.defg * 4;
        this.def += this.defAdd;

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            this.def += this.def * 10 / 100;
        }
        for (Integer tl : new ArrayList<>(this.tlDef)) {
            if (tl != null) {
                this.def += (long) this.def * tl / 100L;
            }
        }
        if (this.player.effectSkill != null && this.player.effectSkill.isPumpkin) {
            this.def -= this.def * 20 / 100;
        }
        this.def = (int) Math.min(Integer.MAX_VALUE, applyVipEquipmentStatBonus(this.def));
    }

    /** Option 76 boosts the wearer's four direct combat stats exactly once. */
    private long applyVipEquipmentStatBonus(long value) {
        if (!this.hasVipEquipment || !this.player.isPl() || value <= 0) {
            return value;
        }
        return value + value / 10L;
    }

    private void setCrit() {
        this.crit = this.critg;
        this.crit += this.critAdd;
        this.crit += this.critdragon;
        //biến khỉ
        if (this.player.effectSkill.isMonkey) {
            this.crit = 110;
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia2) {
            this.crit += 10;
        }

        if (player.setClothes.thanVuTruKaio >= 2) {
            this.crit += 20;
        }
    }

    private void resetPoint() {
        this.voHieuChuong = 0;
        this.hpAdd = 0;
        this.mpAdd = 0;
        this.dameAdd = 0;
        this.defAdd = 0;
        this.critAdd = 0;
        this.tlHp.clear();
        this.tlMp.clear();
        this.tlDef.clear();
        this.tlDame.clear();
        this.tlDameCrit.clear();
        this.tlDameAttMob.clear();
        this.tlSDCM = 0;
        this.tlHpHoiBanThanVaDongDoi = 0;
        this.tlMpHoiBanThanVaDongDoi = 0;
        this.hpHoi = 0;
        this.mpHoi = 0;
        this.mpHoiCute = 0;
        this.tlHpHoi = 0;
        this.tlMpHoi = 0;
        this.tlHutHp = 0;
        this.tlHutMp = 0;
        this.tlHutHpMob = 0;
        this.tlHutHpMpXQ = 0;
        this.tlPST = 0;
        this.tlHoiKiKhiBiDanh = 0;
        this.tlSatThuongChuan = 0;
        this.phanDonCanChien = 0;
        this.congDucTriThuong = 0;
        this.tlDameQuaiBay = 0;
        this.tlDameQuaiKhi = 0;
        this.tlDameQuaiMatDat = 0;
        this.tlDameTocNamec = 0;
        this.tlDameTocTraiDat = 0;
        this.shieldCooldownReductionPercent = 0;
        this.blindDurationReductionSeconds = 0;
        this.petKamejokoDamageBonus = 0;
        this.petAntomicDamageBonus = 0;
        this.petMasenkoDamageBonus = 0;
        this.criticalEvasionPercent = 0;
        this.tlDameTocXayda = 0;
        this.damageReductionFromTraiDat = 0;
        this.damageReductionFromNamec = 0;
        this.damageReductionFromXayda = 0;
        this.bossTrueDamagePercent = 0;
        this.clanRecoveryPercent = 0;
        this.rageDamagePercent = 0;
        this.maPhongBaReductionSeconds = 0;
        this.shieldControlReductionPercent = 0;
        this.coldAuraDrainPercent = 0;
        this.tlTNSM.clear();
        this.tlHoiTheLuc = 0;
        this.tlDameDeTu = 0;
        this.tlSucHuyDiet = 0;
        this.tlDameAttMob.clear();
        this.tlGold = 0;
        this.tlNeDon = 0;
        this.tlNeDonXinbato = 0;
        this.tlBom = 0;
        this.tlGiap = 0;
        this.tlxgcc = 0;
        this.tlxgc = 0;
        this.tlchinhxac = 0;
        this.tlTNSMPet = 0;
        this.xChuong = 0;
        this.option156MaxPunchBonus = 0;
        this.lowKiDamageReduction = 0;
        this.dailyGemFromMob = 0;
        this.cuteMpRecoveryPercent = 0;
        this.flyingMobDamagePercent = 0;
        this.tdhsDurationReductionPercent = 0;
        this.mpRecovery10SecondsPercent = 0;
        this.setTinhAn = 0;
        this.setNhatAn = 0;
        this.setNguyetAn = 0;
        this.tlSexyDame = 0;
        this.tlSubSD = 0;
        this.tlHpGiamODo = 0;
        this.tlSpeed = 0;
        this.teleport = false;
        this.wearingVoHinh = false;
        this.isKhongLanh = false;
        this.khangTDHS = false;
        this.isTanHinh = false;
        this.isHoaDa = false;
        this.khangHoaDa = false;
        this.isLamCham = false;
        this.isBienSocola = false;
        this.bienSocola30Seconds = false;
        this.isBienCarrot = false;
        this.isDoSPL = false;
        this.isThoBulma = false;
        this.hasVipEquipment = false;
        this.khongBiQuaiChuDongTanCong = false;
        this.flyWithoutKi = false;
        this.flyAndRecoverKi = false;
        this.flyAndRecoverHpKi = false;
        this.canFindEventItemBare = false;
        this.isBienPumpkin = false;
        this.hasFireStrike = false;
        this.hasColdAura = false;
        this.hasAbsorbBurst = false;
        this.hasPoisonStrike = false;
    }

    public void addHp(long hp) {
        if (hp > 0) {
            long potentialHp = (long) this.hp + hp;
            if (potentialHp > this.hpMax) {
                this.hp = this.hpMax;
            } else {
                this.hp = (int) Math.min(potentialHp, 2_147_483_647);
            }
        }
    }

    public void addMp(long mp) {
        long potentialMp = this.mp + mp;

        if (potentialMp > this.mpMax) {
            this.mp = this.mpMax;
        } else if (potentialMp < 0) {
            this.mp = 0;
        } else {
            this.mp = (int) potentialMp;
        }
    }

    public void setHp(long hp) {
        if (hp < 0) {
            this.hp = 0;
        } else {
            this.hp = (int) Math.min(hp, 2_147_483_647);
        }
    }

    public void setMp(long mp) {
        if (mp < 0) {
            this.mp = 0;
        } else {
            this.mp = (int) Math.min(mp, 2_147_483_647);
        }
    }

    private void setIsCrit() {
        if (intrinsic != null && intrinsic.id == 25
                && this.getCurrPercentHP() <= intrinsic.param1) {
            isCrit = true;
        } else if (isCrit100) {
            isCrit100 = false;
            isCrit = true;
        } else {
            isCrit = Util.isTrue(this.crit, ConstRatio.PER100);
        }
    }

    public int getDameAttack(boolean isAttackMob) {
        setIsCrit();
        long dameAttack = this.dame;
        if (this.player.effectSkin != null && this.player.effectSkin.punchComboPercent > 0) {
            dameAttack += dameAttack * this.player.effectSkin.punchComboPercent / 100L;
        }
        intrinsic = this.player.playerIntrinsic.intrinsic;
        percentDameIntrinsic = 0;
        int percentDameSkill = 0;
        byte percentXDame = 0;
        Skill skillSelect = player.playerSkill.skillSelect;
        if (skillSelect.template.id != Skill.DICH_CHUYEN_TUC_THOI && isCritTele) {
            isCrit = true;
            isCritTele = false;
        }
        switch (skillSelect.template.id) {
            case Skill.DRAGON:
                if (intrinsic.id == 1) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.KAMEJOKO:
                if (intrinsic.id == 2) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.songoku == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.SUPER_KAME:
            case Skill.LIEN_HOAN_CHUONG:
            case Skill.MA_PHONG_BA:
            case Skill.KHI_NGUYEN_TRAM:
            case Skill.HELLZONE_GRENADE:
            case Skill.BARRIER_PRISON:
            case Skill.SUPER_GHOST_KAMIKAZE:
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.GALICK:
                if (intrinsic.id == 16) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.kakarot == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.ANTOMIC:
                if (intrinsic.id == 17) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.TU_SAT:
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.cadicM == 4) {
                    percentXDame = 20;
                } else if (this.player.setClothes.cadicM == 5) {
                    percentXDame = 50;
                }
                break;
            case Skill.DEMON:
                if (intrinsic.id == 8) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.MASENKO:
                if (intrinsic != null && intrinsic.id == 9) {
                    percentXDame += intrinsic.param1;
                }
                if (this.player.setClothes.nail == 5) {
                    percentXDame += 80;
                }

                percentDameSkill = skillSelect.damage + percentXDame;
                break;

            case Skill.LIEN_HOAN:
                if (intrinsic.id == 13) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.ocTieu == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.KAIOKEN:
                percentDameSkill = skillSelect.damage;
                if (player.setClothes.thanVuTruKaio >= 5) {
                    percentXDame = 25;
                }
                break;
            case Skill.DICH_CHUYEN_TUC_THOI:
                isCrit = true;
                isCritTele = true;
                dameAttack = Util.nextInt((int) (int) Math.min(2_147_483_647L, (dameAttack - (dameAttack / 100 * 5))),
                        (int) (int) Math.min(2_147_483_647L, (dameAttack + (dameAttack / 100 * 5))));
                dameAttack = SkillMasteryService.gI().applyDamageStat(skillSelect, (int) dameAttack);
                break;
            case Skill.MAKANKOSAPPO:
                percentDameSkill = skillSelect.damage;
                int dameSkill = (int) Math.min(2_147_483_647L, (long) this.mpMax * percentDameSkill / 100);
                if (this.player.setClothes.picolo == 5) {
                    dameSkill = (int) Math.min(2_147_483_647L, (long) dameSkill * 2L);
                }
                return dameSkill;
            case Skill.QUA_CAU_KENH_KHI:
                long hpmob = 0;
                long hppl = 0;
                for (Mob mob : this.player.zone.mobs) {
                    if (!mob.isDie()
                            && Util.getDistance(this.player, mob) <= SkillUtil.getRangeQCKK(this.player.playerSkill.skillSelect)) {
                        hpmob += mob.point.hp;
                    }
                }

                for (Player pl : this.player.zone.getHumanoids()) {
                    if (!pl.isDie() && this.player.id != pl.id
                            && Util.getDistance(this.player, pl) <= SkillUtil.getRangeQCKK(this.player.playerSkill.skillSelect)) {
                        hppl += pl.nPoint.hp;
                    }
                }

                long dameqckk = (hpmob * 10 / 100) + (hppl * 10 / 100) + this.dame * 10;

                if (this.player.setClothes.kirin == 5) {
                    dameqckk *= 2;
                }

                dameqckk = dameqckk + (Util.nextInt(-5, 5) * dameqckk / 100);
                dameqckk = SkillMasteryService.gI().applyDamageStat(skillSelect,
                        (int) Math.min(Integer.MAX_VALUE, dameqckk));
                if (dameqckk > 2_147_483_647) {
                    dameqckk = 2_147_483_647;
                }
                return (int) dameqckk;
            case Skill.DE_TRUNG:
                if (player.setClothes.pikkoroDaimao == 5) {
                    dameAttack *= 2;
                }
                if (dameAttack > 2_147_483_647) {
                    dameAttack = 2_147_483_647;
                }
                return (int) dameAttack;
        }

        if (intrinsic.id == 18 && this.player.effectSkill.isMonkey) {
            percentDameIntrinsic = intrinsic.param1;
        }

        if (percentDameSkill
                != 0) {
            dameAttack = dameAttack * percentDameSkill / 100;
        }

        dameAttack += (dameAttack * percentDameIntrinsic / 100);
        dameAttack += (dameAttack * dameAfter / 100);
        if (this.player.effectSkill != null && this.player.effectSkill.isDameBuff && tlSexyDame
                == 0) {
            int tiLeDame = this.player.effectSkill.tileDameBuff;
            dameAttack += (dameAttack * tiLeDame / 100L);
        }
        if (isAttackMob) {
            for (Integer tl : this.tlDameAttMob) {
                dameAttack += (dameAttack * tl / 100);
            }
            if (this.player.isPet && ((Pet) this.player).master.charms.tdDeTu > System.currentTimeMillis()) {
                dameAttack *= 2;
            }
        }

        dameAfter = 0;

        if (isCrit) {
            dameAttack *= 2;
            dameAttack += (dameAttack * tlSDCM / 100);
        }

        dameAttack += ((long) dameAttack * percentXDame / 100);

        long tempDameAttack = (long) (dameAttack / 100L * 5L);
        if (tempDameAttack
                <= 0) {
            tempDameAttack = 1;
        }
        dameAttack += (long) (Util.getOne(
                -1, 1) * Util.nextInt((int) tempDameAttack
                ) + 1);

        if (player.effectSkin != null && player.effectSkin.isXChuong && (player.playerSkill.skillSelect.template.id == Skill.KAMEJOKO || player.playerSkill.skillSelect.template.id == Skill.ANTOMIC || player.playerSkill.skillSelect.template.id == Skill.MASENKO)) {
            dameAttack *= xChuong;
            player.effectSkin.isXDame = true;
            player.effectSkin.isXChuong = false;
            player.effectSkin.lastTimeXChuong = System.currentTimeMillis();
        }

        if (dameAttack
                > 2_147_483_647) {
            dameAttack = 2_147_483_647;
        }
        return (int) Math.min(Integer.MAX_VALUE,
                applyMasterPetSkillDamageBonus(skillSelect, dameAttack));
    }

    public int getCurrPercentHP() {
        if (this.hpMax == 0) {
            return 100;
        }
        return (int) ((long) this.hp * 100 / this.hpMax);
    }

    public int getCurrPercentMP() {
        return (int) ((long) this.mp * 100 / this.mpMax);
    }

    public void setFullHpMp() {
        this.hp = this.hpMax;
        this.mp = this.mpMax;
    }

    public void subHP(long sub) {
        this.hp -= sub;
        if (this.hp <= 0) {
            this.hp = 0;
            this.setHp(0);
        }
    }

    public void subMP(long sub) {
        this.mp -= sub;
        if (this.mp <= 0) {
            this.mp = 0;
        }

        if (this.mp > 2_147_483_647) {
            this.mp = 2_147_483_647;
        }
    }

    /** Calculates reward from a mob attack, including options 83 and 88. */
    public long calSucManhTiemNang(long tiemNang) {
        return calSucManhTiemNang(tiemNang, true);
    }

    /** PvP rewards retain global bonuses but never use mob-only options 83/88. */
    public long calSucManhTiemNangPvp(long tiemNang) {
        return calSucManhTiemNang(tiemNang, false);
    }

    private long calSucManhTiemNang(long tiemNang, boolean includeMobRewardOptions) {
        if (power < getPowerLimit()) {
            if (includeMobRewardOptions && this.tlTNSM != null) {
                for (Integer tl : this.tlTNSM) {
                    if (tl != null) {
                        tiemNang += ((long) tiemNang * tl / 100);
                    }
                }
            }
            long tn = tiemNang;
            if (this.player.charms.tdTriTue > System.currentTimeMillis()) {
                tiemNang += tn;
            }
            if (this.player.charms.tdTriTue3 > System.currentTimeMillis()) {
                tiemNang += tn * 3;
            }
            if (this.player.charms.tdTriTue4 > System.currentTimeMillis()) {
                tiemNang += tn * 4;
            }
            if (this.player.charms.tdTriTue4 > System.currentTimeMillis()) {
                tiemNang += tn * 4;
            }
            if (this.player.timevip > System.currentTimeMillis()) {
                tiemNang += tn * 3;
            }
            if (this.player.effectSkill.isChibi && this.player.typeChibi == 2) {
                tiemNang += tn * 2;
            }
            if (this.player.getSession() != null && this.player.getSession().vip > 0 || this.player.isPet
                    && ((Pet) this.player).master.getSession() != null && ((Pet) this.player).master.getSession().vip > 0) {
                tiemNang += tn * 3;
            }
            if (this.player.itemTime != null && this.player.itemTime.isUseDK) {
                tiemNang += tn * 2;
            }
            if (player.zone.map.mapId >= 135 && player.zone.map.mapId <= 138) {
                if (this.player.itemTime != null && this.player.itemTime.isUseKhoBauX2) {
                    tiemNang += tn * 2;
                }
            }
            if (this.player.satellite != null && this.player.satellite.isIntelligent) {
                tiemNang += tn / 5;
            }
            if (this.intrinsic != null && this.intrinsic.id == 24) {
                tiemNang += ((long) tiemNang * this.intrinsic.param1 / 100);
            }
            if (this.power >= 60_000_000_000L) {
                tiemNang -= ((long) tiemNang * 80 / 100);
            }
            if (this.player.isPet) {
                if (((Pet) this.player).master.itemTime.isUseBuaSanta) {
                    tiemNang += tn * 2;
                }
                if (((Pet) this.player).master.nPoint != null && ((Pet) this.player).master.nPoint.tlTNSMPet > 0) {
                    tiemNang += tn * ((Pet) this.player).master.nPoint.tlTNSMPet / 100L;
                }
            }
            if (MapService.gI().isMapNguHanhSon(this.player.zone.map.mapId)) {
                tiemNang *= 1;
            }
            if (MapService.gI().AllMap(this.player.zone.map.mapId)) {
                //  tiemNang *= 10; //x x3 tiềm năng toàn server
            }
            if (MapService.gI().isMapBanDoKhoBau(this.player.zone.map.mapId)) {
                tiemNang *= 1.5;
            }
            if (this.player.cFlag != 0) {
                if (this.player.cFlag == 8) {
                    tiemNang += ((long) tiemNang * 10 / 100);
                } else {
                    tiemNang += ((long) tiemNang * 5 / 100);
                }
            }
            tiemNang *= Manager.RATE_EXP_SERVER;
            tiemNang = calSubTNSM(tiemNang);
            if (tiemNang <= 0) {
                tiemNang = 1;
            }
        } else {
            tiemNang = 10;
        }
        return tiemNang;
    }

    public long calSubTNSM(long tiemNang) {
        if (power >= 90_000_000_000L) {
            tiemNang /= 100;
        } else if (power >= 80_000_000_000L) {
            tiemNang /= 90;
        } else if (power >= 60_000_000_000L) {
            tiemNang /= 50;
        } else if (power >= 50_000_000_000L) {
            tiemNang /= 40;
        } else if (power >= 40_000_000_000L) {
            tiemNang /= 30;
        }
        return tiemNang;
    }

    public short getTileHutHp(boolean isMob) {
        if (isMob) {
            return (short) (this.tlHutHp + this.tlHutHpMob);
        } else {
            return this.tlHutHp;
        }
    }

    public short getTiLeHutMp() {
        return this.tlHutMp;
    }

    public int subDameInjureWithDeff(long dame) {
        long def = this.def;
        dame -= def;
        if (dame < 0) {
            dame = 1;
        }
        return (int) dame;
    }

    /*------------------------------------------------------------------------*/
    public boolean canOpenPower() {
        return this.power >= getPowerLimit();
    }

    public long getPowerLimit() {
        return PlayerConfig.getPowerLimit(limitPower);
    }

    public long getPowerNextLimit() {
        return PlayerConfig.getPowerLimit(limitPower + 1);
    }

    public int getHpMpLimit() {
        return PlayerConfig.getHpMpLimit(limitPower);
    }

    public int getDameLimit() {
        return PlayerConfig.getDamageLimit(limitPower);
    }

    public short getDefLimit() {
        return (short) PlayerConfig.getDefenseLimit(limitPower);
    }

    public byte getCritLimit() {
        return (byte) PlayerConfig.getCriticalLimit(limitPower);
    }

    public void powerUp(long power) {
        this.power += power;
        TaskService.gI().checkDoneTaskPower(player, this.power);
    }

    public void tiemNangUp(long tiemNang) {
        this.tiemNang += tiemNang;
    }

    public void increasePoint(byte type, short point) {
        increasePoint(type, point, true);
    }

    /**
     * Option 13 is a counter stored directly on every equipped item carrying
     * that option, so it remains visible and survives relogging with the item.
     */
    public void increaseMobKillCounters() {
        if (this.player == null || this.player.inventory == null || this.player.inventory.itemsBody == null) {
            return;
        }
        boolean changed = false;
        for (Item item : this.player.inventory.itemsBody) {
            if (item == null || !item.isNotNullItem() || item.itemOptions == null) {
                continue;
            }
            for (Item.ItemOption option : item.itemOptions) {
                if (option.optionTemplate != null && option.optionTemplate.id == 13 && option.param < 9999) {
                    option.param = Math.min(9999, Math.max(0, option.param) + 1);
                    changed = true;
                }
            }
        }
        if (changed) {
            InventoryService.gI().sendItemBody(this.player);
        }
    }

    public int getTrueDamagePercent() {
        return Math.max(0, Math.min(100, this.tlSatThuongChuan));
    }

    public int getKiRecoveryWhenHitPercent() {
        return Math.max(0, Math.min(100, this.tlHoiKiKhiBiDanh));
    }

    public void increasePoint(byte type, short point, boolean notify) {
        if (point <= 0 || point > 1000) {
            return;
        }
        boolean increased = false;
        long tiemNangUse;
        if (type == 0) {
            int pointHp = point * 20;
            tiemNangUse = point * (2 * (this.hpg + 1000) + pointHp - 20) / 2;
            if ((this.hpg + pointHp) <= getHpMpLimit()) {
                if (doUseTiemNang(tiemNangUse, notify)) {
                    hpg += pointHp;
                    increased = true;
                }
            } else {
                if (notify) {
                    Service.gI().sendThongBaoOK(getNotifyPlayer(), "Vui lòng mở giới hạn sức mạnh");
                }
                return;
            }
        }
        if (type == 1) {
            int pointMp = point * 20;
            tiemNangUse = point * (2 * (this.mpg + 1000) + pointMp - 20) / 2;
            if ((this.mpg + pointMp) <= getHpMpLimit()) {
                if (doUseTiemNang(tiemNangUse, notify)) {
                    mpg += pointMp;
                    increased = true;
                }
            } else {
                if (notify) {
                    Service.gI().sendThongBaoOK(getNotifyPlayer(), "Vui lòng mở giới hạn sức mạnh");
                }
                return;
            }
        }
        if (type == 2) {
            TaskService.gI().checkDoneTaskNangCS(player);
            tiemNangUse = point * (2 * this.dameg + point - 1) / 2 * 100;
            if ((this.dameg + point) <= getDameLimit()) {
                if (doUseTiemNang(tiemNangUse, notify)) {
                    dameg += point;
                    increased = true;
                }
                TaskService.gI().checkDoneTaskNangCS(player);
            } else {
                if (notify) {
                    Service.gI().sendThongBaoOK(getNotifyPlayer(), "Vui lòng mở giới hạn sức mạnh");
                }
                return;
            }
        }
        if (type == 3) {
            // Sum the price of every requested armor point.  The previous
            // implementation charged the one-point price even for +10/+100.
            tiemNangUse = (long) point * (2L * (this.defg + 5L) + point - 1L) / 2L * 100_000L;
            if ((this.defg + point) <= getDefLimit()) {
                if (doUseTiemNang(tiemNangUse, notify)) {
                    defg += point;
                    increased = true;
                }
            } else {
                if (notify) {
                    Service.gI().sendThongBaoOK(getNotifyPlayer(), "Vui lòng mở giới hạn sức mạnh");
                }
                return;
            }
        }
        if (type == 4) {
            tiemNangUse = 0L;
            for (int i = 0; i < point; i++) {
                int criticalLevel = Math.min(this.critg + i, CRITICAL_POTENTIAL_COSTS.length - 1);
                tiemNangUse += CRITICAL_POTENTIAL_COSTS[criticalLevel];
            }
            if ((this.critg + point) <= getCritLimit()) {
                if (doUseTiemNang(tiemNangUse, notify)) {
                    critg += point;
                    increased = true;
                }
            } else {
                if (notify) {
                    Service.gI().sendThongBaoOK(getNotifyPlayer(), "Vui lòng mở giới hạn sức mạnh");
                }
                return;
            }
        }
        if (increased) {
            Service.gI().point(player);
        }
    }

    private boolean doUseTiemNang(long tiemNang) {
        return doUseTiemNang(tiemNang, true);
    }

    private boolean doUseTiemNang(long tiemNang, boolean notify) {
        if (this.tiemNang < tiemNang) {
            if (notify) {
                Service.gI().sendThongBaoOK(getNotifyPlayer(), "Bạn không đủ tiềm năng");
            }
            return false;
        }
        if (this.tiemNang >= tiemNang && this.tiemNang - tiemNang >= 0) {
            this.tiemNang -= tiemNang;
            TaskService.gI().checkDoneTaskUseTiemNang(player);
            return true;
        }
        return false;
    }

    private Player getNotifyPlayer() {
        if (this.player != null && this.player.isPet && this.player instanceof Pet) {
            Pet pet = (Pet) this.player;
            if (pet.master != null) {
                return pet.master;
            }
        }
        return this.player;
    }

    public long getFullTN() {
        long tnhp = 0, tnki = 0, tnsd = 0, tng = 0, tncm = 0;

        if (hpg > 0) {
            tnhp = (((hpg / 20L) * (50L + (50L + (hpg / 20L) - 1L)) / 2L) * 20L);
        }
        if (mpg > 0) {
            tnki = (((mpg / 20L) * (50L + (50L + (mpg / 20L) - 1L)) / 2L) * 20L);
        }
        if (dameg > 0) {
            tnsd = ((dameg * (dameg - 1L) * 100L) / 2L);
        }
        if (defg > 0) {
            tng = ((defg * (500000L + (500000L + (defg - 1L) * 100000L))) / 2L);
        }
        if (critg > 0) {
            for (int i = 0; i < critg; i++) {
                tncm += CRITICAL_POTENTIAL_COSTS[Math.min(i, CRITICAL_POTENTIAL_COSTS.length - 1)];
            }
        }
        return tnhp + tnki + tnsd + tng + tncm;
    }

    //--------------------------------------------------------------------------
    private static final long PERIODIC_RECOVERY_INTERVAL_MS = 30_000L;
    private static final long STAMINA_RECOVERY_INTERVAL_MS = 60_000L;
    private static final long OPTION_62_STAMINA_INTERVAL_MS = 3_600_000L;
    private static final int FLIGHT_RECOVERY_PERCENT = 2;

    private long lastTimeHoiPhuc;
    private long lastTimeHoiStamina;
    private long lastTimeOption62StaminaCheck;
    private long option62StaminaElapsedMillis;

    public void update() {
        if (player == null) {
            return;
        }
        if (player.effectSkill != null) {
            if (player.effectSkill.isCharging && player.effectSkill.countCharging < 10) {
                int tiLeHoiPhuc = SkillUtil.getPercentCharge(player.playerSkill.skillSelect);
                if (player.effectSkill.isCharging && !player.isDie() && !player.effectSkill.isHaveEffectSkill()
                        && (hp < hpMax || mp < mpMax)) {
                    long hpRecovered = hpMax / 100 * tiLeHoiPhuc;
                    long mpRecovered = mpMax / 100 * tiLeHoiPhuc;

                    if (hp + hpRecovered > 2_147_483_647) {
                        hpRecovered = 2_147_483_647 - hp;
                    }
                    if (mp + mpRecovered > 2_147_483_647) {
                        mpRecovered = 2_147_483_647 - mp;
                    }

                    PlayerService.gI().hoiPhuc(player, hpRecovered, mpRecovered);

                    if (player.effectSkill.countCharging % 3 == 0) {
                        Service.gI().chat(player, "Phục hồi năng lượng " + getCurrPercentHP() + "%");
                    }
                } else {
                    EffectSkillService.gI().stopCharge(player);
                }
                if (++player.effectSkill.countCharging >= 10) {
                    EffectSkillService.gI().stopCharge(player);
                }
            }

            if (Util.canDoWithTime(lastTimeHoiPhuc, PERIODIC_RECOVERY_INTERVAL_MS)) {
                long hpRecovery = hpHoi;
                long mpRecovery = mpHoi;

                // Options 80/81 are included in hpHoi/mpHoi and are therefore
                // applied exclusively by this 30-second recovery tick.
                if (player.isFly) {
                    if (flyAndRecoverHpKi) {
                        hpRecovery += (long) hpMax * FLIGHT_RECOVERY_PERCENT / 100L;
                        mpRecovery += (long) mpMax * FLIGHT_RECOVERY_PERCENT / 100L;
                    } else if (flyAndRecoverKi) {
                        mpRecovery += (long) mpMax * FLIGHT_RECOVERY_PERCENT / 100L;
                    }
                }

                if ((hp < hpMax && hpRecovery > 0) || (mp < mpMax && mpRecovery > 0)) {
                    PlayerService.gI().hoiPhuc(this.player, hpRecovery, mpRecovery);
                }
                this.lastTimeHoiPhuc = System.currentTimeMillis();
            }

            if (Util.canDoWithTime(lastTimeHoiStamina, STAMINA_RECOVERY_INTERVAL_MS) && this.stamina < this.maxStamina) {
                this.stamina++;
                this.lastTimeHoiStamina = System.currentTimeMillis();

                if (!this.player.isBoss && !this.player.isPet) {
                    PlayerService.gI().sendCurrentStamina(this.player);
                }
            }
        }
    }

    /**
     * Option 62 advances only while the player still has the option equipped.
     * Taking the item off pauses its accumulated clock; using another item
     * cannot produce an instant stamina refill.
     */
    public void updateOption62StaminaRecovery() {
        long now = System.currentTimeMillis();
        if (lastTimeOption62StaminaCheck == 0L) {
            lastTimeOption62StaminaCheck = now;
            return;
        }

        long elapsed = Math.max(0L, now - lastTimeOption62StaminaCheck);
        lastTimeOption62StaminaCheck = now;
        if (!player.isPl() || player.isPet || player.isBoss || player.isBot || tlHoiTheLuc <= 0) {
            return;
        }

        option62StaminaElapsedMillis = Math.min(OPTION_62_STAMINA_INTERVAL_MS,
                option62StaminaElapsedMillis + elapsed);
        if (option62StaminaElapsedMillis < OPTION_62_STAMINA_INTERVAL_MS) {
            return;
        }
        option62StaminaElapsedMillis = 0L;

        int max = Math.max(0, maxStamina);
        int current = Math.max(0, stamina);
        int restored = Math.max(1, max * tlHoiTheLuc / 100);
        int newStamina = Math.min(max, current + restored);
        if (newStamina == current) {
            return;
        }
        stamina = (short) newStamina;
        PlayerService.gI().sendCurrentStamina(player);
    }

    public void dispose() {
        this.intrinsic = null;
        this.player = null;
        this.tlHp = null;
        this.tlMp = null;
        this.tlDef = null;
        this.tlDame = null;
        this.tlDameAttMob = null;
        this.tlTNSM = null;
    }

    public long calPercent(long param, int percent) {
        return param * percent / 100;
    }

}
