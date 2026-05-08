/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

public final class Library {
    public static final int TCN_MAJOR_VERSION = 1;
    public static final int TCN_MINOR_VERSION = 1;
    public static final int TCN_PATCH_VERSION = 33;
    public static final int TCN_IS_DEV_VERSION = 0;
    public static final int APR_MAJOR_VERSION = 1;
    public static final int APR_MINOR_VERSION = 7;
    public static final int APR_PATCH_VERSION = 0;
    public static final int APR_IS_DEV_VERSION = 0;
    public static final boolean APR_HAVE_IPV6 = true;
    public static final boolean APR_HAS_SHARED_MEMORY = true;
    public static final boolean APR_HAS_THREADS = true;
    public static final boolean APR_HAS_SENDFILE = true;
    public static final boolean APR_HAS_MMAP = true;
    public static final boolean APR_HAS_FORK = true;
    public static final boolean APR_HAS_RANDOM = true;
    public static final boolean APR_HAS_OTHER_CHILD = false;
    public static final boolean APR_HAS_DSO = true;
    public static final boolean APR_HAS_SO_ACCEPTFILTER = false;
    public static final boolean APR_HAS_UNICODE_FS = false;
    public static final boolean APR_HAS_PROC_INVOKED = false;
    public static final boolean APR_HAS_USER = true;
    public static final boolean APR_HAS_LARGE_FILES = true;
    public static final boolean APR_HAS_XTHREAD_FILES = false;
    public static final boolean APR_HAS_OS_UUID = false;
    public static final boolean APR_IS_BIGENDIAN = false;
    public static final boolean APR_FILES_AS_SOCKETS = true;
    public static final boolean APR_CHARSET_EBCDIC = false;
    public static final boolean APR_TCP_NODELAY_INHERITED = false;
    public static final boolean APR_O_NONBLOCK_INHERITED = false;
    public static final int APR_SIZEOF_VOIDP = Long.BYTES;
    public static final int APR_PATH_MAX = 4096;
    public static int APRMAXHOSTLEN = 256;
    public static final int APR_MAX_IOVEC_SIZE = 1024;
    public static final int APR_MAX_SECS_TO_LINGER = 30;
    public static final int APR_MMAP_THRESHOLD = 0;
    public static final int APR_MMAP_LIMIT = 0;

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
