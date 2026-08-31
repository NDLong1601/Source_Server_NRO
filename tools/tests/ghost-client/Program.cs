using System;
using System.IO;
using System.Collections.Generic;

static class Program
{
    static int assertions;
    static void Check(bool ok, string label) { assertions++; if (!ok) throw new Exception(label); }
    static int Int(Stream s) { return (s.ReadByte() << 24) | (s.ReadByte() << 16) | (s.ReadByte() << 8) | s.ReadByte(); }
    static long Long(Stream s) { return ((long)(uint)Int(s) << 32) | (uint)Int(s); }
    static void Clock(long now) { Game1.mSystem.now = Game2.mSystem.now = now; }
    static void Reset()
    {
        Game1.Effect2.vEffect2 = new Game1.MyVector(); Game1.Effect2.vRemoveEffect2 = new Game1.MyVector();
        Game2.Effect2.vEffect2 = new Game2.MyVector(); Game2.Effect2.vRemoveEffect2 = new Game2.MyVector();
    }
    static void Deliver(byte[] bytes)
    {
        var a = new Game1.Message(bytes); var b = new Game2.Message(bytes);
        a.reader().readByte(); b.reader().readByte();
        int ca = a.reader().readInt(), cb = b.reader().readInt(); a.reader().readShort(); b.reader().readShort();
        Game1.SuperGhostKamikazeEffect.receive(ca, a); Game2.SuperGhostKamikazeEffect.receive(cb, b);
        Check(a.stream.Position == a.stream.Length && b.stream.Position == b.stream.Length, "complete edge-case packet");
    }
    static void DrawCount(int expected, string label)
    {
        var a = new Game1.mGraphics(); var b = new Game2.mGraphics();
        foreach (Game1.Effect2 e in Game1.Effect2.vEffect2.data) e.paint(a);
        foreach (Game2.Effect2 e in Game2.Effect2.vEffect2.data) e.paint(b);
        Check(a.calls.Count == expected && b.calls.Count == expected, label);
    }
    static void Active(int owner, int id, string label)
    {
        Check(Game1.SuperGhostKamikazeEffect.activeCastId(owner) == id && Game2.SuperGhostKamikazeEffect.activeCastId(owner) == id, label);
    }
    static void EdgeCases(byte[] spawn, byte[] snapshot)
    {
        Reset(); Clock(1000); Deliver(spawn);
        for (int frame = 0; frame < 3; frame++)
        {
            Clock(1000 + 180 * frame); var a = new Game1.mGraphics(); var b = new Game2.mGraphics();
            foreach (Game1.Effect2 e in Game1.Effect2.vEffect2.data) e.paint(a);
            foreach (Game2.Effect2 e in Game2.Effect2.vEffect2.data) e.paint(b);
            Check(a.calls[0].frame == frame && b.calls[0].frame == frame, "ordered summon frame " + frame);
        }
        Clock(51_869); Active(101, 1, "50-second lifespan does not overflow signed short"); DrawCount(7, "ghosts visible until deadline");
        Clock(51_870); Active(101, 0, "expired formation cannot send orders"); DrawCount(0, "expired ghosts hidden without end packet");
        Clock(52_371);
        foreach (Game1.Effect2 e in Game1.Effect2.vEffect2.data) e.update();
        foreach (Game2.Effect2 e in Game2.Effect2.vEffect2.data) e.update();
        Check(Game1.Effect2.vRemoveEffect2.size() == 1 && Game2.Effect2.vRemoveEffect2.size() == 1, "expiry removes effect");

        Reset(); Clock(9000); Deliver(snapshot); DrawCount(5, "late observer sees five remaining, no resurrected spent ghosts");
        Clock(10000); Deliver(snapshot); Deliver(spawn); DrawCount(5, "duplicate and older packets do not reset formation");
        Clock(51_870); Active(101, 0, "duplicate snapshots never extend deadline");

        Reset(); Clock(1000); Deliver(spawn);
        byte[] other = (byte[])spawn.Clone(); other[4] = 102; other[11] = 2; Deliver(other);
        Active(101, 1, "first owner remains isolated"); Active(102, 2, "second owner has independent formation");
        Clock(2000); DrawCount(14, "two casters render separate formations");
    }
    static void Main(string[] args)
    {
        var variants = new HashSet<int>(); int packets = 0; bool retained = false, left = false, right = false;
        byte[] spawn = null, snapshot = null;
        using (var input = File.OpenRead(args[0]))
        {
            while (input.Position < input.Length)
            {
                long now = Long(input) + 1000; int length = Int(input); byte[] bytes = new byte[length]; input.ReadExactly(bytes);
                if (bytes[7] == 10) spawn = bytes; if (bytes[7] == 15) snapshot = bytes;
                Game1.mSystem.now = Game2.mSystem.now = now;
                // Target position changes for the second order at server t=8s.
                Game1.GameScr.target.cx = Game2.GameScr.target.cx = now > 9000 ? -100 : 360;
                var a = new Game1.Message(bytes); var b = new Game2.Message(bytes);
                Check(a.reader().readByte() == 25 && b.reader().readByte() == 25, "effect subtype");
                int ownerA = a.reader().readInt(), ownerB = b.reader().readInt();
                Check(a.reader().readShort() == 31 && b.reader().readShort() == 31, "skill template");
                Game1.SuperGhostKamikazeEffect.receive(ownerA, a); Game2.SuperGhostKamikazeEffect.receive(ownerB, b);
                Check(a.stream.Position == a.stream.Length && b.stream.Position == b.stream.Length, "both clients consume complete Java payload");
                var ga = new Game1.mGraphics(); var gb = new Game2.mGraphics();
                foreach (Game1.Effect2 e in Game1.Effect2.vEffect2.data) e.paint(ga);
                foreach (Game2.Effect2 e in Game2.Effect2.vEffect2.data) e.paint(gb);
                Check(ga.calls.Count == gb.calls.Count, "same draw count Game1/Game2");
                int idle = 0, flight = 0;
                for (int i = 0; i < ga.calls.Count; i++)
                {
                    var x = ga.calls[i]; var y = gb.calls[i];
                    Check(x.frame == y.frame && x.x == y.x && x.y == y.y && x.flip == y.flip, "same frame/position/direction in both clients");
                    if (x.frame == 3) idle++;
                    if (x.frame >= 4 && x.frame <= 6) { flight++; variants.Add(x.frame); left |= x.flip == 1; right |= x.flip == 0; }
                    Check(x.w == (x.frame == 7 ? 72 : 66), "logical size independent of zoom");
                }
                Check(flight <= 1, "at most one flying visual");
                if (now == 9000) { retained = idle == 5; Check(Game1.SuperGhostKamikazeEffect.activeCastId(101) == 1, "same cast usable after two kills"); }
                packets++;
            }
        }
        Check(retained, "five orbiting ghosts in snapshot after two hits");
        Check(variants.Count == 3 && left && right, "all three variants and both directions rendered");
        Check(Game1.SuperGhostKamikazeEffect.activeCastId(101) == 0, "exhausted cast cannot accept orders");
        Game1.mSystem.now += 1000; Game2.mSystem.now += 1000;
        foreach (Game1.Effect2 e in Game1.Effect2.vEffect2.data) e.update();
        foreach (Game2.Effect2 e in Game2.Effect2.vEffect2.data) e.update();
        Check(Game1.Effect2.vRemoveEffect2.size() == 1 && Game2.Effect2.vRemoveEffect2.size() == 1, "end cleans up after impact finishes");
        EdgeCases(spawn, snapshot);
        Console.WriteLine("PASS C# replay: " + packets + " Java packets, " + assertions + " assertions, both Game1/Game2");
    }
}
