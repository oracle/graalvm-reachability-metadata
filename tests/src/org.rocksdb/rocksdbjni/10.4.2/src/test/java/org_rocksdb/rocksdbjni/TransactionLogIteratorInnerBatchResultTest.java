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
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.TransactionLogIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionLogIteratorInnerBatchResultTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @Test
    void transactionLogBatchesReplayAtomicWritesInSequenceOrder() throws Exception {
        try (Options options = new Options().setCreateIfMissing(true).setWalTtlSeconds(3600);
             RocksDB source = RocksDB.open(options, tempDir.resolve("source").toString());
             RocksDB replica = RocksDB.open(options, tempDir.resolve("replica").toString());
             WriteOptions writeOptions = new WriteOptions()) {
            long firstSequence = source.getLatestSequenceNumber() + 1;
            try (WriteBatch changes = new WriteBatch()) {
                changes.put(bytes("account"), bytes("open"));
                changes.put(bytes("balance"), bytes("100"));
                source.write(writeOptions, changes);
            }
            source.put(bytes("balance"), bytes("150"));

            try (TransactionLogIterator log = source.getUpdatesSince(0)) {
                log.status();
                assertThat(log.isValid()).isTrue();
                TransactionLogIterator.BatchResult first = log.getBatch();
                assertThat(first.sequenceNumber()).isEqualTo(firstSequence);
                try (WriteBatch batch = first.writeBatch()) {
                    assertThat(batch.count()).isEqualTo(2);
                    replica.write(writeOptions, batch);
                }
                assertThat(replica.get(bytes("account"))).isEqualTo(bytes("open"));
                assertThat(replica.get(bytes("balance"))).isEqualTo(bytes("100"));

                log.next();
                log.status();
                assertThat(log.isValid()).isTrue();
                TransactionLogIterator.BatchResult second = log.getBatch();
                assertThat(second.sequenceNumber()).isEqualTo(firstSequence + 2);
                try (WriteBatch batch = second.writeBatch()) {
                    assertThat(batch.count()).isEqualTo(1);
                    replica.write(writeOptions, batch);
                }
                assertThat(replica.get(bytes("account"))).isEqualTo(source.get(bytes("account")));
                assertThat(replica.get(bytes("balance"))).isEqualTo(bytes("150"));
                assertThat(replica.get(bytes("balance"))).isEqualTo(source.get(bytes("balance")));
                log.next();
                log.status();
                assertThat(log.isValid()).isFalse();
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
