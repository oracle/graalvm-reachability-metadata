/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class LombokLaunchTestSupport {
    private LombokLaunchTestSupport() {
    }

    static Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    static Object newInstance(String className, Class<?>[] parameterTypes, Object... args) throws Throwable {
        Constructor<?> constructor = loadClass(className).getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) throws Throwable {
        return invoke(null, loadClass(className), methodName, parameterTypes, args);
    }

    static Object invoke(Object target, Class<?> declaringClass, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Throwable {
        Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    static void withShadowOverride(ThrowingAction action) throws Throwable {
        String previousOverride = System.getProperty("shadow.override.lombok");
        System.setProperty("shadow.override.lombok", findShadowOverrideRoot().toString());
        try {
            action.run();
        } finally {
            if (previousOverride == null) {
                System.clearProperty("shadow.override.lombok");
            } else {
                System.setProperty("shadow.override.lombok", previousOverride);
            }
        }
    }

    private static Path findShadowOverrideRoot() {
        for (Path candidate : List.of(
                Path.of("build", "resources", "test"),
                Path.of("..", "resources", "test"),
                Path.of("..", "..", "resources", "test"))) {
            Path absoluteCandidate = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absoluteCandidate.resolve("META-INF/ShadowClassLoader"))) {
                return absoluteCandidate;
            }
        }

        throw new IllegalStateException("Could not locate the Lombok shadow override resources directory");
    }

    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Throwable;
    }
}
