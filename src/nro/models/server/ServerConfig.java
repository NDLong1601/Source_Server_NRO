package nro.models.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import nro.models.network.Sender;
import nro.models.player.PlayerAutosavePolicy;

/** Immutable, validated server configuration snapshot. */
public final class ServerConfig {

    private final byte serverId;
    private final String name;
    private final String ip;
    private final int port;
    private final byte loginWaitSeconds;
    private final int maxConnectionsPerIp;
    private final int maxPlayers;
    private final byte experienceRate;
    private final boolean local;
    private final boolean test;
    private final boolean daoAutoUpdater;
    private final int senderMaxMessages;
    private final long senderMaxBytes;
    private final String serverLinks;
    private final PlayerAutosavePolicy playerAutosavePolicy;

    private ServerConfig(byte serverId, String name, String ip, int port,
            byte loginWaitSeconds, int maxConnectionsPerIp, int maxPlayers,
            byte experienceRate, boolean local, boolean test, boolean daoAutoUpdater,
            int senderMaxMessages, long senderMaxBytes, String serverLinks,
            PlayerAutosavePolicy playerAutosavePolicy) {
        this.serverId = serverId;
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.loginWaitSeconds = loginWaitSeconds;
        this.maxConnectionsPerIp = maxConnectionsPerIp;
        this.maxPlayers = maxPlayers;
        this.experienceRate = experienceRate;
        this.local = local;
        this.test = test;
        this.daoAutoUpdater = daoAutoUpdater;
        this.senderMaxMessages = senderMaxMessages;
        this.senderMaxBytes = senderMaxBytes;
        this.serverLinks = serverLinks;
        this.playerAutosavePolicy = playerAutosavePolicy;
    }

    public static ServerConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return from(properties);
    }

    public static ServerConfig from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        byte serverId = byteValue(properties, "server.sv", (byte) 1, 0, Byte.MAX_VALUE);
        String name = text(properties, "server.name", "Ngọc Rồng Online");
        if ("Local".equals(name)) {
            name = " Local";
        }
        String ip = text(properties, "server.ip", "127.0.0.1");
        int port = intValue(properties, "server.port", 14445, 1, 65_535);
        byte wait = byteValue(properties, "server.waitlogin", (byte) 5, 0, Byte.MAX_VALUE);
        int maxPerIp = intValue(properties, "server.maxperip", 1_000, 1, 1_000_000);
        int maxPlayers = intValue(properties, "server.maxplayer", 2_000, 1, 1_000_000);
        byte rate = byteValue(properties, "server.expserver", (byte) 1, 1, Byte.MAX_VALUE);
        int senderMessages = intValue(properties, "server.sender.max_messages",
                Sender.getDefaultMaxQueueMessages(), 1, 1_000_000);
        long senderBytes = longValue(properties, "server.sender.max_bytes",
                Sender.getDefaultMaxQueueBytes(), 1, Long.MAX_VALUE);
        long autosaveRpo = longValue(properties, "player.autosave.rpo_ms", 60_000L,
                10_000L, 900_000L);
        long autosaveRetry = longValue(properties, "player.autosave.retry_ms", 5_000L,
                100L, autosaveRpo);
        int autosaveAttempts = intValue(properties, "player.autosave.max_attempts", 5, 1, 20);

        List<String> links = new ArrayList<>();
        links.add(name + ":" + ip + ":" + port + ":0");
        for (int i = 1; i <= 10; i++) {
            String value = properties.getProperty("server.sv" + i);
            if (value != null && !value.isBlank()) {
                links.add(value.trim() + ":0");
            }
        }
        return new ServerConfig(serverId, name, ip, port, wait, maxPerIp, maxPlayers, rate,
                booleanValue(properties, "server.local", false),
                booleanValue(properties, "server.test", false),
                booleanValue(properties, "server.daoautoupdater", false),
                senderMessages, senderBytes, String.join(",", links),
                new PlayerAutosavePolicy(autosaveRpo, autosaveRetry, autosaveAttempts));
    }

    private static String text(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Configuration " + key + " must not be blank");
        }
        return normalized;
    }

    private static boolean booleanValue(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private static byte byteValue(Properties properties, String key, byte defaultValue, int min, int max) {
        return (byte) intValue(properties, key, defaultValue, min, max);
    }

    private static int intValue(Properties properties, String key, int defaultValue, int min, int max) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("Configuration " + key + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Configuration " + key + " must be an integer", error);
        }
    }

    private static long longValue(Properties properties, String key, long defaultValue, long min, long max) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("Configuration " + key + " is outside the supported range");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Configuration " + key + " must be an integer", error);
        }
    }

    public byte serverId() { return serverId; }
    public String name() { return name; }
    public String ip() { return ip; }
    public int port() { return port; }
    public byte loginWaitSeconds() { return loginWaitSeconds; }
    public int maxConnectionsPerIp() { return maxConnectionsPerIp; }
    public int maxPlayers() { return maxPlayers; }
    public byte experienceRate() { return experienceRate; }
    public boolean local() { return local; }
    public boolean test() { return test; }
    public boolean daoAutoUpdater() { return daoAutoUpdater; }
    public int senderMaxMessages() { return senderMaxMessages; }
    public long senderMaxBytes() { return senderMaxBytes; }
    public String serverLinks() { return serverLinks; }
    public PlayerAutosavePolicy playerAutosavePolicy() { return playerAutosavePolicy; }
}
