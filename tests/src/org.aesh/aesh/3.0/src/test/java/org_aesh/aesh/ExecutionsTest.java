/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.activator.AeshCommandActivatorProvider;
import org.aesh.command.impl.activator.AeshOptionActivatorProvider;
import org.aesh.command.impl.completer.AeshCompleterInvocationProvider;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.context.CommandContext;
import org.aesh.command.impl.converter.AeshConverterInvocationProvider;
import org.aesh.command.impl.invocation.AeshInvocationProviders;
import org.aesh.command.impl.invocation.DefaultCommandInvocation;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.impl.validator.AeshValidatorInvocationProvider;
import org.aesh.command.invocation.CommandInvocationBuilder;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.option.Option;
import org.aesh.command.option.ParentCommand;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.command.shell.Shell;
import org.aesh.parser.LineParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExecutionsTest {
    private static ParentSessionCommand capturedParentCommand;
    private static String capturedChildName;
    private static String capturedParentTenant;

    @BeforeEach
    void resetCapturedExecution() {
        capturedParentCommand = null;
        capturedChildName = null;
        capturedParentTenant = null;
    }

    @Test
    void executeInjectsParentCommandFieldsWhenInvocationIsInSubCommandMode() throws Exception {
        ParentSessionCommand parentCommand = new ParentSessionCommand();
        AeshCommandContainerBuilder<DefaultCommandInvocation> containerBuilder = new AeshCommandContainerBuilder<>();

        try (CommandContainer<DefaultCommandInvocation> parentContainer = containerBuilder.create(parentCommand)) {
            parentContainer.addLine(new LineParser().parseLine("session --tenant production"));
            parentContainer.parseAndPopulate(invocationProviders(), null);

            CommandContext commandContext = new CommandContext("aesh> ");
            commandContext.push(parentContainer.getParser(), parentCommand);
            CommandRuntime<DefaultCommandInvocation> runtime = runtimeForChild(commandContext);

            CommandResult result = runtime.executeCommand("deploy --name api-service");

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(capturedParentCommand).isSameAs(parentCommand);
            assertThat(capturedParentTenant).isEqualTo("production");
            assertThat(capturedChildName).isEqualTo("api-service");
        }
    }

    private static CommandRuntime<DefaultCommandInvocation> runtimeForChild(CommandContext commandContext)
            throws Exception {
        CommandRegistry<DefaultCommandInvocation> registry = AeshCommandRegistryBuilder
                .<DefaultCommandInvocation>builder()
                .command(ChildDeployCommand.class)
                .create();
        return AeshCommandRuntimeBuilder.<DefaultCommandInvocation>builder()
                .commandRegistry(registry)
                .commandInvocationBuilder(new ContextAwareInvocationBuilder(commandContext))
                .build();
    }

    private static InvocationProviders invocationProviders() {
        return new AeshInvocationProviders(
                new AeshConverterInvocationProvider(),
                new AeshCompleterInvocationProvider(),
                new AeshValidatorInvocationProvider(),
                new AeshOptionActivatorProvider(),
                new AeshCommandActivatorProvider());
    }

    @CommandDefinition(name = "session", description = "Parent command for an interactive session")
    public static class ParentSessionCommand implements Command<DefaultCommandInvocation> {
        @Option(name = "tenant", required = true)
        private String tenant;

        @Override
        public CommandResult execute(DefaultCommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    public static class ChildDeployBaseCommand implements Command<DefaultCommandInvocation> {
        @ParentCommand
        private ParentSessionCommand parentCommand;

        @Override
        public CommandResult execute(DefaultCommandInvocation commandInvocation) {
            capturedParentCommand = parentCommand;
            capturedParentTenant = parentCommand.tenant;
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "deploy", description = "Child command using its parent command")
    public static class ChildDeployCommand extends ChildDeployBaseCommand {
        @Option(name = "name", required = true)
        private String name;

        @Override
        public CommandResult execute(DefaultCommandInvocation commandInvocation) {
            capturedChildName = name;
            return super.execute(commandInvocation);
        }
    }

    private static final class ContextAwareInvocationBuilder
            implements CommandInvocationBuilder<DefaultCommandInvocation> {
        private final CommandContext commandContext;

        private ContextAwareInvocationBuilder(CommandContext commandContext) {
            this.commandContext = commandContext;
        }

        @Override
        public DefaultCommandInvocation build(CommandRuntime<DefaultCommandInvocation> runtime,
                CommandInvocationConfiguration configuration,
                CommandContainer<DefaultCommandInvocation> commandContainer) {
            return new ContextAwareDefaultCommandInvocation(runtime, configuration, commandContainer, commandContext);
        }
    }

    private static final class ContextAwareDefaultCommandInvocation extends DefaultCommandInvocation {
        private final CommandContext commandContext;

        private ContextAwareDefaultCommandInvocation(CommandRuntime<DefaultCommandInvocation> runtime,
                CommandInvocationConfiguration configuration,
                CommandContainer<DefaultCommandInvocation> commandContainer,
                CommandContext commandContext) {
            super(runtime, configuration, commandContainer, (Shell) null);
            this.commandContext = commandContext;
        }

        @Override
        public CommandContext getCommandContext() {
            return commandContext;
        }
    }
}
