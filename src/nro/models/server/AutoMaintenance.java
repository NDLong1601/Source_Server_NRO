package nro.models.server;

import java.time.LocalTime;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 *
 * @author By Mr Blue
 *
 */

public class AutoMaintenance extends Thread {

    public static boolean AutoMaintenance = false;
    public static final int hours = 4;
    public static final int mins = 30;
    private static AutoMaintenance instance;
    public static boolean isRunning;
    private long lastAnnouncementTime = 0;
    private boolean hasResetAfterMaintenance = false;

    public static AutoMaintenance gI() {
        if (instance == null) {
            instance = new AutoMaintenance();
        }
        return instance;
    }

    @Override
    public void run() {
        boolean hasAnnounced = false;
        long announceStartTime = 0L;

        while (true) {
            try {
                if (AutoMaintenance) {
                    LocalTime currentTime = LocalTime.now();
                    LocalTime maintenanceTime = LocalTime.of(hours, mins);
                    LocalTime maintenanceEndTime = maintenanceTime.plusMinutes(5);

                    // Nếu thời gian nằm trong khoảng bảo trì
                    if (!hasAnnounced && currentTime.isAfter(maintenanceTime.minusSeconds(1)) && currentTime.isBefore(maintenanceEndTime)) {
                        announceStartTime = System.currentTimeMillis();
                        hasAnnounced = true;
                        hasResetAfterMaintenance = false; // Reset lại flag reset khi bắt đầu lại
                        Logger.log(Logger.PURPLE, "Bắt đầu đếm ngược bảo trì (5 phút)");
                    }

                    if (hasAnnounced) {
                        long elapsed = System.currentTimeMillis() - announceStartTime;
                        long totalWait = 5 * 60 * 1000;
                        long remainingMs = totalWait - elapsed;
                        if (remainingMs < 0) remainingMs = 0;

                        long remainingSeconds = remainingMs / 1000;
                        long minutesLeft = remainingSeconds / 60;
                        long secondsLeft = remainingSeconds % 60;

                        if (lastAnnouncementTime == 0 || System.currentTimeMillis() - lastAnnouncementTime >= 15_000) {
                            Service.gI().sendThongBaoAllPlayer(
                                    String.format("Máy chủ sẽ bảo trì trong %d phút %d giây nữa. Vui lòng thoát game để tránh mất dữ liệu.",
                                            minutesLeft, secondsLeft));
                            Logger.log(Logger.PURPLE, "Thông báo bảo trì tự động đã gửi.");
                            lastAnnouncementTime = System.currentTimeMillis();
                        }

                        if (elapsed >= totalWait) {
                            if (!Maintenance.isRunning && !isRunning) {
                                Logger.log(Logger.PURPLE, "Đang tiến hành quá trình bảo trì tự động...");
                                Maintenance.gI().startCountdown();
                                isRunning = true;
                                AutoMaintenance = false;
                            }
                        }
                    }

                    // Reset sau bảo trì - CHẶN LOG SPAM
                    if (currentTime.isAfter(maintenanceEndTime) && !hasResetAfterMaintenance) {
                        hasAnnounced = false;
                        lastAnnouncementTime = 0;
                        isRunning = false;
                        hasResetAfterMaintenance = true; // Đánh dấu đã reset
                        Logger.log(Logger.PURPLE, "Reset AutoMaintenance sau thời gian bảo trì.\n");
                    }
                }

                Thread.sleep(1000); // Check mỗi giây
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
