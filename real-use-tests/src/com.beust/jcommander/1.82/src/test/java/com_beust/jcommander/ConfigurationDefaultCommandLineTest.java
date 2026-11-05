/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IDefaultProvider;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCommander's default-provider subsystem for a deployment CLI configuration.
public class ConfigurationDefaultCommandLineTest {
    @Test
    public void combinesConfigurationDefaultsWithExplicitCommandLineOverrides() {
        DeploymentOptions options = new DeploymentOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("deployment-tool")
                .addObject(options)
                .build();
        commander.setDefaultProvider(new ConfigurationDefaults(Map.of(
                "--profile", "production",
                "--region", "eu-west-1",
                "--format", "table")));

        commander.parse("--region", "us-east-1");

        assertEquals("production", options.profile);
        assertEquals("us-east-1", options.region);
        assertEquals("table", options.format);
        assertTrue(options.defaultsWereApplied());
    }

    @Parameters(commandDescription = "Create a deployment plan")
    public static class DeploymentOptions {
        @Parameter(names = "--profile", description = "deployment profile", required = true)
        private String profile;

        @Parameter(names = "--region", description = "deployment region", required = true)
        private String region;

        @Parameter(names = "--format", description = "plan output format", required = true)
        private String format;

        private boolean defaultsWereApplied() {
            return "production".equals(profile) && "table".equals(format);
        }
    }

    public static class ConfigurationDefaults implements IDefaultProvider {
        private final Map<String, String> values;

        public ConfigurationDefaults(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String getDefaultValueFor(String optionName) {
            return values.get(optionName);
        }
    }
}
