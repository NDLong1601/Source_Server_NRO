package nro.models.ledger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.consts.ConstTaskBadges;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.task.BadgesTask;
import nro.models.player.Pet;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 * SEC-05: Authoritative domain coordinator for durable VND ledger and recoverable purchase delivery.
 * Enforces strict transaction boundaries, two-stage durable outbox delivery, and concurrency safety.
 */
@SuppressWarnings("unchecked")
public class MoneyLedgerService {

    public static final String POLICY_VERSION = "SEC-05-V1";
    public static final int MIN_CONVERT_VND = 10_000;
    public static final int MAX_CONVERT_VND = 5_000_000;
    public static final int MAX_VIP_PURCHASES_PER_SEASON = 4;

    private static MoneyLedgerService instance;
    private MoneyLedgerRepository repository;

    // In-memory active intent tracking: key = playerId + ":" + productType.name()
    private final Map<Player, Map<VndProductType, VndPurchaseIntent>> activeIntents =
        Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static synchronized MoneyLedgerService gI() {
        if (instance == null) {
            instance = new MoneyLedgerService(new JdbcMoneyLedgerRepository());
        }
        return instance;
    }

    public MoneyLedgerService(MoneyLedgerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public MoneyLedgerRepository getRepository() {
        return repository;
    }


    // =========================================================================
    // PURCHASE RESULT DEFINITION
    // =========================================================================

    public enum PurchaseOutcome {
        SUCCESS,
        PENDING_DELIVERY,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        INVALID_INTENT,
        PAYLOAD_MISMATCH,
        VIP_LIMIT_REACHED,
        VIP_SEASON_CLOSED,
        UNACTIVATED_ACCOUNT,
        PLAYER_QUARANTINED,
        FAILED
    }

    public record PurchaseResult(
        PurchaseOutcome outcome,
        String message,
        int balanceBefore,
        int balanceAfter,
        String purchaseKey
    ) {
        public boolean isSuccess() {
            return outcome == PurchaseOutcome.SUCCESS;
        }

        public boolean isPending() {
            return outcome == PurchaseOutcome.PENDING_DELIVERY;
        }
    }

    // =========================================================================
    // INTENT LIFECYCLE
    // =========================================================================

    public VndPurchaseIntent createIntent(Player player, VndProductType productType, int presetAmount) {
        if (player == null || player.getSession() == null) {
            return null;
        }
        VndPurchaseIntent intent = new VndPurchaseIntent(
            player.getSession().userId,
            player.id,
            productType,
            presetAmount,
            POLICY_VERSION
        );
        intent.bindSession(player.getSession());
        synchronized (activeIntents) {
            activeIntents.computeIfAbsent(player, ignored -> new java.util.EnumMap<>(VndProductType.class))
                .put(productType, intent);
        }
        return intent;
    }

    public VndPurchaseIntent getActiveIntent(Player player, VndProductType productType) {
        if (player == null) {
            return null;
        }
        synchronized (activeIntents) {
            Map<VndProductType, VndPurchaseIntent> intents = activeIntents.get(player);
            VndPurchaseIntent intent = intents == null ? null : intents.get(productType);
            if (intent != null && (intent.isExpired() || !intent.matches(player))) {
                intents.remove(productType);
                return null;
            }
            return intent;
        }
    }

    public void clearActiveIntent(Player player, VndProductType productType) {
        if (player != null) {
            synchronized (activeIntents) {
                Map<VndProductType, VndPurchaseIntent> intents = activeIntents.get(player);
                if (intents != null) intents.remove(productType);
            }
        }
    }

    // =========================================================================
    // VIP SEASON VALIDATION
    // =========================================================================

    public static LocalDateTime getVipSeasonStartDate() {
        return LocalDateTime.of(LocalDate.now().getYear(), Month.JUNE, 5, 0, 0, 0);
    }

    public static LocalDateTime getVipSeasonEndDate() {
        return LocalDateTime.of(LocalDate.now().getYear(), Month.DECEMBER, 5, 23, 59, 59);
    }

    public static boolean isVipSeasonActive() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(getVipSeasonStartDate()) && !now.isAfter(getVipSeasonEndDate());
    }

    // =========================================================================
    // CORE PURCHASE EXECUTION
    // =========================================================================

