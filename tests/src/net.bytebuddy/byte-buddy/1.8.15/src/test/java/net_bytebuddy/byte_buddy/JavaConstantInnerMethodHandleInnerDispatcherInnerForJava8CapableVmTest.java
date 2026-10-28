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

public class JavaConstantInnerMethodHandleInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void revealsConstructorHandleWithExplicitLookup() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle handle = lookup.findConstructor(StringBuilder.class, MethodType.methodType(void.class, String.class));
        JavaConstant.MethodHandle constant = JavaConstant.MethodHandle.ofLoaded(handle, lookup);
        assertEquals(JavaConstant.MethodHandle.HandleType.INVOKE_SPECIAL_CONSTRUCTOR, constant.getHandleType());
        assertEquals("<init>", constant.getName());
        assertTrue(constant.getOwnerType().represents(StringBuilder.class));
        assertTrue(constant.getReturnType().represents(void.class));
        assertEquals("(Ljava/lang/String;)V", constant.getDescriptor());
    }
}
