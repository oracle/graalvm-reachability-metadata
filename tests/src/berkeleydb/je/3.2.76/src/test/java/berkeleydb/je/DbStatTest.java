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
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.DbStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DbStatTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void compilerGeneratedClassLookupResolvesDbStatClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbStat.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                DbStat.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        String defaultClassName = new String(
                "com.sleepycat.je.util.DbStat".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        String className = System.getProperty("berkeleydb.je.DbStatTest.className", defaultClassName);
        Class<?> resolvedClass = (Class<?>) classLookup.invokeExact(className);

        assertThat(resolvedClass).isEqualTo(DbStat.class);
    }

    @Test
    void mainVersionCommandInitializesUsageWithDbStatClass() throws Exception {
        Optional<String> jacocoAgentArgument = jacocoAgentArgument();
        if (jacocoAgentArgument.isEmpty()) {
            clearDbStatClassCache();
            new DbStat(null, "records");
            return;
        }

        ProcessResult result = invokeMainInForkedJvm(jacocoAgentArgument.get(), "-V");

        assertThat(result.exitCode()).as(result.output()).isEqualTo(0);
        assertThat(result.output()).contains(JEVersion.CURRENT_VERSION.toString());
    }

    @Test
    void statsPrintsStatisticsForExistingDatabase(@TempDir Path tempDir) throws Exception {
        Environment environment = newEnvironment(tempDir);
        Database database = null;
        try {
            database = openDatabase(environment, "records");
            OperationStatus status = database.put(null, entry("alpha"), entry("one"));
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            database.close();
            database = null;

            ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
            PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
            try {
                clearDbStatClassCache();
                DbStat stat = new DbStat(environment, "records");
                assertThat(stat.stats(output)).isTrue();
            } finally {
                output.close();
            }

            assertThat(outputBytes.toString(StandardCharsets.UTF_8))
                    .contains("numLeafNodes=")
                    .contains("numDeletedLeafNodes=")
                    .contains("mainTreeMaxDepth=");
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

    private static Database openDatabase(Environment environment, String name) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        return environment.openDatabase(null, name, config);
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void clearDbStatClassCache() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbStat.class, MethodHandles.lookup());
        VarHandle classCache = lookup.findStaticVarHandle(
                DbStat.class,
                "class$com$sleepycat$je$util$DbStat",
                Class.class);
        classCache.set((Class<?>) null);
    }

    private static Optional<String> jacocoAgentArgument() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .findFirst()
                .map(DbStatTest::forceJacocoAppendMode);
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

    private static ProcessResult invokeMainInForkedJvm(String javaAgentArgument, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add(javaAgentArgument);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(DbStat.class.getName());
        command.addAll(List.of(arguments));

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

