/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.internal.RedirectSpecificationImpl;
import io.restassured.specification.RedirectSpecification;
import io.restassured.specification.RequestSpecification;
import org.apache.http.client.params.ClientPNames;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RedirectSpecificationImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeExact(
                "io.restassured.internal.RedirectSpecificationImpl");

        assertSame(RedirectSpecificationImpl.class, resolvedClass);
    }

    @Test
    void loadsNamedClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = "java.lang.String";

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    RedirectSpecificationImpl.class,
                    "class$",
                    new Object[] {"io.restassured.internal.RedirectSpecificationImpl"});

            assertSame(RedirectSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void loadsNamedClassThroughGroovyStaticDispatch() {
        String className = "java.lang.String";

        try {
            Class<?> resolvedClass = (Class<?>) InvokerHelper.invokeStaticMethod(
                    RedirectSpecificationImpl.class,
                    "class$",
                    new Object[] {className});

            assertEquals(className, resolvedClass.getName());
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(
                        "io.restassured.internal.RedirectSpecificationImplMissingClass"));

        assertEquals("io.restassured.internal.RedirectSpecificationImplMissingClass", error.getMessage());
    }

    @Test
    void createsRedirectSpecificationThroughRequestSpecificationDsl() {
        RedirectSpecification redirectSpecification = RestAssured.given().redirects();

        assertEquals(RedirectSpecificationImpl.class, redirectSpecification.getClass());
    }

    @Test
    void storesRedirectOptionsAsHttpClientParameters() {
        RequestSpecification requestSpecification = RestAssured.given();
        Map<String, Object> httpClientParams = new HashMap<>();
        RedirectSpecificationImpl redirectSpecification = new RedirectSpecificationImpl(
                requestSpecification,
                httpClientParams);

        assertSame(requestSpecification, redirectSpecification.max(12));
        assertSame(requestSpecification, redirectSpecification.follow(false));
        assertSame(requestSpecification, redirectSpecification.allowCircular(true));
        assertSame(requestSpecification, redirectSpecification.rejectRelative(true));

        assertEquals(12, httpClientParams.get(ClientPNames.MAX_REDIRECTS));
        assertEquals(false, httpClientParams.get(ClientPNames.HANDLE_REDIRECTS));
        assertEquals(true, httpClientParams.get(ClientPNames.ALLOW_CIRCULAR_REDIRECTS));
        assertEquals(true, httpClientParams.get(ClientPNames.REJECT_RELATIVE_REDIRECT));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RedirectSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                RedirectSpecificationImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
