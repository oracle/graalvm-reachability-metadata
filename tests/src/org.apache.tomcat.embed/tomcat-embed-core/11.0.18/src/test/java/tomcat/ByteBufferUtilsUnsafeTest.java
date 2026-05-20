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

public class ByteBufferUtilsUnsafeTest {

    @Test
    void cleansDirectBuffersThroughPublicUtility() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8);
        buffer.put((byte) 1);

        ByteBufferUtils.cleanDirectBuffer(buffer);

        assertThat(buffer.isDirect()).isTrue();
    }
}
