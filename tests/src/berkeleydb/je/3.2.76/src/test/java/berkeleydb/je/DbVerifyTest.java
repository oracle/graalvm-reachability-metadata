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
import com.sleepycat.je.util.DbVerify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DbVerifyTest {

    @Test
    void compilerGeneratedClassLookupResolvesDbVerifyClass() throws Exception {
        Method classLookup = DbVerify.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        String defaultClassName = new String(
                "com.sleepycat.je.util.DbVerify".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        String className = System.getProperty("berkeleydb.je.DbVerifyTest.className", defaultClassName);
        Class<?> resolvedClass = (Class<?>) classLookup.invoke(null, className);

        assertThat(resolvedClass).isEqualTo(DbVerify.class);
    }

    @Test
    void verifyReportsExistingDatabaseAsValid(@TempDir Path tempDir) throws Exception {
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
                DbVerify verifier = new DbVerify(environment, "records", false);
                assertThat(verifier.verify(output)).isTrue();
            } finally {
                output.close();
            }

            assertThat(outputBytes.toString(StandardCharsets.UTF_8))
                    .contains("Verifying database records")
                    .contains("Checking tree for records")
                    .contains("Checking obsolete offsets for records");
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
}
