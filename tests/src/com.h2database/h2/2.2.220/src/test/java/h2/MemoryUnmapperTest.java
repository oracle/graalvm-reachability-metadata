/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    @Test
    void unmapsDirectBufferWhenCleanerHackIsEnabled() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        buffer.putInt(42);
        buffer.flip();

        assertThat(MemoryUnmapper.unmap(buffer)).isTrue();
    }

    @Test
    void returnsFalseForHeapBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(32);

        assertThat(MemoryUnmapper.unmap(buffer)).isFalse();
    }
}
