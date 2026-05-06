/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.activator.AeshCommandActivatorProvider;
import org.aesh.command.impl.activator.AeshOptionActivatorProvider;
import org.aesh.command.impl.completer.AeshCompleterInvocationProvider;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.context.CommandContext;
import org.aesh.command.impl.converter.AeshConverterInvocationProvider;
import org.aesh.command.impl.invocation.AeshInvocationProviders;
import org.aesh.command.impl.validator.AeshValidatorInvocationProvider;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.ParentCommand;
import org.aesh.parser.LineParser;
import org.junit.jupiter.api.Test;

public class AeshCommandPopulatorTest {
    @Test
    void resetFieldClearsPrimitiveAndBooleanWrapperOptionsThatWereNotParsed() throws Exception {
        ResettablePrimitiveCommand command = new ResettablePrimitiveCommand();

        parseAndPopulate(command, "resettable");

        assertThat(command.intValue).isZero();
        assertThat(command.charValue).isEqualTo('\u0000');
        assertThat(command.longValue).isZero();
        assertThat(command.floatValue).isZero();
        assertThat(command.doubleValue).isZero();
        assertThat(command.enabled).isFalse();
    }

    @Test
    void resetFieldAttemptsToClearShortOptionsThatWereNotParsed() {
        assertThatThrownBy(() -> parseAndPopulate(new ShortPrimitiveCommand(), "short-primitive"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetFieldAttemptsToClearByteOptionsThatWereNotParsed() {
        assertThatThrownBy(() -> parseAndPopulate(new BytePrimitiveCommand(), "byte-primitive"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextAwarePopulationInjectsParentCommandsAndInheritedValues() throws Exception {
        ParentModeCommand parentCommand = new ParentModeCommand();
        InheritedChildCommand childCommand = new InheritedChildCommand();
        AeshCommandContainerBuilder<CommandInvocation> builder = new AeshCommandContainerBuilder<>();
        InvocationProviders invocationProviders = invocationProviders();

        try (CommandContainer<CommandInvocation> parentContainer = builder.create(parentCommand);
                CommandContainer<CommandInvocation> childContainer = builder.create(childCommand)) {
            parentContainer.addLine(new LineParser().parseLine("parent-mode --environment production artifact-a"));
            parentContainer.parseAndPopulate(invocationProviders, null);

            CommandContext commandContext = new CommandContext("aesh> ");
            commandContext.push(parentContainer.getParser(), parentCommand);

            childContainer.addLine(new LineParser().parseLine("inherited-child"));
            childContainer.parseAndPopulate(invocationProviders, null, commandContext);
        }

        InheritedChildBaseCommand childBase = childCommand;
        assertThat(childBase.parentCommand).isSameAs(parentCommand);
        assertThat(childCommand.environment).isEqualTo("production");
        assertThat(childBase.artifactName).isEqualTo("artifact-a");
    }

    private static void parseAndPopulate(Command<CommandInvocation> command, String line)
            throws Exception {
        AeshCommandContainerBuilder.parseAndPopulate(command, line);
    }

    private static InvocationProviders invocationProviders() {
        return new AeshInvocationProviders(
                new AeshConverterInvocationProvider(),
                new AeshCompleterInvocationProvider(),
                new AeshValidatorInvocationProvider(),
                new AeshOptionActivatorProvider(),
                new AeshCommandActivatorProvider());
    }

    @CommandDefinition(name = "resettable", description = "Exercises resettable fields")
    public static class ResettablePrimitiveCommand implements Command<CommandInvocation> {
        @Option(name = "int-value")
        private int intValue = 41;

        @Option(name = "char-value")
        private char charValue = 'x';

        @Option(name = "long-value")
        private long longValue = 41L;

        @Option(name = "float-value")
        private float floatValue = 41.0f;

        @Option(name = "double-value")
        private double doubleValue = 41.0d;

        @Option(name = "enabled", hasValue = false)
        private Boolean enabled = Boolean.TRUE;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "short-primitive", description = "Exercises short field reset")
    public static class ShortPrimitiveCommand implements Command<CommandInvocation> {
        @Option(name = "short-value")
        private short shortValue = 41;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "byte-primitive", description = "Exercises byte field reset")
    public static class BytePrimitiveCommand implements Command<CommandInvocation> {
        @Option(name = "byte-value")
        private byte byteValue = 41;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "parent-mode", description = "Parent command with inherited values")
    public static class ParentModeCommand implements Command<CommandInvocation> {
        @Option(name = "environment", inherited = true)
        private String environment;

        @Argument(inherited = true)
        private String artifactName;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    public static class InheritedChildBaseCommand implements Command<CommandInvocation> {
        @ParentCommand
        private ParentModeCommand parentCommand;

        @Argument
        private String artifactName;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }

    @CommandDefinition(name = "inherited-child", description = "Child command receiving inherited values")
    public static class InheritedChildCommand extends InheritedChildBaseCommand {
        @Option(name = "environment")
        private String environment;
    }
}
