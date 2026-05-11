/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RequestSpecificationImplSendRequestClosure17DirectAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequestSpecificationImplInner_sendRequest_closure17Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_sendRequest_closure17";
    private static final String TARGET_CLASS_NAME = "io.restassured.internal.ResponseParserRegistrar";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingSendRequestClosure17Target";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(TARGET_CLASS_NAME);

            assertEquals(TARGET_CLASS_NAME, resolvedClass.getName());
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

            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplSendRequestClosure17DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error
                && (NativeImageSupport.isUnsupportedFeatureError(error)
                        || isNativeClosureInitializationFailure(error))) {
            return;
        }
        throw throwable;
    }

    private static boolean isNativeClosureInitializationFailure(Error error) {
        return error instanceof NoClassDefFoundError
                && (("Could not initialize class " + CLOSURE_CLASS_NAME).equals(error.getMessage())
                        || "Could not initialize class groovy.lang.Closure".equals(error.getMessage()));
    }
}
