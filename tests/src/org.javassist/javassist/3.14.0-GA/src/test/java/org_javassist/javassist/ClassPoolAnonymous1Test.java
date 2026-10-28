/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassPoolAnonymous1Test {
    @Test
    void initializesPoolsAndResolvesClassesFromAParentPool() throws Exception {
        ClassPool parent = TestSupport.classPool();
        CtClass annotation = parent.get("javassist.bytecode.annotation.Annotation");
        ClassPool child = new ClassPool(parent);
        assertSame(annotation, child.get(annotation.getName()));
        assertEquals("(Ljava/lang/String;)Ljavassist/bytecode/annotation/MemberValue;",
                child.get(annotation.getName()).getDeclaredMethod("getMemberValue").getSignature());
        assertSame(CtClass.intType, child.get("int"));
    }
}
