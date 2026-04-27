/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.PrivilegedExceptionAction;

import org.junit.jupiter.api.Test;

public class SecurityActions7Test {
    private static final String SET_FIELD_ACTION_CLASS = "javassist.util.proxy.SecurityActions$7";

    @Test
    void privilegedExceptionActionWritesFieldValue() throws Throwable {
        Class<?> actionClass = Class.forName(SET_FIELD_ACTION_CLASS);
        MethodHandle constructor = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup()).findConstructor(
                actionClass,
                MethodType.methodType(void.class, Field.class, Object.class, Object.class));
        MutableTarget target = new MutableTarget();
        Field field = MutableTarget.class.getField("value");
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(field, target, "updated");

        action.run();

        assertThat(target.value).isEqualTo("updated");
    }

    public static class MutableTarget {
        public String value = "original";
    }
}
