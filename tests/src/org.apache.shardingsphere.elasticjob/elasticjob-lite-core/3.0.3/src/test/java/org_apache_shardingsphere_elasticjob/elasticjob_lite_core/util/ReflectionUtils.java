/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Field;

@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReflectionUtils {
    @SneakyThrows
    public static void setFieldValue(final Object target, final String fieldName, final Object fieldValue) {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, fieldValue);
    }

    @SneakyThrows
    public static void setSuperclassFieldValue(final Object target, final String fieldName, final Object fieldValue) {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, fieldValue);
    }
}
