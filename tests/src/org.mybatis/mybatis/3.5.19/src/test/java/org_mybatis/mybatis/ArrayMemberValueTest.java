/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.javassist.bytecode.ConstPool;
import org.apache.ibatis.javassist.bytecode.annotation.Annotation;
import org.apache.ibatis.javassist.bytecode.annotation.ArrayMemberValue;
import org.apache.ibatis.javassist.bytecode.annotation.MemberValue;
import org.apache.ibatis.javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayMemberValueTest {
    @Test
    void materializesArrayValuedAnnotationMember() throws Exception {
        ConstPool pool = new ConstPool("ArrayAnnotationModel");
        Annotation model = AnnotationSupport.annotation(AnnotationSupport.Names.class, pool);
        ArrayMemberValue values = new ArrayMemberValue(pool);
        values.setValue(new MemberValue[]{new StringMemberValue("Ada", pool), new StringMemberValue("Grace", pool)});
        model.addMemberValue("value", values);
        AnnotationSupport.Names annotation = (AnnotationSupport.Names) model.toAnnotationType(getClass().getClassLoader(), null);
        assertArrayEquals(new String[]{"Ada", "Grace"}, annotation.value());
    }
}
