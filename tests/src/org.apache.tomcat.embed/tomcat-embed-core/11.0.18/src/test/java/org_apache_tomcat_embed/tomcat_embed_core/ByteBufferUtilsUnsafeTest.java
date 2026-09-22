/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferUtilsUnsafeTest {

    @Test
    void expandsDirectBufferAndPreservesWrittenBytes() {
        ByteBuffer original = ByteBuffer.allocateDirect(2);
        original.put((byte) 11);
        original.put((byte) 29);

        ByteBuffer expanded = ByteBufferUtils.expand(original, 8);
        expanded.flip();

        assertThat(expanded.isDirect()).isTrue();
        assertThat(expanded.capacity()).isEqualTo(8);
        assertThat(expanded.get()).isEqualTo((byte) 11);
        assertThat(expanded.get()).isEqualTo((byte) 29);
        ByteBufferUtils.cleanDirectBuffer(expanded);
    }
}
