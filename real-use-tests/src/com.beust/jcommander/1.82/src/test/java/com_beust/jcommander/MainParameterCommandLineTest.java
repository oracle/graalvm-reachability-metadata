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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's positional main-parameter parsing subsystem for an artifact inspection tool.
public class MainParameterCommandLineTest {
    @Test
    public void parsesNamedOptionsTogetherWithPositionalArtifactCoordinates() {
        InspectOptions options = new InspectOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("artifact-inspector")
                .addObject(options)
                .build();

        commander.parse(
                "--format", "json",
                "com.example:payments:2.4.0",
                "com.example:ledger:1.8.3");

        assertEquals("json", options.format);
        assertEquals(
                List.of("com.example:payments:2.4.0", "com.example:ledger:1.8.3"),
                options.artifacts);
    }

    @Parameters(commandDescription = "Inspect published artifacts")
    public static class InspectOptions {
        @Parameter(names = "--format", description = "output format", required = true)
        private String format;

        @Parameter(description = "artifact coordinates to inspect")
        private List<String> artifacts = new ArrayList<>();
    }
}
