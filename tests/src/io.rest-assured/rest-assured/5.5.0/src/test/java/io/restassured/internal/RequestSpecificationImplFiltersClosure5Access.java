/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class RequestSpecificationImplFiltersClosure5Access {
    private RequestSpecificationImplFiltersClosure5Access() {
    }

    public static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    public static Class<?> resolveWithJavaReflectionDispatch(String className) throws Throwable {
        Method classResolver = RequestSpecificationImpl$_filters_closure5.class.getDeclaredMethod(
                "class$",
                String.class);
        classResolver.setAccessible(true);
        try {
            return (Class<?>) classResolver.invoke(null, className);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static MethodHandle classResolver() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        return lookup.findStatic(
                RequestSpecificationImpl$_filters_closure5.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
