/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_junit.arquillian_junit_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous3Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.junit.SecurityActions";

    @Test
    void getFieldsWithAnnotationReturnsAnnotatedFieldsFromClassHierarchy() throws Exception {
        List<Field> fields = invokeGetFieldsWithAnnotation(ChildSubject.class, CoveredField.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactly("childValue", "parentValue");
        assertThat(fields).allMatch(Field::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Field> invokeGetFieldsWithAnnotation(
            Class<?> source, Class<? extends Annotation> annotationClass) throws Exception {
        Method getFieldsWithAnnotation = Class.forName(SECURITY_ACTIONS_CLASS_NAME).getDeclaredMethod(
                "getFieldsWithAnnotation", Class.class, Class.class);
        getFieldsWithAnnotation.setAccessible(true);
        return (List<Field>) getFieldsWithAnnotation.invoke(null, source, annotationClass);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface CoveredField {
    }

    private static class ParentSubject {
        @CoveredField
        private String parentValue;
    }

    private static final class ChildSubject extends ParentSubject {
        @CoveredField
        private String childValue;

        private String ignoredValue;
    }
}
