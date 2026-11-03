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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCommander's help-option subsystem for a CLI that can show help before configuration.
public class HelpOptionCommandLineTest {
    @Test
    public void acceptsHelpWithoutRequiringTheCommandConfiguration() {
        ToolOptions options = new ToolOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("artifact-tool")
                .addObject(options)
                .build();

        commander.parse("--help");

        assertTrue(options.help);
        assertNull(options.repository);
    }

    @Parameters(commandDescription = "Publish an artifact")
    public static class ToolOptions {
        @Parameter(names = "--help", description = "show command help", help = true)
        private boolean help;

        @Parameter(names = "--repository", description = "artifact repository URL", required = true)
        private String repository;
    }
}
