/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class OptimisticTransactionApiTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void transactionOptionsAndOverloadsCommitChanges() throws Exception {
        Path path = Files.createDirectory(tempDir.resolve("transaction-db"));
        try (Options options = new Options().setCreateIfMissing(true);
             OptimisticTransactionDB db = OptimisticTransactionDB.open(options, path.toString());
             WriteOptions writeOptions = new WriteOptions();
             OptimisticTransactionOptions transactionOptions = new OptimisticTransactionOptions()) {
            TransactionalOptions<OptimisticTransactionOptions> optionsInterface = transactionOptions;
            assertThat(optionsInterface.setSetSnapshot(true)).isSameAs(transactionOptions);
            assertThat(transactionOptions.isSetSnapshot()).isTrue();

            TransactionalDB transactionalDb = db;
            try (Transaction transaction = transactionalDb.beginTransaction(writeOptions,
                    optionsInterface)) {
                transaction.put(bytes("first"), bytes("value"));
                transaction.commit();
            }
            assertThat(db.get(bytes("first"))).isEqualTo(bytes("value"));

            try (Transaction reusable = transactionalDb.beginTransaction(writeOptions)) {
                Transaction transaction = transactionalDb.beginTransaction(writeOptions,
                        optionsInterface, reusable);
                assertThat(transaction).isSameAs(reusable);
                transaction.put(bytes("second"), bytes("value"));
                transaction.commit();
            }
            assertThat(db.get(bytes("second"))).isEqualTo(bytes("value"));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
