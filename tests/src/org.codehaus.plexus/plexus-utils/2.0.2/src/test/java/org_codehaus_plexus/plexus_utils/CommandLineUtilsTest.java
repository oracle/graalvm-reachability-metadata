/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineUtilsTest {
    @Test
    void readsSystemEnvironmentVariablesThroughCommandLineUtils() throws IOException {
        Properties envVars = CommandLineUtils.getSystemEnvVars(true);

        assertThat(envVars).isNotNull();
        assertThat(envVars).containsAllEntriesOf(System.getenv());
    }

    @Test
    void normalizesSystemEnvironmentVariableNamesWhenCaseInsensitive() throws IOException {
        Properties expectedEnvVars = new Properties();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            expectedEnvVars.put(entry.getKey().toUpperCase(Locale.ENGLISH), entry.getValue());
        }

        Properties envVars = CommandLineUtils.getSystemEnvVars(false);

        assertThat(envVars).isNotNull();
        assertThat(envVars).containsAllEntriesOf(expectedEnvVars);
    }
}
