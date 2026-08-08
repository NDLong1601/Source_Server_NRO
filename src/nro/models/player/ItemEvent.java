package nro.models.player;

import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class ItemEvent {

    public Player player;

    public long lastTVGSTime;
    public long lastItemChuongDong;
    public long lastItemBanhQuy;
    public long lastItemCaTuyet;
    public long lastItemKeoDuong;
    public long lastItemKeoNguoiTuyet;
    public long lastItemManhVo;

    public int remainingChuongDongCount;
    public int remainingBanhQuyCount;
    public int remainingCaTuyetCount;
    public int remainingKeoDuongCount;
    public int remainingKeoNguoiTuyetCount;
    public int remainingTVGSCount;
    public int remainingManhVo;
    
    public long lastHHTime;

    public int remainingHHCount;

    public long lastBNTime;

    public int remainingBNCount;

    public ItemEvent(Player player) {
        this.player = player;
    }

    public boolean canDropTatVoGiangSinh(int maxCount) {
        if (Util.isAfterMidnight(lastTVGSTime)) {
            remainingTVGSCount = maxCount;
            lastTVGSTime = System.currentTimeMillis();
            return true;
        } else if (remainingTVGSCount > 0) {
            remainingTVGSCount--;
            return true;
        }
        return false;
    }

    public boolean canDropRemainingChuongDongCount(int maxCount) {
        if (Util.isAfterMidnight(lastItemChuongDong)) {
            remainingChuongDongCount = maxCount;
            lastItemChuongDong = System.currentTimeMillis();
            return true;
        } else if (remainingChuongDongCount > 0) {
            remainingChuongDongCount--;
            return true;
        }
        return false;
    }

    public boolean canDropRemainingBanhQuyCount(int maxCount) {
        if (Util.isAfterMidnight(lastItemBanhQuy)) {
            remainingBanhQuyCount = maxCount;
            lastItemBanhQuy = System.currentTimeMillis();
            return true;
        } else if (remainingBanhQuyCount > 0) {
            remainingBanhQuyCount--;
            return true;
        }
        return false;
    }

    public boolean canDropRemainingCaTuyetCount(int maxCount) {
        if (Util.isAfterMidnight(lastItemCaTuyet)) {
            remainingCaTuyetCount = maxCount;
            lastItemCaTuyet = System.currentTimeMillis();
            return true;
        } else if (remainingCaTuyetCount > 0) {
            remainingCaTuyetCount--;
            return true;
        }
        return false;
    }

    public boolean canDropRemainingKeoDuongCount(int maxCount) {
        if (Util.isAfterMidnight(lastItemKeoDuong)) {
            remainingKeoDuongCount = maxCount;
            lastItemKeoDuong = System.currentTimeMillis();
            return true;
        } else if (remainingKeoDuongCount > 0) {
            remainingKeoDuongCount--;
            return true;
        }
        return false;
    }

    public boolean canDropRemainingKeoNguoiTuyetCount(int maxCount) {
        if (Util.isAfterMidnight(lastItemKeoNguoiTuyet)) {
            remainingKeoNguoiTuyetCount = maxCount;
            lastItemKeoNguoiTuyet = System.currentTimeMillis();
            return true;
        } else if (remainingKeoNguoiTuyetCount > 0) {
            remainingKeoNguoiTuyetCount--;
            return true;
        }
        return false;
    }

    public boolean canDropHoaHong(int maxCount) {
        if (Util.isAfterMidnight(lastHHTime)) {
            remainingHHCount = maxCount;
            lastHHTime = System.currentTimeMillis();
            return true;
        } else if (remainingHHCount > 0) {
            remainingHHCount--;
            return true;
        }
        return false;
    }

    public boolean canDropBinhNuoc(int maxCount) {
        if (Util.isAfterMidnight(lastBNTime)) {
            remainingBNCount = maxCount;
            lastBNTime = System.currentTimeMillis();
            return true;
        } else if (remainingBNCount > 0) {
            remainingBNCount--;
            return true;
        }
        return false;
    }

   public boolean canDropManhVo(int maxCount) {
    if (Util.isAfterMidnight(lastItemManhVo)) {
        remainingManhVo = maxCount;
        lastItemManhVo = System.currentTimeMillis();
        return true;
    } else if (remainingManhVo > 0) {
        remainingManhVo--;
        return true;
    }
    return false;
}
}
