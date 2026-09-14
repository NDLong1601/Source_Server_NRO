package nro.models.social;

import java.nio.file.Path;
import java.util.Properties;

/** Pure, green regression checks for the phase-1 social-v2 boundary. */
public final class SocialV2ProtocolRegressionTest {

    private SocialV2ProtocolRegressionTest() {
    }

    public static void main(String[] args) {
        verifyCommandAndActionTable();
        verifyVersionAndFeatureGate();
        verifyConfiguredFailClosedDefaults();
        verifyModifiedUtfAndPacketBounds();
        System.out.println("SocialV2ProtocolRegressionTest: PASS");
    }

    private static void verifyCommandAndActionTable() {
        check(SocialV2Protocol.COMMAND_SOCIAL == -80, "social command changed");
        check(SocialV2Protocol.COMMAND_PRIVATE_CHAT_REQUEST == -72, "chat request command changed");
        check(SocialV2Protocol.COMMAND_PRIVATE_CHAT_EVENT == 92, "chat event command changed");
        check(SocialV2Protocol.isLegacyFriendAction(0), "action 0 must remain legacy");
        check(SocialV2Protocol.isLegacyFriendAction(1), "action 1 must remain legacy");
        check(SocialV2Protocol.isLegacyFriendAction(2), "action 2 must remain legacy");
        check(!SocialV2Protocol.isV2Action(2), "legacy action leaked into v2 range");
        check(SocialV2Protocol.isV2Action(3) && SocialV2Protocol.isV2Action(12),
                "v2 action range must be 3..12");
        check("SEARCH".equals(SocialV2Protocol.actionName(SocialV2Protocol.SEARCH)),
                "search action name missing");
        check(SocialV2Protocol.actionNames().size() == 13, "action table must not have gaps or collisions");
        check(SocialV2Protocol.RESULT_OK == 0 && SocialV2Protocol.RESULT_ERROR == 1,
                "result envelope changed");
        check(SocialV2Protocol.ErrorCode.FEATURE_DISABLED.wireValue() == 1,
                "feature-disabled error code changed");
        check(SocialV2Protocol.ErrorCode.PACKET_TOO_LARGE.wireValue() == 15,
                "packet-size error code changed");
        check(SocialV2Protocol.ErrorCode.fromWireValue(11) == SocialV2Protocol.ErrorCode.OFFLINE,
                "error-code lookup changed");
        expectFailure(() -> SocialV2Protocol.ErrorCode.fromWireValue(16));
    }

    private static void verifyVersionAndFeatureGate() {
        Properties values = new Properties();
        values.setProperty(SocialV2FeatureFlags.ENABLED_KEY, "true");
        values.setProperty(SocialV2FeatureFlags.MIGRATION_ENABLED_KEY, "true");
        SocialV2FeatureFlags flags = SocialV2FeatureFlags.from(values);
        check(!SocialV2Protocol.isV2ClientVersion(222), "legacy client must remain legacy");
        check(SocialV2Protocol.isV2ClientVersion(223), "v2 threshold must be inclusive");
        check(!flags.allowsClientVersion(222), "old client must not receive v2");
        check(flags.allowsClientVersion(223), "enabled v2 client must receive v2");
    }

    private static void verifyConfiguredFailClosedDefaults() {
        SocialV2FeatureFlags defaults = SocialV2FeatureFlags.from(new Properties());
        check(!defaults.isEnabled(), "missing config must disable social v2");
        check(!defaults.isMigrationEnabled(), "missing config must disable migration");

        SocialV2FeatureFlags deployed = SocialV2FeatureFlags.load(
                Path.of("config", "social", "social_features.properties"));
        check(!deployed.isEnabled(), "deployed social v2 flag must start disabled");
        check(!deployed.isMigrationEnabled(), "deployed migration switch must start disabled");

        Properties migrationOnly = new Properties();
        migrationOnly.setProperty(SocialV2FeatureFlags.MIGRATION_ENABLED_KEY, "true");
        check(!SocialV2FeatureFlags.from(migrationOnly).isMigrationEnabled(),
                "migration may not bypass disabled social v2");
    }

    private static void verifyModifiedUtfAndPacketBounds() {
        check(SocialV2Protocol.modifiedUtfLength("A") == 1, "ASCII UTF length incorrect");
        check(SocialV2Protocol.modifiedUtfLength("\u0000") == 2, "NUL UTF length incorrect");
        check(SocialV2Protocol.modifiedUtfLength("\u20AC") == 3, "BMP UTF length incorrect");
        check(SocialV2Protocol.modifiedUtfLength("\uD83D\uDE00") == 6,
                "supplementary UTF length incorrect");
        check(SocialV2Protocol.fitsModifiedUtf("x".repeat(80), SocialV2Protocol.MAX_CHAT_CODE_POINTS),
                "80-character chat must fit");
        check(!SocialV2Protocol.fitsModifiedUtf("x".repeat(81), SocialV2Protocol.MAX_CHAT_CODE_POINTS),
                "81-character chat must not fit");
        SocialV2Protocol.requirePacketPayloadLength(65_535L);
        expectFailure(() -> SocialV2Protocol.requirePacketPayloadLength(65_536L));
    }

    private static void expectFailure(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid packet length must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
