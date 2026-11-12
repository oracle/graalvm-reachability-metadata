/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferUtilsTest {
    @Test
    void expandsDirectBufferAndPreservesWrittenData() {
        ByteBuffer original = ByteBuffer.allocateDirect(4);
        original.putInt(12345);
        ByteBuffer expanded = ByteBufferUtils.expand(original, 32);
        try {
            assertThat(expanded.isDirect()).isTrue();
            assertThat(expanded.capacity()).isEqualTo(32);
            assertThat(expanded.position()).isEqualTo(4);
            expanded.putInt(789);
            expanded.flip();
            assertThat(expanded.getInt()).isEqualTo(12345);
            assertThat(expanded.getInt()).isEqualTo(789);
            assertThat(ByteBufferUtils.expand(expanded, 8)).isSameAs(expanded);
        } finally {
            ByteBufferUtils.cleanDirectBuffer(expanded);
        }
    }
}
