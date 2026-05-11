/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RequestSpecificationImpl;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertSame;

public class RequestSpecificationImplAnonymous1Test {
    private static final String ANONYMOUS_SERIALIZER_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl$1";

    @Test
    void invokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> anonymousSerializerClass = RequestSpecificationImpl.class.getClassLoader()
                    .loadClass(ANONYMOUS_SERIALIZER_CLASS_NAME);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    anonymousSerializerClass,
                    MethodHandles.lookup());
            MethodHandle classResolver = lookup.findStatic(
                    anonymousSerializerClass,
                    "class$",
                    MethodType.methodType(Class.class, String.class));

            Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
