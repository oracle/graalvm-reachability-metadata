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
import com.sleepycat.je.util.DbDump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DbDumpTest {

    @Test
    void compilerGeneratedClassLookupResolvesDbDumpClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DbDump.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                DbDump.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invokeExact("com.sleepycat.je.util.DbDump");

        assertThat(resolvedClass).isEqualTo(DbDump.class);
    }

    @Test
    void dumpPrintsDatabaseHeaderAndEntries(@TempDir Path tempDir) throws Exception {
        Environment environment = newEnvironment(tempDir);
        Database database = null;
        try {
            database = openDatabase(environment, "records");
            OperationStatus status = database.put(null, entry("alpha"), entry("one"));
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            database.close();
            database = null;

            ByteArrayOutputStream dumpBytes = new ByteArrayOutputStream();
            PrintStream dumpOutput = new PrintStream(dumpBytes, true, StandardCharsets.UTF_8);
            try {
                DbDump dump = new DbDump(environment, "records", dumpOutput, null, false);
                dump.dump();
            } finally {
                dumpOutput.close();
            }

            String dump = dumpBytes.toString(StandardCharsets.UTF_8);
            assertThat(dump)
                    .contains("VERSION=3")
                    .contains("format=bytevalue")
                    .contains("type=btree")
                    .contains("dupsort=0")
                    .contains("HEADER=END")
                    .contains(" 616c706861")
                    .contains(" 6f6e65")
                    .contains("DATA=END");
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