    public PurchaseResult executePurchase(Player player, VndProductType productType, int requestedAmount) {
        if (player == null || player.inventory == null) {
            return new PurchaseResult(PurchaseOutcome.FAILED, "Người chơi không hợp lệ", 0, 0, null);
        }
        PurchaseResult result;
        synchronized (player.inventory) {
            try {
                result = executeLocked(player, productType, requestedAmount);
            } catch (RuntimeException ex) {
                player.persistenceQuarantined = true;
                Logger.error("[SEC-05] Purchase requires recovery, playerId=" + player.id);
                result = new PurchaseResult(PurchaseOutcome.PLAYER_QUARANTINED,
                    "Giao dịch cần kiểm tra; vui lòng đăng nhập lại", 0, 0, null);
            }
        }
        if (player.persistenceQuarantined && player.getSession() != null
            && nro.models.server.Client.gI().getPlayerByUser(player.getSession().userId) == player) {
            nro.models.server.Client.gI().kickSession(player.getSession());
        }
        return result;
    }

    private PurchaseResult executeLocked(Player player, VndProductType productType, int requestedAmount) {
        // 1. Basic binding & admission checks
        if (player == null || player.getSession() == null) {
            return new PurchaseResult(PurchaseOutcome.FAILED, "Người chơi không hợp lệ", 0, 0, null);
        }
        if (player.persistenceQuarantined) {
            return new PurchaseResult(PurchaseOutcome.PLAYER_QUARANTINED,
                "Trạng thái giao dịch chưa xác định; vui lòng đăng nhập lại", 0, 0, null);
        }
        if (player.getSession().userId <= 0 || player.id <= 0) {
            return new PurchaseResult(PurchaseOutcome.FAILED, "Tài khoản không hợp lệ", 0, 0, null);
        }
        Player registered = nro.models.server.Client.gI().getPlayerByUser(player.getSession().userId);
        if (player.getSession().player != player || (registered != null && registered != player)) {
            return new PurchaseResult(PurchaseOutcome.INVALID_INTENT,"Phiên đăng nhập đã thay đổi",0,0,null);
        }

        // 2. Product-specific validations
        if (productType.isVip()) {
            if (!isVipSeasonActive()) {
                return new PurchaseResult(PurchaseOutcome.VIP_SEASON_CLOSED,
                    "Mùa VIP đã kết thúc hoặc chưa mở. Bạn không thể mua VIP vào lúc này.", 0, 0, null);
            }
            int requiredCost = productType.getFixedCost();
            if (requestedAmount != requiredCost) {
                return new PurchaseResult(PurchaseOutcome.INVALID_AMOUNT,
                    "Giá tiền gói VIP không hợp lệ (" + requiredCost + " VND)", 0, 0, null);
            }
            // Admission is reserved under the database account/player locks.
            // Prechecking here would race and would reject successful replays.
        } else if (productType == VndProductType.TRADE_GOLD || productType == VndProductType.TRADE_GEM) {
            if (requestedAmount < MIN_CONVERT_VND || requestedAmount > MAX_CONVERT_VND) {
                return new PurchaseResult(PurchaseOutcome.INVALID_AMOUNT,
                    "Tối thiểu 10.000Đ và tối đa 5.000.000Đ", 0, 0, null);
            }
            if (productType == VndProductType.TRADE_GOLD && !player.getSession().actived) {
                return new PurchaseResult(PurchaseOutcome.UNACTIVATED_ACCOUNT,
                    "Vui lòng kích hoạt tài khoản!", 0, 0, null);
            }
        } else {
            return new PurchaseResult(PurchaseOutcome.FAILED, "Loại sản phẩm không được hỗ trợ", 0, 0, null);
        }

        // 3. Purchase Intent & stable identity
        VndPurchaseIntent intent = getActiveIntent(player, productType);
        if (intent == null) {
            return new PurchaseResult(PurchaseOutcome.FAILED,
                "Phiên giao dịch không hợp lệ hoặc đã hết hạn; vui lòng thao tác lại", 0, 0, null);
        }
        if (intent.isExpired()) {
            clearActiveIntent(player, productType);
            return new PurchaseResult(PurchaseOutcome.FAILED,
                "Phiên giao dịch đã hết hạn; vui lòng thao tác lại", 0, 0, null);
        }
        String purchaseKey = intent.getPurchaseKey();
        String payloadFingerprint = VndPurchaseIntent.computeFingerprint(productType, requestedAmount, POLICY_VERSION);

        // 4. Build frozen complete entitlement
        VndEntitlementPayload entitlement = buildEntitlement(player, productType, requestedAmount);
        String frozenPayloadJson = entitlement.toJsonString();

        // 5. STAGE 1 — Atomic Debit & Pending Outbox
        MoneyLedgerRepository.DebitResult debitResult = repository.recordDebitAndPendingOutbox(
            purchaseKey,
            player.getSession().userId,
            player.id,
            productType,
            requestedAmount,
            payloadFingerprint,
            frozenPayloadJson,
            POLICY_VERSION
        );

        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.UNKNOWN) {
            player.persistenceQuarantined = true;
            return new PurchaseResult(PurchaseOutcome.PLAYER_QUARANTINED,
                "Giao dịch cần kiểm tra; vui lòng đăng nhập lại", 0, 0, purchaseKey);
        }
        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.VIP_LIMIT_REACHED) {
            return new PurchaseResult(PurchaseOutcome.VIP_LIMIT_REACHED,
                "Bạn đã mua tối đa 4 lượt VIP mùa này rồi!", 0, 0, purchaseKey);
        }

        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.INSUFFICIENT_BALANCE) {
            return new PurchaseResult(PurchaseOutcome.INSUFFICIENT_FUNDS,
                "Số dư không đủ, vui lòng nạp thêm", debitResult.balanceBefore(), debitResult.balanceAfter(), purchaseKey);
        }
        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.CONFLICTING_PAYLOAD) {
            return new PurchaseResult(PurchaseOutcome.PAYLOAD_MISMATCH,
                "Phiên giao dịch bị xung đột; vui lòng mở lại menu", 0, 0, purchaseKey);
        }
        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.ALREADY_COMMITTED) {
            player.getSession().vnd = repository.readBalance(player.getSession().userId);
            // Already delivered idempotently!
            return new PurchaseResult(PurchaseOutcome.SUCCESS,
                "Giao dịch đã hoàn tất thành công", debitResult.balanceBefore(), debitResult.balanceAfter(), purchaseKey);
        }
        if (debitResult.status() == MoneyLedgerRepository.DebitStatus.FAILED) {
            return new PurchaseResult(PurchaseOutcome.FAILED,
                "Giao dịch thất bại; vui lòng thử lại sau", 0, 0, purchaseKey);
        }

        // 6. STAGE 2 — Capacity Check & Delivery
        MoneyLedgerRepository.OutboxRecord durable = repository.findOutboxRecord(purchaseKey);
        if (durable == null) throw new IllegalStateException("Committed entitlement missing");
        player.getSession().vnd = repository.readBalance(player.getSession().userId);
        return deliverEntitlement(player, productType, purchaseKey,
            VndEntitlementPayload.fromJsonString(durable.frozenEntitlementJson()), player.getSession().vnd);
    }

    /**
     * Executes Stage 2 of purchase delivery or recovers a previously pending outbox entry.
     */
    public PurchaseResult deliverEntitlement(
        Player player,
        VndProductType productType,
        String purchaseKey,
        VndEntitlementPayload entitlement,
        int balanceAfter
    ) {
        return deliverEntitlement(player, productType, purchaseKey, entitlement, balanceAfter, true);
    }

    private PurchaseResult deliverEntitlement(Player player, VndProductType productType,
        String purchaseKey, VndEntitlementPayload entitlement, int balanceAfter, boolean notifyClient) {
        Object playerLock = player.inventory != null ? player.inventory : player;
        synchronized (playerLock) {
            // Verify quarantine
            if (player.persistenceQuarantined) {
                return new PurchaseResult(PurchaseOutcome.PLAYER_QUARANTINED,
                    "Trạng thái giao dịch chưa xác định; vui lòng đăng nhập lại", 0, balanceAfter, purchaseKey);
            }

            // Check if already delivered by a concurrent thread
            MoneyLedgerRepository.OutboxRecord existingRecord = repository.findOutboxRecord(purchaseKey);
            if (existingRecord == null || existingRecord.playerId() != player.id
                || player.getSession() == null || existingRecord.accountId() != player.getSession().userId) {
                throw new IllegalStateException("Entitlement ownership mismatch");
            }
            if (existingRecord != null && "DELIVERED".equalsIgnoreCase(existingRecord.status())) {
                return new PurchaseResult(PurchaseOutcome.SUCCESS, "Giao dịch đã hoàn tất thành công", 0, balanceAfter, purchaseKey);
            }
            entitlement = VndEntitlementPayload.fromJsonString(existingRecord.frozenEntitlementJson());
            productType = existingRecord.productType();

            // Check bag capacity
            List<Item> simulatedBag = deepCopyItems(player.inventory.itemsBag);
            boolean capacityOk = true;
            for (VndEntitlementPayload.ItemSpec spec : entitlement.getItems()) {
                Item it = spec.toItem();
                it.createTime = existingRecord.createdAt();
                if (!InventoryService.gI().addItemList(simulatedBag, it)) {
                    capacityOk = false;
                    break;
                }
            }

            if (!capacityOk) {
                repository.recordOutboxFailure(purchaseKey, "INSUFFICIENT_BAG_CAPACITY");
                if (notifyClient) Service.gI().sendThongBao(player, "Hành trang không đủ chỗ trống. Phần thưởng đang chờ nhận lại.");
                return new PurchaseResult(PurchaseOutcome.PENDING_DELIVERY,
                    "Hành trang không đủ chỗ trống. Phần thưởng đang chờ nhận lại.", 0, balanceAfter, purchaseKey);
            }

            // Check gem cap
            if (entitlement.getGem() > 0) {
                long projectedGem = (long) player.inventory.gem + entitlement.getGem();
                if (player.inventory.gem < 0 || projectedGem > PlayerConfig.getMaxGem()) {
                    repository.recordOutboxFailure(purchaseKey, "GEM_CAP_EXCEEDED");
                    if (notifyClient) Service.gI().sendThongBao(player, "Số lượng ngọc đã đạt giới hạn tối đa.");
                    return new PurchaseResult(PurchaseOutcome.PENDING_DELIVERY,
                        "Số lượng ngọc đã đạt giới hạn tối đa.", 0, balanceAfter, purchaseKey);
                }
            }

            // Build updated player states
            List<Item> finalBag = simulatedBag;
            long finalGold = player.inventory.gold + entitlement.getGold();
            int finalGem = player.inventory.gem + entitlement.getGem();

            // Prepare VIP updates
            byte finalVip = player.vip;
            long finalTimeVip = player.timevip;
            int finalVipPurchaseCount = player.vipPurchaseCount;
            if (entitlement.isVipPurchase()) {
                if (entitlement.getVipLevel() > finalVip) {
                    finalVip = (byte) entitlement.getVipLevel();
                }
                finalVipPurchaseCount++;
                long now = existingRecord.createdAt();
                if (finalTimeVip < now) {
                    finalTimeVip = now + entitlement.getVipDurationMs();
                } else {
                    finalTimeVip += entitlement.getVipDurationMs();
                }
            }

            // Pet creation if needed
            Pet newPetObj = null;
            if (entitlement.isCreateNormalPetIfMissing() && player.pet == null) {
                newPetObj = constructNormalPet(player, purchaseKey);
            }

            // Badges task updates
            List<BadgesTask> updatedBadges = deepCopyBadges(player.dataTaskBadges);
            for (VndEntitlementPayload.BadgeProgressSpec bs : entitlement.getBadgeUpdates()) {
                applyBadgeProgress(updatedBadges, bs.badgeTaskId, bs.amount);
            }

            // Event point updates
            int finalEventPoint = Math.addExact(player.event != null ? player.event.getEventPoint() : 0, entitlement.getEventPoints());
            if (entitlement.getEventPoints() > 0) {
                applyBadgeProgress(updatedBadges, ConstTaskBadges.XSMAX, entitlement.getEventPoints());
            }

            // JSON serialization for atomic DB commit
            String itemsBagJson = serializeItemsBag(finalBag);
            String dataInventoryJson = serializeInventory(player, finalGold, finalGem);
            String dataVipJson = serializeDataVip(player, finalVip, finalTimeVip, finalVipPurchaseCount);
            // Existing pets are independently mutable; never rewrite one from a
            // purchase snapshot. Only creation belongs to this entitlement.
            String petJson = newPetObj == null ? null : serializePet(player, newPetObj);
            String dataTaskBadgesJson = JSONValue.toJSONString(updatedBadges);
            String dataEventJson = serializeDataEvent(player);

            // Execute DB delivery transaction
            boolean delivered = repository.markDeliveredWithPlayerState(
                purchaseKey,
                player.id,
                itemsBagJson,
                dataInventoryJson,
                dataVipJson,
                petJson,
                dataTaskBadgesJson,
                finalEventPoint,
                dataEventJson
            );

            if (!delivered) {
                // Ambiguous commit check: probe outbox via fresh read
                {
                    // Indeterminate: quarantine player to prevent dirty autosave overwrite
                    player.persistenceQuarantined = true;
                    Logger.error("[SEC-05] Quarantined player after ambiguous delivery commit, playerId=" + player.id + ", purchaseKey=" + purchaseKey);
                    return new PurchaseResult(PurchaseOutcome.PLAYER_QUARANTINED,
                        "Trạng thái giao dịch chưa xác định; vui lòng đăng nhập lại", 0, balanceAfter, purchaseKey);
                }
            }

            // Apply to live player in RAM
            player.inventory.itemsBag.clear();
            player.inventory.itemsBag.addAll(finalBag);
            player.inventory.gold = finalGold;
            player.inventory.gem = finalGem;
            player.vip = finalVip;
            player.timevip = finalTimeVip;
            player.vipPurchaseCount = finalVipPurchaseCount;
            if (newPetObj != null && player.pet == null) {
                player.pet = newPetObj;
            }
            player.dataTaskBadges = updatedBadges;
            if (player.event != null) {
                player.event.setEventPoint(finalEventPoint);
            }

            // Refresh session projection
            if (player.getSession() != null) {
                player.getSession().vnd = repository.readBalance(player.getSession().userId);
            }


            // Send client updates
            try {
                if (notifyClient) {
                    InventoryService.gI().sendItemBags(player);
                    Service.gI().sendMoney(player);
                    sendSuccessNotification(player, productType, entitlement);
                }
            } catch (RuntimeException notificationFailure) {
                Logger.error("[SEC-05] Committed purchase notification failed, key=" + purchaseKey);
            }

            return new PurchaseResult(PurchaseOutcome.SUCCESS, "Giao dịch thành công", 0, balanceAfter, purchaseKey);
        }
    }

    // =========================================================================
    // LOGIN RECOVERY
    // =========================================================================

    public void recoverPendingDeliveriesOnLogin(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            List<MoneyLedgerRepository.OutboxRecord> pending = repository.findPendingDeliveriesForPlayer(player.id);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            Logger.log(Logger.GREEN, "[SEC-05] Found " + pending.size() + " pending deliveries for player " + player.name + " (id=" + player.id + ")");
            for (MoneyLedgerRepository.OutboxRecord rec : pending) {
                VndEntitlementPayload payload = VndEntitlementPayload.fromJsonString(rec.frozenEntitlementJson());
                deliverEntitlement(player, rec.productType(), rec.purchaseKey(), payload, player.getSession().vnd, false);
                if (player.persistenceQuarantined) break;
            }
        } catch (Exception e) {
            player.persistenceQuarantined = true;
            Logger.logException(MoneyLedgerService.class, e, "[SEC-05] Error recovering pending deliveries for playerId=" + player.id);
        }
    }

    // =========================================================================
    // REWARD BUILDER & HELPERS
    // =========================================================================

    public VndEntitlementPayload buildEntitlement(Player player, VndProductType productType, int amount) {
        VndEntitlementPayload payload = new VndEntitlementPayload();
        switch (productType) {
            case TRADE_GOLD -> {
                int goldBars = (amount / 1000) * 4;
                payload.addItem((short) 457, goldBars);
                int tickets = (amount / 10000) * 10;
                payload.addItem((short) 718, tickets);
                payload.addBadgeProgress(ConstTaskBadges.DAI_GIA_MOI_NHU, amount);
                payload.addBadgeProgress(ConstTaskBadges.EM_XINH_EM_DEP, amount);
                int eventPoints = (amount / 10000) * 50;
                payload.setEventPoints(eventPoints);
            }
            case TRADE_GEM -> {
                payload.setGem(amount);
                int tickets = (amount / 10000) * 10;
                payload.addItem((short) 718, tickets);
                payload.addBadgeProgress(ConstTaskBadges.DAI_GIA_MOI_NHU, amount);
                payload.addBadgeProgress(ConstTaskBadges.EM_XINH_EM_DEP, amount);
                int eventPoints = (amount / 10000) * 50;
                payload.setEventPoints(eventPoints);
            }
            case VIP_1 -> {
                payload.setVipPurchase(true);
                payload.setVipLevel(1);
                payload.setVipDurationMs(30L * 24 * 3600 * 1000);
                payload.addItem((short) 457, 200);
                payload.addItem((short) 459, 10);
                payload.addItem((short) 1252, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 10),
                    new VndEntitlementPayload.OptionSpec(77, 10),
                    new VndEntitlementPayload.OptionSpec(103, 10),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 1248, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 10),
                    new VndEntitlementPayload.OptionSpec(77, 10),
                    new VndEntitlementPayload.OptionSpec(103, 10),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 1256, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 10),
                    new VndEntitlementPayload.OptionSpec(77, 10),
                    new VndEntitlementPayload.OptionSpec(103, 10),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 987, 5);
                payload.setCreateNormalPetIfMissing(true);
            }
            case VIP_2 -> {
                payload.setVipPurchase(true);
                payload.setVipLevel(2);
                payload.setVipDurationMs(30L * 24 * 3600 * 1000);
                payload.addItem((short) 457, 500);
                payload.addItem((short) 459, 10);
                payload.addItem((short) 1252, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 1248, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 1254, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.addItem((short) 987, 10);
                payload.addItem((short) 584, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 24),
                    new VndEntitlementPayload.OptionSpec(77, 24),
                    new VndEntitlementPayload.OptionSpec(117, 15),
                    new VndEntitlementPayload.OptionSpec(93, 30)
                ));
                payload.setCreateNormalPetIfMissing(true);
            }
            case VIP_3 -> {
                payload.setVipPurchase(true);
                payload.setVipLevel(3);
                payload.setVipDurationMs(30L * 24 * 3600 * 1000);
                payload.addItem((short) 457, 700);
                payload.addItem((short) 459, 10);
                payload.addItem((short) 1252, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12)
                ));
                payload.addItem((short) 1248, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12)
                ));
                payload.addItem((short) 1254, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 12),
                    new VndEntitlementPayload.OptionSpec(77, 12),
                    new VndEntitlementPayload.OptionSpec(103, 12)
                ));
                payload.addItem((short) 987, 30);
                payload.addItem((short) 584, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 24),
                    new VndEntitlementPayload.OptionSpec(77, 24),
                    new VndEntitlementPayload.OptionSpec(117, 15)
                ));
                payload.addItem((short) 1655, 2, List.of(
                    new VndEntitlementPayload.OptionSpec(30, 0)
                ));
                payload.addItem((short) 956, 10);
                payload.setCreateNormalPetIfMissing(true);
            }
            case VIP_4 -> {
                payload.setVipPurchase(true);
                payload.setVipLevel(4);
                payload.setVipDurationMs(30L * 24 * 3600 * 1000);
                payload.addItem((short) 457, 1000);
                payload.addItem((short) 459, 10);
                payload.addItem((short) 568, 1);
                payload.addItem((short) 987, 50);
                payload.addItem((short) 1554, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 15),
                    new VndEntitlementPayload.OptionSpec(77, 15),
                    new VndEntitlementPayload.OptionSpec(103, 15),
                    new VndEntitlementPayload.OptionSpec(14, 10)
                ));
                payload.addItem((short) 1771, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 18),
                    new VndEntitlementPayload.OptionSpec(77, 18),
                    new VndEntitlementPayload.OptionSpec(5, 18),
                    new VndEntitlementPayload.OptionSpec(14, 10),
                    new VndEntitlementPayload.OptionSpec(236, 15)
                ));
                payload.addItem((short) 1772, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 15),
                    new VndEntitlementPayload.OptionSpec(77, 15),
                    new VndEntitlementPayload.OptionSpec(103, 15),
                    new VndEntitlementPayload.OptionSpec(236, 15)
                ));
                payload.addItem((short) 1557, 1, List.of(
                    new VndEntitlementPayload.OptionSpec(50, 25),
                    new VndEntitlementPayload.OptionSpec(77, 25),
                    new VndEntitlementPayload.OptionSpec(117, 25),
                    new VndEntitlementPayload.OptionSpec(236, 25)
                ));
                payload.addItem((short) 1655, 5, List.of(
                    new VndEntitlementPayload.OptionSpec(30, 0)
                ));
                payload.addItem((short) 1204, 20);
                payload.setCreateNormalPetIfMissing(false);
            }
        }
        return payload;
    }

    private void sendSuccessNotification(Player player, VndProductType productType, VndEntitlementPayload entitlement) {
        if (productType == VndProductType.TRADE_GOLD) {
            int thoiVang = (entitlement.getItems().size() > 0) ? entitlement.getItems().get(0).quantity : 0;
            int tickets = (entitlement.getItems().size() > 1) ? entitlement.getItems().get(1).quantity : 0;
            Service.gI().sendThongBao(player, "Bạn nhận thêm " + entitlement.getEventPoints() + " điểm sự kiện!");
            Service.gI().sendThongBao(player, "Đã đổi thành công. Bạn nhận được " + thoiVang + " thỏi vàng và " + tickets + " vé tặng ngọc.");
        } else if (productType == VndProductType.TRADE_GEM) {
            int tickets = (!entitlement.getItems().isEmpty()) ? entitlement.getItems().get(0).quantity : 0;
            Service.gI().sendThongBao(player, "Bạn nhận thêm " + entitlement.getEventPoints() + " điểm sự kiện!");
            Service.gI().sendThongBao(player, "Đã nạp thành công. Bạn nhận được " + entitlement.getGem() + " ngọc và " + tickets + " vé tặng ngọc.");
        } else if (productType.isVip()) {
            Service.gI().sendThongBao(player, "Mua VIP " + productType.getVipLevel() + " thành công! Bạn đã mua " + player.vipPurchaseCount + "/4 lượt VIP mùa này.");
        }
    }

    private Pet constructNormalPet(Player player, String purchaseKey) {
        java.util.Random random = new java.util.Random(purchaseKey.hashCode());
        Pet pet = new Pet(player);
        pet.name = "$Đệ tử";
        pet.gender = (byte) random.nextInt(3);
        pet.id = player.isPl() ? -player.id : -Math.abs(player.id) - 100000;
        pet.nPoint.power = 2000;
        pet.nPoint.limitPower = 0;
        pet.typePet = 0;
        pet.type = 0;
        pet.nPoint.stamina = 1000;
        pet.nPoint.maxStamina = 1000;
        pet.nPoint.hpg = 1000 + random.nextInt(2001);
        pet.nPoint.mpg = 1000 + random.nextInt(2001);
        pet.nPoint.dameg = 50 + random.nextInt(51);
        pet.nPoint.defg = 10 + random.nextInt(41);
        pet.nPoint.critg = 1 + random.nextInt(5);
        for (int i = 0; i < 7; i++) {
            pet.inventory.itemsBody.add(ItemService.gI().createItemNull());
        }
        pet.playerSkill.skills.add(SkillUtil.createSkill(random.nextInt(3) * 2, 1));
        for (int i = 0; i < 4; i++) {
            pet.playerSkill.skills.add(SkillUtil.createEmptySkill());
        }
        pet.nPoint.setFullHpMp();
        return pet;
    }

    private static List<Item> deepCopyItems(List<Item> source) {
        List<Item> copy = new ArrayList<>(source.size());
        for (Item item : source) {
            copy.add(item == null ? null : ItemService.gI().copyItem(item));
        }
        return copy;
    }

    private static List<BadgesTask> deepCopyBadges(List<BadgesTask> source) {
        if (source == null) return new ArrayList<>();
        List<BadgesTask> copy = new ArrayList<>(source.size());
        for (BadgesTask bt : source) {
            if (bt != null) {
                BadgesTask c = new BadgesTask();
                c.id = bt.id;
                c.count = bt.count;
                c.countMax = bt.countMax;
                c.idBadgesReward = bt.idBadgesReward;
                copy.add(c);
            }
        }
        return copy;
    }

    private static void applyBadgeProgress(List<BadgesTask> list, int id, int amount) {
        if (list == null || amount <= 0) return;
        for (BadgesTask bt : list) {
            if (bt.id == id) {
                bt.count = (int) Math.min((long) bt.count + amount, bt.countMax);
                break;
            }
        }
    }

    private static String serializeInventory(Player player, long gold, int gem) {
        JSONArray values = new JSONArray();
        values.add(Math.min(gold, PlayerConfig.getMaxGold()));
        values.add(gem);
        values.add(player.inventory.ruby);
        values.add(player.inventory.coupon);
        values.add(player.inventory.event);
        return values.toJSONString();
    }

    private static String serializeItemsBag(List<Item> items) {
        JSONArray bag = new JSONArray();
        for (Item item : items) {
            JSONArray row = new JSONArray();
            JSONArray options = new JSONArray();
            if (item != null && item.isNotNullItem() && item.template != null) {
                row.add((int) item.template.id);
                row.add(item.quantity);
                if (item.itemOptions != null) {
                    for (Item.ItemOption option : item.itemOptions) {
                        if (option == null || option.optionTemplate == null) {
                            continue;
                        }
                        JSONArray encodedOption = new JSONArray();
                        encodedOption.add((int) option.optionTemplate.id);
                        encodedOption.add(option.param);
                        options.add(encodedOption.toJSONString());
                    }
                }
            } else {
                row.add(-1);
                row.add(0);
            }
            row.add(options.toJSONString());
            row.add(item != null ? item.createTime : 0L);
            bag.add(row.toJSONString());
        }
        return bag.toJSONString();
    }

    private static String serializeDataVip(Player player, byte vip, long timeVip, int vipPurchaseCount) {
        JSONArray arr = new JSONArray();
        arr.add(player.timesPerDayCuuSat);
        arr.add(player.lastTimeCuuSat);
        arr.add(player.nhanDeTuNangVIP);
        arr.add(player.nhanVangNangVIP);
        arr.add(player.nhanSKHVIP);
        arr.add(vip);
        arr.add(timeVip);
        arr.add(vipPurchaseCount);
        return arr.toJSONString();
    }

    private static String serializePet(Player player, Pet pet) {
        if (pet == null) {
            return "[]";
        }
        JSONArray dataArray = new JSONArray();
        // petInfo
        JSONArray info = new JSONArray();
        info.add((int) pet.typePet);
        info.add((int) pet.gender);
        info.add(pet.name);
        info.add(player.fusion != null ? (int) player.fusion.typeFusion : 0);
        info.add(0);
        info.add((int) pet.status);
        dataArray.add(info.toJSONString());

        // petPoint
        JSONArray point = new JSONArray();
        point.add((int) pet.nPoint.limitPower);
        point.add(pet.nPoint.power);
        point.add(pet.nPoint.tiemNang);
        point.add((int) pet.nPoint.stamina);
        point.add((int) pet.nPoint.maxStamina);
        point.add(pet.nPoint.hpg);
        point.add(pet.nPoint.mpg);
        point.add(pet.nPoint.dameg);
        point.add(pet.nPoint.defg);
        point.add((int) pet.nPoint.critg);
        point.add(pet.nPoint.hp);
        point.add(pet.nPoint.mp);
        dataArray.add(point.toJSONString());

        // petBody
        JSONArray items = new JSONArray();
        for (Item item : pet.inventory.itemsBody) {
            JSONArray dataItem = new JSONArray();
            JSONArray options = new JSONArray();
            if (item != null && item.isNotNullItem() && item.template != null) {
                dataItem.add((int) item.template.id);
                dataItem.add(item.quantity);
                if (item.itemOptions != null) {
                    for (ItemOption io : item.itemOptions) {
                        if (io != null && io.optionTemplate != null) {
                            JSONArray opt = new JSONArray();
                            opt.add((int) io.optionTemplate.id);
                            opt.add(io.param);
                            options.add(opt.toJSONString());
                        }
                    }
                }
                dataItem.add(options.toJSONString());
            } else {
                dataItem.add(-1);
                dataItem.add(0);
                dataItem.add(options.toJSONString());
            }
            dataItem.add(item != null ? item.createTime : 0L);
            items.add(dataItem.toJSONString());
        }
        dataArray.add(items.toJSONString());

        // petSkill
        JSONArray petSkills = new JSONArray();
        for (int skillIndex = 0; skillIndex < 5; skillIndex++) {
            var s = skillIndex < pet.playerSkill.skills.size() ? pet.playerSkill.skills.get(skillIndex) : null;
            JSONArray pskill = new JSONArray();
            if (s != null && s.skillId != -1 && s.template != null) {
                pskill.add((int) s.template.id);
                pskill.add(s.point);
                pskill.add(s.lastTimeUseThisSkill);
                pskill.add((int) s.currLevel);
                pskill.add(s.getActualLevel());
                pskill.add(s.masteryProgress);
            } else {
                pskill.add(-1);
                pskill.add(0);
                pskill.add(0L);
                pskill.add(0);
                pskill.add(0);
                pskill.add(0);
            }
            petSkills.add(pskill.toJSONString());
        }
        dataArray.add(petSkills.toJSONString());

        return dataArray.toJSONString();
    }

    private static String serializeDataEvent(Player player) {
        JSONArray arr = new JSONArray();
        arr.add(player.eventPointType1);
        arr.add(player.eventPointType2);
        arr.add(player.eventPointType3);
        arr.add(player.eventPointType4);
        arr.add(player.eventPointType5);
        arr.add(player.eventPointType6);
        arr.add(player.checkDailyReward);
        arr.add(player.checkTopReward1);
        arr.add(player.checkTopReward2);
        arr.add(player.checkTopReward3);
        return arr.toJSONString();
    }
}
