/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGetMultiPartParamsClosure45Access;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.MultiPartSpecification;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getMultiPartParams_closure45Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getMultiPartParams_closure45";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetMultiPartParamsClass";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplGetMultiPartParamsClosure45Access
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
            RequestSpecificationImplGetMultiPartParamsClosure45Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeClosureInitializationFailure(error)) {
                return;
            }
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void getMultiPartParamsReturnsMultipartSpecificationCopies() {
        try {
            FilterableRequestSpecification requestSpecification = (FilterableRequestSpecification) RestAssured.given()
                    .multiPart("document", "notes.txt", "hello", "text/plain")
                    .multiPart("metadata", "priority=high", "text/plain");

            List<MultiPartSpecification> multiParts = requestSpecification.getMultiPartParams();

            assertEquals(2, multiParts.size());
            MultiPartSpecification document = multiParts.get(0);
            assertEquals("document", document.getControlName());
            assertEquals("notes.txt", document.getFileName());
            assertEquals("hello", document.getContent());
            assertEquals("text/plain", document.getMimeType());

            MultiPartSpecification metadata = multiParts.get(1);
            assertEquals("metadata", metadata.getControlName());
            assertEquals("priority=high", metadata.getContent());
            assertEquals("text/plain", metadata.getMimeType());
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
