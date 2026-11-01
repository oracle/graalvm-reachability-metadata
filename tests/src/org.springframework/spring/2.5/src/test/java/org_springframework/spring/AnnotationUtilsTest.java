/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AnnotationUtilsTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Route {
        String value() default "default";
        int order() default 1;
    }

    public static class Parent {
        @Route(value = "greeting", order = 3)
        public void execute() {
        }
    }

    public static class Child extends Parent {
        @Override
        public void execute() {
        }
    }

    @Test
    void findsInheritedMethodAnnotationAndExtractsAttributes() throws Exception {
        Method method = Child.class.getMethod("execute");
        Route route = AnnotationUtils.findAnnotation(method, Route.class);
        assertNotNull(route);
        assertEquals("greeting", AnnotationUtils.getValue(route));
        assertEquals(3, AnnotationUtils.getAnnotationAttributes(route).get("order"));
        assertEquals("default", AnnotationUtils.getDefaultValue(route));
    }
}
