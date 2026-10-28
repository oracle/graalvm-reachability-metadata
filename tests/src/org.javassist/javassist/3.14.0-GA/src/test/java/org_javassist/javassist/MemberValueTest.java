/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemberValueTest {
    public @interface Payload {
        Class<?> value();
    }

    @Test
    void resolvesClassValuedAnnotationMembersIncludingArrayTypes() throws Exception {
        ConstPool constants = new ConstPool("example.PayloadOwner");
        ClassMemberValue member = new ClassMemberValue("java.lang.String[][]", constants);
        Annotation annotation = new Annotation(Payload.class.getName(), constants);
        annotation.addMemberValue("value", member);
        Payload payload = (Payload) annotation.toAnnotationType(Payload.class.getClassLoader(), null);
        assertEquals(String[][].class, payload.value());
        member.setValue("java.lang.Integer");
        assertEquals(Integer.class, payload.value());
        member.setValue("int[]");
        assertEquals(int[].class, payload.value());
    }
}
