/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiOutput.Enabled;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("org.springframework.boot.ansi.AnsiOutput")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AnsiOutputTest {

    private static final String PSEUDO_TERMINAL_CHILD = AnsiOutputTest.class.getName();

    private Enabled previousEnabled;

    @BeforeEach
    void captureAnsiOutputState() {
        this.previousEnabled = AnsiOutput.getEnabled();
    }

    @AfterEach
    void restoreAnsiOutputState() {
        AnsiOutput.setEnabled(this.previousEnabled);
        AnsiOutput.setConsoleAvailable(null);
    }

    @Test
    @Order(1)
    void detectUsesTheCurrentSystemConsole() {
        Console console = System.console();
        AnsiOutput.setEnabled(Enabled.DETECT);
        AnsiOutput.setConsoleAvailable(null);

        String output = AnsiOutput.toString(AnsiColor.GREEN, "boot");

        if (console == null || isWindows()) {
            assertThat(output).isEqualTo("boot");
        }
        else {
            assertThat(output).isIn("\033[32mboot\033[0;39m", "boot");
        }
        if (PSEUDO_TERMINAL_CHILD.equals(System.getenv("ANSI_OUTPUT_PTY_CHILD"))) {
            assertThat(console).isNotNull();
            System.exit(0);
        }
    }

    @Test
    @Order(2)
    void nativeExecutableCanDetectAnsiWhenLaunchedWithPseudoTerminal() throws Exception {
        Optional<List<String>> command = currentNativeTestCommand();
        if (command.isPresent() && System.getenv("ANSI_OUTPUT_PTY_CHILD") == null
                && isPseudoTerminalLauncherAvailable()) {
            Path xmlOutputDir = Files.createTempDirectory("ansi-output-native-child-");
            List<String> childCommand = withChildXmlOutput(command.get(), xmlOutputDir);
            ProcessBuilder processBuilder = new ProcessBuilder("script", "-q", "-e", "-c", shellCommand(childCommand),
                    "/dev/null");
            processBuilder.environment().put("ANSI_OUTPUT_PTY_CHILD", PSEUDO_TERMINAL_CHILD);
            Process process = processBuilder.redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            boolean finished = process.waitFor(Duration.ofSeconds(55).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            assertThat(finished).isTrue();
            assertThat(process.exitValue()).isZero();
        }
    }

    @Test
    void alwaysEncodesAnsiElements() {
        AnsiOutput.setEnabled(Enabled.ALWAYS);

        String output = AnsiOutput.toString(AnsiColor.GREEN, "boot");

        assertThat(output).isEqualTo("\033[32mboot\033[0;39m");
    }

    @Test
    void neverStripsAnsiElements() {
        AnsiOutput.setEnabled(Enabled.NEVER);

        String output = AnsiOutput.toString(AnsiColor.GREEN, "boot");

        assertThat(output).isEqualTo("boot");
    }

    private static Optional<List<String>> currentNativeTestCommand() {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isEmpty() || isJavaLauncher(command.get())) {
            return Optional.empty();
        }
        List<String> result = new ArrayList<>();
        result.add(command.get());
        ProcessHandle.current()
            .info()
            .arguments()
            .ifPresent((arguments) -> result.addAll(Arrays.asList(arguments)));
        return Optional.of(result);
    }

    private static boolean isJavaLauncher(String command) {
        String name = Path.of(command).getFileName().toString().toLowerCase(Locale.ENGLISH);
        return name.equals("java") || name.equals("java.exe");
    }

    private static boolean isPseudoTerminalLauncherAvailable() {
        try {
            Process process = new ProcessBuilder("script", "--version").redirectErrorStream(true).start();
            return process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)
                    && process.exitValue() == 0;
        }
        catch (IOException ex) {
            return false;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<String> withChildXmlOutput(List<String> command, Path xmlOutputDir) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            String argument = command.get(i);
            if ("--xml-output-dir".equals(argument)) {
                i++;
            }
            else {
                result.add(argument);
            }
        }
        result.add("--xml-output-dir");
        result.add(xmlOutputDir.toString());
        return result;
    }

    private static String shellCommand(List<String> command) {
        StringBuilder result = new StringBuilder();
        for (String argument : command) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append('\'').append(argument.replace("'", "'\\''")).append('\'');
        }
        return result.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }

}
