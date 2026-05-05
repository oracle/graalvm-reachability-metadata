/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous3Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.test.spi.SecurityActions";

    @Test
    void getFieldsWithAnnotationFindsAnnotatedPrivateFieldsAcrossClassHierarchy() throws Exception {
        ChildComponent component = new ChildComponent();

        List<Field> fields = invokeGetFieldsWithAnnotation(ChildComponent.class, Injected.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("childDependency", "parentDependency");
        assertThat(fieldValue(fields, "childDependency", component)).isEqualTo("child");
        assertThat(fieldValue(fields, "parentDependency", component)).isEqualTo("parent");
    }

    private static List<Field> invokeGetFieldsWithAnnotation(
            Class<?> source, Class<? extends Annotation> annotationClass) throws Exception {
        Method getFieldsWithAnnotation = securityActionsClass().getDeclaredMethod(
                "getFieldsWithAnnotation", Class.class, Class.class);
        getFieldsWithAnnotation.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Field> fields = (List<Field>) getFieldsWithAnnotation.invoke(null, source, annotationClass);
        return fields;
    }

    private static Object fieldValue(List<Field> fields, String fieldName, Object target) throws IllegalAccessException {
        Field matchingField = fields.stream()
                .filter(field -> field.getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field " + fieldName));
        return matchingField.get(target);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface Injected {
    }

    private static class ParentComponent {
        @Injected
        private final String parentDependency = "parent";

        @SuppressWarnings("unused")
        private final String ignoredParentDependency = "ignored parent";
    }

    private static final class ChildComponent extends ParentComponent {
        @Injected
        private final String childDependency = "child";

        @SuppressWarnings("unused")
        private final String ignoredChildDependency = "ignored child";
    }
}
