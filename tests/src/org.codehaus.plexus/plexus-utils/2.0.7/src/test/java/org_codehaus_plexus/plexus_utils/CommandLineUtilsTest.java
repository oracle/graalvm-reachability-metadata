/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineUtilsTest {
    @Test
    void readsEnvironmentVariablesThroughPublicApi() throws Exception {
        Properties environment = CommandLineUtils.getSystemEnvVars(true);

        assertThat(environment).isNotNull();
        assertThat(environment.size()).isEqualTo(System.getenv().size());
        assertKnownEnvironmentVariable(environment, false);
    }

    @Test
    void normalizesEnvironmentVariableNamesWhenCaseInsensitive() throws Exception {
        Properties environment = CommandLineUtils.getSystemEnvVars(false);

        assertThat(environment).isNotNull();
        assertThat(environment.keySet()).allSatisfy(key -> assertThat(key.toString())
                .isEqualTo(key.toString().toUpperCase(Locale.ENGLISH)));
        assertKnownEnvironmentVariable(environment, true);
    }

    private static void assertKnownEnvironmentVariable(Properties environment, boolean upperCaseKey) {
        Map<String, String> expectedEnvironment = System.getenv();
        Map<String, Integer> normalizedKeyCounts = new HashMap<>();
        for (String key : expectedEnvironment.keySet()) {
            String normalizedKey = key.toUpperCase(Locale.ENGLISH);
            normalizedKeyCounts.put(normalizedKey, normalizedKeyCounts.getOrDefault(normalizedKey, 0) + 1);
        }

        for (Map.Entry<String, String> entry : expectedEnvironment.entrySet()) {
            String normalizedKey = entry.getKey().toUpperCase(Locale.ENGLISH);
            if (upperCaseKey && normalizedKeyCounts.get(normalizedKey) > 1) {
                continue;
            }
            String expectedKey = upperCaseKey ? normalizedKey : entry.getKey();

            assertThat(environment).containsEntry(expectedKey, entry.getValue());
            return;
        }
    }
}
