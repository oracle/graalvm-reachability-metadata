/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal.common.assertion;

public final class AssertParameter {
    private AssertParameter() {
    }

    public static <T> T notNull(T object, Class<?> type) {
        return notNull(object, type.getSimpleName());
    }

    public static <T> T notNull(T object, String parameterName) {
        if (object == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
        return object;
    }
}
