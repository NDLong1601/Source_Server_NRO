package nro.models.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import nro.models.map.phoban.BlackBallWar;
import nro.models.services_dungeon.MajinBuuService;

/**
 *
 * @author By Mr Blue
 *
 */
public class TimeUtil {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final byte SECOND = 1;
    public static final byte MINUTE = 2;
    public static final byte HOUR = 3;
    public static final byte DAY = 4;
    public static final byte WEEK = 5;
    public static final byte MONTH = 6;
    public static final byte YEAR = 7;

    public static String convertMillisecondToMinute(long time) {
        return String.format("%02d phút", TimeUnit.MILLISECONDS.toMinutes(time));
    }

    public static String convertMillisecondToHour(long time) {
        return String.format("%02d giờ", TimeUnit.MILLISECONDS.toHours(time));
    }

    public static String convertMillisecondToDay(long time) {
        return String.format("%02d ngày", TimeUnit.MILLISECONDS.toDays(time));
    }

    public static long diffDate(Date d1, Date d2, byte type) {
        long diffMillis = Math.abs(d1.getTime() - d2.getTime());
        return switch (type) {
            case SECOND ->
                TimeUnit.MILLISECONDS.toSeconds(diffMillis);
            case MINUTE ->
                TimeUnit.MILLISECONDS.toMinutes(diffMillis);
            case HOUR ->
                TimeUnit.MILLISECONDS.toHours(diffMillis);
            case DAY ->
                TimeUnit.MILLISECONDS.toDays(diffMillis);
            case WEEK ->
                diffMillis / (7L * 24 * 60 * 60 * 1000);
            case MONTH ->
                diffMillis / (30L * 24 * 60 * 60 * 1000);
            case YEAR ->
                diffMillis / (365L * 24 * 60 * 60 * 1000);
            default ->
                0;
        };
    }

