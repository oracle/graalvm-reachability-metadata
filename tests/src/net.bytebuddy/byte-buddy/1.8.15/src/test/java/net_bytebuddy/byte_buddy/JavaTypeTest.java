/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaTypeTest {
    @Test
    void resolvesRuntimeTypesAndTheirDescriptions() throws Exception {
        assertEquals(MethodType.class, JavaType.METHOD_TYPE.load());
        assertTrue(JavaType.METHOD_TYPE.getTypeStub().isInstance(MethodType.methodType(String.class)));
        assertEquals(MethodHandles.Lookup.class, JavaType.METHOD_HANDLES_LOOKUP.load());
        assertTrue(JavaType.METHOD_HANDLES_LOOKUP.getTypeStub().isInstance(MethodHandles.lookup()));
        assertTrue(JavaType.MODULE.getTypeStub().isInstance(String.class.getModule()));
        for (JavaType type : JavaType.values()) {
            assertTrue(type.getTypeStub().represents(type.load()));
        }
    }
}
