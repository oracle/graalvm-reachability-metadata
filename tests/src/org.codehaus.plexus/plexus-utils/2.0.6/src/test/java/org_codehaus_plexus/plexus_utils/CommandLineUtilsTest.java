/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineUtilsTest {
    @Test
    void readsEnvironmentVariablesThroughSystemGetenvMethod() throws IOException {
        Properties envVars = CommandLineUtils.getSystemEnvVars(true);

        assertThat(envVars).isNotNull();
        for (Map.Entry<String, String> envVar : System.getenv().entrySet()) {
            assertThat(envVars).containsEntry(envVar.getKey(), envVar.getValue());
            return;
        }
        assertThat(envVars).isEmpty();
    }
}
