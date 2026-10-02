/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.aspectj.weaver.tools.cache.AbstractIndexedFileCacheBacking;
import org.aspectj.weaver.tools.cache.CachedClassEntry;
import org.aspectj.weaver.tools.cache.CachedClassReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractIndexedFileCacheBackingTest {
    @TempDir
    Path cacheDirectory;

    @Test
    void writesAndReadsSerializedIndexEntries() throws Exception {
        TestIndexedFileCacheBacking backing = new TestIndexedFileCacheBacking(cacheDirectory.toFile());
        AbstractIndexedFileCacheBacking.IndexEntry entry = indexEntry(
                "sample/Type.class",
                true,
                false,
                0x5a17L,
                0x8eafL
        );

        backing.writeEntries(entry);
        AbstractIndexedFileCacheBacking.IndexEntry[] restoredEntries = backing.readIndex(backing.getIndexFile());

        assertThat(backing.getIndexFile()).isFile();
        assertThat(restoredEntries).containsExactly(entry);
    }

    @Test
    void readsIndexIntoKeyedMap() throws Exception {
        TestIndexedFileCacheBacking backing = new TestIndexedFileCacheBacking(cacheDirectory.toFile());
        AbstractIndexedFileCacheBacking.IndexEntry firstEntry = indexEntry(
                "sample/First.class",
                false,
                false,
                0x101L,
                0x202L
        );
        AbstractIndexedFileCacheBacking.IndexEntry ignoredEntry = indexEntry(
                "sample/Ignored.class",
                false,
                true,
                0x303L,
                0L
        );

        backing.writeEntries(firstEntry, ignoredEntry);
        Map<String, AbstractIndexedFileCacheBacking.IndexEntry> restoredIndex = backing.readIndexMap();

        assertThat(restoredIndex)
                .containsEntry(firstEntry.key, firstEntry)
                .containsEntry(ignoredEntry.key, ignoredEntry);
        assertThat(restoredIndex.keySet()).containsExactly(firstEntry.key, ignoredEntry.key);
    }

    private static AbstractIndexedFileCacheBacking.IndexEntry indexEntry(
            String key,
            boolean generated,
            boolean ignored,
            long crcClass,
            long crcWeaved
    ) {
        AbstractIndexedFileCacheBacking.IndexEntry entry = new AbstractIndexedFileCacheBacking.IndexEntry();
        entry.key = key;
        entry.generated = generated;
        entry.ignored = ignored;
        entry.crcClass = crcClass;
        entry.crcWeaved = crcWeaved;
        return entry;
    }

    private static final class TestIndexedFileCacheBacking extends AbstractIndexedFileCacheBacking {
        private final Map<String, IndexEntry> index = new TreeMap<>();

        private TestIndexedFileCacheBacking(File cacheDirectory) {
            super(cacheDirectory);
        }

        @Override
        protected Map<String, IndexEntry> getIndex() {
            return index;
        }

        @Override
        public void remove(CachedClassReference ref) {
            index.remove(ref.getKey());
        }

        @Override
        public void clear() {
            index.clear();
        }

        @Override
        public CachedClassEntry get(CachedClassReference ref, byte[] originalBytes) {
            return null;
        }

        @Override
        public void put(CachedClassEntry entry, byte[] originalBytes) {
            IndexEntry indexEntry = createIndexEntry(entry, originalBytes);
            if (indexEntry != null) {
                index.put(indexEntry.key, indexEntry);
            }
        }

        private void writeEntries(IndexEntry... entries) throws IOException {
            writeIndex(getIndexFile(), entries);
        }

        private Map<String, IndexEntry> readIndexMap() {
            return readIndex();
        }
    }
}
