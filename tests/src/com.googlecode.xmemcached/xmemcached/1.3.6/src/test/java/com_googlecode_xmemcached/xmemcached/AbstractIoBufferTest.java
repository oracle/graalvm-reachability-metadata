/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_xmemcached.xmemcached;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.code.yanf4j.buffer.IoBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AbstractIoBufferTest {
    @Test
    void writesAndReadsSerializableObjects() throws Exception {
        List<String> value = new ArrayList<>();
        value.add("alpha");
        value.add("beta");
        IoBuffer buffer = IoBuffer.allocate(32).setAutoExpand(true);

        buffer.putObject(value);
        buffer.flip();

        assertThat(buffer.getObject()).isEqualTo(value);
        assertThat(buffer.remaining()).isZero();
    }
}
