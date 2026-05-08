/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

public final class Library {
    public static int TCN_MAJOR_VERSION = 1;
    public static int TCN_MINOR_VERSION = 1;
    public static int TCN_PATCH_VERSION = 33;
    public static int TCN_IS_DEV_VERSION;
    public static int APR_MAJOR_VERSION = 1;
    public static int APR_MINOR_VERSION = 7;
    public static int APR_PATCH_VERSION;
    public static int APR_IS_DEV_VERSION;
    public static boolean APR_HAVE_IPV6 = true;
    public static boolean APR_HAS_SHARED_MEMORY = true;
    public static boolean APR_HAS_THREADS = true;
    public static boolean APR_HAS_SENDFILE = true;
    public static boolean APR_HAS_MMAP = true;
    public static boolean APR_HAS_FORK = true;
    public static boolean APR_HAS_RANDOM = true;
    public static boolean APR_HAS_OTHER_CHILD;
    public static boolean APR_HAS_DSO = true;
    public static boolean APR_HAS_SO_ACCEPTFILTER;
    public static boolean APR_HAS_UNICODE_FS;
    public static boolean APR_HAS_PROC_INVOKED;
    public static boolean APR_HAS_USER = true;
    public static boolean APR_HAS_LARGE_FILES = true;
    public static boolean APR_HAS_XTHREAD_FILES;
    public static boolean APR_HAS_OS_UUID;
    public static boolean APR_IS_BIGENDIAN;
    public static boolean APR_FILES_AS_SOCKETS = true;
    public static boolean APR_CHARSET_EBCDIC;
    public static boolean APR_TCP_NODELAY_INHERITED;
    public static boolean APR_O_NONBLOCK_INHERITED;
    public static int APR_SIZEOF_VOIDP = Long.BYTES;
    public static int APR_PATH_MAX = 4096;
    public static int APRMAXHOSTLEN = 256;
    public static int APR_MAX_IOVEC_SIZE = 1024;
    public static int APR_MAX_SECS_TO_LINGER = 30;
    public static int APR_MMAP_THRESHOLD;
    public static int APR_MMAP_LIMIT;

    private Library() {
    }

    public static void terminate() {
    }

    public static String versionString() {
        return "stub-tcnative";
    }

    public static String aprVersionString() {
        return "stub-apr";
    }

    public static long globalPool() {
        return 1;
    }

    public static boolean initialize(String libraryName) {
        return true;
    }
}
