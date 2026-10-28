/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.invoke.MethodType;

import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaConstantInnerMethodTypeInnerDispatcherInnerCreationActionTest {
    @Test
    void describesNoArgumentMethodType() {
        JavaConstant.MethodType constant = JavaConstant.MethodType.ofLoaded(MethodType.methodType(void.class));
        assertEquals("()V", constant.getDescriptor());
        assertTrue(constant.getParameterTypes().isEmpty());
        assertTrue(constant.getReturnType().represents(void.class));
    }
}
