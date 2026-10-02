/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_json;

import com.azure.json.implementation.jackson.core.JsonFactory;
import com.azure.json.implementation.jackson.core.JsonGenerator;
import com.azure.json.implementation.jackson.core.Version;
import com.azure.json.implementation.jackson.core.util.VersionUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionUtilTest {
    @Test
    void generatorReportsItsPackageVersionWhileWritingJson() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Version version;

        try (JsonGenerator generator = new JsonFactory().createGenerator(output)) {
            version = generator.version();
            generator.writeStartObject();
            generator.writeStringField("message", "hello");
            generator.writeEndObject();
        }

        assertFalse(version.isUnknownVersion());
        assertEquals("{\"message\":\"hello\"}", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void returnsUnknownVersionWhenMavenPropertiesAreUnavailable() {
        ClassLoader classLoader = VersionUtil.class.getClassLoader();
        Version version = VersionUtil.mavenVersionFor(classLoader, "invalid.group", "missing-artifact");

        assertTrue(version.isUnknownVersion());
    }
}
