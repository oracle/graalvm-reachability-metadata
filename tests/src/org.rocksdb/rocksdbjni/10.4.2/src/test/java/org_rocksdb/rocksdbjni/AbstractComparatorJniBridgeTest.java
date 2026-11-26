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
import org.rocksdb.AbstractComparator;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ComparatorOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.IndexShorteningMode;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractComparatorJniBridgeTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @Test
    void directBufferComparatorOrdersKeysAndBuildsTableIndexes() throws Exception {
        verifyComparatorCallbacks(true, "direct");
    }

    @Test
    void heapBufferComparatorOrdersKeysAndBuildsTableIndexes() throws Exception {
        verifyComparatorCallbacks(false, "heap");
    }

    private void verifyComparatorCallbacks(boolean directBuffers, String directory) throws Exception {
        Path dbPath = tempDir.resolve(directory);
        BlockBasedTableConfig table = new BlockBasedTableConfig()
                .setBlockSize(256)
                .setIndexShortening(IndexShorteningMode.kShortenSeparatorsAndSuccessor);
        try (ComparatorOptions comparatorOptions = new ComparatorOptions().setUseDirectBuffer(directBuffers);
             ReverseComparator comparator = new ReverseComparator(comparatorOptions);
             Options options = new Options().setCreateIfMissing(true)
                     .setComparator(comparator).setTableFormatConfig(table)) {
            List<String> expectedKeys = new ArrayList<>();
            try (RocksDB db = RocksDB.open(options, dbPath.toString());
                 FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
                for (int index = 0; index < 128; index++) {
                    String key = "key-" + (1000 + index);
                    db.put(bytes(key), bytes(key + "-payload-".repeat(16)));
                    expectedKeys.add(0, key);
                }
                db.flush(flushOptions);
                assertThat(comparator.comparisons.get()).isPositive();
                assertThat(comparator.separators.get()).isPositive();
                assertThat(comparator.successors.get()).isPositive();
            }

            try (RocksDB reopened = RocksDB.openReadOnly(options, dbPath.toString());
                 RocksIterator iterator = reopened.newIterator()) {
                List<String> actualKeys = new ArrayList<>();
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    String key = new String(iterator.key(), StandardCharsets.UTF_8);
                    actualKeys.add(key);
                    assertThat(iterator.value()).isEqualTo(bytes(key + "-payload-".repeat(16)));
                    assertThat(reopened.get(bytes(key))).isEqualTo(iterator.value());
                }
                iterator.status();
                assertThat(actualKeys).containsExactlyElementsOf(expectedKeys);
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ReverseComparator extends AbstractComparator {
        private final AtomicInteger comparisons = new AtomicInteger();
        private final AtomicInteger separators = new AtomicInteger();
        private final AtomicInteger successors = new AtomicInteger();

        private ReverseComparator(ComparatorOptions options) {
            super(options);
        }

        @Override
        public String name() {
            return "test.reverse-byte-order";
        }

        @Override
        public int compare(ByteBuffer first, ByteBuffer second) {
            comparisons.incrementAndGet();
            return second.compareTo(first);
        }

        @Override
        public void findShortestSeparator(ByteBuffer start, ByteBuffer limit) {
            separators.incrementAndGet();
            super.findShortestSeparator(start, limit);
        }

        @Override
        public void findShortSuccessor(ByteBuffer key) {
            successors.incrementAndGet();
            super.findShortSuccessor(key);
        }
    }
}
