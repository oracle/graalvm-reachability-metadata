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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    @Test
    void findsAnnotatedFieldsAcrossClassHierarchy() throws Exception {
        List<Field> fields = getFieldsWithAnnotation(ChildComponent.class, Marker.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactly("childValue", "parentValue");
    }

    @SuppressWarnings("unchecked")
    private static List<Field> getFieldsWithAnnotation(
            Class<?> source,
            Class<Marker> annotationClass) throws Exception {
        Class<?> securityActions = Class.forName("org.jboss.arquillian.core.spi.SecurityActions");
        Method getFieldsWithAnnotation = securityActions.getDeclaredMethod(
                "getFieldsWithAnnotation",
                Class.class,
                Class.class);
        getFieldsWithAnnotation.setAccessible(true);
        return (List<Field>) getFieldsWithAnnotation.invoke(null, source, annotationClass);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static class ParentComponent {
        @Marker
        private String parentValue;

        private String ignoredParentValue;
    }

    private static final class ChildComponent extends ParentComponent {
        @Marker
        private String childValue;

        private String ignoredChildValue;
    }
}
