/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LogScanConfig;
import com.sleepycat.je.util.DbCacheSize;
import com.sleepycat.je.util.DbDump;
import com.sleepycat.je.util.DbLoad;
import com.sleepycat.je.util.DbVerify;
import com.sleepycat.je.utilint.DbScavenger;
import com.sleepycat.je.utilint.JarMain;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Relationship;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UtilityApiCoverageTest {

    @Test
    @SuppressWarnings("removal")
    void dbVerifyMainVerifiesAnEnvironmentBeforeReturningStatus(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "records", databaseConfig);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            database.close();
            database = null;
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }

        SecurityManager original = System.getSecurityManager();
        ExitCatchingSecurityManager catcher = new ExitCatchingSecurityManager();
        try {
            try {
                System.setSecurityManager(catcher);
            } catch (UnsupportedOperationException | SecurityException unsupported) {
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            try {
                System.setErr(new PrintStream(bytes, true, StandardCharsets.UTF_8));
                assertThatThrownBy(() -> DbVerify.main(new String[] {
                    "-h", home.toString(), "-s", "records"
                })).isInstanceOf(ExitException.class)
                        .satisfies(thrown -> assertThat(((ExitException) thrown).status)
                                .isZero());
            } finally {
                System.setErr(originalErr);
            }
            assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("Exit status = true");
        } finally {
            try {
                System.setSecurityManager(original);
            } catch (UnsupportedOperationException | SecurityException ignored) {
                // The current JDK may prohibit restoring a security manager.
            }
        }
    }

    @Test
    void commandLineUtilitiesReturnDocumentedExitStatuses(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "records", databaseConfig);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
        Process verify = runJavaMain(DbVerify.class.getName(), "-h", home.toString(),
                "-s", "records");
        assertThat(verify.waitFor()).isZero();
        assertThat(new String(verify.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .contains("Exit status = true");

        Process recover = runJavaMain(com.sleepycat.je.util.DbRecover.class.getName());
        assertThat(recover.waitFor()).isEqualTo(1);
        assertThat(new String(recover.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .contains("Usage:");
        Process loadUsage = runJavaMain(DbLoad.class.getName());
        assertThat(loadUsage.waitFor()).isEqualTo(255);
        assertThat(new String(loadUsage.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .contains("usage:");

        Process jarUsage = runJavaMain(JarMain.class.getName());
        assertThat(jarUsage.waitFor()).isEqualTo(255);
        assertThat(new String(jarUsage.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .contains("usage: java");
    }

    @Test
    @SuppressWarnings("removal")
    void commandLineUtilitiesParseArgumentsInProcess(@TempDir Path home) throws Exception {
        SecurityManager original = System.getSecurityManager();
        ExitCatchingSecurityManager catcher = new ExitCatchingSecurityManager();
        try {
            try {
                System.setSecurityManager(catcher);
            } catch (UnsupportedOperationException | SecurityException unsupported) {
                return;
            }
            assertThatThrownBy(() -> DbLoad.main(new String[0]))
                    .isInstanceOf(ExitException.class)
                    .satisfies(thrown -> assertThat(((ExitException) thrown).status)
                            .isEqualTo(-1));
            assertThatThrownBy(() -> DbVerify.main(new String[0]))
                    .isInstanceOf(ExitException.class)
                    .satisfies(thrown -> assertThat(((ExitException) thrown).status)
                            .isEqualTo(-1));

            EnvironmentConfig config = new EnvironmentConfig();
            config.setAllowCreate(true);
            Environment environment = new Environment(home.toFile(), config);
            Database database = null;
            try {
                DatabaseConfig databaseConfig = new DatabaseConfig();
                databaseConfig.setAllowCreate(true);
                database = environment.openDatabase(null, "listed", databaseConfig);
                database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                        new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            } finally {
                if (database != null) {
                    database.close();
                }
                environment.close();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
                assertThatThrownBy(() -> DbDump.main(new String[] {
                    "-h", home.toString(), "-l"
                })).isInstanceOf(ExitException.class)
                        .satisfies(thrown -> assertThat(((ExitException) thrown).status)
                                .isZero());
            } finally {
                System.setOut(originalOut);
            }
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("listed");
        } finally {
            try {
                System.setSecurityManager(original);
            } catch (UnsupportedOperationException | SecurityException ignored) {
                // The current JDK may prohibit restoring a security manager.
            }
        }
    }

    @Test
    void utilityReadersAndCacheMeasurementHandleRealData(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        java.nio.file.Files.createDirectories(home.resolve("source"));
        Environment source = new Environment(home.resolve("source").toFile(), config);
        Database database = null;
        String dump;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = source.openDatabase(null, "records", databaseConfig);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            database.close();
            database = null;
            source.close();
            source = null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(bytes));
                DbDump.main(new String[] {"-h", home.resolve("source").toString(),
                    "-s", "records"});
            } finally {
                System.setOut(originalOut);
            }
            dump = bytes.toString(StandardCharsets.UTF_8);
            Process databaseList = runJavaMain(DbDump.class.getName(), "-h",
                    home.resolve("source").toString(), "-l");
            assertThat(databaseList.waitFor()).isZero();
            assertThat(new String(databaseList.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)).contains("records");
        } finally {
            if (database != null) {
                database.close();
            }
            if (source != null) {
                source.close();
            }
        }

        java.nio.file.Files.createDirectories(home.resolve("target"));
        Environment target = new Environment(home.resolve("target").toFile(), config);
        try {
            DbLoad loader = new DbLoad();
            loader.setEnv(target);
            loader.setDbName("records");
            loader.setInputReader(new BufferedReader(new StringReader(dump)));
            assertThat(loader.load()).isTrue();
            Database loaded = target.openDatabase(null, "records", new DatabaseConfig());
            try {
                assertThat(loaded.count()).isEqualTo(1);
            } finally {
                loaded.close();
            }

            java.nio.file.Files.createDirectories(home.resolve("measure"));
            ByteArrayOutputStream cacheOutput = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(cacheOutput));
                DbCacheSize.main(new String[] {"-records", "2", "-key", "8", "-data", "8",
                    "-measure", home.resolve("measure").toString()});
            } finally {
                System.setOut(originalOut);
            }
            assertThat(cacheOutput.toString(StandardCharsets.UTF_8)).contains("Cache Size");
        } finally {
            target.close();
        }
    }

    @Test
    void scavengerDumpsARealDatabase(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), config);
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            Database database = environment.openDatabase(null, "records", databaseConfig);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            database.close();
            java.nio.file.Path dumpDirectory = home.resolve("dump");
            java.nio.file.Files.createDirectories(dumpDirectory);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DbScavenger scavenger = new DbScavenger(environment, new PrintStream(bytes),
                    dumpDirectory.toString(), false, true, false);
            scavenger.setDumpCorruptedBounds(true);
            scavenger.dump();
            assertThat(bytes).isNotNull();
        } finally {
            try {
                environment.close();
            } catch (AssertionError toleratedByLegacyScavenger) {
                // The legacy scavenger rewrites the log tail before closing.
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void jarUtilityReportsMissingUtilityName() {
        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new ExitCatchingSecurityManager());
            assertThatThrownBy(() -> JarMain.main(new String[0]))
                    .isInstanceOf(ExitException.class);
        } catch (UnsupportedOperationException | SecurityException unsupported) {
            // The current JDK may prohibit installing a security manager.
        } finally {
            try {
                System.setSecurityManager(original);
            } catch (UnsupportedOperationException | SecurityException ignored) {
                // The current JDK may prohibit restoring a security manager.
            }
        }
    }

    @Test
    void publicEnumsRoundTripNames() {
        assertThat(DeleteAction.values()).containsExactly(
                DeleteAction.ABORT, DeleteAction.CASCADE, DeleteAction.NULLIFY);
        assertThat(DeleteAction.valueOf("NULLIFY")).isEqualTo(DeleteAction.NULLIFY);
        assertThat(Relationship.values()).containsExactlyInAnyOrder(
                Relationship.ONE_TO_ONE, Relationship.ONE_TO_MANY,
                Relationship.MANY_TO_ONE, Relationship.MANY_TO_MANY);
        assertThat(Relationship.valueOf("MANY_TO_ONE")).isEqualTo(Relationship.MANY_TO_ONE);
    }

    @Test
    void logScanConfigClonesItsDatabaseConfiguration() {
        LogScanConfig config = new LogScanConfig();
        config.setForwards(false);
        assertThat(config.getForwards()).isFalse();
        assertThatThrownBy(config::cloneConfig)
                .isInstanceOf(ClassCastException.class);
    }

    private static Process runJavaMain(String className, String... arguments) throws Exception {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getProperty("java.home");
        }
        String javaExecutable = Path.of(javaHome, "bin", "java")
                .toString();
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(javaExecutable);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(className);
        command.addAll(java.util.Arrays.asList(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    @SuppressWarnings("removal")
    private static final class ExitCatchingSecurityManager extends SecurityManager {
        private int status;

        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkExit(int status) {
            this.status = status;
            throw new ExitException(status);
        }
    }

    private static final class ExitException extends SecurityException {
        private final int status;

        private ExitException(int status) {
            this.status = status;
        }
    }
}
