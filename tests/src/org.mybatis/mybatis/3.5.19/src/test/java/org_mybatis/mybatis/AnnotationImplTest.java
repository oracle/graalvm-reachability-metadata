/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.javassist.bytecode.ConstPool;
import org.apache.ibatis.javassist.bytecode.annotation.Annotation;
import org.apache.ibatis.javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationImplTest {
    @Test
    void convertsAnnotationModelToTypedAnnotation() throws Exception {
        ConstPool pool = new ConstPool("AnnotationModel");
        Annotation model = AnnotationSupport.annotation(AnnotationSupport.Label.class, pool);
        model.addMemberValue("value", new StringMemberValue("worker", pool));
        AnnotationSupport.Label annotation = (AnnotationSupport.Label) model.toAnnotationType(getClass().getClassLoader(), null);
        AnnotationSupport.Label declared = AnnotationSupport.Annotated.class.getAnnotation(AnnotationSupport.Label.class);
        assertEquals("worker", annotation.value());
        assertEquals(AnnotationSupport.Label.class, annotation.annotationType());
        assertEquals(declared, annotation);
        assertEquals(annotation, declared);
        assertEquals(declared.hashCode(), annotation.hashCode());
    }
}
