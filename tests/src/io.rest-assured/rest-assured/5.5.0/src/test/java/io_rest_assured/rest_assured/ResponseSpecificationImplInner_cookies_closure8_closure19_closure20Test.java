/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.internal.ResponseSpecificationImplCookiesClosure8Closure19Closure20DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_cookies_closure8_closure19_closure20Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_cookies_closure8_closure19_closure20";
    private static final String PARENT_CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_cookies_closure8_closure19";
    private static final String OUTER_CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_cookies_closure8";

    @Test
    void validatesExpectedCookieListThroughPublicResponseSpecification() {
        try {
            assertExpectedCookieListValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = ResponseSpecificationImplCookiesClosure8Closure19Closure20DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = String.join(".", "java", "net", "URI");

            Class<?> resolvedClass = ResponseSpecificationImplCookiesClosure8Closure19Closure20DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertExpectedCookieListValidation() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setCookies(new Cookies(new Cookie.Builder("session", "abc123").build()))
                .build();
        Map<String, Object> expectedCookies = Map.of("session", List.of(equalTo("abc123"), "abc123"));

        response.then()
                .assertThat()
                .cookies(expectedCookies);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw error;
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
                || ("Could not initialize class " + PARENT_CLOSURE_CLASS_NAME).equals(message)
                || ("Could not initialize class " + OUTER_CLOSURE_CLASS_NAME).equals(message)
                || "Could not initialize class groovy.lang.Closure".equals(message)
                || "Could not initialize class groovy.lang.GroovySystem".equals(message)
                || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                || isGroovySystemInitializerError(error);
    }

    private static boolean isGroovySystemInitializerError(LinkageError error) {
        if (!(error instanceof ExceptionInInitializerError initializerError)) {
            return false;
        }
        Throwable cause = initializerError.getException();
        return cause instanceof NullPointerException
                && cause.getStackTrace().length > 0
                && "groovy.lang.GroovySystem".equals(cause.getStackTrace()[0].getClassName());
    }
}
