/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.impl.SecurityActions";

    @Test
    void getFieldsWithAnnotationFindsAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = invokeGetFieldsWithAnnotation(ChildHolder.class, Marker.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("childValue", "parentValue");
        assertThat(fields).allMatch(Field::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Field> invokeGetFieldsWithAnnotation(
            Class<?> source,
            Class<? extends Annotation> annotationClass) throws Exception {
        Method getFieldsWithAnnotationMethod = securityActionsClass().getDeclaredMethod(
                "getFieldsWithAnnotation",
                Class.class,
                Class.class);
        getFieldsWithAnnotationMethod.setAccessible(true);
        return (List<Field>) getFieldsWithAnnotationMethod.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return RemoteExtensionLoader.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentHolder {
        @Marker
        private String parentValue;
    }

    private static final class ChildHolder extends ParentHolder {
        @Marker
        private String childValue;

        @SuppressWarnings("unused")
        private String unmarkedValue;
    }
}
