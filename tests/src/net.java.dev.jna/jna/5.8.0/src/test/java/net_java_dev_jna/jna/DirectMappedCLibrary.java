/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;

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

    /**
     * One overload per {@code com.sun.jna.ptr} type, all bound to
     * {@code memcpy(void *, const void *, size_t)}. Every {@code ByReference}
     * is a {@code PointerType}, hence a {@code NativeMapped}, so registering
     * these builds a default value for each parameter type the same way
     * {@code labs} does for {@code NativeLong}.
     */
    public static native Pointer memcpy(ByteByReference dst, ByteByReference src, long n);

    public static native Pointer memcpy(ShortByReference dst, ShortByReference src, long n);

    public static native Pointer memcpy(IntByReference dst, IntByReference src, long n);

    public static native Pointer memcpy(LongByReference dst, LongByReference src, long n);

    public static native Pointer memcpy(FloatByReference dst, FloatByReference src, long n);

    public static native Pointer memcpy(DoubleByReference dst, DoubleByReference src, long n);

    public static native Pointer memcpy(NativeLongByReference dst, NativeLongByReference src, long n);

    public static native Pointer memcpy(PointerByReference dst, PointerByReference src, long n);

    private DirectMappedCLibrary() {
    }
}
