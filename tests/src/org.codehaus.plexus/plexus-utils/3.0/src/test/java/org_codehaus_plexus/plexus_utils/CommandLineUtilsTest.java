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
        Map<String, String> systemEnvironment = System.getenv();

        Properties environment = CommandLineUtils.getSystemEnvVars(true);

        assertThat(environment).isNotNull();
        systemEnvironment.forEach((key, value) -> assertThat(environment.getProperty(key)).isEqualTo(value));
    }

    @Test
    void normalizesEnvironmentVariableNamesWhenCaseInsensitive() throws Exception {
        Properties environment = CommandLineUtils.getSystemEnvVars(false);

        assertThat(environment).isNotNull();
        System.getenv().keySet().stream().findFirst().ifPresent(key -> assertThat(
                environment.getProperty(key.toUpperCase(Locale.ENGLISH))).isNotNull());
    }
}
