
package nro.models.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.ItemService;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 *
 * @author By Mr Blue
 */
public class TopConDuongRanDoc {

    @Getter
    private List<Player> list = new ArrayList<>();
    private static final TopConDuongRanDoc INSTANCE = new TopConDuongRanDoc();

    public static TopConDuongRanDoc getInstance() {
        return INSTANCE;
    }

    public void load() {
        list.clear();

        try (Connection con = LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT *, "
                + "SUBSTRING_INDEX(SUBSTRING_INDEX(thanhTichCDRD, ',', 1), '[', -1) AS so1, "
                + "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(thanhTichCDRD, ',', 2), ',', -1) AS SIGNED) AS so2, "
                + "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(thanhTichCDRD, ',', 3), ',', -1) AS SIGNED) AS so3, "
                + "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(thanhTichCDRD, ',', 4), ',', -1) AS SIGNED) AS so4 "
                + "FROM player "
                + "WHERE CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(thanhTichCDRD, ',', 2), ',', -1), ']', 1) AS SIGNED) > 0 "
                + "ORDER BY so2 DESC, so1 ASC LIMIT 100")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Player player = extractPlayerFromResultSet(rs);
                    list.add(player);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Player extractPlayerFromResultSet(ResultSet rs) throws SQLException {
        Player player = new Player();

        player.id = rs.getInt("id");
        player.name = rs.getString("name");
        player.head = rs.getShort("head");
        player.gender = rs.getByte("gender");

        player.nameClan = rs.getString("so1");
        player.levelKhiGasDone = rs.getInt("so2");
        player.timeKhiGasDone = rs.getLong("so3");

        player.lastTimeUpdateTopKhiGas = (System.currentTimeMillis() - rs.getLong("so4")) / 1000;

        extractDataPoint(rs.getString("data_point"), player);
        extractItemsBody(rs.getString("items_body"), player);

        return player;
    }

    private void extractDataPoint(String dataPoint, Player player) {
        JSONValue jv = new JSONValue();
        JSONArray dataArray = (JSONArray) jv.parse(dataPoint);
        player.nPoint.power = Long.parseLong(dataArray.get(11).toString());
        dataArray.clear();
    }

    private void extractItemsBody(String itemsBody, Player player) {
        JSONValue jv = new JSONValue();
        Object parsedData = jv.parse(itemsBody);

        if (parsedData instanceof JSONArray) {
            JSONArray dataArray = (JSONArray) parsedData;

            for (Object itemDataObject : dataArray) {
                if (itemDataObject instanceof String) {
                    Item item = createItemFromDataObject((String) itemDataObject);
                    player.inventory.itemsBody.add(item);
                } else {
                }
            }

            dataArray.clear();
        } else {
        }
    }

    private Item createItemFromDataObject(Object itemData) {
        JSONValue jv = new JSONValue();

        try {
            if (itemData instanceof String str) {
                Object parsed = jv.parse(str);
                if (!(parsed instanceof JSONArray)) {
                    return ItemService.gI().createItemNull();
                }
                itemData = parsed;
            }

            if (!(itemData instanceof JSONArray)) {
                return ItemService.gI().createItemNull();
            }

            JSONArray dataItem = (JSONArray) itemData;
            short tempId = Short.parseShort(String.valueOf(dataItem.get(0)));

            if (tempId == -1) {
                return ItemService.gI().createItemNull();
            }

            int quantity = Integer.parseInt(String.valueOf(dataItem.get(1)));
            Item item = ItemService.gI().createNewItem(tempId, quantity);

            Object optionObj = dataItem.get(2);
            if (optionObj instanceof JSONArray optionsArray) {
                for (Object optObj : optionsArray) {
                    if (optObj instanceof JSONArray opt && opt.size() >= 2) {
                        int optionId = Integer.parseInt(String.valueOf(opt.get(0)));
                        int param = Integer.parseInt(String.valueOf(opt.get(1)));
                        item.itemOptions.add(new Item.ItemOption(optionId, param));
                    }
                }
            }

            item.createTime = Long.parseLong(String.valueOf(dataItem.get(3)));

            if (ItemService.gI().isOutOfDateTime(item)) {
                return ItemService.gI().createItemNull();
            }

            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return ItemService.gI().createItemNull();
        }
    }
}
