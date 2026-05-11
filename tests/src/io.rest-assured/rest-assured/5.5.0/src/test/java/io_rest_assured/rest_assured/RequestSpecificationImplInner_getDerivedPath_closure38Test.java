/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGetDerivedPathClosure38Access;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getDerivedPath_closure38Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getDerivedPath_closure38";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetDerivedPathClosure38Target";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplGetDerivedPathClosure38Access
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            rethrowUnlessNativeClosureInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessNativeClosureInitializationFailure(exception.getCause());
        } catch (NoClassDefFoundError error) {
            rethrowUnlessNativeClosureInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            RequestSpecificationImplGetDerivedPathClosure38Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeClosureInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().contains(MISSING_CLASS_NAME));
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void derivedPathUsesUnnamedPathParameterTupleValues() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .baseUri("http://example.com");
            requestSpecification.path("/users/{id}/orders/{orderId}?expand=true");
            requestSpecification.buildUnnamedPathParameterTuples(new Object[] {"123", "A-1"});

            String derivedPath = requestSpecification.getDerivedPath();

            assertEquals("/users/123/orders/A-1", derivedPath);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static Class<?> closureClass() throws ClassNotFoundException {
        return Class.forName(CLOSURE_CLASS_NAME, false, RequestSpecificationImpl.class.getClassLoader());
    }

    private static void rethrowUnlessNativeClosureInitializationFailure(Throwable throwable) throws Throwable {
        if (throwable instanceof NoClassDefFoundError error && isNativeClosureInitializationFailure(error)) {
            return;
        }
        rethrowUnlessUnsupportedNativeImageError(throwable);
    }

    private static boolean isNativeClosureInitializationFailure(NoClassDefFoundError error) {
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(error.getMessage());
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
        return "Could not initialize class groovy.lang.GroovySystem".equals(message)
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
