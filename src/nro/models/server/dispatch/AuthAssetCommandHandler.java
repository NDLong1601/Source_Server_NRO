package nro.models.server.dispatch;

import java.io.IOException;
import nro.models.consts.ConstAchievement;
import nro.models.consts.ConstIgnoreName;
import nro.models.consts.ConstTask;
import nro.models.data.DataGame;
import nro.models.data.ItemData;
import nro.models.data.LocalManager;
import nro.models.data.LocalResultSet;
import nro.models.database.PlayerDAO;
import nro.models.database.SuperRankDAO;
import nro.models.map.service.NpcService;
import nro.models.network.IpRedactor;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player.Player;
import nro.models.player_badges.BadgesService;
import nro.models.server.Maintenance;
import nro.models.server.ServerManager;
import nro.models.server.ServerNotify;
import nro.models.services.AchievementService;
import nro.models.services.ClanService;
import nro.models.services.DayNightService;
import nro.models.services.FlagBagService;
import nro.models.services.IntrinsicService;
import nro.models.services.ItemTimeService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillMasteryService;
import nro.models.services.SkillService;
import nro.models.services.SubMenuService;
import nro.models.services.TaskService;
import nro.models.services_dungeon.TrainingService;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.Util;

public final class AuthAssetCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) throws Exception {
        switch (context.command()) {
            case 42 -> {
                // Registration payload is intentionally disabled and must not fall through.
            }
            case -74 -> handleResourceRequest(context);
            case -87 -> DataGame.updateData(context.session());
            case -67 -> {
                int iconId = context.message().reader().readInt();
                if (context.session().isAssetReady()) {
                    DataGame.sendIcon(context.session(), iconId);
                }
            }
            case 66 -> {
                String imageName = context.message().reader().readUTF();
                if (!AssetRequestPolicy.isSafeImageName(imageName)) {
                    throw new IOException("Invalid image asset name");
                }
                if (context.session().isAssetReady()) {
                    DataGame.sendImageByName(context.session(), imageName);
                }
            }
            case -66 -> handleEffectTemplate(context);
            case -62 -> FlagBagService.gI().sendIconFlagChoose(
                    context.player(), context.message().reader().readByte());
            case -63 -> FlagBagService.gI().sendIconEffectFlag(
                    context.player(), context.message().reader().readByte() & 0xFF);
            case -32 -> {
                int backgroundId = context.message().reader().readShort();
                if (context.session().isAssetReady()) {
                    DataGame.sendItemBGTemplate(context.session(), backgroundId);
                }
            }
            case -41 -> Service.gI().sendCaption(
                    context.session(), context.message().reader().readByte());
            case 11 -> DataGame.requestMobTemplate(
                    context.session(), context.message().reader().readByte());
            case -27 -> context.session().sendKey();
            case -111 -> DataGame.sendDataImageVersion(context.session());
            case -28 -> messageNotMap(context.session(), context.message());
            case -29 -> messageNotLogin(context.session(), context.message());
            case -30 -> messageSubCommand(context.session(), context.message());
            case -101 -> login2(context.session(), context.message());
            case -38 -> finishUpdate(context.player());
            default -> throw new IllegalArgumentException("Auth/asset handler does not own command "
                    + context.command());
        }
    }

    private void handleResourceRequest(CommandContext context) throws IOException {
        if (!context.session().isAssetReady()) {
            Logger.warning("[Asset] Bỏ qua yêu cầu resource trước setClientType từ "
                    + IpRedactor.redact(context.session().ipAddress) + "\n");
            return;
        }
        Logger.warning("Địa chỉ " + IpRedactor.redact(context.session().ipAddress)
                + " đang tải dữ liệu\n");
        byte type = context.message().reader().readByte();
        if (type == 1) {
            DataGame.sendSizeRes(context.session());
        } else if (type == 2) {
            DataGame.sendRes(context.session());
        }
    }

    private void handleEffectTemplate(CommandContext context) throws IOException {
        int effectId = context.message().reader().readShort();
        int templateId = effectId;
        if (context.player().zone == null) {
            return;
        }
        int shenronType = context.player().zone.shenronType;
        if (templateId == 25 && shenronType != -1
                && context.player().zone.map.mapId != 0
                && context.player().zone.map.mapId != 7
                && context.player().zone.map.mapId != 14) {
            templateId = shenronType == 1 ? 59 : shenronType == 0 ? 59 : 60;
        }
        DataGame.sendEffectTemplate(context.session(), effectId, templateId);
    }

    public void messageNotLogin(MySession session, Message message) {
        if (message == null) {
            return;
        }
        try {
            switch (message.reader().readByte()) {
                case 0 -> session.login(message.reader().readUTF(), message.reader().readUTF());
                case 2 -> Service.gI().setClientType(session, message);
                default -> {
                }
            }
        } catch (IOException e) {
            session.disconnect();
        }
    }

    public void messageNotMap(MySession session, Message message) {
        if (message == null) {
            return;
        }
        Player player = null;
        try {
            player = session.player;
            switch (message.reader().readByte()) {
                case 2 -> createChar(session, message);
                case 6 -> DataGame.updateMap(session);
                case 7 -> DataGame.updateSkill(session);
                case 8 -> ItemData.updateItem(session);
                case 10 -> DataGame.sendMapTemp(session, message.reader().readUnsignedByte());
                case 13 -> finishInitialMapLoad(player);
                default -> {
                }
            }
        } catch (IOException e) {
            Logger.logException(AuthAssetCommandHandler.class, e);
        }
    }

    private void finishInitialMapLoad(Player player) {
        if (player == null || !player.isPl()) {
            return;
        }
        DataGame.preloadPlayerItemIconsWithRetry(player);
        Service.gI().player(player);
        Service.gI().Send_Caitrang(player);
        BadgesService.sendVisualHiddenState(player);
        Service.gI().sendFlagBag(player);
        player.playerSkill.sendSkillShortCut();
        ItemTimeService.gI().sendAllItemTime(player);
        sendThongBaoServer(player);
        if (TaskService.gI().getIdTask(player) == ConstTask.TASK_0_0) {
            NpcService.gI().createTutorial(player, -1,
                    "Chào Mừng " + player.name + " Đến Với: " + ServerManager.NAME + "\n"
                    + "Nhiệm vụ đầu tiên của bạn là di chuyển\n"
                    + "Bạn hãy di chuyển nhân vật theo mũi tên chỉ hướng");
        }
        if (player.inventory.itemsBody.get(10).isNotNullItem()) {
            Service.gI().sendChibi(player);
        }
        player.zone.mapInfo(player);
        DayNightService.gI().sendCurrentState(player);
        if (player.getSession().version >= 220) {
            for (Skill skill : player.playerSkill.skills) {
                if (!SkillMasteryService.gI().shouldSendProgressToClient(skill)) {
                    continue;
                }
                SkillMasteryService.gI().refreshClientProgress(skill);
                SkillService.gI().sendCurrLevelSpecial(player, skill);
            }
        }
        Service.gI().sendTimeSkill(player);
        TrainingService.gI().tnsmLuyenTapUp(player);
        player.sendNewPet();
        if (player.getSession() != null && player.getSession().tongnap > 0) {
            AchievementService.gI().checkDoneTask(player, ConstAchievement.LAN_DAU_NAP_NGOC);
        }
    }

    public void messageSubCommand(MySession session, Message message) {
        if (message == null) {
            return;
        }
        try {
            Player player = session.player;
            switch (message.reader().readByte()) {
                case 16 -> {
                    byte type = message.reader().readByte();
                    short point = message.reader().readShort();
                    if (player != null && player.nPoint != null) {
                        player.nPoint.increasePoint(type, point);
                    }
                }
                case 18 -> {
                    byte type = message.reader().readByte();
                    short point = message.reader().readShort();
                    if (player != null && player.pet != null && player.pet.nPoint != null) {
                        player.pet.nPoint.increasePoint(type, point);
                        player.lastTimeOpenPetInfo = System.currentTimeMillis();
                        Service.gI().showInfoPet(player);
                    }
                }
                case 64 -> SubMenuService.gI().controller(
                        player, message.reader().readInt(), message.reader().readShort());
                default -> {
                }
            }
        } catch (IOException e) {
            Logger.logException(AuthAssetCommandHandler.class, e);
        }
    }

    private void sendThongBaoServer(Player player) {
        Service.gI().sendThongBaoFromAdmin(player, "X3 Kinh nghiệm đến hết ngày 11/5."
                + "\nSự kiện Goku Day."
                + "\nĐua TOP nhận quà cực khủng."
                + "\nTích điềm đổi quà."
                + "\nChi tiết xem tại diễn đàn, fanpage.");
    }

    public void createChar(MySession session, Message message) {
        if (Maintenance.isRunning) {
            return;
        }
        LocalResultSet result = null;
        boolean created = false;
        try {
            String name = message.reader().readUTF();
            int gender = message.reader().readByte();
            int hair = message.reader().readByte();
            if (name.length() >= 5 && name.length() <= 10) {
                result = LocalManager.executeQuery("select * from player where name = ?", name);
                if (result.first()) {
                    Service.gI().sendThongBaoOK(session, "Tên nhân vật đã tồn tại");
                } else if (Util.haveSpecialCharacter(name)) {
                    Service.gI().sendThongBaoOK(
                            session, "Tên nhân vật không được chứa ký tự đặc biệt");
                } else {
                    boolean allowedName = true;
                    for (String ignoredName : ConstIgnoreName.IGNORE_NAME) {
                        if (name.equals(ignoredName)) {
                            Service.gI().sendThongBaoOK(session, "Tên nhân vật đã tồn tại");
                            allowedName = false;
                            break;
                        }
                    }
                    if (allowedName) {
                        created = PlayerDAO.createNewPlayer(
                                session.userId, name.toLowerCase(), (byte) gender, hair);
                    }
                }
            } else {
                Service.gI().sendThongBaoOK(session,
                        "Tên nhân vật chỉ đồng ý các ký tự a-z, 0-9 và chiều dài từ 5 đến 10 ký tự");
            }
        } catch (Exception e) {
            Logger.logException(AuthAssetCommandHandler.class, e);
        } finally {
            if (result != null) {
                result.dispose();
            }
        }
        if (created) {
            session.login(session.uu, session.pp);
        }
    }

    public void login2(MySession session, Message message) {
        Service.gI().sendThongBaoOK(session,
                "Truy Cập: " + ServerManager.DOMAIN + "\n Đề Đăng Ký & Tải Game");
    }

    public void sendInfo(MySession session) {
        try {
            Player player = session.player;
            DataGame.sendTileSetInfo(session);
            IntrinsicService.gI().sendInfoIntrinsic(player);
            Service.gI().point(player);
            TaskService.gI().sendTaskMain(player);
            Service.gI().clearMap(player);
            ClanService.gI().sendMyClan(player);
            PlayerService.gI().sendMaxStamina(player);
            PlayerService.gI().sendCurrentStamina(player);
            Service.gI().sendDanhQuaiNhanNgoc(player);
            Service.gI().sendNangDong(player);
            Service.gI().sendHavePet(player);
            Service.gI().sendTopRank(player);
            if (player.superRank != null && player.superRank.rank < 1) {
                player.superRank.rank = SuperRankDAO.getRank((int) player.id);
                player.superRank.lastRewardTime = System.currentTimeMillis();
                SuperRankDAO.insertData(player);
            }
            ServerNotify.gI().sendNotifyTab(player);
            player.setClothes.setup();
            if (player.pet != null) {
                player.pet.setClothes.setup();
            }
            ItemTimeService.gI().sendCanAutoPlay(player);
            player.start();
        } catch (Exception ignored) {
        }
    }

    public void finishUpdate(Player player) {
        if (player != null && player.getSession() != null) {
            player.getSession().finishUpdate = true;
        }
    }
}
