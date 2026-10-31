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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteBufferUtilsTest {
    @Test
    void expandsDirectBufferWithoutLosingWrittenData() {
        ByteBuffer original = ByteBuffer.allocateDirect(4);
        original.putInt(123456);
        ByteBuffer expanded = ByteBufferUtils.expand(original, 16);
        try {
            assertTrue(expanded.isDirect());
            assertEquals(16, expanded.capacity());
            assertEquals(4, expanded.position());
            expanded.putInt(654321);
            expanded.flip();
            assertEquals(123456, expanded.getInt());
            assertEquals(654321, expanded.getInt());
        } finally {
            ByteBufferUtils.cleanDirectBuffer(expanded);
        }
    }
}
