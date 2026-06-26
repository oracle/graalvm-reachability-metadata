/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT.HRESULT;

public class Win32ExceptionTest {
    @Test
    public void reflectedSuppressionAddsSecondaryWin32Exception() throws Throwable {
        Win32Exception primary = new TestWin32Exception(
                5,
                new HRESULT(0x80070005),
                "Access is denied");
        Win32Exception secondary = new TestWin32Exception(
                2,
                new HRESULT(0x80070002),
                "File not found");

        addSuppressedReflected().invoke(primary, secondary);

        assertThat(primary.getHR().intValue()).isEqualTo(0x80070005);
        assertThat(primary.getSuppressed()).containsExactly(secondary);
    }

    private static MethodHandle addSuppressedReflected() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Win32Exception.class,
                MethodHandles.lookup());
        return lookup.findVirtual(
                Win32Exception.class,
                "addSuppressedReflected",
                MethodType.methodType(void.class, Throwable.class));
    }

    private static final class TestWin32Exception extends Win32Exception {
        private TestWin32Exception(int code, HRESULT hr, String message) {
            super(code, hr, message);
        }
    }
}
