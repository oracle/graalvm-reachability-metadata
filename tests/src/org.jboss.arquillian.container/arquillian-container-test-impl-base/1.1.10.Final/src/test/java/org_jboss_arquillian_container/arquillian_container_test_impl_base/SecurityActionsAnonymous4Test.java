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
import java.lang.reflect.Method;
import java.util.List;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.impl.SecurityActions";

    @Test
    void getMethodsWithAnnotationFindsAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        List<Method> methods = invokeGetMethodsWithAnnotation(ChildOperations.class, Marker.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("childOperation", "parentOperation");
        assertThat(methods).allMatch(Method::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Method> invokeGetMethodsWithAnnotation(
            Class<?> source,
            Class<? extends Annotation> annotationClass) throws Exception {
        Method getMethodsWithAnnotationMethod = securityActionsClass().getDeclaredMethod(
                "getMethodsWithAnnotation",
                Class.class,
                Class.class);
        getMethodsWithAnnotationMethod.setAccessible(true);
        return (List<Method>) getMethodsWithAnnotationMethod.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return RemoteExtensionLoader.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentOperations {
        @Marker
        private void parentOperation() {
        }
    }

    private static final class ChildOperations extends ParentOperations {
        @Marker
        private void childOperation() {
        }

        @SuppressWarnings("unused")
        private void unmarkedOperation() {
        }
    }
}
