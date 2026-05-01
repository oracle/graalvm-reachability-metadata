/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.core.spi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.core.spi.context.Context;

public final class SecurityActionsInvoker {
    private static final String MANAGER_IMPL_CLASS = "org.jboss.arquillian.core.impl.ManagerImpl";

    private SecurityActionsInvoker() {
    }

    public static Manager createManagerWithClassLoader(ClassLoader classLoader) {
        Collection<Class<? extends Context>> contexts = Collections.emptySet();
        Collection<Class<?>> extensions = Collections.emptySet();
        return SecurityActions.newInstance(
                MANAGER_IMPL_CLASS,
                new Class<?>[] {Collection.class, Collection.class},
                new Object[] {contexts, extensions},
                Manager.class,
                classLoader);
    }

    public static String setPrivateFieldValue(String originalValue, String updatedValue) {
        MutableTarget target = new MutableTarget(originalValue);
        try {
            SecurityActions.setFieldValue(MutableTarget.class, target, "value", updatedValue);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("MutableTarget.value should be available", e);
        }
        return target.value;
    }

    public static List<String> annotatedFieldNames() {
        List<Field> fields = SecurityActions.getFieldsWithAnnotation(AnnotatedChild.class, CoveredField.class);
        List<String> names = new ArrayList<String>();
        for (Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    private static final class MutableTarget {
        private String value;

        private MutableTarget(String value) {
            this.value = value;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface CoveredField {
    }

    private static class AnnotatedParent {
        @CoveredField
        private String parentValue;
    }

    private static final class AnnotatedChild extends AnnotatedParent {
        @CoveredField
        private String childValue;

        private String unannotatedValue;
    }
}
