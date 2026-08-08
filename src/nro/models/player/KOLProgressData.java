package nro.models.player;

/**
 *
 * @author By Mr Blue
 */
public class KOLProgressData {

    public int kolQuestStage;
    public int kolVIPQuestStage;
    public int destronGas70CompletionCount;
    public int martialArtsTournamentWins;
    public int dailySuperHardQuestCompletionCount;
    public int bossBabyDefeatParticipationCount;
    public long monsterKillCountAutoTrain;

    public KOLProgressData() {
        this.kolQuestStage = 1;
        this.kolVIPQuestStage = 1;
        this.destronGas70CompletionCount = 0;
        this.martialArtsTournamentWins = 0;
        this.dailySuperHardQuestCompletionCount = 0;
        this.bossBabyDefeatParticipationCount = 0;
        this.monsterKillCountAutoTrain = 0;
    }
}
