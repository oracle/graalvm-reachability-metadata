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
    void expandsDirectBufferWithoutLosingWrittenBytes() {
        ByteBuffer original = ByteBuffer.allocateDirect(4);
        original.putInt(123456);
        ByteBuffer expanded = ByteBufferUtils.expand(original, 16);
        try {
            assertThat(expanded.isDirect()).isTrue();
            assertThat(expanded.capacity()).isEqualTo(16);
            assertThat(expanded.position()).isEqualTo(Integer.BYTES);
            expanded.putInt(789);
            expanded.flip();
            assertThat(expanded.getInt()).isEqualTo(123456);
            assertThat(expanded.getInt()).isEqualTo(789);
            assertThat(ByteBufferUtils.expand(expanded, 8)).isSameAs(expanded);
        } finally {
            ByteBufferUtils.cleanDirectBuffer(expanded);
        }
    }
}
