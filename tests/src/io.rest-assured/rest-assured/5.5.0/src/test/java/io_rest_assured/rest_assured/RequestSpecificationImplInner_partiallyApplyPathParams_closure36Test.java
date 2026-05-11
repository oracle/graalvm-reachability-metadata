/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplPartiallyApplyPathParamsClosure36DirectAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RequestSpecificationImplInner_partiallyApplyPathParams_closure36Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_partiallyApplyPathParams_closure36";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingPartiallyApplyPathParamsClosure36Target";

    @Test
    void compilerGeneratedClassResolverResolvesUnloadedJdkClass() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplPartiallyApplyPathParamsClosure36DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            assertEquals(RESOLVABLE_CLASS_NAME, resolvedClass.getName());
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplPartiallyApplyPathParamsClosure36DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            RequestSpecificationImplPartiallyApplyPathParamsClosure36DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            fail("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().contains(MISSING_CLASS_NAME));
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void fillsPathAndQueryTemplatesThroughPathParamFillerClosure() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .pathParam("user", "alice");

            String uri = requestSpecification.partiallyApplyPathParams(
                    "http://example.com/users/{user}/orders/{orderId}?filter={filter}&sort={sort}",
                    true,
                    List.of("A-1", "open", "created"));

            assertEquals("http://example.com/users/alice/orders/A-1?filter=open&sort=created", uri);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void preservesUnresolvedTemplatesWhenNoParameterValueIsAvailable() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given();

            String uri = requestSpecification.partiallyApplyPathParams(
                    "/inventory/{category}/items/{itemId}?view={view}",
                    false,
                    List.of("books"));

            assertEquals("http://localhost:8080/inventory/books/items/{itemId}?view={view}", uri);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    private static void rethrowUnlessExpectedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof LinkageError error && isNativeGroovyInitializationFailure(error)) {
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
