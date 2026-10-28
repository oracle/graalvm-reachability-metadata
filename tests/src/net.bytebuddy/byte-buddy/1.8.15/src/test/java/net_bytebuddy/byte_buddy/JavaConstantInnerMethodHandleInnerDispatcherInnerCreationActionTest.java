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

import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaConstantInnerMethodHandleInnerDispatcherInnerCreationActionTest {
    @Test
    void convertsStaticHandleToConstantPoolEntry() throws Exception {
        MethodHandle handle = MethodHandles.publicLookup().findStatic(Math.class, "max",
                MethodType.methodType(int.class, int.class, int.class));
        JavaConstant.MethodHandle constant = JavaConstant.MethodHandle.ofLoaded(handle);
        Handle entry = (Handle) constant.asConstantPoolValue();
        assertEquals("java/lang/Math", entry.getOwner());
        assertEquals("max", entry.getName());
        assertEquals("(II)I", entry.getDesc());
        assertEquals(Opcodes.H_INVOKESTATIC, entry.getTag());
    }
}
