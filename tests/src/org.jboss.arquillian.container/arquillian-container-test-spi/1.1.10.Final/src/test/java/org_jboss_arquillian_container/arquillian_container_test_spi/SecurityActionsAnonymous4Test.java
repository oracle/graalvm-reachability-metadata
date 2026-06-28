/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.container.test.spi.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous4Test {
    @Test
    void getMethodsWithAnnotationFindsAnnotatedMethodsAcrossClassHierarchy() throws Exception {
        List<Method> methods = SecurityActions.getMethodsWithAnnotation(ChildTarget.class, Marker.class);
        ChildTarget target = new ChildTarget();

        assertThat(methods)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("baseValue", "childValue");
        assertThat(methodValue(methods, target, "baseValue")).isEqualTo("base");
        assertThat(methodValue(methods, target, "childValue")).isEqualTo("child");
    }

    private static Object methodValue(
            List<Method> methods,
            ChildTarget target,
            String methodName) throws Exception {
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                return method.invoke(target);
            }
        }
        throw new AssertionError("Method not found: " + methodName);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class BaseTarget {
        @Marker
        private String baseValue() {
            return "base";
        }

        @SuppressWarnings("unused")
        private String ignoredBaseValue() {
            return "ignored";
        }
    }

    private static final class ChildTarget extends BaseTarget {
        @Marker
        private String childValue() {
            return "child";
        }

        @SuppressWarnings("unused")
        private String ignoredChildValue() {
            return "ignored";
        }
    }
}
