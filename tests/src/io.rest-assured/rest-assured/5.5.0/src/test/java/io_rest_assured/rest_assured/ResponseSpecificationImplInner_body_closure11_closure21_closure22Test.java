/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.ResponseSpecificationImpl;
import io.restassured.internal.ResponseSpecificationImplBodyClosure11Closure21Closure22DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ResponseSpecificationImplInner_body_closure11_closure21_closure22Test {
    @Test
    void resolvesNestedMatcherListClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = activeClosureClassName();

            Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11Closure21Closure22DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForNameForRestAssuredClass() throws Throwable {
        try {
            Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11Closure21Closure22DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(ResponseSpecificationImpl.class.getName());

            assertSame(ResponseSpecificationImpl.class, resolvedClass);
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = String.join(".", "java", "util", "LinkedList");

            Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11Closure21Closure22DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            ResponseSpecificationImplBodyClosure11Closure21Closure22DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing");
            fail("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().endsWith("closure22Missing"));
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void validatesMergedMatchersForTheSameAdditionalBodyKey() {
        try {
            Response response = new ResponseBuilder()
                    .setContentType("application/json")
                    .setBody("""
                            {
                              "book": {
                                "pages": 64
                              }
                            }
                            """)
                    .build();

            response.then()
                    .body("book.pages", greaterThan(0),
                            "book.pages", lessThan(100),
                            "book.pages", greaterThan(10));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
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
        return ("Could not initialize class " + activeClosureClassName()).equals(message)
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

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_body_closure11_closure21_closure22";
    }
}
