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

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCommander's usage rendering subsystem for a command-line tool's help screen.
public class CommandLineHelpTest {
    @Test
    public void rendersRootAndCommandSpecificHelpForAnArtifactTool() {
        JCommander commander = JCommander.newBuilder()
                .programName("artifact-tool")
                .addCommand("publish", new PublishOptions(), "push")
                .addCommand("inspect", new InspectOptions())
                .build();

        StringBuilder rootUsage = new StringBuilder();
        commander.getUsageFormatter().usage(rootUsage);

        assertTrue(rootUsage.toString().contains("artifact-tool"));
        assertTrue(rootUsage.toString().contains("publish"));
        assertTrue(rootUsage.toString().contains("Inspect a published artifact"));

        StringBuilder publishUsage = new StringBuilder();
        commander.getUsageFormatter().usage("publish", publishUsage);

        assertTrue(publishUsage.toString().contains("Publish a signed artifact"));
        assertTrue(publishUsage.toString().contains("--repository"));
        assertTrue(publishUsage.toString().contains("--dry-run"));
    }

    @Parameters(commandDescription = "Publish a signed artifact")
    public static class PublishOptions {
        @Parameter(
                names = {"--repository", "-r"},
                description = "artifact repository URL",
                required = true)
        private String repository;

        @Parameter(names = "--dry-run", description = "show the plan without publishing")
        private boolean dryRun;
    }

    @Parameters(commandDescription = "Inspect a published artifact")
    public static class InspectOptions {
        @Parameter(names = "--artifact", description = "artifact coordinate to inspect", required = true)
        private String artifact;
    }
}
