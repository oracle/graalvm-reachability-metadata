/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

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

public class LoadableSecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.core.impl.loadable.SecurityActions";

    @Test
    void getFieldsWithAnnotationDiscoversAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = invokeGetFieldsWithAnnotation(AnnotatedChild.class, InjectedField.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("childInjection", "inheritedInjection");
        assertThat(fields).allMatch(Field::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Field> invokeGetFieldsWithAnnotation(
            Class<?> source, Class<? extends Annotation> annotationClass) throws Exception {
        Method getFieldsWithAnnotation = securityActionsClass().getDeclaredMethod(
                "getFieldsWithAnnotation", Class.class, Class.class);
        getFieldsWithAnnotation.setAccessible(true);
        return (List<Field>) getFieldsWithAnnotation.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface InjectedField {
    }

    @SuppressWarnings("unused")
    private static class AnnotatedBase {
        @InjectedField
        private String inheritedInjection;

        private String inheritedPlainField;
    }

    @SuppressWarnings("unused")
    private static final class AnnotatedChild extends AnnotatedBase {
        @InjectedField
        private String childInjection;

        private String childPlainField;
    }
}
