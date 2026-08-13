package nro.models.player;

public class LearnSkill {
    public long Time;
    public short ItemTemplateSkillId;
    public int Potential;

    public LearnSkill() {
        Time = -1;
        ItemTemplateSkillId = -1;
        Potential = 0;
    }
}
