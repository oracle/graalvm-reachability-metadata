/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.Help.Ansi;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerHelpInnerAnsiTest {
    private static final String CHILD_PROCESS_ARGUMENT = "ansi-child";
    private static final Duration CHILD_PROCESS_TIMEOUT = Duration.ofSeconds(20);

    @Test
    void autoAnsiEvaluatesTerminalWhenTtyModeIsRequested() {
        String previousAnsiMode = System.getProperty("picocli.ansi");
        System.setProperty("picocli.ansi", "tty");
        try {
            boolean enabled = Ansi.AUTO.enabled();
            String renderedText = Ansi.AUTO.string("@|bold terminal text|@");

            assertThat(renderedText).contains("terminal text");
            assertThat(renderedText).doesNotContain("@|bold").doesNotContain("|@");
            assertThat(Ansi.valueOf(enabled).enabled()).isEqualTo(enabled);
        } finally {
            restoreAnsiMode(previousAnsiMode);
        }
    }

    @Test
    void autoAnsiEvaluatesConsoleTerminalInPseudoTerminalProcess() throws Exception {
        ProcessResult result = runChildProcessInPseudoTerminal();

        assertThat(result.output()).contains("ansi-child-enabled=true");
        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    public static void main(String[] args) {
        if (args.length == 1 && CHILD_PROCESS_ARGUMENT.equals(args[0])) {
            runAnsiChildProcess();
        }
    }

    private static void runAnsiChildProcess() {
        String previousAnsiMode = System.getProperty("picocli.ansi");
        System.setProperty("picocli.ansi", "tty");
        try {
            boolean enabled = Ansi.AUTO.enabled();
            String renderedText = Ansi.AUTO.string("@|bold child terminal text|@");

            if (!enabled || !renderedText.contains("\u001B[") || !renderedText.contains("child terminal text")) {
                throw new IllegalStateException("ANSI AUTO did not detect the pseudo terminal");
            }
            System.out.println("ansi-child-enabled=" + enabled);
        } finally {
            restoreAnsiMode(previousAnsiMode);
        }
    }

    private static ProcessResult runChildProcessInPseudoTerminal() throws IOException, InterruptedException {
        Path javaExecutable = Paths.get(System.getProperty("java.home"), "bin", javaExecutableName());
        Path scriptExecutable = Paths.get("/usr/bin/script");
        Path typescript = Files.createTempFile("picocli-ansi-pty", ".log");
        List<String> javaCommand = childJavaCommand(javaExecutable);
        List<String> command = new ArrayList<>();
        command.add(scriptExecutable.toString());
        command.add("-q");
        command.add("-e");
        command.add("-c");
        command.add(toShellCommand(javaCommand));
        command.add(typescript.toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean completed = process.waitFor(CHILD_PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String typescriptOutput = Files.readString(typescript, StandardCharsets.UTF_8);
        Files.deleteIfExists(typescript);
        assertThat(completed).as(output + typescriptOutput).isTrue();
        return new ProcessResult(process.exitValue(), output + typescriptOutput);
    }

    private static List<String> childJavaCommand(Path javaExecutable) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(jacocoAgentArguments());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(CommandLineInnerHelpInnerAnsiTest.class.getName());
        command.add(CHILD_PROCESS_ARGUMENT);
        return command;
    }

    private static List<String> jacocoAgentArguments() {
        List<String> arguments = new ArrayList<>();
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.toLowerCase().contains("jacoco")) {
                arguments.add(argument.replace("append=false", "append=true"));
            }
        }
        return arguments;
    }

    private static String toShellCommand(List<String> command) {
        List<String> quoted = new ArrayList<>();
        for (String argument : command) {
            quoted.add(shellQuote(argument));
        }
        return String.join(" ", quoted);
    }

    private static String shellQuote(String argument) {
        return "'" + argument.replace("'", "'\\''") + "'";
    }

    private static String javaExecutableName() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "java.exe" : "java";
    }

    private static void restoreAnsiMode(String previousAnsiMode) {
        if (previousAnsiMode == null) {
            System.clearProperty("picocli.ansi");
        } else {
            System.setProperty("picocli.ansi", previousAnsiMode);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
