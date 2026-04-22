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
}
