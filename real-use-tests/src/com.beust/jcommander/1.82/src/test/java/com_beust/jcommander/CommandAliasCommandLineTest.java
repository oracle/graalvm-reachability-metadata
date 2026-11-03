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

/// Exercises JCommander's command-alias subsystem for a release CLI with a backwards-compatible command name.
public class CommandAliasCommandLineTest {
    @Test
    public void dispatchesAnAliasToTheCanonicalReleaseCommand() {
        PublishOptions options = new PublishOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("release-tool")
                .addCommand("publish", options, "push")
                .build();

        commander.parse("push", "--repository", "https://repo.example.test/releases");

        assertEquals("publish", commander.getParsedCommand());
        assertEquals("push", commander.getParsedAlias());
        assertEquals("https://repo.example.test/releases", options.repository);
    }

    @Parameters(commandDescription = "Publish a release artifact")
    public static class PublishOptions {
        @Parameter(names = "--repository", description = "artifact repository URL", required = true)
        private String repository;
    }
}
