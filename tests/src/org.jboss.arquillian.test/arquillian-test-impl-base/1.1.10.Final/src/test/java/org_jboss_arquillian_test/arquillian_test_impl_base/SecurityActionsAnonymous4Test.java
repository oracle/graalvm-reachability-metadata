/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
        "org.jboss.arquillian.test.impl.enricher.resource.SecurityActions";

    @Test
    void getMethodsWithAnnotationFindsAnnotatedMethodsAcrossClassHierarchy() throws Throwable {
        MethodHandle getMethodsWithAnnotation = securityActionsLookup().findStatic(securityActionsType(),
            "getMethodsWithAnnotation", MethodType.methodType(List.class, Class.class, Class.class));

        @SuppressWarnings("unchecked")
        List<Method> methods = (List<Method>) getMethodsWithAnnotation.invoke(AnnotatedChild.class, TestMarker.class);

        assertThat(methods)
            .extracting(Method::getName)
            .containsExactlyInAnyOrder("childResourceMethod", "parentResourceMethod");
    }

    private static MethodHandles.Lookup securityActionsLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securityActionsType(), MethodHandles.lookup());
    }

    private static Class<?> securityActionsType() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface TestMarker {
    }

    private static class AnnotatedParent {
        @TestMarker
        private void parentResourceMethod() {
        }

        private void unmarkedParentMethod() {
        }
    }

    private static final class AnnotatedChild extends AnnotatedParent {
        @TestMarker
        private void childResourceMethod() {
        }

        private void unmarkedChildMethod() {
        }
    }
}
