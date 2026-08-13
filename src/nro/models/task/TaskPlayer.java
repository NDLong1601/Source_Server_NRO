package nro.models.task;

public class TaskPlayer {

    public TaskMain taskMain;

    public SideTask sideTask;

    public ClanTask clanTask;

    public TaskPlayer() {
        this.sideTask = new SideTask();
        this.clanTask = new ClanTask();
    }

    public void dispose() {
        this.taskMain = null;
        this.sideTask = null;
        this.clanTask = null;
    }

}
