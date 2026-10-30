/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.time.MonthDay;
import java.util.Map;
import java.util.logging.Level;

import io.helidon.config.mp.MpConfigSources;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpConfigImplTest {
    @Test
    void convertsValueUsingPublicStringConstructor() {
        Config config = config(Map.of("message", "service ready"));

        StringBuilder message = config.getValue("message", StringBuilder.class);

        assertEquals("service ready", message.toString());
        assertEquals("service ready!", message.append('!').toString());
    }

    @Test
    void convertsValueUsingParseCharSequenceFactory() {
        Config config = config(Map.of("anniversary", "--06-15"));

        MonthDay anniversary = config.getValue("anniversary", MonthDay.class);

        assertEquals(MonthDay.of(6, 15), anniversary);
    }

    @Test
    void convertsValueUsingValueOfFactory() {
        Config config = config(Map.of("endpoint", "api.internal:8080"));

        Endpoint endpoint = config.getValue("endpoint", Endpoint.class);

        assertEquals("api.internal", endpoint.host());
        assertEquals(8080, endpoint.port());
    }

    @Test
    void convertsValueUsingParseStringFactory() {
        Config config = config(Map.of("logging.level", "WARNING"));

        Level level = config.getValue("logging.level", Level.class);

        assertEquals(Level.WARNING, level);
    }

    @Test
    void readsCommaSeparatedArraysWithEscapedCommasAndEmptyElements() {
        Config config = config(Map.of(
                "ports", "8080,8081,9090",
                "labels", "primary\\,east,,backup,"));

        assertArrayEquals(new int[]{8080, 8081, 9090}, config.getValue("ports", int[].class));
        assertArrayEquals(new String[]{"primary,east", "backup"}, config.getValue("labels", String[].class));
    }

    @Test
    void readsIndexedArraysAndConvertsTheirComponents() {
        Config config = config(Map.of(
                "ports.0", "8080",
                "ports.1", "9090",
                "anniversaries.0", "--01-10",
                "anniversaries.1", "--09-25"));

        assertArrayEquals(new int[]{8080, 9090}, config.getOptionalValue("ports", int[].class).orElseThrow());
        assertArrayEquals(new MonthDay[]{MonthDay.of(1, 10), MonthDay.of(9, 25)},
                config.getValue("anniversaries", MonthDay[].class));
    }

    @Test
    void arrayConvertersConvertStandaloneValues() {
        Config config = config(Map.of());
        Converter<Integer[]> ports = config.getConverter(Integer[].class).orElseThrow();
        Converter<MonthDay[]> anniversaries = config.getConverter(MonthDay[].class).orElseThrow();
        Converter<String[]> labels = config.getConverter(String[].class).orElseThrow();

        assertArrayEquals(new Integer[]{8080, 9090}, ports.convert("8080,9090"));
        assertArrayEquals(new MonthDay[]{MonthDay.of(2, 14), MonthDay.of(10, 31)},
                anniversaries.convert("--02-14,--10-31"));
        assertArrayEquals(new String[]{"primary,east", "backup"}, labels.convert("primary\\,east,backup"));
    }

    public record Endpoint(String host, int port) {
        public static Endpoint valueOf(String value) {
            int separator = value.lastIndexOf(':');
            return new Endpoint(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
        }
    }

    private static Config config(Map<String, String> values) {
        return ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(values))
                .build();
    }
}
