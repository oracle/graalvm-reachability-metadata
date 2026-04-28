/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.PrivilegedExceptionAction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous6Test {
    @Test
    void privilegedExceptionActionRunSetsFieldValue() throws Throwable {
        Class<?> actionClass = SecurityActionsAnonymous6Test.class.getClassLoader()
                .loadClass("javassist.util.proxy.SecurityActions$6");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(
                actionClass,
                MethodType.methodType(void.class, Field.class, Object.class, Object.class));
        MutableTarget target = new MutableTarget("initial");
        Field valueField = MutableTarget.class.getField("value");
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(
                valueField,
                target,
                "updated");

        Object result = action.run();

        assertThat(result).isNull();
        assertThat(target.value()).isEqualTo("updated");
    }

    public static class MutableTarget {
        public String value;

        public MutableTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
