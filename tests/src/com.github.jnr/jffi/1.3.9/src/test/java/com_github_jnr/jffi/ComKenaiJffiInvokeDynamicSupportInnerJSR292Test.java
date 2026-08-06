/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.CallContext;
import com.kenai.jffi.CallingConvention;
import com.kenai.jffi.InvokeDynamicSupport;
import com.kenai.jffi.Library;
import com.kenai.jffi.Type;
import java.lang.invoke.MethodHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComKenaiJffiInvokeDynamicSupportInnerJSR292Test {
    @Test
    void createsAndInvokesMethodHandleForFastNumericNativeFunction() throws Throwable {
        long getpidAddress = findGetpidAddress();
        assertThat(getpidAddress).isNotZero();

        CallContext callContext = CallContext.getCallContext(Type.SINT32, new Type[0],
                CallingConvention.DEFAULT, true);
        InvokeDynamicSupport.Invoker invoker = InvokeDynamicSupport.getFastNumericInvoker(
                callContext, getpidAddress);

        assertThat(invoker).isNotNull();
        assertThat(invoker.getMethod().getName()).isIn("invokeI0", "invokeL0", "invokeN0");
        assertThat(invoker.getMethodHandle()).isInstanceOf(MethodHandle.class);

        MethodHandle methodHandle = (MethodHandle) invoker.getMethodHandle();
        Number processId = (Number) methodHandle.invoke();
        assertThat(processId.longValue()).isPositive();
    }

    private static long findGetpidAddress() {
        Library currentProcess = Library.getDefault();
        long address = currentProcess.getSymbolAddress("getpid");
        if (address != 0) {
            return address;
        }

        for (String libraryName : new String[] {"libc.so.6", "libc.dylib", "c"}) {
            Library library = Library.openLibrary(libraryName, Library.LAZY | Library.LOCAL);
            if (library != null) {
                address = library.getSymbolAddress("getpid");
                if (address != 0) {
                    return address;
                }
            }
        }
        return 0;
    }
}
