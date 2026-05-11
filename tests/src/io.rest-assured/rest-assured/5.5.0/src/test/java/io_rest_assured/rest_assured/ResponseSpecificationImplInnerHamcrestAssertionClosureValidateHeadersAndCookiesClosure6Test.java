/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Access;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Test {
    @Test
    void resolvesValidateHeadersAndCookiesClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure6Missing"));
    }

    @Test
    void resolvesRestAssuredClassOnlyKnownAtRuntimeThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "assertion", "CookieMatcher");

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
    void reportsMissingClassThroughJavaReflectionDispatch() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithJavaReflectionDispatch(activeClosureClassName() + "Unavailable"));

        assertTrue(error.getMessage().endsWith("closure6Unavailable"));
    }

    @Test
    void validatesCookieMatchersThroughValidateHeadersAndCookiesClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectCookie("session", equalTo("active"))
                .build();
        Response response = responseWithSetCookie("session=active; Path=/; HttpOnly");

        specification.validate(response);
    }

    @Test
    void reportsCookieMatcherFailuresThroughValidateHeadersAndCookiesClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectCookie("session", equalTo("active"))
                .build();
        Response response = responseWithSetCookie("session=expired; Path=/; HttpOnly");

        AssertionError error = assertThrows(AssertionError.class, () -> specification.validate(response));

        assertTrue(error.getMessage().contains("1 expectation failed"));
        assertTrue(error.getMessage().contains("session"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Access
                .resolveWithGeneratedDirectAccess(className);
    }

    private static Class<?> resolveWithJavaReflectionDispatch(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Access
                .resolveWithJavaReflectionDispatch(className);
    }

    private static Response responseWithSetCookie(String setCookieHeader) {
        return new ResponseBuilder()
                .setStatusCode(200)
                .setStatusLine("HTTP/1.1 200 OK")
                .setHeader("Set-Cookie", setCookieHeader)
                .build();
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_HamcrestAssertionClosure_validateHeadersAndCookies_closure6";
    }
}
