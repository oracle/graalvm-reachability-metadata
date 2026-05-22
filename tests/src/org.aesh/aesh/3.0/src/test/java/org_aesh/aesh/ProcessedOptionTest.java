/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.operator.OperatorType;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.io.PipelineResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProcessedOptionTest {
    private static ScalarValues scalarValues;
    private static GroupValues groupValues;
    private static Boolean negatedFlag;
    private static LinkedList<String> concreteItems;
    private static String redirectedInput;

    @Test
    void nonPublicCommandReceivesScalarOption() throws Exception {
        scalarValues = null;

        CommandResult result = runtimeFor(PrivateScalarCommand.class).executeCommand("scalar --message hello");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(scalarValues.message()).isEqualTo("hello");
    }

    @Test
    void optionGroupsPopulateInterfaceAndConcreteMapFields() throws Exception {
        groupValues = null;

        CommandResult result = runtimeFor(GroupOptionsCommand.class)
                .executeCommand("groups -Dthreads=4 -Dtimeout=30 -Pbatch=2");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(groupValues.defines())
                .containsEntry("threads", 4)
                .containsEntry("timeout", 30);
        assertThat(groupValues.priorities())
                .isInstanceOf(LinkedHashMap.class)
                .containsEntry("batch", 2);
    }

    @Test
    void negatedBooleanOptionInjectsFalseValue() throws Exception {
        negatedFlag = null;

        CommandResult result = runtimeFor(NegatableFlagCommand.class).executeCommand("flags --no-enabled");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(negatedFlag).isFalse();
    }

    @Test
    void optionListPopulatesConcreteCollectionField() throws Exception {
        concreteItems = null;

        CommandResult result = runtimeFor(ConcreteListCommand.class).executeCommand("list --item=one,two");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(concreteItems)
                .isInstanceOf(LinkedList.class)
                .containsExactly("one", "two");
    }

    @Test
    void inputRedirectionInjectsPipelineResourceIntoArgumentField(@TempDir Path tempDir) throws Exception {
        redirectedInput = null;
        Path source = tempDir.resolve("pipeline-input.txt");
        Files.writeString(source, "from redirection", StandardCharsets.UTF_8);

        CommandResult result = runtimeFor(EnumSet.allOf(OperatorType.class), ResourceArgumentCommand.class)
                .executeCommand("resource < " + source.toAbsolutePath());

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(redirectedInput).isEqualTo("from redirection");
    }

    @SafeVarargs
    private static CommandRuntime<CommandInvocation> runtimeFor(Class<? extends Command>... commands) throws Exception {
        return runtimeFor(EnumSet.noneOf(OperatorType.class), commands);
    }

    @SafeVarargs
    private static CommandRuntime<CommandInvocation> runtimeFor(
            EnumSet<OperatorType> operators,
            Class<? extends Command>... commands) throws Exception {
        CommandRegistry<CommandInvocation> registry = AeshCommandRegistryBuilder.<CommandInvocation>builder()
                .commands(commands)
                .create();
        return AeshCommandRuntimeBuilder.<CommandInvocation>builder()
                .commandRegistry(registry)
                .operators(operators)
                .build();
    }

    @CommandDefinition(name = "scalar", description = "Receives a scalar option on a non-public command")
    private static class PrivateScalarCommand implements Command<CommandInvocation> {
        @Option(name = "message")
        private String message;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            scalarValues = new ScalarValues(message);
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "groups", description = "Receives option groups")
    public static class GroupOptionsCommand implements Command<CommandInvocation> {
        @OptionGroup(shortName = 'D')
        private Map<String, Integer> defines;

        @OptionGroup(shortName = 'P')
        private LinkedHashMap<String, Integer> priorities;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            groupValues = new GroupValues(defines, priorities);
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "flags", description = "Receives a negated boolean option")
    public static class NegatableFlagCommand implements Command<CommandInvocation> {
        @Option(name = "enabled", hasValue = false, negatable = true)
        private boolean enabled = true;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            negatedFlag = enabled;
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "list", description = "Receives a concrete option list")
    public static class ConcreteListCommand implements Command<CommandInvocation> {
        @OptionList(name = "item")
        private LinkedList<String> items;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            concreteItems = items;
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "resource", description = "Receives redirected input as a pipeline resource")
    public static class ResourceArgumentCommand implements Command<CommandInvocation> {
        @Argument
        private PipelineResource input;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException {
            try (InputStream inputStream = input.read()) {
                redirectedInput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return CommandResult.SUCCESS;
            } catch (IOException exception) {
                throw new CommandException(exception);
            }
        }
    }

    private record ScalarValues(String message) {
    }

    private record GroupValues(
            Map<String, Integer> defines,
            LinkedHashMap<String, Integer> priorities) {
    }
}
