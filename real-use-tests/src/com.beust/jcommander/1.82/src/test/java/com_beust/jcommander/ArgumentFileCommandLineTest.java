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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's argument-file expansion subsystem for a batch-oriented CLI.
public class ArgumentFileCommandLineTest {
    @Test
    public void expandsAnArgumentFileWithCommentsAndSeparatedOptions() throws IOException {
        Path argumentFile = Files.createTempFile("artifact-tool-", ".args");
        Files.writeString(
                argumentFile,
                "# release configuration\n"
                        + "--repository\n"
                        + "https://repo.example.test/releases\n"
                        + "\n"
                        + "--channel\n"
                        + "stable\n"
                        + "--artifact\n"
                        + "com.example:payments:2.4.0\n",
                StandardCharsets.UTF_8);

        try {
            ReleaseOptions options = new ReleaseOptions();
            JCommander commander = JCommander.newBuilder()
                    .programName("artifact-tool")
                    .addObject(options)
                    .build();
            commander.setExpandAtSign(true);
            commander.parse("@" + argumentFile);

            assertEquals("https://repo.example.test/releases", options.repository);
            assertEquals("stable", options.channel);
            assertEquals("com.example:payments:2.4.0", options.artifact);
        } finally {
            Files.deleteIfExists(argumentFile);
        }
    }

    @Parameters(commandDescription = "Publish an artifact from a release configuration")
    public static class ReleaseOptions {
        @Parameter(names = "--repository", description = "artifact repository URL", required = true)
        private String repository;

        @Parameter(names = "--channel", description = "release channel", required = true)
        private String channel;

        @Parameter(names = "--artifact", description = "artifact coordinate", required = true)
        private String artifact;
    }
}
