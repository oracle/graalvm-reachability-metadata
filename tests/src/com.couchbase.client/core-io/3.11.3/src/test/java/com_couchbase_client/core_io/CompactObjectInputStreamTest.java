/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CompactObjectInputStreamTest {
    @Test
    void restoresPrimitiveClassDescriptorsThroughCompactProtocol() {
        Class<?>[] types = {int.class, boolean.class, double.class, void.class, int[].class};
        Class<?>[] restored = (Class<?>[]) NettySerializationSupport.throughChannels(types);
        assertArrayEquals(types, restored);
    }
}
