package nro.models.shop_ky_gui;

import java.util.ArrayList;
import java.util.List;
import nro.models.item.Item;
import nro.models.player_system.Template.ItemOptionTemplate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** Canonical, strict codec for consignment option data stored in SQL. */
public final class ConsignItemOptionsCodec {

    private static final int MAX_OPTIONS = Byte.MAX_VALUE;

    private ConsignItemOptionsCodec() {
    }

    @SuppressWarnings("unchecked")
    public static String encode(List<Item.ItemOption> options) {
        JSONArray encoded = new JSONArray();
        if (options == null) {
            return encoded.toJSONString();
        }
        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Too many consignment item options: " + options.size());
        }
        for (Item.ItemOption option : options) {
            if (option == null || option.optionTemplate == null) {
                throw new IllegalArgumentException("Consignment item contains an invalid option");
            }
            JSONObject row = new JSONObject();
            row.put("id", String.valueOf(option.optionTemplate.id));
            row.put("param", String.valueOf(option.param));
            encoded.add(row);
        }
        return encoded.toJSONString();
    }

    public static List<Item.ItemOption> decode(String json) {
        return decode(json, null);
    }

    public static List<Item.ItemOption> decode(String json, List<ItemOptionTemplate> templates) {
        List<Item.ItemOption> decoded = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "null".equalsIgnoreCase(json.trim())) {
            return decoded;
        }
        Object parsed = JSONValue.parse(json);
        if (!(parsed instanceof JSONArray)) {
            throw new IllegalArgumentException("Consignment itemOption is not a JSON array");
        }
        JSONArray values = (JSONArray) parsed;
        if (values.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Consignment itemOption exceeds 127 entries");
        }
        for (Object value : values) {
            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException("Consignment itemOption contains a non-object entry");
            }
            JSONObject row = (JSONObject) value;
            if (row.get("id") == null || row.get("param") == null) {
                throw new IllegalArgumentException("Consignment itemOption entry is incomplete");
            }
            int id = Integer.parseInt(String.valueOf(row.get("id")));
            int param = Integer.parseInt(String.valueOf(row.get("param")));
            if (templates == null) {
                decoded.add(new Item.ItemOption(id, param));
            } else {
                if (id < 0 || id >= templates.size() || templates.get(id).id != id) {
                    throw new IllegalArgumentException("Unknown consignment option template id " + id);
                }
                decoded.add(new Item.ItemOption(templates.get(id), param));
            }
        }
        return decoded;
    }
}
