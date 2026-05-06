/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh.readlineconsole;

import static org.assertj.core.api.Assertions.assertThat;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.aesh.command.settings.Settings;
import org.aesh.command.settings.SettingsBuilder;
import org.aesh.command.validator.ValidatorInvocation;
import org.aesh.console.ReadlineConsole;
import org.junit.jupiter.api.Test;

public class ReadlineConsoleTest {
    private static final String SCANNED_PACKAGE = "org_aesh.aesh.readlineconsole";

    @Test
    void constructorScansConfiguredPackagesAndRegistersAnnotatedCommands() {
        Settings<CommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation,
                OptionActivator, CommandActivator> settings = SettingsBuilder
                        .<CommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation,
                                OptionActivator, CommandActivator>builder()
                        .setScanForCommandPackages(SCANNED_PACKAGE)
                        .enableAlias(false)
                        .enableExport(false)
                        .build();

        ReadlineConsole console = new ReadlineConsole(settings);

        assertThat(console.helpInfo("scan-command"))
                .contains("Registered through package scanning")
                .contains("--message");
    }

    @CommandDefinition(name = "scan-command", description = "Registered through package scanning")
    public static final class ScannedCommand implements Command<CommandInvocation> {
        @Option(description = "Message to print")
        private String message;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            commandInvocation.println(message == null ? "scanned" : message);
            return CommandResult.SUCCESS;
        }
    }
}
