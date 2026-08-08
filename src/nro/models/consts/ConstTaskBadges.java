package nro.models.consts;

public class ConstTaskBadges {

    public static final byte DAI_GIA_MOI_NHU = 1;
    public static final byte TRUM_UOC_RONG = 2;
    public static final byte TRUM_SAN_BOSS = 3;
    public static final byte THANH_DAP_DO_7 = 4;
    public static final byte CAO_THU_SIEU_HANG = 5;
    public static final byte NONG_DAN_CHAM_CHI = 6;
    public static final byte KE_THAO_TUNG_SOI = 7;
    public static final byte NUOC_ANH_BAO = 8;
    public static final byte ONG_THAN_VE_CHAI = 9;
    public static final byte BI_MOC_SACH_TUI = 10;
    public static final byte O_DO = 11;
    public static final byte GO_DAU_TRE = 12;
    public static final byte GO_DAU_TRE1 = 13;
    public static final byte GO_DAU_TRE2 = 14;
    public static final byte XSMAX = 15;
    public static final byte EM_XINH_EM_DEP = 16;
    public static final byte ME_RONG = 17;
    public static final byte KOL = 18;

    public static String getNameById(int id) {
        return switch (id) {
            case DAI_GIA_MOI_NHU ->
                "Đại Gia Mới Nhú";
            case TRUM_UOC_RONG ->
                "Trùm Ước Rồng";
            case TRUM_SAN_BOSS ->
                "Trùm Săn Boss";
            case THANH_DAP_DO_7 ->
                "Thánh Đập Đồ +7";
            case CAO_THU_SIEU_HANG ->
                "Cao Thủ Siêu Hạng";
            case NONG_DAN_CHAM_CHI ->
                "Nông Dân Chăm Chỉ";
            case KE_THAO_TUNG_SOI ->
                "Kẻ Tháo Tung Sói";
            case NUOC_ANH_BAO ->
                "Nước Anh Bao";
            case ONG_THAN_VE_CHAI ->
                "Ông Thần Ve Chai";
            case BI_MOC_SACH_TUI ->
                "Bị Móc Sạch Túi";
            case O_DO ->
                "Thánh ở dơ";
            case GO_DAU_TRE ->
                "Gõ Đầu Trẻ";
            case GO_DAU_TRE1 ->
                "Gõ Đầu Trẻ";
            case GO_DAU_TRE2 ->
                "Gõ Đầu Trẻ";
            case XSMAX ->
                "XSMAX";
            case EM_XINH_EM_DEP ->
                "Em Xinh Em Đẹp";
            case ME_RONG ->
                "Mẹ Rồng";
            case KOL ->
                "KOL";
            default ->
                "Danh hiệu không xác định";
        };
    }
}
