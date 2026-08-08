package nro.models.utils;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 *
 * @author By Mr Blue
 * 
 */

public class SystemMetrics {

    private static String formatMemory(double used, double total) {
        return String.format("(%.1f/%.1f) GB", used, total);
    }

    private static String formatPercentage(double percentage) {
        return String.format("%.0f%%", percentage);
    }

    private static double getUsedMemoryGB() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = osBean.getFreeMemorySize();
        long usedMemory = totalMemory - freeMemory;
        return (double) usedMemory / (1024 * 1024 * 1024);
    }

    private static double getTotalMemoryGB() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        return (double) totalMemory / (1024 * 1024 * 1024);
    }

    private static double getRAMUsagePercentage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = osBean.getFreeMemorySize();
        long usedMemory = totalMemory - freeMemory;
        return ((double) usedMemory / totalMemory) * 100;
    }

    private static double getCPUUsagePercentage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = osBean.getCpuLoad() * 100;
        return cpuUsage;
    }

    private static double getHeapUsedMemoryGB() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        return (double) usedMemory / (1024 * 1024 * 1024);
    }

    private static double getHeapMaxMemoryGB() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax();
        return (double) maxMemory / (1024 * 1024 * 1024);
    }

    private static double getHeapUsagePercentage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        return ((double) usedMemory / maxMemory) * 100;
    }

    public static String ToString() {
        double usedMemory = getUsedMemoryGB();
        double totalMemory = getTotalMemoryGB();
        double ramUsagePercentage = getRAMUsagePercentage();
        double cpuUsagePercentage = getCPUUsagePercentage();
        double heapUsedMemory = getHeapUsedMemoryGB();
        double heapMaxMemory = getHeapMaxMemoryGB();
        double heapUsagePercentage = getHeapUsagePercentage();

        String string = ("Memory: " + formatMemory(usedMemory, totalMemory) + "\n"
                + "RAM: " + formatPercentage(ramUsagePercentage) + "\n"
                + "CPU: " + formatPercentage(cpuUsagePercentage) + "\n");
        return string;
    }
}
