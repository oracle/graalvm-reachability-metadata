/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.DbDump;
import com.sleepycat.je.util.DbLoad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JE's logical dump/load utilities to migrate a catalog into a separately named database.
@Timeout(60)
public class CatalogDumpLoadTest {
    @TempDir
    Path home;

    @Test
    public void migratesAnExportedCatalogWithoutCopyingEnvironmentLogs() throws DatabaseException, IOException {
        Path sourceHome = Files.createDirectory(home.resolve("source"));
        Path destinationHome = Files.createDirectory(home.resolve("destination"));
        Path exportFile = home.resolve("catalog.dump");
        String coffeeDescription = "Caf\u00e9 beans\nOrigin: Peru\nStorage: pantry\\coffee";
        String teaDescription = "Tea sampler\0green and black";

        try (Catalog source = new Catalog(sourceHome, "catalog", true)) {
            source.put("coffee", coffeeDescription);
            source.put("tea", teaDescription);
            source.put("mug", "Stoneware mug");
            // Printable dumps still escape binary tuple terminators, newlines and backslashes.
            try (PrintStream output = new PrintStream(Files.newOutputStream(exportFile),
                    false, StandardCharsets.US_ASCII)) {
                new DbDump(source.environment, "catalog", output, null, true).dump();
                assertFalse(output.checkError());
            }

            // The migration consumes the exported artifact, not the source's current log files.
            source.put("coffee", "Revised description after export");
            source.put("kettle", "Electric kettle");
            assertEquals(4L, source.database.count());
        }

        EnvironmentConfig destinationConfig = new EnvironmentConfig();
        destinationConfig.setAllowCreate(true);
        destinationConfig.setLockTimeout(10_000_000L);
        Environment destination = new Environment(destinationHome.toFile(), destinationConfig);
        try {
            try (BufferedReader input = Files.newBufferedReader(exportFile, StandardCharsets.US_ASCII)) {
                DbLoad loader = new DbLoad();
                loader.setEnv(destination);
                loader.setDbName("imported-catalog");
                loader.setInputReader(input);
                assertTrue(loader.load());
            }
            assertEquals(List.of("imported-catalog"), destination.getDatabaseNames());
        } finally {
            destination.close();
        }

        // Reopen without creating anything: the imported records must be durable and byte-exact.
        try (Catalog imported = new Catalog(destinationHome, "imported-catalog", false)) {
            assertEquals(3L, imported.database.count());
            assertEquals(coffeeDescription, imported.description("coffee"));
            assertEquals(teaDescription, imported.description("tea"));
            assertEquals("Stoneware mug", imported.description("mug"));
            assertEquals(OperationStatus.NOTFOUND, imported.database.get(
                    null, entry("kettle"), new DatabaseEntry(), LockMode.DEFAULT));
            imported.put("mug", "Stoneware mug - ready for sale");
            assertEquals("Stoneware mug - ready for sale", imported.description("mug"));
        }
        try (Catalog source = new Catalog(sourceHome, "catalog", false)) {
            assertEquals("Revised description after export", source.description("coffee"));
            assertEquals("Electric kettle", source.description("kettle"));
        }
    }

    private static DatabaseEntry entry(String text) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(text, entry);
        return entry;
    }

    private static final class Catalog implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private Catalog(Path directory, String name, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(directory.toFile(), environmentConfig);
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            try {
                database = environment.openDatabase(null, name, databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void put(String sku, String description) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, database.put(null, entry(sku), entry(description)));
        }

        private String description(String sku) throws DatabaseException {
            DatabaseEntry value = new DatabaseEntry();
            assertEquals(OperationStatus.SUCCESS, database.get(null, entry(sku), value, LockMode.DEFAULT));
            return StringBinding.entryToString(value);
        }

        @Override
        public void close() throws DatabaseException {
            try {
                database.close();
            } finally {
                environment.close();
            }
        }
    }
}
