package nro.models.player;

import nro.models.item.Item;
import nro.models.services.ItemService;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SetClothes {

    private Player player;

    public SetClothes(Player player) {
        this.player = player;
    }

    public byte songoku;
    public byte thienXinHang;
    public byte kirin;
    public byte kaioken;
    public byte thanVuTruKaio;

    public byte ocTieu;
    public byte pikkoroDaimao;
    public byte picolo;
    public byte lienHoan;
    public byte nail;

    public byte kakarot;
    public byte cadic;
    public byte nappa;
    public byte giamSatThuong;
    public byte cadicM;

    public byte worldcup;
    public byte setDHD;

    public boolean godClothes;
    public int ctHaiTac = -1;

    public void setup() {
        setDefault();
        setupSKT();

        this.godClothes = true;
        for (int i = 0; i < 5; i++) {
            Item item = this.player.inventory.itemsBody.get(i);
            if (item.isNotNullItem()) {
                if (item.template.id > 567 || item.template.id < 555) {
                    this.godClothes = false;
                    break;
                }
            } else {
                this.godClothes = false;
            }
        }
        Item ct = this.player.inventory.itemsBody.get(5);
        if (ct.isNotNullItem()) {
            switch (ct.template.id) {
                case 618:
                case 619:
                case 620:
                case 621:
                case 622:
                case 623:
                case 624:
                case 626:
                case 627:
                    this.ctHaiTac = ct.template.id;
                    break;

            }
        }

    }

    private void setupSKT() {
        for (int i = 0; i < 5; i++) {
            Item item = this.player.inventory.itemsBody.get(i);

            // Loại bỏ điều kiện kiểm tra isNotNullItem
            boolean isActSet = false;
            for (Item.ItemOption io : item.itemOptions) {
                switch (io.optionTemplate.id) {
                    case 129:
                    case 141:
                        isActSet = true;
                        songoku++;
                        break;
                    case 127:
                    case 139:
                        isActSet = true;
                        thienXinHang++;
                        break;
                    case 128:
                    case 140:
                        isActSet = true;
                        kirin++;
                        break;
                    case 131:
                    case 143:
                        isActSet = true;
                        ocTieu++;
                        break;
                    case 132:
                    case 144:
                        isActSet = true;
                        pikkoroDaimao++;
                        break;
                    case 130:
                    case 142:
                        isActSet = true;
                        picolo++;
                        break;
                    case 135:
                    case 138:
                        isActSet = true;
                        nappa++;
                        break;
                    case 133:
                    case 136:
                        isActSet = true;
                        kakarot++;
                        break;
                    case 134:
                    case 137:
                        isActSet = true;
                        cadic++;
                        break;
                    case 253:
                        isActSet = true;
                        kaioken++;
                        break;
                    case 250:
                        isActSet = true;
                        lienHoan++;
                        break;
                    case 252:
                    case 255:
                        isActSet = true;
                        giamSatThuong++;
                        break;

                    case 21:
                        if (io.param == 80) {
                            setDHD++;
                        }
                        break;
                    case 245:
                    case 246:
                    case 247:
                    case 248:
                        isActSet = true;
                        thanVuTruKaio++;
                        break;
                    case 237:
                    case 238:
                    case 239:
                    case 240:
                        isActSet = true;
                        nail++;
                        break;
                    case 241:
                    case 242:
                    case 243:
                    case 244:
                        isActSet = true;
                        cadicM++;
                        break;
                }

                if (isActSet) {
                    break;
                }
            }
        }
    }

    private void setDefault() {
        this.songoku = 0;
        this.thienXinHang = 0;
        this.kirin = 0;
        this.kaioken = 0;
        this.ocTieu = 0;
        this.pikkoroDaimao = 0;
        this.picolo = 0;
        this.lienHoan = 0;
        this.kakarot = 0;
        this.cadic = 0;
        this.nappa = 0;
        this.giamSatThuong = 0;
        this.thanVuTruKaio = 0;
        this.nail = 0;
        this.cadicM = 0;
        this.setDHD = 0;
        this.worldcup = 0;
        this.godClothes = false;
        this.ctHaiTac = -1;
    }

    public boolean checkSetGod() {
        if (this.player.isBot) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            Item item = this.player.inventory.itemsBody.get(i);
            if (item.isNotNullItem()) {
                if (item.template.id < 555 || item.template.id > 567) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public boolean checkSetDes() {
        for (int i = 0; i < 5; i++) {
            Item item = this.player.inventory.itemsBody.get(i);
            if (item.isNotNullItem()) {
                if (item.template.id < 650 || item.template.id > 662) {

                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public void dispose() {
        this.player = null;
    }
}
