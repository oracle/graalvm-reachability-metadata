/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.Win32Exception;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.assertj.core.api.Assertions.assertThat;

public class Win32ExceptionTest {
    @Test
    void addSuppressedReflectedDelegatesToThrowableAddSuppressed() throws Throwable {
        InspectableWin32Exception exception = new InspectableWin32Exception(5, "Access denied");
        IllegalStateException suppressed = new IllegalStateException("secondary failure");
        MethodHandle addSuppressedReflected = MethodHandles.privateLookupIn(
                Win32Exception.class,
                MethodHandles.lookup()
        ).findVirtual(
                Win32Exception.class,
                "addSuppressedReflected",
                MethodType.methodType(void.class, Throwable.class)
        );

        addSuppressedReflected.invoke(exception, suppressed);

        assertThat(exception.getHR().intValue()).isEqualTo(W32Errors.HRESULT_FROM_WIN32(5).intValue());
        assertThat(exception.getSuppressed()).containsExactly(suppressed);
    }

    private static final class InspectableWin32Exception extends Win32Exception {
        private InspectableWin32Exception(int errorCode, String message) {
            super(errorCode, W32Errors.HRESULT_FROM_WIN32(errorCode), message);
        }
    }
}
