/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class Pool {
    private static final AtomicLong IDS = new AtomicLong(100);

    public static long create(long parent) {
        return IDS.incrementAndGet();
    }

    public static void clear(long pool) {
    }

    public static void destroy(long pool) {
    }

    public static long parentGet(long pool) {
        return 0;
    }

    public static boolean isAncestor(long ancestor, long pool) {
        return false;
    }

    public static long cleanupRegister(long pool, Object data) {
        return IDS.incrementAndGet();
    }

    public static void cleanupKill(long pool, long cleanup) {
    }

    public static void noteSubprocess(long pool, long process, int how) {
    }

    public static ByteBuffer alloc(long pool, int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static ByteBuffer calloc(long pool, int size) {
        return ByteBuffer.allocateDirect(size);
    }

    public static int dataSet(long pool, String key, Object data) {
        return 0;
    }

    public static Object dataGet(long pool, String key) {
        return null;
    }

    public static void cleanupForExec() {
    }
}
