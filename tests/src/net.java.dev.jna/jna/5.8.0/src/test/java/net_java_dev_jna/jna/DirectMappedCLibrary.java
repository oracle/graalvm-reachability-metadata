/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.NativeLong;

/**
 * Direct mapping: the native methods are bound with {@code Native.register}
 * instead of being dispatched through an interface proxy created by
 * {@code Native.load}.
 */
public final class DirectMappedCLibrary {

    public static native int atol(String s);

    /**
     * Takes and returns a {@code NativeMapped} type, so registering it routes
     * through {@code NativeMappedConverter}, which instantiates
     * {@code NativeLong} reflectively.
     */
    public static native NativeLong labs(NativeLong i);

    private DirectMappedCLibrary() {
    }
}
