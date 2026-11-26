/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.NativeLibraryLoader;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLibraryLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void repeatedLibraryLoadsKeepAnOpenDatabaseUsable() throws Exception {
        NativeLibraryLoader loader = NativeLibraryLoader.getInstance();
        loader.loadLibrary(tempDir.toString());

        byte[] key = "loader-key".getBytes(StandardCharsets.UTF_8);
        byte[] value = "loader-value".getBytes(StandardCharsets.UTF_8);
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB database = RocksDB.open(options, tempDir.resolve("database").toString())) {
            database.put(key, value);
            loader.loadLibrary(tempDir.toString());
            assertThat(database.get(key)).isEqualTo(value);
        }
    }
}
