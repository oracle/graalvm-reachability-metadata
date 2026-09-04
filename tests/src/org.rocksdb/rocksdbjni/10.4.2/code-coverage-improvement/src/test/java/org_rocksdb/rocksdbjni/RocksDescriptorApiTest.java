/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RocksDescriptorApiTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void descriptorOpenCreatesAUsableColumnFamilyDatabase() throws Exception {
        Path path = Files.createDirectory(tempDir.resolve("descriptor-db"));
        try (Options createOptions = new Options().setCreateIfMissing(true);
             RocksDB ignored = RocksDB.open(createOptions, path.toString())) {
            assertThat(ignored).isNotNull();
        }

        ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
        List<ColumnFamilyDescriptor> descriptors = List.of(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try (RocksDB db = RocksDB.open(path.toString(), descriptors, handles)) {
            assertThat(handles).hasSize(1);
            db.put(handles.get(0), bytes("key"), bytes("value"));
            assertThat(db.get(handles.get(0), bytes("key"))).isEqualTo(bytes("value"));
        } finally {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
            columnFamilyOptions.close();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
