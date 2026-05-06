/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.DbSpace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

public class DbSpaceTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void mainPrintsGrandTotalsForEnvironment(@TempDir Path tempDir) throws Exception {
        populateEnvironment(tempDir);

        Optional<String> jacocoAgentArgument = jacocoAgentArgument();
        if (jacocoAgentArgument.isPresent()) {
            ProcessResult result = invokeMainInForkedJvm(jacocoAgentArgument.get(), tempDir);

            assertThat(result.exitCode()).as(result.output()).isEqualTo(0);
            assertThat(result.output())
                    .contains("File")
                    .contains("Size (KB)")
                    .contains("% Used")
                    .contains("TOTALS");
            return;
        }

        Environment environment = newReadOnlyEnvironment(tempDir);
        try {
            ByteArrayOutputStream outputBytes = printDbSpace(environment);

            assertThat(outputBytes.toString(StandardCharsets.UTF_8))
                    .contains("File")
                    .contains("Size (KB)")
                    .contains("% Used")
                    .contains("TOTALS");
        } finally {
            environment.close();
        }
    }

    @Test
    void printsUtilizationSummaryForEnvironment(@TempDir Path tempDir) throws Exception {
        Environment environment = newEnvironment(tempDir);
        Database database = null;
        try {
            database = openDatabase(environment, "records");
            OperationStatus status = database.put(null, entry("alpha"), entry("one"));
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            database.close();
            database = null;

            ByteArrayOutputStream outputBytes = printDbSpace(environment);

            assertThat(outputBytes.toString(StandardCharsets.UTF_8))
                    .contains("File")
                    .contains("Size (KB)")
                    .contains("% Used")
                    .contains("TOTALS");
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static void populateEnvironment(Path directory) throws Exception {
        Environment environment = newEnvironment(directory);
        Database database = null;
        try {
            database = openDatabase(environment, "records");
            OperationStatus status = database.put(null, entry("alpha"), entry("one"));
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static Environment newEnvironment(Path directory) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        return new Environment(directory.toFile(), config);
    }

    private static Environment newReadOnlyEnvironment(Path directory) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setReadOnly(true);
        return new Environment(directory.toFile(), config);
    }

    private static Database openDatabase(Environment environment, String name) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        return environment.openDatabase(null, name, config);
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteArrayOutputStream printDbSpace(Environment environment) throws Exception {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        try {
            DbSpace dbSpace = new DbSpace(environment, false, false, true);
            dbSpace.print(output);
            return outputBytes;
        } finally {
            output.close();
        }
    }

    private static Optional<String> jacocoAgentArgument() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .findFirst()
                .map(DbSpaceTest::forceJacocoAppendMode);
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

    private static ProcessResult invokeMainInForkedJvm(String javaAgentArgument, Path environmentHome) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add(javaAgentArgument);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(DbSpace.class.getName());
        command.add("-h");
        command.add(environmentHome.toString());
        command.add("-q");

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

    private record ProcessResult(int exitCode, String output) {
    }
}
