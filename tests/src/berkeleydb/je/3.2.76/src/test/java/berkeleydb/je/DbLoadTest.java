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
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.DbLoad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DbLoadTest {

    @Test
    void mainLoadsPrintableRecordsFromFile(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("records.dump");
        Files.writeString(input, """
                alpha
                one
                beta
                two
                DATA=END
                """, StandardCharsets.UTF_8);

        DbLoad.main(new String[] {"-h", tempDir.toString(), "-f", input.toString(), "-s", "records", "-T"});

        Environment environment = newEnvironment(tempDir);
        Database database = null;
        try {
            database = openDatabase(environment, "records");
            assertThat(read(database, "alpha")).isEqualTo("one");
            assertThat(read(database, "beta")).isEqualTo("two");
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void loadStoresPrintableRecordsWithPublicApi(@TempDir Path tempDir) throws Exception {
        Environment environment = newEnvironment(tempDir);
        Database database = null;
        try {
            DbLoad loader = new DbLoad();
            loader.setEnv(environment);
            loader.setDbName("records");
            loader.setTextFileMode(true);
            loader.setInputReader(new BufferedReader(new StringReader("""
                    alpha
                    one
                    beta
                    two
                    DATA=END
                    """)));

            assertThat(loader.load()).isTrue();

            database = openDatabase(environment, "records");
            assertThat(read(database, "alpha")).isEqualTo("one");
            assertThat(read(database, "beta")).isEqualTo("two");
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
        return environment.openDatabase(null, name, config);
    }

    private static String read(Database database, String key) throws Exception {
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = database.get(null, entry(key), data, LockMode.DEFAULT);
        assertThat(status).isEqualTo(OperationStatus.SUCCESS);
        return new String(data.getData(), StandardCharsets.UTF_8);
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }
}
