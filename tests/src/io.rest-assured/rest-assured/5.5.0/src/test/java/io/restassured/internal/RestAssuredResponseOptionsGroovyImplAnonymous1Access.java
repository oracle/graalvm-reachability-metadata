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

public final class RestAssuredResponseOptionsGroovyImplAnonymous1Access {
    private RestAssuredResponseOptionsGroovyImplAnonymous1Access() {
    }

    public static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver()
            throws NoSuchMethodException, IllegalAccessException, ClassNotFoundException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        return lookup.findStatic(
                loadAnonymousDataToDeserializeClass(),
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> loadAnonymousDataToDeserializeClass() throws ClassNotFoundException {
        String className = RestAssuredResponseOptionsGroovyImpl.class.getName()
                + Character.toString((char) 36)
                + "1";
        return RestAssuredResponseOptionsGroovyImpl.class.getClassLoader().loadClass(className);
    }
}
