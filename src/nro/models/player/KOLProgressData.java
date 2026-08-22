package nro.models.player;

public class KOLProgressData {

    public int kolQuestStage;
    public int kolVIPQuestStage;
    public int destronGas70CompletionCount;
    public int martialArtsTournamentWins;
    public int dailySuperHardQuestCompletionCount;
    public int bossBabyDefeatParticipationCount;
    public long monsterKillCountAutoTrain;
    public int kanaoQuestMapId;
    public int kanaoQuestMobId;
    public int kanaoQuestCount;
    public int kanaoQuestRequiredCount;

    public KOLProgressData() {
        this.kolQuestStage = 1;
        this.kolVIPQuestStage = 1;
        this.destronGas70CompletionCount = 0;
        this.martialArtsTournamentWins = 0;
        this.dailySuperHardQuestCompletionCount = 0;
        this.bossBabyDefeatParticipationCount = 0;
        this.monsterKillCountAutoTrain = 0;
        this.kanaoQuestMapId = -1;
        this.kanaoQuestMobId = -1;
        this.kanaoQuestCount = 0;
        this.kanaoQuestRequiredCount = 0;
    }
}
