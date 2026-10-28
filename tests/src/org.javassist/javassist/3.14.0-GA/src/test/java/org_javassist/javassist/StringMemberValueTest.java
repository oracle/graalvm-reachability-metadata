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

public class StringMemberValueTest {
    public @interface Names {
        String[] value();
    }

    @Test
    void usesStringMemberTypesWhenConvertingAnnotationArrays() throws Exception {
        ConstPool constants = new ConstPool("example.NamesOwner");
        StringMemberValue name = new StringMemberValue("primary", constants);
        ArrayMemberValue names = new ArrayMemberValue(name, constants);
        names.setValue(new MemberValue[]{name});
        Annotation annotation = new Annotation(Names.class.getName(), constants);
        annotation.addMemberValue("value", names);
        Names result = (Names) annotation.toAnnotationType(Names.class.getClassLoader(), null);
        assertArrayEquals(new String[]{"primary"}, result.value());
        name.setValue("secondary");
        assertArrayEquals(new String[]{"secondary"}, result.value());
    }
}
