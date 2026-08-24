/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import okhttp3.internal.io.FileSystem;
import okio.Okio;
import org.junit.jupiter.api.Test;

public class DiskCacheApiCoverageTest {
    @Test
    void editorAndSnapshotRoundTripValuesAndSupportAborts() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("disk-lru-api").toFile();
        DiskLruCache cache = DiskLruCache.create(FileSystem.SYSTEM, directory, 1, 2, 1024L);
        cache.initialize();
        assertThat(cache.getDirectory()).isEqualTo(directory);
        assertThat(cache.getMaxSize()).isEqualTo(1024L);
        DiskLruCache.Editor editor = cache.edit("entry");
        assertThat(editor).isNotNull();
        Okio.buffer(editor.newSink(0)).writeUtf8("key").close();
        Okio.buffer(editor.newSink(1)).writeUtf8("value").close();
        editor.commit();

        DiskLruCache.Snapshot snapshot = cache.get("entry");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.key()).isEqualTo("entry");
        assertThat(snapshot.getLength(0)).isEqualTo(3L);
        assertThat(snapshot.getLength(1)).isEqualTo(5L);
        assertThat(Okio.buffer(snapshot.getSource(0)).readUtf8()).isEqualTo("key");
        assertThat(Okio.buffer(snapshot.getSource(1)).readUtf8()).isEqualTo("value");
        DiskLruCache.Editor replacement = snapshot.edit();
        assertThat(replacement).isNotNull();
        assertThat(Okio.buffer(replacement.newSource(0)).readUtf8()).isEqualTo("key");
        replacement.abortUnlessCommitted();
        snapshot.close();

        DiskLruCache.Editor aborted = cache.edit("aborted");
        Okio.buffer(aborted.newSink(0)).writeUtf8("discarded").close();
        assertThat(cache.remove("aborted")).isTrue();
        aborted.abortUnlessCommitted();
        assertThat(cache.get("aborted")).isNull();
        boolean invalidKeyRejected = false;
        try {
            cache.remove("bad key");
        } catch (IllegalArgumentException expectedInvalidKey) {
            invalidKeyRejected = true;
        }
        assertThat(invalidKeyRejected).isTrue();
        cache.setMaxSize(10L);
        assertThat(cache.getMaxSize()).isEqualTo(10L);
        assertThat(cache.remove("entry")).isTrue();
        assertThat(cache.remove("entry")).isFalse();
        Iterator<DiskLruCache.Snapshot> snapshots = cache.snapshots();
        assertThat(snapshots.hasNext()).isFalse();
        cache.flush();
        cache.evictAll();
        cache.close();
        assertThat(cache.isClosed()).isTrue();
        cache.delete();
    }

    @Test
    void initializeReadsCleanAndDirtyJournalEntriesAndRepairsCorruption() throws Exception {
        File validDirectory = java.nio.file.Files.createTempDirectory("disk-lru-journal").toFile();
        java.nio.file.Files.write(validDirectory.toPath().resolve("journal"),
                ("libcore.io.DiskLruCache\n1\n1\n2\n\nDIRTY stale\nCLEAN good 3 5\n")
                        .getBytes(StandardCharsets.US_ASCII));
        java.nio.file.Files.write(validDirectory.toPath().resolve("good.0"),
                "key".getBytes(StandardCharsets.US_ASCII));
        java.nio.file.Files.write(validDirectory.toPath().resolve("good.1"),
                "value".getBytes(StandardCharsets.US_ASCII));
        DiskLruCache valid = DiskLruCache.create(FileSystem.SYSTEM, validDirectory, 1, 2, 1024L);
        valid.initialize();
        assertThat(valid.size()).isEqualTo(8L);
        assertThat(Okio.buffer(valid.get("good").getSource(1)).readUtf8()).isEqualTo("value");
        valid.close();

        File corruptDirectory = java.nio.file.Files.createTempDirectory("disk-lru-corrupt").toFile();
        java.nio.file.Files.write(corruptDirectory.toPath().resolve("journal"),
                ("libcore.io.DiskLruCache\n1\n1\n2\n\nCLEAN broken nope\n")
                        .getBytes(StandardCharsets.US_ASCII));
        DiskLruCache corrupt = DiskLruCache.create(FileSystem.SYSTEM, corruptDirectory, 1, 2, 1024L);
        corrupt.initialize();
        assertThat(corrupt.size()).isZero();
        corrupt.close();
    }
}
