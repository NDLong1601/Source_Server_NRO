package nro.models.task;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SideTaskTemplate {

    public int id;
    public String name;
    public int[][] count;

    public SideTaskTemplate() {
        this.count = new int[5][2];
    }

}
