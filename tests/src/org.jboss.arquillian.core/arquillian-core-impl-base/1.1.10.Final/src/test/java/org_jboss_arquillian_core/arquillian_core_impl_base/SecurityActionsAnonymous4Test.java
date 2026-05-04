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
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.core.impl.SecurityActions";

    @Test
    void getMethodsWithAnnotationDiscoversAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        List<Method> methods = invokeGetMethodsWithAnnotation(AnnotatedChild.class, Observed.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("observeChild", "observeInherited");
        assertThat(methods).allMatch(Method::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Method> invokeGetMethodsWithAnnotation(
            Class<?> source, Class<? extends Annotation> annotationClass) throws Exception {
        Method getMethodsWithAnnotation = securityActionsClass().getDeclaredMethod(
                "getMethodsWithAnnotation", Class.class, Class.class);
        getMethodsWithAnnotation.setAccessible(true);
        return (List<Method>) getMethodsWithAnnotation.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Observed {
    }

    @SuppressWarnings("unused")
    private static class AnnotatedBase {
        @Observed
        private void observeInherited() {
        }

        private void ignoredInherited() {
        }
    }

    @SuppressWarnings("unused")
    private static final class AnnotatedChild extends AnnotatedBase {
        @Observed
        private void observeChild() {
        }

        private void ignoredChild() {
        }
    }
}
