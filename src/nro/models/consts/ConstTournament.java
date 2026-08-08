package nro.models.consts;

public class ConstTournament {

    public static final int NHI_DONG = 0;
    public static final int SIEU_CAP_1 = 1;
    public static final int SIEU_CAP_2 = 2;
    public static final int SIEU_CAP_3 = 3;
    public static final int NGOAI_HANG = 4;

    public static final int MINS_MAX_CAN_REG = 25;
    public static final int MINS_START = 30;
    public static final int MINS_END = 57;

    public static final String TEXT_TRUAT_QUYEN = "Bạn đã không đến đúng giờ, rất tiếc bạn bị truất quyền tham gia.";
    public static final String TEXT_DOI_THU_BO_CUOC = "Bạn đã thắng vì đối thủ bỏ cuộc, hãy chờ tại đây để thi đấu vòng tiếp theo.";
    public static final String TEXT_NPC_CHAT_ROI_DAI = "Đối thủ đã rời khỏi võ đài, %1 đã giành chiến thắng.";
    public static final String TEXT_DANG_KY_THANH_CONG = "Bạn đã đăng ký thành công! Hãy có mặt trước %1h30. Bây giờ là %2, đến trễ sẽ bị loại.";
    public static final String TEXT_CHIA_BUON = "Xin lỗi vì điều không mong muốn đã xảy ra.";
    public static final String TEXT_DOI_THU_BO_CUOC_ROI_MAP = "Đối thủ đã bỏ cuộc, bạn giành chiến thắng.";
    public static final String TEXT_XU_THUA_BO_CHAY = "Bạn bị xử thua vì đã rời khỏi trận đấu.";
    public static final String TEXT_NPC_CHAT_DOI_THU_BO_CUOC_ROI_MAP = "Đối thủ đã bỏ cuộc, %1 giành chiến thắng.";
    public static final String TEXT_DOI_THU_KIET_SUC = "Đối thủ đã kiệt sức, bạn giành chiến thắng.";
    public static final String TEXT_XU_THUA_KIET_SUC = "Bạn bị xử thua vì kiệt sức.";
    public static final String TEXT_NPC_CHAT_DOI_THU_KIET_SUC = "Đối thủ kiệt sức, %1 đã giành chiến thắng.";
    public static final String TEXT_HET_GIO = "Thời gian thi đấu đã hết.";
    public static final String TEXT_XU_THUA_HET_GIO = "Bạn bị xử thua do hết thời gian.";
    public static final String TEXT_NPC_CHAT_HET_GIO = "Thời gian thi đấu đã hết, %1 giành chiến thắng.";
    public static final String TEXT_HUY_DANG_KY = "Bạn đã hủy đăng ký thành công.";
    public static final String TEXT_THANG_VONG_NAY = "Bạn đã thắng vòng này, xin chờ tại đây ít phút để thi đấu vòng tiếp theo.";
    public static final String TEXT_DA_VO_DICH = "Bạn đã vô địch giải trước, vui lòng đợi giải đấu tiếp theo.";
    public static final String TEXT_VO_DICH = "Chúc mừng bạn đã vô địch giải đấu! Phần thưởng của bạn là 5 viên đá nâng cấp.";
    public static final String TEXT_KHOE_VO_DICH = "Chúc mừng %1 đã vô địch giải %2!";
    public static final String TEXT_CHI_CO_THE_THAM_GIA_GIAI_NHI_DONG = "Bạn chỉ có thể tham gia giải Ngoại hạng hoặc giải Nhi Đồng.";
    public static final String TEXT_CHI_CO_THE_THAM_GIA_GIAI_SIEU_CAP_1 = "Bạn chỉ có thể tham gia giải Ngoại hạng hoặc Siêu cấp 1.";
    public static final String TEXT_CHI_CO_THE_THAM_GIA_GIAI_SIEU_CAP_2 = "Bạn chỉ có thể tham gia giải Ngoại hạng hoặc Siêu cấp 2.";
    public static final String TEXT_CHI_CO_THE_THAM_GIA_GIAI_SIEU_CAP_3 = "Bạn chỉ có thể tham gia giải Ngoại hạng hoặc Siêu cấp 3.";
    public static final String TEXT_CHI_CO_THE_THAM_GIA_GIAI_NGOAI_HANG = "Bạn chỉ có thể tham gia giải Ngoại hạng.";

    public static final String[] tournamentNames = {"Nhi đồng", "Siêu cấp 1", "Siêu cấp 2", "Siêu cấp 3", "Ngoại hạng"};
    public static final int[] tournamentGems = {2, 4, 6, 8, 0};
    public static final int[] tournamentGolds = {0, 0, 0, 0, 10000};
}
