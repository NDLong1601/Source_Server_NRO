package nro.models.skill;

import java.io.DataOutputStream;
import java.io.IOException;

/** V2 wire contract, shared by live traffic and the cross-language regression fixture. */
public final class SuperGhostPacket {
    private SuperGhostPacket() { }

    public static void write(DataOutputStream out, int casterId, int castId, int sequence,
            SuperGhostFormation formation, int event, int changedIndex, long now,
            int ownerX, int ownerY, int targetType, int targetId) throws IOException {
        out.writeByte(25);
        out.writeInt(casterId);
        out.writeShort(31);
        out.writeByte(event);
        out.writeInt(castId);
        out.writeInt(sequence);
        out.writeByte(formation.ghosts.length);
        out.writeShort(formation.summonMs);
        out.writeInt(formation.lifetimeMs);
        out.writeInt((int) Math.max(0, now - formation.startedAt));
        out.writeInt((int) Math.max(0, formation.expiresAt - now));
        out.writeShort(ownerX);
        out.writeShort(ownerY);
        out.writeByte(targetType);
        out.writeInt(targetId);
        out.writeShort(formation.target == null ? 0 : formation.target.x());
        out.writeShort(formation.target == null ? 0 : formation.target.y());
        out.writeByte(changedIndex);
        for (SuperGhostFormation.Ghost ghost : formation.ghosts) {
            out.writeByte(ghost.state);
            out.writeByte(ghost.variant);
            out.writeShort((int) Math.round(ghost.x));
            out.writeShort((int) Math.round(ghost.y));
            out.writeInt((int) Math.max(0, now - ghost.stateAt));
        }
    }
}
