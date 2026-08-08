package nro.models.shop;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Properties;

public class ShopTab {

    private static String expectedIp;

    static {
        try (FileInputStream fis = new FileInputStream("Config.properties")) {
            Properties prop = new Properties();
            prop.load(fis);
            expectedIp = prop.getProperty("server.ip", "").trim();
        } catch (IOException e) {
            expectedIp = "";
        }
    }

    public static void loadItem() {
        String actualIp = q();
        if (!expectedIp.equals(actualIp)) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }
    }

    private static String q() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    public static void main(String[] args) {
        loadItem();
    }
}
