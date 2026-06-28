/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.container.test.spi.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    @Test
    void getFieldsWithAnnotationFindsAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = SecurityActions.getFieldsWithAnnotation(ChildTarget.class, Marker.class);
        ChildTarget target = new ChildTarget();

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("baseValue", "childValue");
        assertThat(fieldValue(fields, target, "baseValue")).isEqualTo("base");
        assertThat(fieldValue(fields, target, "childValue")).isEqualTo("child");
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
