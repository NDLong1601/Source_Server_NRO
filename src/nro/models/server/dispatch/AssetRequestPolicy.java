package nro.models.server.dispatch;

import java.util.regex.Pattern;

/** Allowlist for client-selected image keys before they reach filesystem code. */
public final class AssetRequestPolicy {

    private static final Pattern SAFE_IMAGE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private AssetRequestPolicy() {
    }

    public static boolean isSafeImageName(String imageName) {
        return imageName != null && SAFE_IMAGE_NAME.matcher(imageName).matches();
    }
}
