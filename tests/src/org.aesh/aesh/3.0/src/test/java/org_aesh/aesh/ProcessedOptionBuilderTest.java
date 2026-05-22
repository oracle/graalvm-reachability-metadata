/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.aesh.command.registry.CommandRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProcessedOptionBuilderTest {
    private static File selectedPath;

    @Test
    void fileOptionUsesDefaultCompleterAndConverter(@TempDir Path tempDir) throws Exception {
        selectedPath = null;
        Path source = tempDir.resolve("sample-file.txt");
        Files.writeString(source, "aesh");

        CommandRuntime<CommandInvocation> runtime = runtimeFor(FileOptionCommand.class);

        assertThat(runtime.executeCommand("open --path " + source.toAbsolutePath())).isEqualTo(CommandResult.SUCCESS);
        assertThat(selectedPath).isNotNull();
        assertThat(selectedPath.toPath().toAbsolutePath()).isEqualTo(source.toAbsolutePath());
    }

    private static CommandRuntime<CommandInvocation> runtimeFor(Class<? extends Command> command) throws Exception {
        CommandRegistry<CommandInvocation> registry = AeshCommandRegistryBuilder.<CommandInvocation>builder()
                .command(command)
                .create();
        return AeshCommandRuntimeBuilder.<CommandInvocation>builder()
                .commandRegistry(registry)
                .build();
    }

    @CommandDefinition(name = "open", description = "Opens a file path")
    public static class FileOptionCommand implements Command<CommandInvocation> {
        @Option(name = "path")
        private File path;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            selectedPath = path;
            return CommandResult.SUCCESS;
        }
    }
}
