using System;
using System.Collections.Generic;
using System.IO;
namespace Game2
{
    public class MyVector
    {
        public readonly List<object> data = new List<object>();
        public int size() { return data.Count; }
        public object elementAt(int i) { return data[i]; }
        public void addElement(object o) { data.Add(o); }
    }
    public class Effect2
    {
        public static MyVector vEffect2 = new MyVector(), vRemoveEffect2 = new MyVector();
        public virtual void update() { }
        public virtual void paint(mGraphics g) { }
    }
    public class SkillTemplate { public int id = 31; }
    public class Skill { public SkillTemplate template = new SkillTemplate(); public long lastTimeUseThisSkill; }
    public class Char
    {
        public int charID = 101, cx = 120, cy = 200, cdir = 1, cHP = 1000; public Skill myskill = new Skill();
        public static Char me = new Char(); public static Char myCharz() { return me; }
    }
    public class Mob { public int x = 360; }
    public static class GameScr
    {
        public static Char target = new Char { charID = 201, cx = 360 };
        public static Char observer = new Char { charID = 102 };
        public static Char findCharInMap(int id) { return id == 201 ? target : id == 102 ? observer : null; }
        public static Mob findMobInMap(int id) { return new Mob(); }
    }
    public static class mSystem { public static long now; public static long currentTimeMillis() { return now; } }
    public class Image { public int frame; }
    public static class GameCanvas
    {
        public static Image loadImage(string path) { return new Image { frame = int.Parse(path.Substring(path.LastIndexOf('_') + 1, 1)) }; }
    }
    public class mGraphics
    {
        public sealed class Draw { public int frame, x, y, w, h, flip; }
        public List<Draw> calls = new List<Draw>();
        public void drawImageScale(Image image, int x, int y, int w, int h, int transform)
        {
            calls.Add(new Draw { frame = image.frame, x = x, y = y, w = w, h = h, flip = transform });
        }
    }
    public class Message
    {
        public readonly MemoryStream stream;
        private readonly Reader input;
        public Message(byte[] payload) { stream = new MemoryStream(payload); input = new Reader(stream); }
        public Reader reader() { return input; }
    }
    public class Reader
    {
        private readonly Stream stream;
        public Reader(Stream s) { stream = s; }
        public sbyte readByte() { int b = stream.ReadByte(); if (b < 0) throw new EndOfStreamException(); return unchecked((sbyte)b); }
        public short readShort() { return unchecked((short)(((byte)readByte() << 8) | (byte)readByte())); }
        public int readInt() { return ((byte)readByte() << 24) | ((byte)readByte() << 16) | ((byte)readByte() << 8) | (byte)readByte(); }
    }
}
