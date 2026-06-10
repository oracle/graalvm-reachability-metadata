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
import com.kenai.jffi.Type;
import java.lang.invoke.MethodHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokeDynamicSupportInnerJSR292Test {
    @Test
    void createsBoundMethodHandleForFastNumericCallContext() {
        CallContext callContext = CallContext.getCallContext(
                Type.SINT32,
                new Type[] {Type.SINT32},
                CallingConvention.DEFAULT,
                true);

        InvokeDynamicSupport.Invoker invoker = InvokeDynamicSupport.getFastNumericInvoker(callContext, 0L);

        assertThat(invoker).isNotNull();
        assertThat(invoker.getMethod().getName()).matches("invoke[ILN]1");
        assertThat(invoker.getMethodHandle()).isInstanceOf(MethodHandle.class);

        MethodHandle methodHandle = (MethodHandle) invoker.getMethodHandle();
        assertThat(methodHandle.type().parameterCount()).isEqualTo(1);
    }
}
