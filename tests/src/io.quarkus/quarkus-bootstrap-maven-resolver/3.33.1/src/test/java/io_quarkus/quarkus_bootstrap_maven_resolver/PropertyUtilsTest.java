/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_maven_resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.util.PropertyUtils;

public class PropertyUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPropertiesInStableOrderWithoutTimestamp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("z.last", "final");
        properties.setProperty("a.first", "initial");
        properties.setProperty("middle", "value");

        Path propertiesFile = tempDir.resolve("sorted.properties");
        PropertyUtils.store(properties, propertiesFile, "generated for test");

        String content = Files.readString(propertiesFile);
        assertEquals("""
                # generated for test
                a.first=initial
                middle=value
                z.last=final
                """.replace("\n", System.lineSeparator()), content);
    }

    @Test
    void escapesStoredValuesSoStandardPropertiesCanLoadThem() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("contains space", "leading and trailing ");
        values.put("path:with=separators", "C:\\temp\\demo");
        values.put("unicode", "snowman ☃");

        Path propertiesFile = tempDir.resolve("escaped.properties");
        PropertyUtils.store(values, propertiesFile);

        Properties loaded = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            loaded.load(input);
        }

        assertEquals("leading and trailing ", loaded.getProperty("contains space"));
        assertEquals("C:\\temp\\demo", loaded.getProperty("path:with=separators"));
        assertEquals("snowman ☃", loaded.getProperty("unicode"));
    }

    @Test
    void resolvesBooleanSystemPropertiesWithDefaultsAndEmptyValues() {
        String enabled = "quarkus.bootstrap.test.property-utils.enabled";
        String missing = "quarkus.bootstrap.test.property-utils.missing";
        String empty = "quarkus.bootstrap.test.property-utils.empty";
        String previousEnabled = System.getProperty(enabled);
        String previousMissing = System.getProperty(missing);
        String previousEmpty = System.getProperty(empty);
        try {
            System.setProperty(enabled, "true");
            System.clearProperty(missing);
            System.setProperty(empty, "");

            assertEquals(Boolean.TRUE, PropertyUtils.getBooleanOrNull(enabled));
            assertNull(PropertyUtils.getBooleanOrNull(missing));
            assertTrue(PropertyUtils.getBoolean(missing, true));
            assertFalse(PropertyUtils.getBoolean(missing, false));
            assertTrue(PropertyUtils.getBoolean(empty, false));
        } finally {
            restoreProperty(enabled, previousEnabled);
            restoreProperty(missing, previousMissing);
            restoreProperty(empty, previousEmpty);
        }
    }

    @Test
    void writesToProvidedWriterWithoutClosingIt() throws Exception {
        StringWriter writer = new StringWriter();
        Properties properties = new Properties();
        properties.setProperty("key", "value");

        PropertyUtils.store(properties, writer);
        writer.write("after=store");

        assertEquals("""
                key=value
                after=store""".replace("\n", System.lineSeparator()), writer.toString());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
