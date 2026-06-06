/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_media_type;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.media.type.ParserMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Helidon_common_media_typeTest {
    @Test
    void detectsBuiltInMediaTypesFromExtensions() {
        assertThat(MediaTypes.detectExtensionType("yml"))
                .hasValue(MediaTypes.APPLICATION_X_YAML);
        assertThat(MediaTypes.detectExtensionType("json"))
                .hasValue(MediaTypes.APPLICATION_JSON);
        assertThat(MediaTypes.detectExtensionType("unknown-extension"))
                .isEmpty();
    }

    @Test
    void detectsBuiltInMediaTypesFromResourceLocations() throws Exception {
        assertDetectedType(MediaTypes.detectType("assets/index.html"),
                           MediaTypes.TEXT_HTML);
        assertDetectedType(MediaTypes.detectType(Path.of("/var/www/config.yml")),
                           MediaTypes.APPLICATION_X_YAML);
        assertDetectedType(MediaTypes.detectType(URI.create("https://example.test/api/payload.json?download=true")),
                           MediaTypes.APPLICATION_JSON);
        assertDetectedType(MediaTypes.detectType(URI.create("https://example.test/docs/index.html").toURL()),
                           MediaTypes.TEXT_HTML);
    }

    @Test
    void createsMediaTypesAndExposesStructuredParts() {
        MediaType vendorJson = MediaTypes.create("application/vnd.example+json");

        assertThat(vendorJson.type()).isEqualTo("application");
        assertThat(vendorJson.subtype()).isEqualTo("vnd.example+json");
        assertThat(vendorJson.text()).isEqualTo("application/vnd.example+json");
        assertThat(vendorJson.fullType()).isEqualTo("application/vnd.example+json");
        assertThat(vendorJson.hasSuffix("json")).isTrue();
        assertThat(vendorJson.hasSuffix("+json")).isTrue();
        assertThat(vendorJson.hasSuffix("xml")).isFalse();
    }

    @Test
    void createsRelaxedTextMediaType() {
        MediaType relaxedText = MediaTypes.create("text", ParserMode.RELAXED);

        assertThat(relaxedText).isEqualTo(MediaTypes.TEXT_PLAIN);
        assertThat(relaxedText.type()).isEqualTo("text");
        assertThat(relaxedText.subtype()).isEqualTo("plain");
    }

    @Test
    void identifiesWildcardMediaTypeParts() {
        MediaType wildcardSubtype = MediaTypes.create("application/*");

        assertThat(wildcardSubtype.isWildcardType()).isFalse();
        assertThat(wildcardSubtype.isWildcardSubtype()).isTrue();
        assertThat(MediaTypes.WILDCARD.isWildcardType()).isTrue();
        assertThat(MediaTypes.WILDCARD.isWildcardSubtype()).isTrue();
    }

    private static void assertDetectedType(Optional<MediaType> actual, MediaType expected) {
        assertThat(actual)
                .hasValue(expected);
    }
}
