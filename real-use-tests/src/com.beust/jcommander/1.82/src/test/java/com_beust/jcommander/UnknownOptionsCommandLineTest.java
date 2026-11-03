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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's unknown-option handling for a CLI that forwards plugin options.
public class UnknownOptionsCommandLineTest {
    @Test
    public void preservesUnknownPluginOptionsForForwarding() {
        PluginOptions options = new PluginOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("build-tool")
                .addObject(options)
                .build();
        commander.setAcceptUnknownOptions(true);

        commander.parse(
                "--project", "payments",
                "--plugin-timeout", "30",
                "--plugin-mode", "safe");

        assertEquals("payments", options.project);
        assertEquals(
                List.of("--plugin-timeout", "30", "--plugin-mode", "safe"),
                commander.getUnknownOptions());
    }

    @Parameters(commandDescription = "Build a project and forward plugin options")
    public static class PluginOptions {
        @Parameter(names = "--project", description = "project to build", required = true)
        private String project;
    }
}
