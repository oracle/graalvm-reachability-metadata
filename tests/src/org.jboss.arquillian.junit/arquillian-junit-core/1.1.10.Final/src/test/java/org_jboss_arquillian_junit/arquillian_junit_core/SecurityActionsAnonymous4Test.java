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
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous4Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.junit.SecurityActions";

    @Test
    void getMethodsWithAnnotationReturnsAnnotatedMethodsFromClassHierarchy() throws Exception {
        List<Method> methods = invokeGetMethodsWithAnnotation(ChildSubject.class, CoveredMethod.class);

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactly("childAction", "parentAction");
        assertThat(methods).allMatch(Method::isAccessible);
    }

    @SuppressWarnings("unchecked")
    private static List<Method> invokeGetMethodsWithAnnotation(
            Class<?> source, Class<? extends Annotation> annotationClass) throws Exception {
        Method getMethodsWithAnnotation = Class.forName(SECURITY_ACTIONS_CLASS_NAME).getDeclaredMethod(
                "getMethodsWithAnnotation", Class.class, Class.class);
        getMethodsWithAnnotation.setAccessible(true);
        return (List<Method>) getMethodsWithAnnotation.invoke(null, source, annotationClass);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface CoveredMethod {
    }

    private static class ParentSubject {
        @CoveredMethod
        private void parentAction() {
        }
    }

    private static final class ChildSubject extends ParentSubject {
        @CoveredMethod
        private void childAction() {
        }

        private void ignoredAction() {
        }
    }
}
