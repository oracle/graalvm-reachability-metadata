/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javassist.ClassPool;
import javassist.CtClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CtArrayTest {
    @Test
    void describesArrayComponentsAndImplementedInterfaces() throws Exception {
        ClassPool pool = TestSupport.classPool();
        CtClass array = pool.get("javassist.bytecode.ClassFile[]");
        assertTrue(array.isArray());
        assertEquals("javassist.bytecode.ClassFile", array.getComponentType().getName());
        assertEquals("java.lang.Object", array.getSuperclass().getName());
        Set<String> interfaces = Arrays.stream(array.getInterfaces()).map(CtClass::getName).collect(Collectors.toSet());
        assertEquals(Set.of("java.lang.Cloneable", "java.io.Serializable"), interfaces);
        assertTrue(array.subtypeOf(pool.get("java.lang.Cloneable")));
        assertTrue(array.subtypeOf(pool.get("java.lang.Object")));
        assertTrue(pool.get("javassist.bytecode.ClassFile[][]").getComponentType().isArray());
    }
}
