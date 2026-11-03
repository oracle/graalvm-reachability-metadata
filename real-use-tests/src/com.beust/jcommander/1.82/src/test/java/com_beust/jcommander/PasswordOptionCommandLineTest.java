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
import com.beust.jcommander.internal.Console;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's password-parameter subsystem for a CLI that authenticates to a repository.
public class PasswordOptionCommandLineTest {
    @Test
    public void readsASecretThroughTheConfiguredConsoleWithoutEchoingIt() {
        PromptConsole console = new PromptConsole("release-secret");
        PublishOptions options = new PublishOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("artifact-publisher")
                .console(console)
                .addObject(options)
                .build();

        commander.parse(
                "--repository", "https://repo.example.test/releases",
                "--token");

        assertEquals("https://repo.example.test/releases", options.repository);
        assertEquals("release-secret", options.token);
        assertEquals(List.of("access token: "), console.prompts);
        assertEquals(List.of(false), console.echoInputValues);
    }

    @Parameters(commandDescription = "Publish an artifact to a protected repository")
    public static class PublishOptions {
        @Parameter(names = "--repository", description = "artifact repository URL", required = true)
        private String repository;

        @Parameter(
                names = "--token",
                description = "access token",
                password = true,
                echoInput = false,
                required = true)
        private String token;
    }

    public static class PromptConsole implements Console {
        private final char[] secret;
        private final List<String> prompts = new ArrayList<>();
        private final List<Boolean> echoInputValues = new ArrayList<>();

        public PromptConsole(String secret) {
            this.secret = secret.toCharArray();
        }

        @Override
        public void print(String message) {
            prompts.add(message);
        }

        @Override
        public void println(String message) {
            prompts.add(message);
        }

        @Override
        public char[] readPassword(boolean echoInput) {
            echoInputValues.add(echoInput);
            return secret.clone();
        }
    }
}
