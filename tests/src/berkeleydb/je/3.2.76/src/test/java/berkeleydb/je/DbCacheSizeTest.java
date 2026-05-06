/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.util.DbCacheSize;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DbCacheSizeTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void usageMessageResolvesUtilityCommandClassWhenRequiredArgumentsAreMissing() throws Exception {
        Optional<String> jacocoAgentArgument = jacocoAgentArgument();
        if (jacocoAgentArgument.isPresent()) {
            ProcessResult result = invokeUsageInForkedJvm(jacocoAgentArgument.get());

            assertThat(result.exitCode()).as(result.output()).isEqualTo(2);
            assertThat(result.output())
                    .contains("-key not specified")
                    .contains("usage:")
                    .contains("com.sleepycat.je.util.DbCacheSize")
                    .contains("-records <count>");
            return;
        }

        ByteArrayOutputStream output = invokeMain("-records", "1", "-key", "1");

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Cache Size");
    }

    private static Optional<String> jacocoAgentArgument() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .findFirst()
                .map(DbCacheSizeTest::forceJacocoAppendMode);
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

    private static ProcessResult invokeUsageInForkedJvm(String javaAgentArgument) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add(javaAgentArgument);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(DbCacheSize.class.getName());
        command.add("-records");
        command.add("1");

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(finished).as(new String(output, StandardCharsets.UTF_8)).isTrue();
        return new ProcessResult(process.exitValue(), new String(output, StandardCharsets.UTF_8));
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

    private static ByteArrayOutputStream invokeMain(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);

            DbCacheSize.main(args);
            return output;
        } finally {
            System.setOut(originalOut);
            capture.close();
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
