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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE partial-record I/O to upload documents in chunks, serve byte ranges, and patch headers.
@Timeout(60)
public class DocumentByteRangesTest {
    @TempDir
    Path home;

    @Test
    public void uploadsAndPatchesADocumentWithoutReplacingItsBody() throws DatabaseException {
        String header = "status=DRAFT\ncontent-type=text/plain\n\n";
        String body = "Coffee shipment: 24 cartons, destination warehouse north.\n".repeat(512);
        byte[] uploaded = (header + body).getBytes(StandardCharsets.UTF_8);
        byte[] published = (header.replace("DRAFT", "FINAL") + body).getBytes(StandardCharsets.UTF_8);
        String document = "shipping-manifest.txt";

        try (DocumentStore store = new DocumentStore(home, true)) {
            store.upload(document, uploaded, 4096);
            assertArrayEquals(header.getBytes(StandardCharsets.UTF_8), store.range(document, 0, header.length()));
            // A download range crosses the boundary between two uploaded chunks.
            assertArrayEquals(Arrays.copyOfRange(uploaded, 4080, 4144), store.range(document, 4080, 64));

            store.replaceRange(document, "status=".length(), 5, "FINAL".getBytes(StandardCharsets.UTF_8));
            assertArrayEquals("status=FINAL\n".getBytes(StandardCharsets.UTF_8), store.range(document, 0, 13));
            assertArrayEquals(body.getBytes(StandardCharsets.UTF_8),
                    store.range(document, header.length(), uploaded.length - header.length()));
            // The final download range is clipped to the stored document's end.
            assertArrayEquals(Arrays.copyOfRange(published, published.length - 17, published.length),
                    store.range(document, published.length - 17, 100));
        }

        try (DocumentStore store = new DocumentStore(home, false)) {
            assertArrayEquals(published, store.document(document));
            assertArrayEquals(Arrays.copyOfRange(published, 4080, 4144), store.range(document, 4080, 64));
        }
    }

    private static final class DocumentStore implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private DocumentStore(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            try {
                database = environment.openDatabase(null, "document-content", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void upload(String name, byte[] content, int chunkSize) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS,
                    database.putNoOverwrite(null, key(name), new DatabaseEntry(new byte[0])));
            for (int offset = 0; offset < content.length; offset += chunkSize) {
                byte[] chunk = Arrays.copyOfRange(content, offset, Math.min(offset + chunkSize, content.length));
                // Replacing zero bytes at the current end appends this chunk to the same record.
                replaceRange(name, offset, 0, chunk);
            }
        }

        private void replaceRange(String name, int offset, int length, byte[] replacement) throws DatabaseException {
            DatabaseEntry data = new DatabaseEntry(replacement);
            data.setPartial(offset, length, true);
            assertEquals(OperationStatus.SUCCESS, database.put(null, key(name), data));
        }

        private byte[] range(String name, int offset, int length) throws DatabaseException {
            DatabaseEntry data = new DatabaseEntry();
            data.setPartial(offset, length, true);
            return read(name, data);
        }

        private byte[] document(String name) throws DatabaseException {
            return read(name, new DatabaseEntry());
        }

        private byte[] read(String name, DatabaseEntry data) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, database.get(null, key(name), data, LockMode.DEFAULT));
            return Arrays.copyOfRange(data.getData(), data.getOffset(), data.getOffset() + data.getSize());
        }

        private static DatabaseEntry key(String name) {
            DatabaseEntry key = new DatabaseEntry();
            StringBinding.stringToEntry(name, key);
            return key;
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
