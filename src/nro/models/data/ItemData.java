package nro.models.data;

import nro.models.player_system.Template.ArrHead2Frames;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.server.Manager;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player_system.Template;

public class ItemData {

    /*
     * Message -28 uses an unsigned-short payload length. Item template text
     * now exceeds two such packets, so the paired Unity client accepts any
     * number of subtype-2 append packets and one subtype-3 completion packet.
     */
    private static final int MAX_PACKET_PAYLOAD_BYTES = 65_535;
    private static final int RELOAD_PACKET_OVERHEAD_BYTES = 5;
    private static final int APPEND_PACKET_OVERHEAD_BYTES = 7;
    /*
     * Item text is the only variable-sized field in the legacy two-packet
     * protocol. Cap flavour descriptions sent to the client (not the database
     * value) so every template, including all custom skill books, is present
     * without exceeding the command's unsigned-short length limit.
     */
    private static final int MAX_WIRE_DESCRIPTION_BYTES = 100;
    private static final String WIRE_DESCRIPTION_SUFFIX = "...";

    public static void updateItem(MySession session) {
        updateItemOptionItemplate(session);
        updateItemArrHead2FTemplate(session);
        int total = getWireTemplateCount();
        int start = 0;
        int end = findChunkEnd(start, total, false);
        updateItemTemplate(session, end);
        start = end;
        while (start < total) {
            end = findChunkEnd(start, total, true);
            updateItemTemplate(session, start, end);
            start = end;
        }
        updateItemTemplateComplete(session, total);
    }

    private static int getWireTemplateCount() {
        int total = Manager.ITEM_TEMPLATES.size();
        for (int id = 0; id < total; id++) {
            if (Manager.ITEM_TEMPLATES.get(id).id != id) {
                throw new IllegalStateException("Item template không liên tục tại ID " + id);
            }
        }
        return total;
    }

    /** Finds the largest contiguous range that fits in one item-template packet. */
    private static int findChunkEnd(int start, int total, boolean append) {
        int payloadBytes = append ? APPEND_PACKET_OVERHEAD_BYTES : RELOAD_PACKET_OVERHEAD_BYTES;
        int end = start;
        while (end < total) {
            int itemBytes = templatePacketBytes(Manager.ITEM_TEMPLATES.get(end));
            if (payloadBytes + itemBytes > MAX_PACKET_PAYLOAD_BYTES) {
                break;
            }
            payloadBytes += itemBytes;
            end++;
        }
        if (end == start && start < total) {
            throw new IllegalStateException("Item template " + start
                    + " vượt giới hạn 65.535 byte của một gói dữ liệu.");
        }
        return end;
    }

    private static void assertTemplatePacketFits(int start, int end, boolean append) {
        int payloadBytes = append ? APPEND_PACKET_OVERHEAD_BYTES : RELOAD_PACKET_OVERHEAD_BYTES;
        for (int index = start; index < end; index++) {
            payloadBytes += templatePacketBytes(Manager.ITEM_TEMPLATES.get(index));
        }
        if (payloadBytes > MAX_PACKET_PAYLOAD_BYTES) {
            throw new IllegalStateException("Packet item template " + (append ? "append" : "reload")
                    + " vượt 65.535 byte: " + payloadBytes + " byte, range " + start + ".." + (end - 1));
        }
    }

    private static int templatePacketBytes(Template.ItemTemplate template) {
        // 12 fixed fields plus two unsigned-short UTF lengths.
        return 16 + modifiedUtfLength(template.name) + modifiedUtfLength(wireDescription(template));
    }

    private static String wireDescription(Template.ItemTemplate template) {
        String description = template.description == null ? "" : template.description;
        if (modifiedUtfLength(description) <= MAX_WIRE_DESCRIPTION_BYTES) {
            return description;
        }
        int maximumBodyBytes = MAX_WIRE_DESCRIPTION_BYTES - modifiedUtfLength(WIRE_DESCRIPTION_SUFFIX);
        int end = 0;
        int usedBytes = 0;
        while (end < description.length()) {
            char character = description.charAt(end);
            int characterBytes = modifiedUtfLength(String.valueOf(character));
            if (usedBytes + characterBytes > maximumBodyBytes) {
                break;
            }
            usedBytes += characterBytes;
            end++;
        }
        return description.substring(0, end) + WIRE_DESCRIPTION_SUFFIX;
    }

    private static int modifiedUtfLength(String value) {
        int length = 0;
        for (int index = 0; index < value.length(); index++) {
            int character = value.charAt(index);
            if (character >= 0x0001 && character <= 0x007F) {
                length++;
            } else if (character > 0x07FF) {
                length += 3;
            } else {
                length += 2;
            }
        }
        return length;
    }

    private static void updateItemOptionItemplate(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(0); //update option
            msg.writer().writeByte(Manager.ITEM_OPTION_TEMPLATES.size());
            for (ItemOptionTemplate io : Manager.ITEM_OPTION_TEMPLATES) {
                msg.writer().writeUTF(io.name);
                msg.writer().writeByte(io.type);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateItemTemplate(MySession session, int count) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);

            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(1); //reload itemtemplate
            msg.writer().writeShort(count);
            for (int i = 0; i < count; i++) {
                Template.ItemTemplate itemTemplate = Manager.ITEM_TEMPLATES.get(i);
                msg.writer().writeByte(itemTemplate.type);
                msg.writer().writeByte(itemTemplate.gender);
                msg.writer().writeUTF(itemTemplate.name);
                msg.writer().writeUTF(wireDescription(itemTemplate));
                msg.writer().writeByte(itemTemplate.level);
                msg.writer().writeInt(itemTemplate.strRequire);
                msg.writer().writeShort(itemTemplate.iconID);
                msg.writer().writeShort(itemTemplate.part);
                msg.writer().writeBoolean(itemTemplate.isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateItemTemplate(MySession session, int start, int end) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);

            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(2); //add itemtemplate
            msg.writer().writeShort(start);
            msg.writer().writeShort(end);
            for (int i = start; i < end; i++) {
//                System.out.println("start: " + start + " -> " + end + " id " + Manager.ITEM_TEMPLATES.get(i).id);
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).type);
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).gender);
                msg.writer().writeUTF(Manager.ITEM_TEMPLATES.get(i).name);
                msg.writer().writeUTF(wireDescription(Manager.ITEM_TEMPLATES.get(i)));
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).level);
                msg.writer().writeInt(Manager.ITEM_TEMPLATES.get(i).strRequire);
                msg.writer().writeShort(Manager.ITEM_TEMPLATES.get(i).iconID);
                msg.writer().writeShort(Manager.ITEM_TEMPLATES.get(i).part);
                msg.writer().writeBoolean(Manager.ITEM_TEMPLATES.get(i).isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Marks that every subtype-2 append range has been received by the client. */
    private static void updateItemTemplateComplete(MySession session, int total) {
        Message msg = null;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem);
            msg.writer().writeByte(3); // complete multi-packet itemtemplate refresh
            msg.writer().writeShort(total);
            session.doSendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    private static void updateItemArrHead2FTemplate(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(100);
            msg.writer().writeShort(Manager.ARR_HEAD_2_FRAMES.size());
            for (ArrHead2Frames io : Manager.ARR_HEAD_2_FRAMES) {
                msg.writer().writeByte(io.frames.size());
                for (int i : io.frames) {
                    msg.writer().writeShort(i);
                }
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
