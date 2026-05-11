/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.RestAssured;
import io.restassured.http.Headers;
import io.restassured.internal.RequestSpecificationImplRemoveMergedHeadersIfNeededClosure7DirectAccess;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static io.restassured.config.HeaderConfig.headerConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_removeMergedHeadersIfNeeded_closure7";
    private static final String RESOLVABLE_CLASS_NAME = "io.restassured.http.Header";

    @Test
    void directCompilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplRemoveMergedHeadersIfNeededClosure7DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void configuredOverwritableHeadersReplaceEarlierCaseInsensitiveValues() {
        try {
            RequestSpecification requestSpecification = RestAssured.given()
                    .config(RestAssured.config().headerConfig(headerConfig().overwriteHeadersWithName("X-Trace")))
                    .header("X-Trace", "first")
                    .header("x-trace", "second")
                    .header("X-Other", "kept");

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            Headers headers = queryableSpecification.getHeaders();

            assertEquals(List.of("second"), headers.getValues("X-Trace"));
            assertEquals("kept", headers.getValue("X-Other"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
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
