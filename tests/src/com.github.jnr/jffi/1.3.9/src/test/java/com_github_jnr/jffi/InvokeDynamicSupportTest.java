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
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InvokeDynamicSupportTest {
    @Test
    void bindsAndInvokesANumericFunction() throws Throwable {
        Library library = NativeLibrarySupport.standardLibrary();
        long address = NativeLibrarySupport.symbol(library, "abs");
        CallContext context = CallContext.getCallContext(Type.SINT, new Type[]{Type.SINT}, CallingConvention.DEFAULT, true);
        try {
            InvokeDynamicSupport.Invoker invoker = InvokeDynamicSupport.getFastNumericInvoker(context, address);
            assertNotNull(invoker);
            MethodHandle handle = (MethodHandle) invoker.getMethodHandle();
            assertNotNull(handle);
            assertEquals(47L, ((Number) handle.invokeWithArguments(-47)).longValue());
            assertEquals(12L, ((Number) handle.invokeWithArguments(12)).longValue());
        } finally {
            Reference.reachabilityFence(context);
            Reference.reachabilityFence(library);
        }
    }
}
