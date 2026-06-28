/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

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
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.test.impl.enricher.resource.SecurityActions";

    @Test
    void getMethodsWithAnnotationFindsAnnotatedDeclaredMethodsAcrossClassHierarchy() throws Exception {
        List<Method> annotatedMethods = invokeGetMethodsWithAnnotation(
                ChildMethodResource.class, MethodResource.class);

        assertThat(annotatedMethods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("childResource", "parentResource");
        for (Method method : annotatedMethods) {
            assertThat(method.isAccessible()).isTrue();
            assertThat(method.invoke(new ChildMethodResource())).isEqualTo(method.getName());
        }
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
    private @interface MethodResource {
    }

    private static class ParentMethodResource {
        @MethodResource
        private String parentResource() {
            return "parentResource";
        }

        public String unrelatedParentMethod() {
            return "unrelated";
        }
    }

    private static final class ChildMethodResource extends ParentMethodResource {
        @MethodResource
        private String childResource() {
            return "childResource";
        }

        public String unrelatedChildMethod() {
            return "unrelated";
        }
    }
}
