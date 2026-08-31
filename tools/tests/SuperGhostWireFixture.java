import java.io.*;
import nro.models.skill.SuperGhostFormation;
import nro.models.skill.SuperGhostPacket;

/** Writes real Java packets for replay through the unchanged Game1/Game2 C# effect. */
public final class SuperGhostWireFixture {
    static final class Victim implements SuperGhostFormation.Target {
        int x = 360, hits, limit = 2;
        public boolean valid() { return hits < limit; }
        public int x() { return x; }
        public int y() { return 182; }
    }
    public static void main(String[] args) throws Exception {
        try (DataOutputStream fixture = new DataOutputStream(new FileOutputStream(args[0]))) {
            SuperGhostFormation[] ref = new SuperGhostFormation[1];
            int[] seq={0}, variants={0};
            SuperGhostFormation.Listener listener = new SuperGhostFormation.Listener() {
                public void hit(SuperGhostFormation.Ghost ghost, SuperGhostFormation.Target victim) {
                    ((Victim)victim).hits++;
                }
                public void event(int event,int index,long now) {
                    try {
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                        SuperGhostPacket.write(new DataOutputStream(bytes),101,1,++seq[0],ref[0],event,index,
                                now,120,200,ref[0].target==null?0:2,ref[0].target==null?-1:201);
                        byte[] payload=bytes.toByteArray();
                        if(payload.length!=45+ref[0].ghosts.length*10) throw new AssertionError("wire size");
                        fixture.writeLong(now); fixture.writeInt(payload.length); fixture.write(payload);
                    } catch(IOException ex){throw new RuntimeException(ex);}
                }
            };
            ref[0]=new SuperGhostFormation(7,7,0,120,200,()->variants[0]++%3,listener);
            listener.event(SuperGhostFormation.SPAWN,-1,0);
            ref[0].command(new Victim(),0);
            for(long now=50;now<=8_000;now+=50) ref[0].tick(now,120,200);
            if(ref[0].remaining()!=5)throw new AssertionError("expected five remaining");
            listener.event(SuperGhostFormation.SNAPSHOT,-1,8_000);
            Victim left=new Victim();left.x=-100;left.limit=99;
            ref[0].command(left,8_000);
            for(long now=8_050;now<=20_000;now+=50) ref[0].tick(now,120,200);
            if(!ref[0].ended)throw new AssertionError("expected end");
            System.out.println("PASS wire fixture: "+seq[0]+" full-state packets, 50s lifetime");
        }
    }
}
