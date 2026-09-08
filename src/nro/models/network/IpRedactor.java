package nro.models.network;

public final class IpRedactor {

    private IpRedactor() {
    }

    public static String redact(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String trimmed = ip.trim();
        if (trimmed.contains(".")) {
            // IPv4 address
            String[] parts = trimmed.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***.***";
            }
        } else if (trimmed.contains(":")) {
            // IPv6 address
            int colonIndex = trimmed.indexOf(':', trimmed.indexOf(':') + 1);
            if (colonIndex > 0) {
                return trimmed.substring(0, colonIndex) + ":****:****";
            }
        }
        return "redacted";
    }
}
