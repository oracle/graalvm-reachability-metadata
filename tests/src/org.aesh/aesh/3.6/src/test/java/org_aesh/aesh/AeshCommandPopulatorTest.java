/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.Executor;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.context.CommandContext;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationBuilder;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.ParentCommand;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.command.shell.Shell;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.Prompt;
import org.aesh.terminal.KeyAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AeshCommandPopulatorTest {
    private static PrimitiveValues primitiveValues;
    private static ContextValues contextValues;

    @BeforeEach
    void resetCapturedValues() {
        primitiveValues = null;
        contextValues = null;
    }

    @Test
    void missingPrimitiveOptionsAreResetBeforeCommandExecution() throws Exception {
        CommandRuntime<TestInvocation> runtime = runtimeFor(PrimitiveResetCommand.class);

        CommandResult result = runtime.executeCommand("primitive-reset");

        assertThat(result).isEqualTo(CommandResult.SUCCESS);
        assertThat(primitiveValues.booleanValue()).isFalse();
        assertThat(primitiveValues.wrapperBooleanValue()).isFalse();
        assertThat(primitiveValues.intValue()).isZero();
        assertThat(primitiveValues.charValue()).isEqualTo('\u0000');
        assertThat(primitiveValues.longValue()).isZero();
        assertThat(primitiveValues.floatValue()).isZero();
        assertThat(primitiveValues.doubleValue()).isZero();
    }

    @Test
    void contextModeInjectsParentCommandAndInheritedValuesIntoChildCommand() throws Exception {
        CommandContext commandContext = new CommandContext("aesh> ");
        CommandRuntime<TestInvocation> runtime = runtimeFor(
                commandContext,
                WorkspaceCommand.class,
                ContextChildCommand.class);

        assertThat(runtime.executeCommand("workspace --profile production parent-target"))
                .isEqualTo(CommandResult.SUCCESS);
        assertThat(commandContext.isInSubCommandMode()).isTrue();

        assertThat(runtime.executeCommand("context-child"))
                .isEqualTo(CommandResult.SUCCESS);

        assertThat(contextValues.profile()).isEqualTo("production");
        assertThat(contextValues.target()).isEqualTo("parent-target");
        assertThat(contextValues.parentProfile()).isEqualTo("production");
        assertThat(contextValues.parentTarget()).isEqualTo("parent-target");
    }

    @SafeVarargs
    private static CommandRuntime<TestInvocation> runtimeFor(Class<? extends Command>... commands) throws Exception {
        return runtimeFor(new CommandContext("aesh> "), commands);
    }

    @SafeVarargs
    private static CommandRuntime<TestInvocation> runtimeFor(
            CommandContext commandContext,
            Class<? extends Command>... commands) throws Exception {
        CommandRegistry<TestInvocation> registry = AeshCommandRegistryBuilder.<TestInvocation>builder()
                .commands(commands)
                .create();
        return AeshCommandRuntimeBuilder.<TestInvocation>builder()
                .commandRegistry(registry)
                .commandInvocationBuilder(new TestInvocationBuilder(commandContext))
                .build();
    }

    @CommandDefinition(name = "primitive-reset", description = "Captures reset primitive option values")
    public static class PrimitiveResetCommand implements Command<TestInvocation> {
        @Option(name = "boolean-value", shortName = 'b')
        private boolean booleanValue = true;

        @Option(name = "wrapper-boolean-value", shortName = 'w', hasValue = false)
        private Boolean wrapperBooleanValue = Boolean.TRUE;

        @Option(name = "int-value", shortName = 'i')
        private int intValue = 42;

        @Option(name = "char-value", shortName = 'c')
        private char charValue = 'x';

        @Option(name = "long-value", shortName = 'l')
        private long longValue = 99L;

        @Option(name = "float-value", shortName = 'f')
        private float floatValue = 1.5f;

        @Option(name = "double-value", shortName = 'd')
        private double doubleValue = 2.5d;

        @Override
        public CommandResult execute(TestInvocation commandInvocation) {
            primitiveValues = new PrimitiveValues(
                    booleanValue,
                    wrapperBooleanValue,
                    intValue,
                    charValue,
                    longValue,
                    floatValue,
                    doubleValue);
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "workspace", description = "Enters a workspace context")
    public static class WorkspaceCommand implements Command<TestInvocation> {
        @Option(name = "profile", inherited = true)
        private String profile;

        @Argument(inherited = true)
        private String target;

        @Override
        public CommandResult execute(TestInvocation commandInvocation) {
            commandInvocation.enterSubCommandMode(this);
            return CommandResult.SUCCESS;
        }
    }

    public abstract static class ContextChildBase {
        @ParentCommand
        protected WorkspaceCommand parent;
    }

    @CommandDefinition(name = "context-child", description = "Reads inherited context values")
    public static class ContextChildCommand extends ContextChildBase implements Command<TestInvocation> {
        @Option(name = "profile")
        private String profile;

        @Argument
        private String target;

        @Override
        public CommandResult execute(TestInvocation commandInvocation) {
            contextValues = new ContextValues(profile, target, parent.profile, parent.target);
            return CommandResult.SUCCESS;
        }
    }

    public static class TestInvocationBuilder implements CommandInvocationBuilder<TestInvocation> {
        private final CommandContext commandContext;

        TestInvocationBuilder(CommandContext commandContext) {
            this.commandContext = commandContext;
        }

        @Override
        public TestInvocation build(
                CommandRuntime<TestInvocation> runtime,
                CommandInvocationConfiguration configuration,
                CommandContainer<TestInvocation> commandContainer) {
            return new TestInvocation(runtime, configuration, commandContainer, commandContext);
        }
    }

    public static class TestInvocation implements CommandInvocation {
        private final CommandRuntime<TestInvocation> runtime;
        private final CommandInvocationConfiguration configuration;
        private final CommandContainer<TestInvocation> commandContainer;
        private final CommandContext commandContext;

        TestInvocation(
                CommandRuntime<TestInvocation> runtime,
                CommandInvocationConfiguration configuration,
                CommandContainer<TestInvocation> commandContainer,
                CommandContext commandContext) {
            this.runtime = runtime;
            this.configuration = configuration;
            this.commandContainer = commandContainer;
            this.commandContext = commandContext;
        }

        @Override
        public Shell getShell() {
            return null;
        }

        @Override
        public void setPrompt(Prompt prompt) {
        }

        @Override
        public Prompt getPrompt() {
            return new Prompt(commandContext.buildPrompt(true));
        }

        @Override
        public String getHelpInfo(String commandName) {
            return runtime.commandInfo(commandName);
        }

        @Override
        public String getHelpInfo() {
            return commandContainer.getParser().parsedCommand().printHelp();
        }

        @Override
        public void stop() {
        }

        @Override
        public CommandInvocationConfiguration getConfiguration() {
            return configuration;
        }

        @Override
        public KeyAction input() {
            return null;
        }

        @Override
        public KeyAction input(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public String inputLine() {
            return null;
        }

        @Override
        public String inputLine(Prompt prompt) {
            return null;
        }

        @Override
        public void executeCommand(String input) throws CommandNotFoundException,
                CommandLineParserException,
                OptionValidatorException,
                CommandValidatorException,
                CommandException,
                InterruptedException,
                IOException {
            runtime.executeCommand(input);
        }

        @Override
        public Executor<? extends CommandInvocation> buildExecutor(String line) throws CommandNotFoundException,
                CommandLineParserException,
                OptionValidatorException,
                CommandValidatorException,
                IOException {
            return runtime.buildExecutor(line);
        }

        @Override
        public void print(String msg, boolean paging) {
        }

        @Override
        public void println(String msg, boolean paging) {
        }

        @Override
        public CommandContext getCommandContext() {
            return commandContext;
        }

        @Override
        public boolean enterSubCommandMode(Command<?> command) {
            commandContext.push(commandContainer.getParser(), command);
            return true;
        }

        @Override
        public boolean exitSubCommandMode() {
            if (!commandContext.isInSubCommandMode()) {
                return false;
            }
            commandContext.pop();
            return true;
        }
    }

    private record PrimitiveValues(
            boolean booleanValue,
            Boolean wrapperBooleanValue,
            int intValue,
            char charValue,
            long longValue,
            float floatValue,
            double doubleValue) {
    }

    private record ContextValues(
            String profile,
            String target,
            String parentProfile,
            String parentTarget) {
    }
}
