/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomicArrayTest {

    @Test
    void addAndRemoveSupportNonHandlerComponentTypes() throws Exception {
        Holder holder = new Holder();
        AtomicReferenceFieldUpdater<Holder, String[]> updater =
                AtomicReferenceFieldUpdater.newUpdater(Holder.class, String[].class, "values");
        Object atomicArray = newAtomicArray(updater, String.class);

        invokeMethod(atomicArray, "add", new Class<?>[] {Object.class, Object.class }, holder, "console");
        invokeMethod(atomicArray, "add", new Class<?>[] {Object.class, Object.class }, holder, "file");

        assertThat(holder.values).containsExactly("console", "file");
        assertThat(invokeMethod(
                atomicArray,
                "remove",
                new Class<?>[] {Object.class, Object.class, boolean.class },
                holder,
                "console",
                false
        )).isEqualTo(Boolean.TRUE);
        assertThat(holder.values).containsExactly("file");

        invokeMethod(atomicArray, "clear", new Class<?>[] {Object.class }, holder);
        assertThat(holder.values).isEmpty();
    }

    private static Object newAtomicArray(
            final AtomicReferenceFieldUpdater<Holder, String[]> updater,
            final Class<String> componentType
    ) throws Exception {
        Class<?> atomicArrayType = Class.forName("org.jboss.logmanager.AtomicArray");
        Constructor<?> constructor = atomicArrayType.getDeclaredConstructor(AtomicReferenceFieldUpdater.class, Class.class);
        constructor.setAccessible(true);
        return constructor.newInstance(updater, componentType);
    }

    private static Object invokeMethod(
            final Object target,
            final String methodName,
            final Class<?>[] parameterTypes,
            final Object... arguments
    ) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static final class Holder {
        volatile String[] values = new String[0];
    }
}
