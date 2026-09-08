package nro.models.server.dispatch;

import java.io.IOException;
import nro.models.boss.Boss;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.consts.ConstMap;
import nro.models.consts.ConstNpc;
import nro.models.fishing.FishingService;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.ItemMapService;
import nro.models.map.service.MapService;
import nro.models.map.service.NpcManager;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankService;
import nro.models.server.MenuController;
import nro.models.services.AchievementService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.services_dungeon.BlackBallWarService;
import nro.models.services_func.TransactionService;
import nro.models.services_func.UseItem;
import nro.models.utils.Util;

public final class WorldCombatCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) throws Exception {
        switch (context.command()) {
            case -105 -> handleTimedMapTransition(context);
            case 29 -> ChangeMapService.gI().openZoneUI(context.player());
            case 21 -> ChangeMapService.gI().changeZone(
                    context.player(), context.message().reader().readByte());
            case -7 -> handleMovement(context);
            case 22 -> {
                context.message().reader().readByte();
                NpcManager.getNpc(ConstNpc.DAU_THAN).confirmMenu(
                        context.player(), context.message().reader().readByte());
            }
            case -33, -23 -> {
                ChangeMapService.gI().changeMapWaypoint(context.player());
                Service.gI().hideWaitDialog(context.player());
            }
            case -45 -> handleUseSkill(context);
            case -91 -> handleItemMapSelection(context);
            case -39 -> ChangeMapService.gI().finishLoadMap(context.player());
            case 34 -> SkillService.gI().selectSkill(
                    context.player(), context.message().reader().readShort());
            case 54 -> handleAttackMob(context);
            case -60 -> Service.gI().attackPlayer(
                    context.player(), context.message().reader().readInt());
            case -20 -> {
                if (!context.player().isDie()) {
                    ItemMapService.gI().pickItem(context.player(),
                            context.message().reader().readShort(), false);
                }
            }
            case -15 -> {
                int mapId = MapService.gI().isMapMaBu(context.player().zone.map.mapId)
                        ? 114 : context.player().gender + 21;
                ChangeMapService.gI().changeMapBySpaceShip(context.player(), mapId, 0, -1);
            }
            case -16 -> {
                if (!context.player().isPKDHVT) {
                    PlayerService.gI().hoiSinh(context.player());
                }
            }
            case -104 -> Service.gI().mabaove(
                    context.player(), context.message().reader().readInt());
            case -118 -> handleRankOrAdminTeleport(context);
            case 126 -> handleFishingInput(context);
            case -78 -> context.message().reader().readInt();
            case -114, 27 -> {
                // Preserved legacy no-op commands.
            }
            default -> throw new IllegalArgumentException("World/combat handler does not own command "
                    + context.command());
        }
    }

    private void handleTimedMapTransition(CommandContext context) {
        if (context.player().type == 0 && context.player().maxTime == 30) {
            ChangeMapService.gI().changeMapBySpaceShip(
                    context.player(), 102, -1, Util.nextInt(60, 200));
            context.player().idMark.setGotoFuture(false);
        } else if (context.player().type == 1 && context.player().maxTime == 5) {
            if (context.player().idMark != null && context.player().idMark.isGoToBDKB()) {
                ChangeMapService.gI().changeMap(context.player(),
                        MapService.gI().getMapCanJoin(context.player(), 135, -1), 35, 35);
                context.player().idMark.setGoToBDKB(false);
            }
        } else if (context.player().type == 2 && context.player().maxTime == 5) {
            if (MapService.gI().isMapHanhTinhThucVat(context.player().zone.map.mapId)) {
                ChangeMapService.gI().changeMap(context.player(), 80, -1, -1, 5);
            } else {
                ChangeMapService.gI().changeMap(context.player(), 160, -1, -1, 5);
            }
        } else if (context.player().type == 3 && context.player().maxTime == 5) {
            ChangeMapService.gI().changeMap(context.player(),
                    context.player().idMark.getZoneKhiGasHuyDiet(),
                    context.player().idMark.getXMapKhiGasHuyDiet(),
                    context.player().idMark.getYMapKhiGasHuyDiet());
            context.player().idMark.setZoneKhiGasHuyDiet(null);
        } else if (context.player().type == 4 && context.player().maxTime == 5) {
            if (context.player().idMark != null && context.player().idMark.isGoToKGHD()) {
                ChangeMapService.gI().changeMap(context.player(),
                        MapService.gI().getMapCanJoin(context.player(), 149, -1),
                        100 + Util.nextInt(-10, 10), 336);
                context.player().idMark.setGoToKGHD(false);
            }
        } else if (context.player().type == 5 && context.player().maxTime == 5) {
            ChangeMapService.gI().changeMap(context.player(),
                    MapService.gI().getMapCanJoin(context.player(), 156, -1),
                    100 + Util.nextInt(-10, 10), 336);
        }
    }

    private void handleMovement(CommandContext context) {
        if (context.player().isDie()) {
            Service.gI().charDie(context.player());
            return;
        }
        if (context.player().effectSkill.isHaveEffectSkill()) {
            return;
        }
        int toX = context.player().location.x;
        int toY = context.player().location.y;
        try {
            byte movementType = context.message().reader().readByte();
            toX = context.message().reader().readShort();
            try {
                toY = context.message().reader().readShort();
            } catch (IOException ignored) {
            }
            if (context.player().zone != null
                    && MapService.gI().isMapBlackBallWar(context.player().zone.map.mapId)
                    && Util.getDistance(context.player().location.x, context.player().location.y, toX, toY) > 500) {
                return;
            }
            if (movementType == 1) {
                AchievementService.gI().checkDoneTaskFly(
                        context.player(), context.player().location.x - toX);
            }
        } catch (IOException ignored) {
        }
        PlayerService.gI().playerMove(context.player(), toX, toY);
    }

    private void handleUseSkill(CommandContext context) throws Exception {
        if (!transactionAllowed(context)) {
            return;
        }
        byte status = context.message().reader().readByte();
        SkillService.gI().useSkill(context.player(), null, null, status, context.message());
    }

    private void handleItemMapSelection(CommandContext context) throws IOException {
        switch (context.player().idMark.getTypeChangeMap()) {
            case ConstMap.CHANGE_CAPSULE -> UseItem.gI().choseMapCapsule(
                    context.player(), context.message().reader().readByte());
            case ConstMap.CHANGE_BLACK_BALL -> BlackBallWarService.gI().changeMap(
                    context.player(), context.message().reader().readByte());
            default -> {
            }
        }
    }

    private void handleAttackMob(CommandContext context) throws IOException {
        int mobId = context.message().reader().readByte();
        int masterId = -1;
        boolean ownMob = mobId == -1;
        if (ownMob) {
            masterId = context.message().reader().readInt();
        }
        Service.gI().attackMob(context.player(), mobId, ownMob, masterId);
    }

    private void handleRankOrAdminTeleport(CommandContext context) throws IOException {
        int targetId = context.message().reader().readInt();
        switch (context.player().idMark.getMenuType()) {
            case 0, 1, 2 -> SuperRankService.gI().competing(context.player(), targetId);
            default -> {
                if (!context.player().isAdmin()) {
                    Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
                    return;
                }
                Boss boss = BossManager.gI().getBoss(targetId);
                if (boss != null && boss.zone != null && !boss.isDie()) {
                    ChangeMapService.gI().changeMapYardrat(
                            context.player(), boss.zone, boss.location.x, boss.location.y);
                } else {
                    Service.gI().sendThongBao(
                            context.player(), "Boss chưa xuất hiện hoặc đã bị tiêu diệt");
                }
            }
        }
    }

    private void handleFishingInput(CommandContext context) throws IOException {
        if (context.message().reader().readByte() == FishingService.QUICK_TIME_INPUT_MAGIC
                && context.message().reader().readByte() == FishingService.QUICK_TIME_INPUT) {
            FishingService.gI().submitQuickTimeInput(context.player(),
                    context.message().reader().readInt(),
                    context.message().reader().readUnsignedByte(),
                    context.message().reader().readUnsignedByte());
        }
    }

    private boolean transactionAllowed(CommandContext context) {
        if (!TransactionService.gI().check(context.player())) {
            return true;
        }
        Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
        return false;
    }
}
