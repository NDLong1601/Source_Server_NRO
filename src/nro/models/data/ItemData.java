package nro.models.data;

import nro.models.player_system.Template.ArrHead2Frames;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.server.Manager;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player_system.Template;

public class ItemData {

    /*
     * The legacy client finalizes its item cache after the first subtype-2
     * append packet. Keep exactly one reload packet and one append packet.
     * The template text is data-dependent, so calculate the split instead of
     * relying on a fixed item count that can overflow after a content update.
     */
    private static final int MAX_PACKET_PAYLOAD_BYTES = 65_535;
    private static final int RELOAD_PACKET_OVERHEAD_BYTES = 5;
    private static final int APPEND_PACKET_OVERHEAD_BYTES = 7;

    public static void updateItem(MySession session) {
        updateItemOptionItemplate(session);
        updateItemArrHead2FTemplate(session);
        int total = Manager.ITEM_TEMPLATES.size();
        int firstChunk = findReloadTemplateCount(total);
        assertTemplatePacketFits(0, firstChunk, false);
        updateItemTemplate(session, firstChunk);
        if (firstChunk < total) {
            assertTemplatePacketFits(firstChunk, total, true);
            updateItemTemplate(session, firstChunk, total);
        }
    }

    /**
     * Fits as many leading templates as possible in the reload packet.  This
     * minimizes the trailing append packet while preserving the old client's
     * required one-reload/one-append sequence.
     */
    private static int findReloadTemplateCount(int total) {
        int payloadBytes = RELOAD_PACKET_OVERHEAD_BYTES;
        int end = 0;
        while (end < total) {
            int templateBytes = templatePacketBytes(Manager.ITEM_TEMPLATES.get(end));
            if (payloadBytes + templateBytes > MAX_PACKET_PAYLOAD_BYTES) {
                break;
            }
            payloadBytes += templateBytes;
            end++;
        }
        if (end == 0 && total > 0) {
            throw new IllegalStateException("Item template 0 vượt giới hạn gói 65.535 byte.");
        }
        // The legacy client commits this data only after receiving subtype 2.
        // Reserve the final template for the append packet even when all
        // templates technically fit inside the reload packet.
        if (end == total && total > 1) {
            end--;
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
        return 16 + modifiedUtfLength(template.name) + modifiedUtfLength(template.description);
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
                msg.writer().writeUTF(itemTemplate.description);
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
                msg.writer().writeUTF(Manager.ITEM_TEMPLATES.get(i).description);
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
