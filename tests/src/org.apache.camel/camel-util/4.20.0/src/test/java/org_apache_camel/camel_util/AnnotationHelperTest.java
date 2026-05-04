/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.camel.util.AnnotationHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationHelperTest {
    @Test
    void findsAnnotatedMethodsOnClassHierarchy() {
        List<Method> methods = AnnotationHelper.findMethodsWithAnnotation(AnnotatedChild.class, CamelMarker.class);

        assertThat(methods)
                .extracting(Method::getName)
                .contains("childOperation", "parentOperation")
                .doesNotContain("unannotatedOperation");
    }

    @Test
    void findsMetaAnnotatedMethodsWhenEnabled() {
        List<Method> methods = AnnotationHelper.findMethodsWithAnnotation(
                MetaAnnotatedFixture.class, CamelMarker.class, true);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactly("metaAnnotatedOperation");
    }

    @Test
    void readsMethodAnnotationValuesByName() {
        Method method = onlyMethodAnnotatedWith(MethodValueFixture.class, CamelMarker.class);

        assertThat(AnnotationHelper.getAnnotationValue(method, CamelMarker.class.getName())).isEqualTo("method-value");
        assertThat(AnnotationHelper.getAnnotationValue(method, CamelMarker.class.getName(), "priority")).isEqualTo(7);
    }

    @Test
    void readsFieldAnnotationValuesByName() throws NoSuchFieldException {
        Field field = FieldValueFixture.class.getDeclaredField("annotatedField");

        assertThat(AnnotationHelper.getAnnotationValue(field, CamelMarker.class.getName())).isEqualTo("field-value");
        assertThat(AnnotationHelper.getAnnotationValue(field, CamelMarker.class.getName(), "priority")).isEqualTo(11);
    }

    private static Method onlyMethodAnnotatedWith(Class<?> clazz, Class<CamelMarker> annotationType) {
        List<Method> methods = AnnotationHelper.findMethodsWithAnnotation(clazz, annotationType);

        assertThat(methods).hasSize(1);
        return methods.get(0);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
    public @interface CamelMarker {
        String value();

        int priority() default 0;
    }

    @CamelMarker("meta-marker")
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MetaCamelMarker {
    }

    private static class AnnotatedParent {
        @CamelMarker("parent")
        void parentOperation() {
        }
    }

    private static final class AnnotatedChild extends AnnotatedParent {
        @CamelMarker("child")
        void childOperation() {
        }

        void unannotatedOperation() {
        }
    }

    private static final class MetaAnnotatedFixture {
        @MetaCamelMarker
        void metaAnnotatedOperation() {
        }
    }

    private static final class MethodValueFixture {
        @CamelMarker(value = "method-value", priority = 7)
        void annotatedMethod() {
        }
    }

    private static final class FieldValueFixture {
        @CamelMarker(value = "field-value", priority = 11)
        private String annotatedField;
    }
}
