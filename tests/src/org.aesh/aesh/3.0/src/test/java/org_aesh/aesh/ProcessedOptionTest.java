/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.operator.OperatorType;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.io.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProcessedOptionTest {
    private static String redirectedInput;

    @BeforeEach
    void resetCapturedState() {
        redirectedInput = null;
    }

    @Test
    void injectsScalarOptionIntoNonPublicCommandField() throws Exception {
        ScalarCommand command = new ScalarCommand();

        AeshCommandContainerBuilder.parseAndPopulate(command, "scalar --name alpha");

        assertThat(command.name).isEqualTo("alpha");
    }

    @Test
    void injectsDefaultValuesIntoConcreteCollectionField() throws Exception {
        ConcreteCollectionCommand command = new ConcreteCollectionCommand();

        AeshCommandContainerBuilder.parseAndPopulate(command, "collection");

        assertThat(command.tags).isInstanceOf(ArrayList.class)
                .containsExactly("alpha", "bravo");
    }

    @Test
    void injectsOptionGroupsIntoInterfaceAndConcreteMapFields() throws Exception {
        GroupedOptionsCommand command = new GroupedOptionsCommand();

        AeshCommandContainerBuilder.parseAndPopulate(command, "grouped -Dfirst=one -Psecond=two");

        assertThat(command.defines).isInstanceOf(HashMap.class)
                .containsEntry("first", "one");
        assertThat(command.properties).isInstanceOf(HashMap.class)
                .containsEntry("second", "two");
    }

    @Test
    void injectsRedirectedInputResourceIntoArgumentField(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.txt");
        Files.writeString(input, "redirected resource", StandardCharsets.UTF_8);
        CommandRuntime<CommandInvocation> runtime = runtimeFor(ResourceSinkCommand.class,
                EnumSet.of(OperatorType.REDIRECT_IN));

        CommandResult result = runtime.executeCommand("resource-sink < " + input.toAbsolutePath());

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(redirectedInput).isEqualTo("redirected resource");
    }

    private static CommandRuntime<CommandInvocation> runtimeFor(Class<? extends Command> command,
            EnumSet<OperatorType> operators) throws Exception {
        CommandRegistry<CommandInvocation> registry = AeshCommandRegistryBuilder.<CommandInvocation>builder()
                .command(command)
                .create();
        return AeshCommandRuntimeBuilder.<CommandInvocation>builder()
                .commandRegistry(registry)
                .operators(operators)
                .build();
    }

    @CommandDefinition(name = "scalar", description = "Receives a scalar option")
    private static class ScalarCommand implements Command<CommandInvocation> {
        @Option(name = "name")
        private String name;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "collection", description = "Receives default list values")
    public static class ConcreteCollectionCommand implements Command<CommandInvocation> {
        @OptionList(name = "tag", defaultValue = {"alpha", "bravo"})
        private ArrayList<String> tags;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "grouped", description = "Receives grouped options")
    public static class GroupedOptionsCommand implements Command<CommandInvocation> {
        @OptionGroup(name = "define", shortName = 'D')
        private Map<String, String> defines;

        @OptionGroup(name = "property", shortName = 'P')
        private HashMap<String, String> properties;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "resource-sink", description = "Receives input redirection as a resource")
    public static class ResourceSinkCommand implements Command<CommandInvocation> {
        @Argument
        private Resource input;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException {
            try (InputStream inputStream = input.read()) {
                redirectedInput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new CommandException(exception);
            }
            return CommandResult.SUCCESS;
        }
    }
}
