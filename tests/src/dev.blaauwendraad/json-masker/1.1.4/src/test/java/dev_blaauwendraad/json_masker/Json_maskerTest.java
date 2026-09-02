/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_blaauwendraad.json_masker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import dev.blaauwendraad.masker.json.JsonMasker;
import dev.blaauwendraad.masker.json.ValueMasker;
import dev.blaauwendraad.masker.json.ValueMaskerContext;
import dev.blaauwendraad.masker.json.ValueMaskers;
import dev.blaauwendraad.masker.json.config.JsonMaskingConfig;
import dev.blaauwendraad.masker.json.config.KeyMaskingConfig;
import dev.blaauwendraad.masker.json.path.JsonPath;
import dev.blaauwendraad.masker.json.path.JsonPathParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Json_maskerTest {
    @Test
    void masksMatchingKeysRecursivelyWithDefaultMasks() {
        JsonMasker masker = JsonMasker.getMasker(Set.of("password"));
        String input = "{\"safe\":\"visible\",\"PASSWORD\":\"secret\",\"nested\":{"
                + "\"password\":123,\"active\":true},\"password\":[\"token\",42,false,{\"child\":\"value\"},null]}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"safe\":\"visible\",\"PASSWORD\":\"***\",\"nested\":{"
                        + "\"password\":\"###\",\"active\":true},\"password\":[\"***\",\"###\",\"&&&\","
                        + "{\"child\":\"***\"},null]}");
    }

    @Test
    void appliesCaseSensitiveTypePreservingMasksToByteArrays() {
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .maskKeys("text", "number", "flag")
                .caseSensitiveTargetKeys()
                .maskStringCharactersWith("*")
                .maskNumberDigitsWith(8)
                .maskBooleansWith(false)
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        byte[] input = "{\"text\":\"a\\tb\",\"Text\":\"visible\",\"number\":2048,\"flag\":true}"
                .getBytes(StandardCharsets.UTF_8);

        byte[] masked = masker.mask(input);

        assertThat(new String(masked, StandardCharsets.UTF_8))
                .isEqualTo("{\"text\":\"***\",\"Text\":\"visible\",\"number\":8888,\"flag\":false}");
    }

    @Test
    void leavesOnlyAllowedKeysUnmasked() {
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .allowKeys("id", "name")
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        String input = "{\"id\":7,\"profile\":{\"name\":\"Ada\",\"email\":\"ada@example.com\","
                + "\"active\":true},\"tags\":[\"engineer\",\"admin\"],\"empty\":null}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"id\":7,\"profile\":{\"name\":\"Ada\",\"email\":\"***\","
                        + "\"active\":\"&&&\"},\"tags\":[\"***\",\"***\"],\"empty\":null}");
    }

    @Test
    void allowsOnlyValuesAtConfiguredJsonPaths() {
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .allowJsonPaths("$.profile.name", "$.profile.age", "$.profile.verified")
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        String input = "{\"profile\":{\"name\":\"Ada\",\"age\":36,\"verified\":true},"
                + "\"copy\":{\"name\":\"Ada\",\"age\":36,\"verified\":true}}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"profile\":{\"name\":\"Ada\",\"age\":36,\"verified\":true},"
                        + "\"copy\":{\"name\":\"***\",\"age\":\"###\",\"verified\":\"&&&\"}}");
    }

    @Test
    void masksExactAndWildcardJsonPaths() {
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .maskJsonPaths("$.users.*.token", "$.admin.token")
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        String input = "{\"users\":[{\"token\":\"one\",\"name\":\"A\"},{\"token\":\"two\","
                + "\"name\":\"B\"}],\"admin\":{\"token\":\"root\"},\"other\":{\"token\":\"clear\"}}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"users\":[{\"token\":\"***\",\"name\":\"A\"},{\"token\":\"***\","
                        + "\"name\":\"B\"}],\"admin\":{\"token\":\"***\"},\"other\":{\"token\":\"clear\"}}");
    }

    @Test
    void masksStreamingInputAcrossInternalBufferBoundaries() throws IOException {
        JsonMasker masker = JsonMasker.getMasker(Set.of("secret"));
        String input = "{\"padding\":\"" + "x".repeat(9_000)
                + "\",\"secret\":\"sensitive\",\"after\":1}";

        try (ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            masker.mask(inputStream, outputStream);

            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .isEqualTo(input.replace("\"sensitive\"", "\"***\""));
        }
    }

    @Test
    void appliesPerTargetConfigurationAndJsonPathPrecedence() {
        KeyMaskingConfig emailConfig = KeyMaskingConfig.builder()
                .maskStringsWith(ValueMaskers.email(2, 2, true, "***"))
                .build();
        KeyMaskingConfig nullConfig = KeyMaskingConfig.builder()
                .maskStringsWith(ValueMaskers.withNull())
                .maskNumbersWith(ValueMaskers.withNull())
                .maskBooleansWith(ValueMaskers.withNull())
                .build();
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .maskKeys("secret")
                .maskKeys("email", emailConfig)
                .maskJsonPaths("$.audit.secret", nullConfig)
                .maskStringsWith("[hidden]")
                .maskNumbersWith(-1)
                .maskBooleansWith(false)
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        String input = "{\"email\":\"alexander@example.com\",\"secret\":{\"label\":\"root\","
                + "\"count\":7,\"active\":true},\"audit\":{\"secret\":\"event\"}}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"email\":\"al***er@example.com\",\"secret\":{\"label\":\"[hidden]\","
                        + "\"count\":-1,\"active\":false},\"audit\":{\"secret\":null}}");
        assertThat(config.getKeyConfig("email")).isSameAs(emailConfig);
        assertThat(config.getConfig("missing")).isSameAs(config.getDefaultConfig());
    }

    @Test
    void supportsFunctionBasedAndNoopValueMaskers() {
        KeyMaskingConfig customConfig = KeyMaskingConfig.builder()
                .maskStringsWith(ValueMaskers.withTextFunction(Json_maskerTest::decorateText))
                .maskNumbersWith(ValueMaskers.withRawValueFunction(Json_maskerTest::doubleIntegerLiteral))
                .maskBooleansWith(ValueMaskers.describe("leave flags unchanged", ValueMaskers.noop()))
                .build();
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .maskKeys("payload", customConfig)
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);
        String input = "{\"payload\":{\"text\":\"hello\\nworld\",\"count\":12,\"active\":true}}";

        String masked = masker.mask(input);

        assertThat(masked)
                .isEqualTo("{\"payload\":{\"text\":\"<hello\\nworld>\",\"count\":24,\"active\":true}}");
        assertThat(customConfig.toString()).contains("leave flags unchanged");
    }

    @Test
    void supportsCustomByteLevelValueMaskers() {
        KeyMaskingConfig accountConfig = KeyMaskingConfig.builder()
                .maskStringsWith(new KeepLastFourCharactersMasker())
                .build();
        JsonMaskingConfig config = JsonMaskingConfig.builder()
                .maskKeys("account", accountConfig)
                .build();
        JsonMasker masker = JsonMasker.getMasker(config);

        String masked = masker.mask("{\"account\":\"123456789\",\"label\":\"checking\"}");

        assertThat(masked).isEqualTo("{\"account\":\"***6789\",\"label\":\"checking\"}");
    }

    @Test
    void parsesJsonPathsAndRejectsAmbiguousPathSets() {
        JsonPathParser parser = new JsonPathParser();

        JsonPath path = parser.parse("$.users.*.token");
        JsonPath equivalentPath = new JsonPath(new String[] {"$", "users", "*", "token"});

        assertThat(path.segments()).containsExactly("$", "users", "*", "token");
        assertThat(path.getQueryArgument()).isEqualTo("token");
        assertThat(path.toString()).isEqualTo("$.users.*.token");
        assertThat(path).isEqualTo(equivalentPath);
        assertThat(path.hashCode()).isEqualTo(equivalentPath.hashCode());
        assertThat(parser.tryParse("users.token")).isNull();
        assertThatThrownBy(() -> parser.checkAmbiguity(
                        Set.of(path, parser.parse("$.users.admin.token"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguity");
    }

    private static String decorateText(String value) {
        return "<" + value + ">";
    }

    private static String doubleIntegerLiteral(String value) {
        return String.valueOf(Integer.parseInt(value) * 2);
    }

    private static final class KeepLastFourCharactersMasker implements ValueMasker.StringMasker {
        private static final byte[] MASK = "***".getBytes(StandardCharsets.UTF_8);

        @Override
        public void maskValue(ValueMaskerContext context) {
            if (context.getByte(0) != '"' || context.getByte(context.byteLength() - 1) != '"') {
                throw context.invalidJson("Expected a JSON string", 0);
            }
            int contentLength = context.byteLength() - 2;
            if (contentLength > 4) {
                context.replaceBytes(1, contentLength - 4, MASK, 1);
            }
        }
    }
}
