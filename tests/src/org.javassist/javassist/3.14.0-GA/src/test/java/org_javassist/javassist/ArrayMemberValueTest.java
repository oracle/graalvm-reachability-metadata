/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArrayMemberValueTest {
    public @interface Tags {
        String[] value();
    }

    @Test
    void materializesAnnotationArraysWithTheirDeclaredComponentType() throws Exception {
        ConstPool constants = new ConstPool("example.TagOwner");
        ArrayMemberValue values = new ArrayMemberValue(new StringMemberValue(constants), constants);
        values.setValue(new MemberValue[]{new StringMemberValue("first", constants), new StringMemberValue("second", constants)});
        Annotation annotation = new Annotation(Tags.class.getName(), constants);
        annotation.addMemberValue("value", values);
        Tags tags = (Tags) annotation.toAnnotationType(Tags.class.getClassLoader(), null);
        assertArrayEquals(new String[]{"first", "second"}, tags.value());
        assertEquals(String[].class, tags.value().getClass());
        values.setValue(new MemberValue[0]);
        assertArrayEquals(new String[0], tags.value());
    }
}
