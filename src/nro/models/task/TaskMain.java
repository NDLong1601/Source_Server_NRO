package nro.models.task;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author By Mr Blue
 * 
 */

public class TaskMain {

    public int id;

    public int index;

    public String name;

    public String detail;

    public List<SubTaskMain> subTasks;

    public long lastTime;

    public TaskMain() {
        this.subTasks = new ArrayList<>();
    }

    public TaskMain(TaskMain taskMain) {
        this.id = taskMain.id;
        this.index = taskMain.index;
        this.name = taskMain.name;
        this.detail = taskMain.detail;
        this.subTasks = new ArrayList<>();
        for (SubTaskMain stm : taskMain.subTasks) {
            this.subTasks.add(new SubTaskMain(stm));
        }
    }

}
