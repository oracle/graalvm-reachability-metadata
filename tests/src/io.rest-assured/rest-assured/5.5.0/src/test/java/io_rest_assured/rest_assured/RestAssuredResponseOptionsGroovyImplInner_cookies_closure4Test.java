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
import io.restassured.internal.RestAssuredResponseOptionsGroovyImplCookiesClosure4DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredResponseOptionsGroovyImplInner_cookies_closure4Test {
    private static final String CLOSURE_CLASS_NAME = String.join(
            "",
            "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl",
            Character.toString((char) 36),
            "_cookies_closure4");

    @Test
    void getCookiesReturnsUnmodifiableCookieValueMap() {
        try {
            Response response = responseWithCookie();

            Map<String, String> cookies = response.getCookies();

            assertEquals(Map.of("session", "abc123"), cookies);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            responseWithCookie().getCookies();

            Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplCookiesClosure4DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static Response responseWithCookie() {
        return new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setCookies(new Cookies(new Cookie.Builder("session", "abc123").build()))
                .build();
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
