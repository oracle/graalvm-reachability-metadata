/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    @Test
    void directBufferUnmapUsesUnsafeCleanerWhenAvailable() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);

        assertThat(MemoryUnmapper.unmap(buffer)).isIn(true, false);
    }

    @Test
    void closingMappedStoreAttemptsToUnmapMappedBuffers() throws Exception {
        Path file = Files.createTempFile("h2-mapped-store", ".mv.db");
        Files.deleteIfExists(file);

        MVStore store = new MVStore.Builder().fileName("nioMapped:" + file).open();
        try {
            MVMap<Integer, String> map = store.openMap("test");
            map.put(1, "mapped");
            store.commit();
            assertThat(map.get(1)).isEqualTo("mapped");
        } finally {
            store.close();
            Files.deleteIfExists(file);
        }
    }
}
