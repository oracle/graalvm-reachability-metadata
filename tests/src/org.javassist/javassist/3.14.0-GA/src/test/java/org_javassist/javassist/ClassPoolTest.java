/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassPoolTest {
    @Test
    void inspectsAndCachesAnExistingLibraryClassWithoutDefiningClasses() throws Exception {
        ClassPool pool = TestSupport.classPool();
        CtClass type = pool.get("javassist.bytecode.ClassFile");
        CtMethod method = type.getDeclaredMethod("getName");
        assertEquals("()Ljava/lang/String;", method.getSignature());
        assertEquals("javassist.bytecode.ClassFile", method.getDeclaringClass().getName());
        assertSame(type, pool.get(type.getName()));
        assertEquals("java.lang.Object", type.getSuperclass().getName());
        type.detach();
        assertEquals("javassist.bytecode.ClassFile", pool.get("javassist.bytecode.ClassFile").getName());
    }
}
