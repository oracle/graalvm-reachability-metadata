/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jboss.arquillian.container.test.spi.util.TestRunners;
import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous3Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.spi.util.SecurityActions";

    @Test
    void getFieldsWithAnnotationFindsAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = getFieldsWithAnnotation(ChildFieldFixture.class, Marker.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactly("childField", "parentField");
        assertThat(fields)
                .allMatch(Field::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Field> getFieldsWithAnnotation(
            Class<?> source,
            Class<? extends Annotation> annotationClass) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod(
                "getFieldsWithAnnotation",
                Class.class,
                Class.class);
        method.setAccessible(true);
        return (List<Field>) method.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return TestRunners.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentFieldFixture {
        @Marker
        private String parentField;
    }

    private static final class ChildFieldFixture extends ParentFieldFixture {
        @Marker
        private String childField;

        private String unannotatedField;
    }
}
