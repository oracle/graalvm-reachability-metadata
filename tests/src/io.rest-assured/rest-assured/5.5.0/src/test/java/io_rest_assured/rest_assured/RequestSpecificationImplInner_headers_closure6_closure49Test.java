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

import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_headers_closure6_closure49Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_headers_closure6_closure49";

    @Test
    void generatedHeaderListValueClosureResolvesHeaderClass() throws Throwable {
        assertEquals(Header.class, invokeGeneratedClassLookup(Header.class.getName()));
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = Class.forName(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }
}
