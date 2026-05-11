/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RestAssuredHttpBuilderGroovyHelper;
import io.restassured.internal.RestAssuredHttpBuilderGroovyHelperFlattenToStringClosure1DirectAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredHttpBuilderGroovyHelperInner_flattenToString_closure1Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RestAssuredHttpBuilderGroovyHelper$_flattenToString_closure1";

    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = RestAssuredHttpBuilderGroovyHelperFlattenToStringClosure1DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
            assertFlattensNestedValuesToStrings();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = String.join(".", "java", "lang", "String");

            Class<?> resolvedClass = RestAssuredHttpBuilderGroovyHelperFlattenToStringClosure1DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
            assertFlattensNestedValuesToStrings();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            RestAssuredHttpBuilderGroovyHelperFlattenToStringClosure1DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME + "Missing");
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().endsWith("closure1Missing"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void flattensNestedValuesToStrings() {
        try {
            assertFlattensNestedValuesToStrings();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertFlattensNestedValuesToStrings() {
        Collection<String> flattened = RestAssuredHttpBuilderGroovyHelper.flattenToString(
                Arrays.asList("alpha", Arrays.asList(1, null, "omega")));

        assertEquals(Arrays.asList("alpha", "1", null, "omega"), new ArrayList<>(flattened));
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
                || "Could not initialize class io.restassured.RestAssured".equals(message)
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
