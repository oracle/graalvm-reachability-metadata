/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import java.util.Date;

import org.apache.mina.common.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteBufferAnonymous3Test {
    @Test
    void resolvesObjectAndArrayDescriptorsWithTheSuppliedClassLoader() throws Exception {
        Date timestamp = new Date(123456789L);
        int[] samples = {3, 5, 8};
        ClassLoader loader = ByteBufferAnonymous3Test.class.getClassLoader();
        ByteBuffer buffer = ByteBuffer.allocate(32).setAutoExpand(true);
        try {
            buffer.putObject(timestamp);
            buffer.putObject(samples);
            buffer.flip();

            assertEquals(timestamp, buffer.getObject(loader));
            assertArrayEquals(samples, (int[]) buffer.getObject(loader));
            assertFalse(buffer.hasRemaining());
        } finally {
            buffer.release();
        }
    }

    @Test
    void resolvesPrimitiveClassDescriptors() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(32).setAutoExpand(true);
        try {
            buffer.putObject(int.class);
            buffer.putObject(boolean.class);
            buffer.flip();

            assertSame(int.class, buffer.getObject());
            assertSame(boolean.class, buffer.getObject());
            assertFalse(buffer.hasRemaining());
        } finally {
            buffer.release();
        }
    }
}
