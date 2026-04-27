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

import picocli.CommandLine.Model.CommandSpec;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerUsageMessageSpecAnonymous1Test {
    private static final String CHILD_PROCESS_ARGUMENT = "usage-width-child";
    private static final Duration CHILD_PROCESS_TIMEOUT = Duration.ofSeconds(20);

    @Test
    void autoUsageWidthDetectsTerminalInPseudoTerminalProcess() throws Exception {
        ProcessResult result = runChildProcessInPseudoTerminal();

        assertThat(result.output()).contains("usage-width-child-width=");
        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    public static void main(String[] args) {
        if (args.length == 1 && CHILD_PROCESS_ARGUMENT.equals(args[0])) {
            runUsageWidthChildProcess();
        }
    }

    private static void runUsageWidthChildProcess() {
        String previousUsageWidth = System.getProperty("picocli.usage.width");
        System.setProperty("picocli.usage.width", "AUTO");
        try {
            CommandSpec spec = CommandSpec.create().name("auto-width");
            spec.usageMessage().autoWidth(true);

            int width = spec.usageMessage().width();

            if (width < 55) {
                throw new IllegalStateException("Usage width was below picocli's minimum: " + width);
            }
            System.out.println("usage-width-child-width=" + width);
        } finally {
            restoreUsageWidth(previousUsageWidth);
        }
    }

    private static ProcessResult runChildProcessInPseudoTerminal() throws IOException, InterruptedException {
        Path javaExecutable = Paths.get(System.getProperty("java.home"), "bin", javaExecutableName());
        Path scriptExecutable = Paths.get("/usr/bin/script");
        Path typescript = Files.createTempFile("picocli-usage-width-pty", ".log");
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
        command.add(CommandLineInnerModelInnerUsageMessageSpecAnonymous1Test.class.getName());
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

    private static void restoreUsageWidth(String previousUsageWidth) {
        if (previousUsageWidth == null) {
            System.clearProperty("picocli.usage.width");
        } else {
            System.setProperty("picocli.usage.width", previousUsageWidth);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
