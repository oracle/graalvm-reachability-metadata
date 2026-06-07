/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh.readline_console;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

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
import org.junit.jupiter.api.io.TempDir;

public class ReadlineConsoleTest {
    private static final String SCANNED_PACKAGE = "org_aesh.aesh.readline_console";
    private static final String SCANNED_RESOURCE_PATH = "org_aesh/aesh/readline_console/";

    @Test
    void consoleSettingsCanDiscoverAnnotatedCommandsByPackageScan(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve(SCANNED_RESOURCE_PATH));
        writeScannedCommandClassFile(packageDirectory);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new SinglePackageResourceClassLoader(
                originalClassLoader,
                packageDirectory.toUri().toURL()));
        try {
            ReadlineConsole console = new ReadlineConsole(settingsForPackageScan());

            assertThat(console.helpInfo("scanned-console-command"))
                    .contains("Command discovered through ReadlineConsole package scanning")
                    .contains("--message");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static void writeScannedCommandClassFile(Path packageDirectory) throws IOException {
        String classFileName = ScannedConsoleCommand.class.getName()
                .substring(SCANNED_PACKAGE.length() + 1)
                + ".class";
        try (InputStream inputStream = ScannedConsoleCommand.class.getResourceAsStream(classFileName)) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, packageDirectory.resolve(classFileName));
        }
    }

    private static Settings<CommandInvocation, ConverterInvocation, CompleterInvocation,
            ValidatorInvocation, OptionActivator, CommandActivator> settingsForPackageScan() {
        return SettingsBuilder
                .<CommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation,
                        OptionActivator, CommandActivator>builder()
                .setScanForCommandPackages(SCANNED_PACKAGE)
                .build();
    }

    private static final class SinglePackageResourceClassLoader extends ClassLoader {
        private final URL packageUrl;

        SinglePackageResourceClassLoader(ClassLoader parent, URL packageUrl) {
            super(parent);
            this.packageUrl = packageUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SCANNED_RESOURCE_PATH.equals(name)) {
                return Collections.enumeration(Collections.singleton(packageUrl));
            }
            return super.getResources(name);
        }
    }

    @CommandDefinition(
            name = "scanned-console-command",
            description = "Command discovered through ReadlineConsole package scanning",
            generateHelp = true)
    public static class ScannedConsoleCommand implements Command<CommandInvocation> {
        @Option(name = "message", description = "Message to print")
        private String message;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) {
            return CommandResult.SUCCESS;
        }
    }
}
