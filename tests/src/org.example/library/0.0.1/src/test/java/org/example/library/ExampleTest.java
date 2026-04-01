/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.example.library;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExampleTest {

    @Test
    void coversExactlyHalfOfReflectiveCallSites() {
        Class<?> targetClass = invokePrivate("loadClass", new Class<?>[]{String.class}, "org.example.library.ReflectionTarget");
        Constructor<?> constructor = invokePrivate("noArgConstructor", new Class<?>[]{Class.class}, targetClass);
        Object target = invokePrivate("instantiate", new Class<?>[]{Constructor.class}, constructor);
        Method prefixMethod = invokePrivate("method", new Class<?>[]{Class.class, String.class, Class[].class}, targetClass, "prefix", new Class<?>[0]);

        assertNotNull(target);
        assertEquals("prefix", prefixMethod.getName());
    }

    @Test
    void coversExactlyHalfOfResourceCallSites() {
        List<String> reflectionLines = invokePrivate(
                "loadResourceLines",
                new Class<?>[]{String.class},
                "/org/example/library/messages/reflection-branch.txt"
        );
        Method unusedProbe = invokePrivate("method", new Class<?>[]{Class.class, String.class, Class[].class},
                BranchingLibrary.class, "unusedResourceProbe", new Class<?>[]{String.class});

        assertEquals(List.of("alpha", "beta", "gamma"), reflectionLines);
        assertNotNull(unusedProbe);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokePrivate(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = BranchingLibrary.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(null, arguments);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError("Failed to invoke " + methodName, exception);
        }
    }
}
