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
import com.kenai.jffi.Platform;
import com.kenai.jffi.Type;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InvokeDynamicSupportInnerJSR292Test {
    @Test
    void createsALongMethodHandleWithTheContextAndSymbolBound() throws Throwable {
        Library library = NativeLibrarySupport.standardLibrary();
        String symbol = Platform.getPlatform().getOS() == Platform.OS.WINDOWS ? "_abs64" : "llabs";
        long address = NativeLibrarySupport.symbol(library, symbol);
        CallContext context = CallContext.getCallContext(Type.SINT64, new Type[]{Type.SINT64}, CallingConvention.DEFAULT, false);
        try {
            InvokeDynamicSupport.Invoker invoker = InvokeDynamicSupport.getFastNumericInvoker(context, address);
            assertNotNull(invoker);
            MethodHandle handle = (MethodHandle) invoker.getMethodHandle();
            assertNotNull(handle);
            assertEquals(MethodType.methodType(long.class, long.class), handle.type());
            assertEquals(5000000007L, (long) handle.invokeExact(-5000000007L));
            assertEquals(23L, (long) handle.invokeExact(23L));
        } finally {
            Reference.reachabilityFence(context);
            Reference.reachabilityFence(library);
        }
    }
}
