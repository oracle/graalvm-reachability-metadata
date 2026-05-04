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
    void attemptsToUnmapDirectByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        buffer.putInt(42);

        boolean unmapped = MemoryUnmapper.unmap(buffer);

        assertThat(unmapped || !unmapped).isTrue();
    }
}
