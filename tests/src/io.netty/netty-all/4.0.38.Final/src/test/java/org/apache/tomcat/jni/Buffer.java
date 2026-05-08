/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

public class Buffer {
    public static ByteBuffer malloc(int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static ByteBuffer calloc(int number, int size) {
        return ByteBuffer.allocateDirect(number * size);
    }

    public static ByteBuffer palloc(long pool, int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static ByteBuffer pcalloc(long pool, int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static ByteBuffer create(long memory, int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static void free(ByteBuffer buffer) {
    }

    public static long address(ByteBuffer buffer) {
        return 1;
    }

    public static long size(ByteBuffer buffer) {
        return buffer.capacity();
    }
}
