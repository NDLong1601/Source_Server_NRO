package nro.models.event;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import nro.models.event_list.TopUp;
import nro.models.event_list.TrungThu;
import nro.models.event_list.HungVuong;
import nro.models.event_list.Christmas;
import nro.models.event_list.Halloween;
import nro.models.event_list.LunarNewYear;
import nro.models.event_list.Default;
import nro.models.event_list.InternationalWomensDay;

public class EventManager {

    private static EventManager instance;

    public static boolean LUNNAR_NEW_YEAR = true;

    public static boolean INTERNATIONAL_WOMANS_DAY = true;

    public static boolean CHRISTMAS = true;

    public static boolean HALLOWEEN = true;

    public static boolean HUNG_VUONG = true;

    public static boolean TRUNG_THU = true;

    public static boolean TOP_UP = true;

    public static EventManager gI() {
        if (instance == null) {
            instance = new EventManager();
        }
        return instance;
    }

    public void init() {
        Set<String> activeEvents = loadActiveEvents();
        new Default().init();
        if (LUNNAR_NEW_YEAR && isEnabled(activeEvents, "lunar_new_year", "tet")) {
            new LunarNewYear().init();
        }
        if (INTERNATIONAL_WOMANS_DAY && isEnabled(activeEvents, "womens_day", "international_womens_day", "8_3")) {
            new InternationalWomensDay().init();
        }
        if (HALLOWEEN && isEnabled(activeEvents, "halloween")) {
            new Halloween().init();
        }
        if (CHRISTMAS && isEnabled(activeEvents, "christmas", "noel")) {
            new Christmas().init();
        }
        if (HUNG_VUONG && isEnabled(activeEvents, "hungvuong", "hung_vuong")) {
            new HungVuong().init();
        }
        if (TRUNG_THU && isEnabled(activeEvents, "trungthu", "trung_thu")) {
            new TrungThu().init();
        }
        if (TOP_UP && isEnabled(activeEvents, "topup", "top_up")) {
            new TopUp().init();
        }
    }

    private Set<String> loadActiveEvents() {
        Properties properties = new Properties();
        String configuredEvents = "none";
        try (FileInputStream input = new FileInputStream("Config.properties")) {
            properties.load(input);
            configuredEvents = properties.getProperty("server.event", configuredEvents);
        } catch (IOException e) {
            System.out.println("Khong the doc Config.properties, dung su kien mac dinh: " + configuredEvents);
        }

        Set<String> events = new HashSet<>();
        Arrays.stream(configuredEvents.split(","))
                .map(event -> event.trim().toLowerCase(Locale.ROOT))
                .filter(event -> !event.isEmpty())
                .forEach(events::add);
        return events;
    }

    private boolean isEnabled(Set<String> activeEvents, String... names) {
        if (activeEvents.contains("all")) {
            return true;
        }
        if (activeEvents.contains("none")) {
            return false;
        }
        for (String name : names) {
            if (activeEvents.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
