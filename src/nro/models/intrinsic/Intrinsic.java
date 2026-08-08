package nro.models.intrinsic;
public class Intrinsic {

    public int id;
    public String name;
    public short paramFrom1;
    public short paramTo1;
    public short paramFrom2;
    public short paramTo2;
    public short icon;
    public byte gender;

    public short param1;
    public short param2;

    public Intrinsic() {

    }

    public Intrinsic(Intrinsic intrinsic) {
        this.id = intrinsic.id;
        this.name = intrinsic.name;
        this.paramFrom1 = intrinsic.paramFrom1;
        this.paramTo1 = intrinsic.paramTo1;
        this.paramFrom2 = intrinsic.paramFrom2;
        this.paramTo2 = intrinsic.paramTo2;
        this.icon = intrinsic.icon;
        this.gender = intrinsic.gender;
    }

    public String getDescription() {
        return this.name.replaceAll("p0", String.valueOf(paramFrom1))
                .replaceAll("p1", String.valueOf(paramTo1))
                .replaceAll("p2", String.valueOf(paramFrom2))
                .replaceAll("p3", String.valueOf(paramTo2));
    }

    public String getName() {
        return this.name.replaceAll("p0% đến p1", "p0").replaceAll("p2% đến p3", "p1")
                .replaceAll("p0", String.valueOf(this.param1))
                .replaceAll("p1", String.valueOf(this.param2)) + (this.id != 0 ? " [" + this.paramFrom1 + " đến " + this.paramTo1 + "]" : "");
    }

}
