/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.aesh.util.graal.GraalReflectionCommand;
import org.junit.jupiter.api.Test;

public class GraalReflectionCommandTest {
    private static final Path REFLECTION_FILE = Path.of("generated-reflection-command_reflection.json");

    @Test
    void generatesReflectionConfigurationForCommandClassName() throws Exception {
        Files.deleteIfExists(REFLECTION_FILE);

        try {
            CommandResult result = AeshRuntimeRunner.builder()
                    .command(GraalReflectionCommand.class)
                    .args(GeneratedReflectionCommand.class.getName())
                    .execute();

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(REFLECTION_FILE).exists();

            String reflectionConfiguration = Files.readString(REFLECTION_FILE, StandardCharsets.UTF_8);
            assertThat(reflectionConfiguration)
                    .contains(GeneratedReflectionCommand.class.getName())
                    .contains("\"name\" : \"message\"")
                    .contains("\"allDeclaredConstructors\" : true")
                    .contains("org.aesh.command.impl.parser.AeshOptionParser");
        } finally {
            Files.deleteIfExists(REFLECTION_FILE);
        }
    }

    @CommandDefinition(name = "generated-reflection-command", description = "Command used by the Graal generator")
    public static class GeneratedReflectionCommand implements Command<CommandInvocation> {
        @Option(name = "message", description = "Message to expose as a reflected field")
        private String message;

        public GeneratedReflectionCommand() {
        }

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }
}
