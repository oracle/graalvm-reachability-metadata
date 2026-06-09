/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplAnonymous1Test {
    private static final String SERIALIZER_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl$1";

    @Test
    void resolvesStringClassUsingGeneratedGroovyClassHelper() throws Throwable {
        Class<?> serializerClass = MethodHandles.lookup().findClass(SERIALIZER_CLASS_NAME);

        Class<?> resolvedClass = invokeGeneratedClassLookup(serializerClass, String.class.getName());
        assertThat(resolvedClass).isEqualTo(String.class);
    }

    private static Class<?> invokeGeneratedClassLookup(Class<?> serializerClass, String className) throws Exception {
        Method classHelper = serializerClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