    public static boolean isTimeNowInRange(String fromTime, String toTime, String format) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            LocalTime start = LocalTime.parse(fromTime, formatter);
            LocalTime end = LocalTime.parse(toTime, formatter);
            LocalTime now = LocalTime.now(VIETNAM_ZONE);
            return now.isAfter(start) && now.isBefore(end);
        } catch (Exception e) {
            throw new Exception("Thời gian không hợp lệ");
        }
    }

    public static int getCurrDay() {
        return LocalDate.now(VIETNAM_ZONE).getDayOfWeek().getValue();
    }

    public static int getCurrHour() {
        return LocalTime.now(VIETNAM_ZONE).getHour();
    }

    public static int getCurrMin() {
        return LocalTime.now(VIETNAM_ZONE).getMinute();
    }

    public static String convertTime(int totalSeconds) {
        Duration duration = Duration.ofSeconds(totalSeconds);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" ngày ");
        }
        if (hours > 0) {
            sb.append(hours).append(" giờ ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" phút ");
        }
        if (seconds > 0) {
            sb.append(seconds).append(" giây");
        }

        return sb.toString().trim();
    }

    public static String getTimeLeft(long startTime, int targetSeconds) {
        int elapsed = (int) ((System.currentTimeMillis() - startTime) / 1000);
        int left = Math.max(0, targetSeconds - elapsed);
        return left > 60 ? (left / 60) + " phút" : left + " giây";
    }

    public static String getTimeLeft(long lastTime) {
        int elapsed = (int) ((System.currentTimeMillis() - lastTime) / 1000);
        return elapsed >= 86400 ? (elapsed / 86400) + "n trước"
                : elapsed >= 3600 ? (elapsed / 3600) + "g trước"
                        : elapsed >= 60 ? (elapsed / 60) + "p trước"
                                : elapsed + "gi trước";
    }

    public static int getMinLeft(long lastTime, int targetSeconds) {
        int elapsed = (int) ((System.currentTimeMillis() - lastTime) / 1000);
        int left = Math.max(0, targetSeconds - elapsed);
        return left <= 60 ? 1 : (left / 60);
    }

    public static int getSecondLeft(long lastTime, int targetSeconds) {
        int elapsed = (int) ((System.currentTimeMillis() - lastTime) / 1000);
        return Math.max(0, targetSeconds - elapsed);
    }

    public static String getDateLeft(long lastTime, int targetSeconds) {
        int left = Math.max(0, targetSeconds - (int) ((System.currentTimeMillis() - lastTime) / 1000));
        return convertTime(left);
    }

    public static String convertTimeNow(long lastTime) {
        int elapsed = (int) ((System.currentTimeMillis() - lastTime) / 1000);
        return convertTime(Math.max(0, elapsed));
    }

    public static long getTime(String timeStr, String format) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(VIETNAM_ZONE);
            return LocalDateTime.parse(timeStr, formatter).atZone(VIETNAM_ZONE).toInstant().toEpochMilli();
        } catch (Exception ex) {
            throw new Exception("Thời gian không hợp lệ");
        }
    }

    public static String getTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds <= 0) {
            return "0 giây";
        }

        return (hours <= 0) ? String.format("%d phút %d giây", minutes % 60, seconds % 60)
                : (days <= 0) ? String.format("%d giờ %d phút", hours % 24, minutes % 60)
                        : String.format("%d ngày %d giờ", days, hours % 24);
    }

    public static String getTimeNow(String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(VIETNAM_ZONE);
        return formatter.format(Instant.now());
    }

    public static String getTimeBeforeCurrent(int subtractMillis, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(VIETNAM_ZONE);
        return formatter.format(Instant.ofEpochMilli(System.currentTimeMillis() - subtractMillis));
    }

    public static String formatTime(Date time, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(VIETNAM_ZONE);
        return formatter.format(time.toInstant());
    }

    public static String formatTime(long millis, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(VIETNAM_ZONE);
        return formatter.format(Instant.ofEpochMilli(millis));
    }

    public static boolean isMabuOpen() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return (hour >= 12 && hour < 13);
    }

    public static boolean isMabu14HOpen() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return (hour >= 14 && hour < 15);
    }

    public static boolean is21H() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return (hour >= 21 && hour < 22);
    }

    public static long getStartTimeBlackBallWar() {
        LocalTime startTime = LocalTime.of(BlackBallWar.HOUR_OPEN, BlackBallWar.MIN_OPEN, BlackBallWar.SECOND_OPEN);
        LocalDateTime startDateTime = LocalDateTime.of(LocalDate.now(VIETNAM_ZONE), startTime);
        ZonedDateTime zonedStartTime = startDateTime.atZone(VIETNAM_ZONE);
        return zonedStartTime.toInstant().toEpochMilli();
    }

    public static boolean isBlackBallWarOpen() {
        LocalTime currentTime = LocalTime.now(VIETNAM_ZONE);
        LocalTime startTime = LocalTime.of(BlackBallWar.HOUR_OPEN, BlackBallWar.MIN_OPEN, BlackBallWar.SECOND_OPEN);
        LocalTime endTime = LocalTime.of(BlackBallWar.HOUR_CLOSE, BlackBallWar.MIN_CLOSE, BlackBallWar.SECOND_CLOSE);

        return currentTime.isAfter(startTime) && currentTime.isBefore(endTime);
    }

    public static boolean isBlackBallWarCanPick() {
        LocalTime currentTime = LocalTime.now(VIETNAM_ZONE);
        LocalTime startTime = LocalTime.of(BlackBallWar.HOUR_CAN_PICK_DB, BlackBallWar.MIN_CAN_PICK_DB, BlackBallWar.SECOND_CAN_PICK_DB);

        return currentTime.isAfter(startTime) && isBlackBallWarOpen();
    }

    public static long getSecondsUntilCanPick() {
        LocalTime currentTime = LocalTime.now(VIETNAM_ZONE);
        LocalTime startTime = LocalTime.of(BlackBallWar.HOUR_CAN_PICK_DB, BlackBallWar.MIN_CAN_PICK_DB, BlackBallWar.SECOND_CAN_PICK_DB);

        if (currentTime.isBefore(startTime)) {
            Duration duration = Duration.between(currentTime, startTime);
            return duration.getSeconds();
        } else {
            return 0;
        }
    }

    public static boolean checkTime(long time) {
        return (time - System.currentTimeMillis()) / 1000 > 0;
    }

    public static String convertTimeMS(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + " m " + seconds + " s";
    }

    public static int getCurrMinute() {
        return LocalTime.now().getMinute();
    }

    public static int getCurrSecond() {
        return LocalTime.now().getSecond();
    }

}
