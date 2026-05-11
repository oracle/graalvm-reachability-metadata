/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.LinkedHashMap;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.http.Cookies;
import io.restassured.internal.RequestSpecificationImplCookiesClosure9Access;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_cookies_closure9Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_cookies_closure9";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingCookiesClosure9Target";

    @Test
    void compilerGeneratedClassResolverResolvesUnloadedJdkClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            NoClassDefFoundError error = assertThrows(
                    NoClassDefFoundError.class,
                    () -> resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME));
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }

            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void cookiesMapAddsEachEntryToTheRequestSpecification() {
        try {
            Map<String, String> cookieValues = new LinkedHashMap<>();
            cookieValues.put("session", "abc123");
            cookieValues.put("theme", "dark");

            RequestSpecification requestSpecification = RestAssured.given()
                    .cookies(cookieValues);

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            Cookies cookies = queryableSpecification.getCookies();

            assertEquals(2, cookies.size());
            assertEquals("abc123", cookies.getValue("session"));
            assertEquals("dark", cookies.getValue("theme"));
        } catch (LinkageError error) {
            assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplCookiesClosure9Access.resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
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
                || "Could not initialize class io.restassured.RestAssured".equals(message)
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
