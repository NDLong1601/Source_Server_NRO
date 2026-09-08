package nro.models.server;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.player_system.Template.Part;
import nro.models.player_system.Template.PartDetail;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

public final class AssetDataExporter {

    private AssetDataExporter() {
    }

    public static void exportParts() {
        try (Connection connection = LocalManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("select * from part ORDER BY id ASC");
             ResultSet result = statement.executeQuery()) {
            List<Part> parts = new ArrayList<>();
            while (result.next()) {
                Part part = new Part();
                part.id = result.getShort("id");
                part.type = result.getByte("type");
                JSONArray data = (JSONArray) JSONValue.parse(result.getString("data").replaceAll("\\\"", ""));
                for (Object value : data) {
                    JSONArray detail = (JSONArray) JSONValue.parse(String.valueOf(value));
                    part.partDetails.add(new PartDetail(
                            Short.parseShort(String.valueOf(detail.get(0))),
                            Byte.parseByte(String.valueOf(detail.get(1))),
                            Byte.parseByte(String.valueOf(detail.get(2)))));
                }
                parts.add(part);
            }
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream("data/update_data/part"))) {
                output.writeShort(parts.size());
                for (Part part : parts) {
                    output.writeByte(part.type);
                    for (PartDetail detail : part.partDetails) {
                        output.writeShort(detail.iconId);
                        output.writeByte(detail.dx);
                        output.writeByte(detail.dy);
                    }
                }
            }
        } catch (Exception error) {
            Logger.logException(AssetDataExporter.class, error, "Cannot export part data");
        }
    }
}
