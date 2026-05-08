/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import java.lang.reflect.Field;
import java.util.Set;

import io.smallrye.config.ConfigMappingContext;
import io.smallrye.config.SmallRyeConfig;

final class GeneratedConfigMappingSupport {
    private static final Field CONFIG_FIELD = declaredField(ConfigMappingContext.class, "config");
    private static final Field USED_PROPERTIES_FIELD = declaredField(ConfigMappingContext.class, "usedProperties");

    private GeneratedConfigMappingSupport() {
    }

    static SmallRyeConfig config(final ConfigMappingContext context) {
        try {
            return (SmallRyeConfig) CONFIG_FIELD.get(context);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void markUsed(final ConfigMappingContext context, final String... propertyNames) {
        try {
            @SuppressWarnings("unchecked")
            Set<String> usedProperties = (Set<String>) USED_PROPERTIES_FIELD.get(context);
            for (String propertyName : propertyNames) {
                usedProperties.add(propertyName);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setField(final Class<?> declaringClass, final Object target, final String fieldName, final Object value) {
        try {
            declaredField(declaringClass, fieldName).set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field declaredField(final Class<?> declaringClass, final String name) {
        try {
            Field field = declaringClass.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
