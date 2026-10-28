/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.javassist.bytecode.ConstPool;
import org.apache.ibatis.javassist.bytecode.annotation.Annotation;
import org.apache.ibatis.javassist.bytecode.annotation.ClassMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MemberValueTest {
    @Test
    void resolvesArrayClassInAnnotationMember() throws Exception {
        ConstPool pool = new ConstPool("ClassAnnotationModel");
        Annotation model = AnnotationSupport.annotation(AnnotationSupport.Type.class, pool);
        model.addMemberValue("value", new ClassMemberValue("java.lang.String[]", pool));
        AnnotationSupport.Type annotation = (AnnotationSupport.Type) model.toAnnotationType(getClass().getClassLoader(), null);
        assertEquals(String[].class, annotation.value());
    }
}
