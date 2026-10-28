/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.annotation.RetentionPolicy;

import org.apache.ibatis.javassist.bytecode.ConstPool;
import org.apache.ibatis.javassist.bytecode.annotation.Annotation;
import org.apache.ibatis.javassist.bytecode.annotation.EnumMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumMemberValueTest {
    @Test
    void materializesEnumValuedAnnotationMember() throws Exception {
        ConstPool pool = new ConstPool("EnumAnnotationModel");
        Annotation model = AnnotationSupport.annotation(AnnotationSupport.Mode.class, pool);
        EnumMemberValue value = new EnumMemberValue(pool);
        value.setType(RetentionPolicy.class.getName());
        value.setValue("RUNTIME");
        model.addMemberValue("value", value);
        AnnotationSupport.Mode annotation = (AnnotationSupport.Mode) model.toAnnotationType(getClass().getClassLoader(), null);
        assertEquals(RetentionPolicy.RUNTIME, annotation.value());
    }
}
