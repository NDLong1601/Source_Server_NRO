package nro.models.database;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import nro.models.data.LocalManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import lombok.Getter;
import lombok.Setter;
import nro.models.utils.Logger;

public class EventDAO {

    @Setter
    @Getter
    private static long remainingTimeToIncreasePotentialAndPower = 0;
    @Setter
    @Getter
    private static long remainingTimeToIncreaseHP = 0;
    @Setter
    @Getter
    private static long remainingTimeToIncreaseMP = 0;
    @Setter
    @Getter
    private static long remainingTimeToIncreaseDame = 0;

    public static void loadInternationalWomensDayEvent() {
        try (Connection con = LocalManager.getConnection();) {
            ensureSchema(con);
            PreparedStatement ps = con.prepareStatement("SELECT `data` FROM `event` WHERE `name` = 'international_womens_day'");
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(String.valueOf(rs.getString("data")), JsonObject.class);
                remainingTimeToIncreaseDame = getLong(jsonObject, "damePrecent");
                remainingTimeToIncreaseHP = getLong(jsonObject, "hpPrecent");
                remainingTimeToIncreaseMP = getLong(jsonObject, "mpPrecent");
                remainingTimeToIncreasePotentialAndPower = getLong(jsonObject, "papPrecent");
            }
        } catch (Exception ex) {
            Logger.logException(EventDAO.class, ex, "Không thể tải dữ liệu sự kiện 8/3");
        }
    }

    public static void save() {
        try {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("damePrecent", remainingTimeToIncreaseDame);
            jsonObject.addProperty("hpPrecent", remainingTimeToIncreaseHP);
            jsonObject.addProperty("mpPrecent", remainingTimeToIncreaseMP);
            jsonObject.addProperty("papPrecent", remainingTimeToIncreasePotentialAndPower);

            String jsonData = jsonObject.toString();

            LocalManager.executeUpdate("INSERT INTO `event` (`name`, `data`) VALUES ('international_womens_day', ?) "
                    + "ON DUPLICATE KEY UPDATE `data` = VALUES(`data`)", jsonData);
        } catch (Exception e) {
            Logger.logException(EventDAO.class, e, "Không thể lưu dữ liệu sự kiện 8/3");
        }

    }

    private static void ensureSchema(Connection con) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("CREATE TABLE IF NOT EXISTS `event` ("
                + "`name` varchar(64) NOT NULL, `data` text NOT NULL, "
                + "`updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(), "
                + "PRIMARY KEY (`name`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO `event` (`name`, `data`) "
                + "VALUES ('international_womens_day', '{\"damePrecent\":0,\"hpPrecent\":0,\"mpPrecent\":0,\"papPrecent\":0}')")) {
            ps.executeUpdate();
        }
    }

    private static long getLong(JsonObject data, String key) {
        return data != null && data.has(key) && !data.get(key).isJsonNull()
                ? data.getAsJsonPrimitive(key).getAsLong() : 0;
    }

}
