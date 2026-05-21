/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_threads.jboss_threads;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.PrivilegedAction;

import org.junit.jupiter.api.Test;

public class DeclaredFieldActionTest {
    @Test
    void runReturnsDeclaredFieldWhenNameExists() throws Throwable {
        PrivilegedAction<Field> action = declaredFieldAction(Thread.class, "contextClassLoader");

        Field field = action.run();

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isSameAs(Thread.class);
        assertThat(field.getName()).isEqualTo("contextClassLoader");
    }

    @SuppressWarnings("unchecked")
    private static PrivilegedAction<Field> declaredFieldAction(Class<?> targetClass, String fieldName) throws Throwable {
        Class<?> actionClass = Class.forName("org.jboss.threads.DeclaredFieldAction");
        MethodHandle constructor = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup())
                .findConstructor(actionClass, MethodType.methodType(void.class, Class.class, String.class));
        return (PrivilegedAction<Field>) constructor.invoke(targetClass, fieldName);
    }
}
