package nro.models.server.dispatch;

import nro.models.activity.ActivityClientService;
import nro.models.clan.ClanAppearanceService;
import nro.models.clan.ClanGiftService;
import nro.models.clan.ClanItemStorageService;
import nro.models.clan.ClanProgressionService;
import nro.models.clan.ClanRankingService;
import nro.models.clan.ClanShopService;
import nro.models.clan.ClanTreasuryService;
import nro.models.clan.ClanTreeService;
import nro.models.clan.ClanValueService;
import nro.models.fishing.FishingProgressService;
import nro.models.matches.PVPService;
import nro.models.player_badges.BadgesService;
import nro.models.radar.Card;
import nro.models.server.Command;
import nro.models.services.ChatGlobalService;
import nro.models.services.ClanService;
import nro.models.services.CostumeCollectionService;
import nro.models.services.FriendAndEnemyService;
import nro.models.services.PKHistoryService;
import nro.models.services.RadarService;
import nro.models.services.Service;
import nro.models.services_func.TransactionService;

public final class SocialClanActivityCommandHandler implements CommandHandler {

    @Override
    public void handle(CommandContext context) throws Exception {
        switch (context.command()) {
            case 127 -> handleClanAndCollection(context);
            case -99 -> FriendAndEnemyService.gI().controllerEnemy(
                    context.player(), context.message());
            case 18 -> {
                context.player().changeMapVIP = true;
                FriendAndEnemyService.gI().goToPlayerWithYardrat(
                        context.player(), context.message());
            }
            case -72 -> FriendAndEnemyService.gI().chatPrivate(
                    context.player(), context.message());
            case -80 -> FriendAndEnemyService.gI().controllerFriend(
                    context.player(), context.message());
            case -59 -> handleChallenge(context);
            case ActivityClientService.COMMAND -> ActivityClientService.gI().handleRequest(
                    context.player(), context.message());
            case -71 -> {
                if (transactionAllowed(context)) {
                    ChatGlobalService.gI().chat(
                            context.player(), context.message().reader().readUTF());
                }
            }
            case -46 -> ClanService.gI().getClan(context.player(), context.message());
            case -51 -> ClanService.gI().clanMessage(context.player(), context.message());
            case -54 -> ClanService.gI().clanDonate(context.player(), context.message());
            case -49 -> ClanService.gI().joinClan(context.player(), context.message());
            case -50 -> ClanService.gI().sendListMemberClan(
                    context.player(), context.message().reader().readInt());
            case -56 -> ClanService.gI().clanRemote(context.player(), context.message());
            case -47 -> ClanService.gI().sendListClan(
                    context.player(), context.message().reader().readUTF());
            case -55 -> ClanService.gI().showMenuLeaveClan(context.player());
            case -57 -> ClanService.gI().clanInvite(context.player(), context.message());
            case 44 -> {
                if (transactionAllowed(context)) {
                    Command.gI().chat(context.player(), context.message().reader().readUTF());
                }
            }
            case BadgesService.VISIBILITY_COMMAND -> {
                if (context.player().isPl()) {
                    BadgesService.setVisualHidden(
                            context.player(), context.message().reader().readByte() == 1);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Social/clan/activity handler does not own command " + context.command());
        }
    }

    private void handleClanAndCollection(CommandContext context) throws Exception {
        byte action = context.message().reader().readByte();
        switch (action) {
            case ClanTreasuryService.REQUEST_VIEW,
                    ClanTreasuryService.REQUEST_DEPOSIT_GOLD,
                    ClanTreasuryService.REQUEST_DEPOSIT_GEM,
                    ClanTreasuryService.REQUEST_LEDGER_PAGE ->
                ClanTreasuryService.gI().handleRequest(context.player(), action, context.message());
            case ClanTreeService.REQUEST_VIEW,
                    ClanTreeService.REQUEST_WATER,
                    ClanTreeService.REQUEST_FERTILIZE,
                    ClanTreeService.REQUEST_HARVEST,
                    ClanTreeService.REQUEST_ASK_HELP,
                    ClanTreeService.REQUEST_HELP_WATER,
                    ClanTreeService.REQUEST_COMPLETE_UPGRADE,
                    ClanTreeService.REQUEST_START_UPGRADE ->
                ClanTreeService.gI().handleRequest(context.player(), action, context.message());
            case ClanProgressionService.REQUEST_VIEW,
                    ClanProgressionService.REQUEST_UPGRADE,
                    ClanProgressionService.REQUEST_ALLOCATE,
                    ClanProgressionService.REQUEST_BUFF_SNAPSHOT ->
                ClanProgressionService.gI().handleRequest(context.player(), action, context.message());
            case ClanShopService.REQUEST_VIEW,
                    ClanShopService.REQUEST_RESTOCK,
                    ClanShopService.REQUEST_BUY ->
                ClanShopService.gI().handleRequest(context.player(), action, context.message());
            case ClanGiftService.REQUEST_SEND_GIFT ->
                ClanGiftService.gI().handleRequest(context.player(), action, context.message());
            case ClanItemStorageService.REQUEST_VIEW,
                    ClanItemStorageService.REQUEST_USE ->
                ClanItemStorageService.gI().handleRequest(context.player(), action, context.message());
            case ClanValueService.REQUEST_VIEW ->
                ClanValueService.gI().handleRequest(context.player(), action);
            case ClanRankingService.REQUEST_PAGE ->
                ClanRankingService.gI().handleRequest(context.player(), action, context.message());
            case ClanAppearanceService.REQUEST_VIEW ->
                ClanAppearanceService.gI().handleRequest(context.player(), action);
            case 0 -> RadarService.gI().sendRadar(context.player(), context.player().Cards);
            case 42 -> FishingProgressService.gI().openFishBook(context.player());
            case CostumeCollectionService.ACTION_COLLECTION ->
                CostumeCollectionService.gI().sendCollection(context.player());
            case CostumeCollectionService.ACTION_CLAIM_COLLECTION_ACHIEVEMENT ->
                CostumeCollectionService.gI().claimCollectionAchievement(
                        context.player(), context.message().reader().readShort());
            case PKHistoryService.ACTION_HISTORY ->
                PKHistoryService.gI().sendHistory(context.player());
            case 1 -> toggleRadarCard(context);
            default -> {
            }
        }
    }

    private void toggleRadarCard(CommandContext context) throws Exception {
        short cardId = context.message().reader().readShort();
        Card card = context.player().Cards.stream()
                .filter(candidate -> candidate != null && candidate.Id == cardId)
                .findFirst()
                .orElse(null);
        if (card == null || card.Level == 0) {
            return;
        }
        if (card.Used == 0) {
            if (context.player().Cards.stream()
                    .anyMatch(candidate -> candidate != null && candidate.Used == 1)) {
                Service.gI().sendThongBao(context.player(), "Số thẻ sử dụng đã đạt tối đa");
                return;
            }
            card.Used = 1;
        } else {
            card.Used = 0;
        }
        RadarService.gI().Radar1(context.player(), cardId, card.Used);
        Service.gI().point(context.player());
    }

    private void handleChallenge(CommandContext context) throws Exception {
        if (context.player().baovetaikhoan) {
            Service.gI().sendThongBao(context.player(),
                    "Chức năng bảo vệ đã được bật. Bạn vui lòng kiểm tra lại");
            return;
        }
        PVPService.gI().controllerThachDau(context.player(), context.message());
    }

    private boolean transactionAllowed(CommandContext context) {
        if (!TransactionService.gI().check(context.player())) {
            return true;
        }
        Service.gI().sendThongBao(context.player(), "Không thể thực hiện");
        return false;
    }
}
