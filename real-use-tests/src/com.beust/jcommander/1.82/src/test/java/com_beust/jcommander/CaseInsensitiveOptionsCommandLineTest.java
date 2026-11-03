/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's case-insensitive option matching subsystem for a deployment CLI.
public class CaseInsensitiveOptionsCommandLineTest {
    @Test
    public void acceptsOptionsWrittenWithDifferentCapitalization() {
        DeploymentOptions options = new DeploymentOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("deployment-tool")
                .addObject(options)
                .build();
        commander.setCaseSensitiveOptions(false);

        commander.parse(
                "--REGION", "us-east-1",
                "--output-format", "summary");

        assertEquals("us-east-1", options.region);
        assertEquals("summary", options.outputFormat);
    }

    @Parameters(commandDescription = "Create a deployment plan")
    public static class DeploymentOptions {
        @Parameter(names = "--region", description = "deployment region", required = true)
        private String region;

        @Parameter(names = "--output-format", description = "deployment report format", required = true)
        private String outputFormat;
    }
}
