/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_leveldbjni.leveldbjni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBComparator;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.Range;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;
import org.junit.jupiter.api.Test;

public class LeveldbjniTest {
    @Test
    public void storesReadsDeletesAndReopensDatabase() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-lifecycle-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions())) {
                db.put(bytes("alpha"), bytes("one"));
                db.put(bytes("unicode"), bytes("Gr\u00fc\u00dfe"));

                assertThat(db.get(bytes("alpha"))).containsExactly(bytes("one"));
                assertThat(asString(db.get(bytes("unicode")))).isEqualTo("Gr\u00fc\u00dfe");

                db.delete(bytes("alpha"));
                assertThat(db.get(bytes("alpha"))).isNull();
            }

            try (DB reopened = factory.open(databasePath.toFile(), existingDatabaseOptions())) {
                assertThat(asString(reopened.get(bytes("unicode")))).isEqualTo("Gr\u00fc\u00dfe");
                reopened.put(bytes("after-reopen"), bytes("still writable"));
                assertThat(asString(reopened.get(bytes("after-reopen")))).isEqualTo("still writable");
            }
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void writeBatchAppliesAtomicMutationsAndIteratorNavigatesKeys() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-iterator-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions())) {
                db.put(bytes("key-00"), bytes("obsolete"));
                try (WriteBatch batch = db.createWriteBatch()) {
                    batch.delete(bytes("key-00"));
                    batch.put(bytes("key-01"), bytes("value-01"));
                    batch.put(bytes("key-02"), bytes("value-02"));
                    batch.put(bytes("key-03"), bytes("value-03"));
                    batch.put(bytes("key-04"), bytes("value-04"));
                    db.write(batch);
                }

                assertThat(db.get(bytes("key-00"))).isNull();
                assertThat(asString(db.get(bytes("key-03")))).isEqualTo("value-03");

                try (DBIterator iterator = db.iterator()) {
                    iterator.seekToFirst();
                    assertThat(iterator.peekNext().getKey()).containsExactly(bytes("key-01"));
                    assertThat(nextKey(iterator)).isEqualTo("key-01");

                    iterator.seek(bytes("key-03"));
                    assertThat(iterator.hasNext()).isTrue();
                    assertThat(nextKey(iterator)).isEqualTo("key-03");

                    iterator.seekToLast();
                    assertThat(iterator.peekNext().getKey()).containsExactly(bytes("key-04"));
                    assertThat(iterator.peekPrev().getKey()).containsExactly(bytes("key-03"));
                    assertThat(iterator.peekNext().getKey()).containsExactly(bytes("key-04"));
                    assertThat(iterator.hasPrev()).isTrue();
                    assertThat(previousKey(iterator)).isEqualTo("key-03");
                }
            }
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void snapshotsProvideStableReadViewAcrossUpdates() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-snapshot-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions())) {
                db.put(bytes("account"), bytes("initial"), new WriteOptions().sync(true));
                try (Snapshot snapshot = db.getSnapshot()) {
                    db.put(bytes("account"), bytes("updated"));
                    db.put(bytes("second"), bytes("visible-later"));

                    ReadOptions readOptions = newSnapshotReadOptions(snapshot);
                    assertThat(asString(db.get(bytes("account"), readOptions))).isEqualTo("initial");
                    assertThat(db.get(bytes("second"), readOptions)).isNull();

                    try (DBIterator iterator = db.iterator(readOptions)) {
                        iterator.seekToFirst();
                        assertThat(iterator.hasNext()).isTrue();
                        Map.Entry<byte[], byte[]> entry = iterator.next();
                        assertThat(asString(entry.getKey())).isEqualTo("account");
                        assertThat(asString(entry.getValue())).isEqualTo("initial");
                        assertThat(iterator.hasNext()).isFalse();
                    }

                    assertThat(asString(db.get(bytes("account")))).isEqualTo("updated");
                    assertThat(asString(db.get(bytes("second")))).isEqualTo("visible-later");
                }
            }
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void customComparatorControlsIteratorOrdering() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-comparator-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions().comparator(reverseLexicographicComparator()))) {
                db.put(bytes("apple"), bytes("red"));
                db.put(bytes("banana"), bytes("yellow"));
                db.put(bytes("cherry"), bytes("dark red"));

                assertThat(asString(db.get(bytes("banana")))).isEqualTo("yellow");

                try (DBIterator iterator = db.iterator()) {
                    iterator.seekToFirst();
                    assertThat(nextKey(iterator)).isEqualTo("cherry");
                    assertThat(nextKey(iterator)).isEqualTo("banana");
                    assertThat(nextKey(iterator)).isEqualTo("apple");
                    assertThat(iterator.hasNext()).isFalse();

                    iterator.seek(bytes("banana"));
                    assertThat(iterator.hasNext()).isTrue();
                    assertThat(nextKey(iterator)).isEqualTo("banana");

                    iterator.seekToLast();
                    assertThat(previousKey(iterator)).isEqualTo("banana");
                }
            }
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void exposesPropertiesApproximateSizesCompactionRepairAndDestroy() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-maintenance-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions())) {
                for (int i = 0; i < 20; i++) {
                    db.put(bytes(String.format("key-%02d", i)), bytes("value-" + i));
                }

                long[] sizes = db.getApproximateSizes(
                        new Range(bytes("key-00"), bytes("key-10")),
                        new Range(bytes("key-10"), bytes("key-99")));
                assertThat(sizes).hasSize(2);
                assertThat(sizes[0]).isGreaterThanOrEqualTo(0L);
                assertThat(sizes[1]).isGreaterThanOrEqualTo(0L);

                assertThat(db.getProperty("leveldb.stats")).isNotNull();
                db.compactRange(bytes("key-00"), bytes("key-99"));
            }

            factory.repair(databasePath.toFile(), existingDatabaseOptions());
            try (DB repaired = factory.open(databasePath.toFile(), existingDatabaseOptions())) {
                assertThat(asString(repaired.get(bytes("key-07")))).isEqualTo("value-7");
            }

            factory.destroy(databasePath.toFile(), new Options());
            assertThatThrownBy(() -> factory.open(databasePath.toFile(), existingDatabaseOptions()))
                    .isInstanceOf(IOException.class);
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void suspendAndResumeCompactionsKeepsDatabaseAvailableForWrites() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-compactions-");
        try {
            try (DB db = factory.open(databasePath.toFile(), databaseOptions())) {
                db.suspendCompactions();
                try {
                    for (int i = 0; i < 12; i++) {
                        db.put(bytes(String.format("paused-%02d", i)), bytes("value-" + i));
                    }
                    assertThat(asString(db.get(bytes("paused-05")))).isEqualTo("value-5");
                } finally {
                    db.resumeCompactions();
                }

                db.put(bytes("after-resume"), bytes("writes continue"));
                assertThat(asString(db.get(bytes("after-resume")))).isEqualTo("writes continue");
            }
        } finally {
            deleteRecursively(databasePath);
        }
    }

    @Test
    public void factoryRejectsInvalidOpenModesAndConvertsStrings() throws Exception {
        Path databasePath = Files.createTempDirectory("leveldbjni-factory-");
        try {
            assertThat(asString(bytes("plain text"))).isEqualTo("plain text");
            assertThat(asString(bytes("snowman-\u2603"))).isEqualTo("snowman-\u2603");
            assertThat(factory.toString()).isNotBlank();

            Path missingPath = databasePath.resolve("missing");
            assertThatThrownBy(() -> factory.open(missingPath.toFile(), existingDatabaseOptions()))
                    .isInstanceOf(IOException.class);

            try (DB ignored = factory.open(databasePath.toFile(), databaseOptions())) {
                assertThat(ignored).isNotNull();
            }

            Options duplicateRejectedOptions = databaseOptions().errorIfExists(true);
            assertThatThrownBy(() -> factory.open(databasePath.toFile(), duplicateRejectedOptions))
                    .isInstanceOf(IOException.class);
        } finally {
            deleteRecursively(databasePath);
        }
    }

    private static Options databaseOptions() {
        return new Options()
                .createIfMissing(true)
                .compressionType(CompressionType.NONE)
                .blockSize(4 * 1024)
                .blockRestartInterval(16)
                .writeBufferSize(1024 * 1024)
                .maxOpenFiles(32)
                .cacheSize(1024 * 1024)
                .paranoidChecks(true)
                .verifyChecksums(true);
    }

    private static Options existingDatabaseOptions() {
        return databaseOptions().createIfMissing(false);
    }

    private static DBComparator reverseLexicographicComparator() {
        return new DBComparator() {
            @Override
            public String name() {
                return "reverse-lexicographic";
            }

            @Override
            public int compare(byte[] left, byte[] right) {
                return asString(right).compareTo(asString(left));
            }

            @Override
            public byte[] findShortestSeparator(byte[] start, byte[] limit) {
                return start;
            }

            @Override
            public byte[] findShortSuccessor(byte[] key) {
                return key;
            }
        };
    }

    private static ReadOptions newSnapshotReadOptions(Snapshot snapshot) {
        return new ReadOptions()
                .snapshot(snapshot)
                .fillCache(false)
                .verifyChecksums(true);
    }

    private static String nextKey(DBIterator iterator) {
        Map.Entry<byte[], byte[]> entry = iterator.next();
        return asString(entry.getKey());
    }

    private static String previousKey(DBIterator iterator) {
        Map.Entry<byte[], byte[]> entry = iterator.prev();
        return asString(entry.getKey());
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(LeveldbjniTest::deleteIfExists);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
