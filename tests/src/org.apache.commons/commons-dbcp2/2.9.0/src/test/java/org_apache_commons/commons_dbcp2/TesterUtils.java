/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import java.lang.reflect.Field;

public final class TesterUtils {
    public static Object getField(final Object target, final String fieldName)
            throws Exception {
        final Class<?> clazz = target.getClass();
        final Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private TesterUtils() {
    }
}
