/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the delimiter-splitting subsystem to parse an application configuration line.
 */
public class DelimitedConfigParsingTest {

    @Test
    @Timeout(60)
    void parsesDelimitedConfigurationWhilePreservingValueText() {
        String configuration = "region = eu-west-1 ; endpoint = https://api.example.test/v1?a=1&b=2 ; retries=3";
        ConfigurationParser parser = new ConfigurationParser();

        Map<String, String> values = parser.parse(configuration);

        assertEquals(
                Map.of(
                        "region", "eu-west-1",
                        "endpoint", "https://api.example.test/v1?a=1&b=2",
                        "retries", "3"),
                values);
    }

    /** Application configuration parsing that separates fields and their key-value pairs. */
    private static final class ConfigurationParser {

        private final Pattern fieldSeparator = Pattern.compile("\\s*;\\s*");
        private final Pattern keyValueSeparator = Pattern.compile("\\s*=\\s*");

        Map<String, String> parse(String configuration) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String field : fieldSeparator.split(configuration)) {
                String[] keyAndValue = keyValueSeparator.split(field, 2);
                if (keyAndValue.length != 2) {
                    throw new IllegalArgumentException("Malformed configuration field: " + field);
                }
                values.put(keyAndValue[0], keyAndValue[1]);
            }
            return values;
        }
    }
}
