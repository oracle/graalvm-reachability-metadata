/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineUtilsTest {
    @Test
    void readsEnvironmentVariablesThroughSystemGetenv() throws Exception {
        Properties envVars = CommandLineUtils.getSystemEnvVars(true);

        assertThat(envVars).isNotNull();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            assertThat(envVars.getProperty(entry.getKey())).isEqualTo(entry.getValue());
        }
    }

    @Test
    void canNormalizeEnvironmentVariableNames() throws Exception {
        Properties envVars = CommandLineUtils.getSystemEnvVars(false);

        assertThat(envVars).isNotNull();
        for (String key : System.getenv().keySet()) {
            assertThat(envVars).containsKey(key.toUpperCase(Locale.ENGLISH));
        }
    }
}
