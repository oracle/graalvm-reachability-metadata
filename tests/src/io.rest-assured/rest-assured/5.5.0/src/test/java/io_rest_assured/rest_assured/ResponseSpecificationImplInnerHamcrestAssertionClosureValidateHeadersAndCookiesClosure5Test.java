/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure5Access;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureValidateHeadersAndCookiesClosure5Test {
    @Test
    void resolvesValidateHeadersAndCookiesClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRestAssuredClassOnlyKnownAtRuntimeThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "http", "CharsetExtractor");

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesValidateHeadersAndCookiesClosureThroughJavaReflectionDispatch() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = resolveWithJavaReflectionDispatch(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure5Missing"));
    }

    @Test
    void validatesHeaderMatchersThroughValidateHeadersAndCookiesClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectHeader("X-Response-Mode", equalTo("validated"))
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setStatusLine("HTTP/1.1 200 OK")
                .setHeader("X-Response-Mode", "validated")
                .build();

        specification.validate(response);
    }

    @Test
    void reportsHeaderMatcherFailuresThroughValidateHeadersAndCookiesClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectHeader("X-Response-Mode", equalTo("validated"))
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setStatusLine("HTTP/1.1 200 OK")
                .setHeader("X-Response-Mode", "unexpected")
                .build();

        AssertionError error = assertThrows(AssertionError.class, () -> specification.validate(response));

        assertTrue(error.getMessage().contains("1 expectation failed"));
        assertTrue(error.getMessage().contains("X-Response-Mode"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure5Access
                .resolveWithGeneratedDirectAccess(className);
    }

    private static Class<?> resolveWithJavaReflectionDispatch(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure5Access
                .resolveWithJavaReflectionDispatch(className);
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_HamcrestAssertionClosure_validateHeadersAndCookies_closure5";
    }
}
