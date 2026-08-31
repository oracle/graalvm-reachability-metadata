/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractWriteBatchApiTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @Test
    void writeBatchInterfaceExposesTheBatchForGenericBatchUsers() throws RocksDBException {
        try (WriteBatch batch = new WriteBatch()) {
            batch.put(bytes("key"), bytes("value"));
            AbstractWriteBatch genericBatch = batch;
            WriteBatch exposed = genericBatch.getWriteBatch();

            assertThat(exposed).isSameAs(batch);
            assertThat(exposed.count()).isEqualTo(1);
            assertThat(exposed.hasPut()).isTrue();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
