package nro.models.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase-1 contract test. It must be RED until the social-v2 boundary exists;
 * no schema, command handler, or database behavior is required here.
 */
public final class SocialV2PhaseOneContractTest {

    private SocialV2PhaseOneContractTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path protocol = root.resolve("src/nro/models/social/SocialV2Protocol.java");
        Path flags = root.resolve("src/nro/models/social/SocialV2FeatureFlags.java");
        Path config = root.resolve("config/social/social_features.properties");
        Path document = root.resolve("docs/protocol/social_v2_protocol_contract.md");

        requireExists("protocol contract", protocol);
        requireExists("feature-flag boundary", flags);
        requireExists("disabled rollout configuration", config);
        requireExists("wire-contract document", document);

        String protocolSource = Files.readString(protocol, StandardCharsets.UTF_8);
        String configSource = Files.readString(config, StandardCharsets.UTF_8);
        String documentSource = Files.readString(document, StandardCharsets.UTF_8);

        requireContains("signed social command", protocolSource, "COMMAND_SOCIAL = -80");
        requireContains("legacy chat command", protocolSource, "COMMAND_PRIVATE_CHAT_REQUEST = -72");
        requireContains("legacy chat event", protocolSource, "COMMAND_PRIVATE_CHAT_EVENT = 92");
        requireContains("v2 client version gate", protocolSource, "SOCIAL_V2_CLIENT_VERSION = 223");
        requireContains("action 3 presence", protocolSource, "PRESENCE = 3");
        requireContains("action 12 pending badge", protocolSource, "PENDING_COUNT = 12");
        requireContains("page size", protocolSource, "PAGE_SIZE = 20");
        requireContains("friend limit", protocolSource, "MAX_FRIENDS = 100");
        requireContains("packet boundary", protocolSource, "MAX_PACKET_BYTES = 65_535");
        requireContains("disabled feature", configSource, "social_v2.enabled=false");
        requireContains("disabled migration", configSource, "social_v2.migration_enabled=false");
        requireContains("legacy action zero", documentSource, "Action `0`");
        requireContains("legacy action one", documentSource, "Action `1`");
        requireContains("legacy action two", documentSource, "Action `2`");
        requireContains("new search action", documentSource, "| `4` search");
        requireContains("error envelope", documentSource, "Error envelope");
        System.out.println("SocialV2PhaseOneContractTest: PASS");
    }

    private static void requireExists(String label, Path path) {
        if (!Files.isRegularFile(path)) {
            throw new AssertionError(label + " is missing: " + path);
        }
    }

    private static void requireContains(String label, String text, String expected) {
        if (!text.contains(expected)) {
            throw new AssertionError(label + " must contain: " + expected);
        }
    }
}
