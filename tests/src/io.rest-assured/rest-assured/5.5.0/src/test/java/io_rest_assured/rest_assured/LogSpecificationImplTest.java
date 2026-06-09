/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.restassured.internal.LogSpecificationImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogSpecificationImplTest {
    @Test
    void resolvesRequestLogSpecificationUsingGeneratedGroovyClassHelper() throws Throwable {
        MethodHandle classHelper = generatedClassLookup();
        String requestLogSpecificationClassName = "io.restassured.internal.RequestLogSpecificationImpl";

        Class<?> result = invokeGeneratedClassLookup(classHelper, requestLogSpecificationClassName);

        assertThat(result.getName()).isEqualTo(requestLogSpecificationClassName);
    }

    private static MethodHandle generatedClassLookup() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LogSpecificationImpl.class, MethodHandles.lookup());
        return lookup.findStatic(LogSpecificationImpl.class, "class$", MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> invokeGeneratedClassLookup(MethodHandle classHelper, String className) throws Throwable {
        return (Class<?>) classHelper.invokeExact(className);
    }
}
