/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.util.DbRunAction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DbRunActionTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void mainWithoutRequiredArgumentsPrintsUsageBeforeExiting() throws Throwable {
        List<String> agentArguments = forwardedAgentArguments();
        if (agentArguments.isEmpty()) {
            ByteArrayOutputStream output = invokeUsageWithClearedClassCache();

            assertUsageMessage(output);
            return;
        }

        ProcessResult result = invokeMainInForkedJvm(agentArguments);

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertUsageMessage(result.output());
    }

    @Test
    void usageMessageResolvesRunActionUtilityCommandClass() throws Throwable {
        ByteArrayOutputStream output = invokeUsageWithClearedClassCache();

        assertUsageMessage(output);
    }

    private static List<String> forwardedAgentArguments() {
        List<String> forwardedArguments = new ArrayList<>();
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) {
                forwardedArguments.add(forceJacocoAppendMode(argument));
            } else if (argument.startsWith("-agentlib:native-image-agent")) {
                forwardedArguments.add(argument);
            }
        }
        return forwardedArguments;
    }

    private static String forceJacocoAppendMode(String javaAgentArgument) {
        if (javaAgentArgument.contains("append=")) {
            return javaAgentArgument.replaceAll("append=[^,]*", "append=true");
        }
        if (javaAgentArgument.contains("=")) {
            return javaAgentArgument + ",append=true";
        }
        return javaAgentArgument + "=append=true";
    }

    private static ProcessResult invokeMainInForkedJvm(List<String> agentArguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.addAll(agentArguments);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(DbRunAction.class.getName());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        byte[] output = process.getInputStream().readAllBytes();
        String outputText = new String(output, StandardCharsets.UTF_8);
        assertThat(finished).as(outputText).isTrue();
        return new ProcessResult(process.exitValue(), outputText);
    }

    private static Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("java"));
    }

    private static String executableName(String name) {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return name + ".exe";
        }
        return name;
    }

    private static ByteArrayOutputStream invokeUsageWithClearedClassCache() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRunAction.class, MethodHandles.lookup());
        MethodHandle usage = lookup.findStatic(DbRunAction.class, "usage", MethodType.methodType(void.class));
        clearDbRunActionClassCache();
        return captureStandardOut(() -> {
            usage.invokeExact();
        });
    }

    private static void clearDbRunActionClassCache() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbRunAction.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                DbRunAction.class,
                "class$com$sleepycat$je$util$DbRunAction",
                Class.class);
        classCache.set((Class<?>) null);
    }

    private static void assertUsageMessage(ByteArrayOutputStream output) {
        assertUsageMessage(output.toString(StandardCharsets.UTF_8));
    }

    private static void assertUsageMessage(String output) {
        assertThat(output)
                .contains("Usage:")
                .contains("com.sleepycat.je.util.DbRunAction")
                .contains("-h <environment home>")
                .contains("-a <batchClean|compress|evict|checkpoint|removeDb|removeDbAndClean|activateCleaner>")
                .contains("-stats (print every 30 seconds)");
    }

    private static ByteArrayOutputStream captureStandardOut(ThrowingRunnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);
            action.run();
            return output;
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        } finally {
            System.setOut(originalOut);
            capture.close();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
