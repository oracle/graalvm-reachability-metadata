/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RestAssuredResponseOptionsGroovyImpl;
import io.restassured.internal.RestAssuredResponseOptionsGroovyImplAsClosure3Access;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredResponseOptionsGroovyImplInner_as_closure3Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl"
            + Character.toString((char) 36)
            + "_as_closure3";

    @Test
    void compilerGeneratedClassResolverUsesClassForNameForRestAssuredClass() throws Throwable {
        try {
            Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplAsClosure3Access
                    .resolveWithCompilerGeneratedClassResolver(RestAssuredResponseOptionsGroovyImpl.class.getName());

            assertSame(RestAssuredResponseOptionsGroovyImpl.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForNameForOwnClosureClass() throws Throwable {
        try {
            Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplAsClosure3Access
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void responseAsWithoutContentTypeCallsActiveClosure() {
        Response response = new ResponseBuilder()
                .setBody("{\"message\":\"hello\"}")
                .setContentType("")
                .build();

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> response.as(Message.class));

            assertTrue(error.getMessage().contains("Cannot parse content to class "));
            assertTrue(error.getMessage().contains(Message.class.getName()));
            assertTrue(error.getMessage().contains("no content-type was present"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static final class Message {
        private String message;

        String getMessage() {
            return message;
        }
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
