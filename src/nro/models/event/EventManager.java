package nro.models.event;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
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
import nro.models.admin.AdminEventConfigService;

public class EventManager {

    private static EventManager instance;

    private volatile Set<String> activeEvents = Collections.singleton("none");

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
        activeEvents = Collections.unmodifiableSet(loadActiveEvents());
        new Default().init("default");
        if (LUNNAR_NEW_YEAR && isEnabled(activeEvents, "lunar_new_year", "tet")) {
            new LunarNewYear().init("lunar_new_year");
        }
        if (INTERNATIONAL_WOMANS_DAY && isEnabled(activeEvents, "womens_day", "international_womens_day", "8_3")) {
            new InternationalWomensDay().init("womens_day");
        }
        if (HALLOWEEN && isEnabled(activeEvents, "halloween")) {
            new Halloween().init("halloween");
        }
        if (CHRISTMAS && isEnabled(activeEvents, "christmas", "noel")) {
            new Christmas().init("christmas");
        }
        if (HUNG_VUONG && isEnabled(activeEvents, "hungvuong", "hung_vuong")) {
            new HungVuong().init("hungvuong");
        }
        if (TRUNG_THU && isEnabled(activeEvents, "trungthu", "trung_thu")) {
            new TrungThu().init("trungthu");
        }
        if (TOP_UP && isEnabled(activeEvents, "topup", "top_up")) {
            new TopUp().init("topup");
        }
        if (isEnabled(activeEvents, "summer", "he", "mua_he")) {
            AdminEventConfigService.gI().spawnAdditionalBosses("summer", Collections.emptySet());
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
                .map(event -> canonicalName(event.trim().toLowerCase(Locale.ROOT)))
                .filter(event -> !event.isEmpty())
                .forEach(events::add);
        if (events.isEmpty()) {
            events.add("none");
        }
        return events;
    }

    private String canonicalName(String name) {
        return switch (name) {
            case "tet" -> "lunar_new_year";
            case "international_womens_day", "8_3" -> "womens_day";
            case "noel" -> "christmas";
            case "hung_vuong" -> "hungvuong";
            case "trung_thu" -> "trungthu";
            case "top_up" -> "topup";
            case "he", "mua_he" -> "summer";
            default -> name;
        };
    }

    public boolean isActive(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return false;
        }
        String canonical = canonicalName(eventName.trim().toLowerCase(Locale.ROOT));
        return activeEvents.contains("all") || activeEvents.contains(canonical);
    }

    public Set<String> getActiveEvents() {
        return new HashSet<>(activeEvents);
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
