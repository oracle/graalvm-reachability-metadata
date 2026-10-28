/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.invoke.MethodType;

import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaConstantInnerMethodTypeInnerDispatcherInnerForJava7CapableVmTest {
    @Test
    void preservesReturnAndParameterTypes() {
        JavaConstant.MethodType constant = JavaConstant.MethodType.ofLoaded(
                MethodType.methodType(String.class, int.class, long.class));
        assertTrue(constant.getReturnType().represents(String.class));
        assertEquals(2, constant.getParameterTypes().size());
        assertTrue(constant.getParameterTypes().get(0).represents(int.class));
        assertTrue(constant.getParameterTypes().get(1).represents(long.class));
        assertEquals(Type.getMethodType("(IJ)Ljava/lang/String;"), constant.asConstantPoolValue());
    }
}
