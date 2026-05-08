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
import java.lang.reflect.Method;
import java.util.List;

import org.jboss.arquillian.container.test.spi.util.TestRunners;
import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.spi.util.SecurityActions";

    @Test
    void getMethodsWithAnnotationFindsAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        List<Method> methods = getMethodsWithAnnotation(ChildMethodFixture.class, Marker.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactly("childMethod", "parentMethod");
        assertThat(methods)
                .allMatch(Method::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Method> getMethodsWithAnnotation(
            Class<?> source,
            Class<? extends Annotation> annotationClass) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod(
                "getMethodsWithAnnotation",
                Class.class,
                Class.class);
        method.setAccessible(true);
        return (List<Method>) method.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return TestRunners.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentMethodFixture {
        @Marker
        private void parentMethod() {
        }
    }

    private static final class ChildMethodFixture extends ParentMethodFixture {
        @Marker
        private void childMethod() {
        }

        private void unannotatedMethod() {
        }
    }
}
