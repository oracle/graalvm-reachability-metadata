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

public class AbstractIoBufferAnonymous3Test {
    @Test
    void resolvesSerializedClassDescriptorsWithTheRequestedClassLoader() throws Exception {
        List<String> value = new ArrayList<>();
        value.add("descriptor");
        IoBuffer buffer = IoBuffer.allocate(32).setAutoExpand(true);
        buffer.putObject(value);
        buffer.flip();

        Object decoded = buffer.getObject(AbstractIoBufferAnonymous3Test.class.getClassLoader());

        assertThat(decoded).isEqualTo(value);
        assertThat(decoded).isInstanceOf(ArrayList.class);
    }
}
