/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class Win32ExceptionTest {
    @Test
    void addSuppressedReflectedAttachesTheException() throws Throwable {
        TestWin32Exception exception = new TestWin32Exception(WinError.ERROR_ACCESS_DENIED);
        IllegalStateException suppressed = new IllegalStateException("cleanup failed");

        MethodHandle addSuppressedReflected = MethodHandles.privateLookupIn(
                Win32Exception.class,
                MethodHandles.lookup())
                .findVirtual(
                        Win32Exception.class,
                        "addSuppressedReflected",
                        MethodType.methodType(void.class, Throwable.class));

        addSuppressedReflected.invoke(exception, suppressed);

        int expectedHresult = W32Errors.HRESULT_FROM_WIN32(WinError.ERROR_ACCESS_DENIED).intValue();
        assertThat(exception.getHR().intValue()).isEqualTo(expectedHresult);
        assertThat(exception).hasMessage("Access denied");
        assertThat(exception.getSuppressed()).containsExactly(suppressed);
    }

    private static final class TestWin32Exception extends Win32Exception {
        private static final long serialVersionUID = 1L;

        private TestWin32Exception(int code) {
            super(code, W32Errors.HRESULT_FROM_WIN32(code), "Access denied");
        }
    }
}
