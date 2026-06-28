/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jboss.arquillian.container.test.spi.util.TestRunners;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.spi.util.SecurityActions";

    @Test
    void getFieldsWithAnnotationFindsAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = invokeGetFieldsWithAnnotation(ChildTarget.class, Marker.class);
        ChildTarget target = new ChildTarget();

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("baseValue", "childValue");
        assertThat(fieldValue(fields, target, "baseValue")).isEqualTo("base");
        assertThat(fieldValue(fields, target, "childValue")).isEqualTo("child");
    }

    @SuppressWarnings("unchecked")
    private static List<Field> invokeGetFieldsWithAnnotation(
            Class<?> source,
            Class<? extends Annotation> annotationClass) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod(
                "getFieldsWithAnnotation",
                Class.class,
                Class.class);
        method.setAccessible(true);
        return (List<Field>) method.invoke(null, source, annotationClass);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return TestRunners.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static Object fieldValue(
            List<Field> fields,
            ChildTarget target,
            String fieldName) throws Exception {
        for (Field field : fields) {
            if (field.getName().equals(fieldName)) {
                return field.get(target);
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class BaseTarget {
        @Marker
        private final String baseValue = "base";

        @SuppressWarnings("unused")
        private final String ignoredBaseValue = "ignored";
    }

    private static final class ChildTarget extends BaseTarget {
        @Marker
        private final String childValue = "child";

        @SuppressWarnings("unused")
        private final String ignoredChildValue = "ignored";
    }
}
