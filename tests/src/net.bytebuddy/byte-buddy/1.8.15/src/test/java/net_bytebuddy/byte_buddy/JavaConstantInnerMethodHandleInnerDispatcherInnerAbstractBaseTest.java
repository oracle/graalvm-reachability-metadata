/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaConstantInnerMethodHandleInnerDispatcherInnerAbstractBaseTest {
    @Test
    void describesPublicVirtualMethodHandle() throws Exception {
        MethodHandle handle = MethodHandles.publicLookup().findVirtual(String.class, "substring",
                MethodType.methodType(String.class, int.class));
        JavaConstant.MethodHandle constant = JavaConstant.MethodHandle.ofLoaded(handle);
        assertEquals("substring", constant.getName());
        assertTrue(constant.getOwnerType().represents(String.class));
        assertTrue(constant.getReturnType().represents(String.class));
        assertTrue(constant.getParameterTypes().getOnly().represents(int.class));
        assertEquals("(I)Ljava/lang/String;", constant.getDescriptor());
        assertEquals(JavaConstant.MethodHandle.HandleType.INVOKE_VIRTUAL, constant.getHandleType());
        assertEquals(getClass(), JavaConstant.MethodHandle.lookupType(MethodHandles.lookup()));
    }
}
