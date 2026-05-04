/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous4Test {
    @Test
    void findsAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        List<Method> methods = getMethodsWithAnnotation(ChildComponent.class, Marker.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("childAction", "parentAction");
    }

    @SuppressWarnings("unchecked")
    private static List<Method> getMethodsWithAnnotation(
            Class<?> source,
            Class<Marker> annotationClass) throws Exception {
        Class<?> securityActions = Class.forName("org.jboss.arquillian.core.spi.SecurityActions");
        Method getMethodsWithAnnotation = securityActions.getDeclaredMethod(
                "getMethodsWithAnnotation",
                Class.class,
                Class.class);
        getMethodsWithAnnotation.setAccessible(true);
        return (List<Method>) getMethodsWithAnnotation.invoke(null, source, annotationClass);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentComponent {
        @Marker
        private void parentAction() {
        }

        private void ignoredParentAction() {
        }
    }

    private static final class ChildComponent extends ParentComponent {
        @Marker
        private void childAction() {
        }

        private void ignoredChildAction() {
        }
    }
}
