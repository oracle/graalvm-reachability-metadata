/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandles;
import java.util.Base64;

import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplNewFilterContextClosure13DirectAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_newFilterContext_closure13Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_newFilterContext_closure13";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingNewFilterContextClosure13Target";
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAFQEAVmlvL3Jlc3Rhc3N1cmVkL2ludGVybmFsL1JlcXVlc3RTcGVjaWZpY2F0
            aW9uSW1wbE5ld0ZpbHRlckNvbnRleHRDbG9zdXJlMTNEaXJlY3RJbnZva2VyBwABAQAQamF2
            YS9sYW5nL09iamVjdAcAAwEABjxpbml0PgEAAygpVgwABQAGCgAEAAcBAARDb2RlAQAIPGNs
            aW5pdD4BAExpby9yZXN0YXNzdXJlZC9pbnRlcm5hbC9SZXF1ZXN0U3BlY2lmaWNhdGlvbklt
            cGwkX25ld0ZpbHRlckNvbnRleHRfY2xvc3VyZTEzBwALAQAGY2xhc3MkAQAlKExqYXZhL2xh
            bmcvU3RyaW5nOylMamF2YS9sYW5nL0NsYXNzOwwADQAOCgAMAA8BADBpby5yZXN0YXNzdXJl
            ZC5pbnRlcm5hbC5SZXF1ZXN0U3BlY2lmaWNhdGlvbkltcGwIABEBAApTb3VyY2VGaWxlAQBD
            UmVxdWVzdFNwZWNpZmljYXRpb25JbXBsTmV3RmlsdGVyQ29udGV4dENsb3N1cmUxM0RpcmVj
            dEludm9rZXIuamF2YQAhAAIABAAAAAAAAgABAAUABgABAAkAAAARAAEAAQAAAAUqtwAIsQAA
            AAAACAAKAAYAAQAJAAAAEwABAAAAAAAHEhK4ABBXsQAAAAAAAQATAAAAAgAU
            """);

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplNewFilterContextClosure13DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    RequestSpecificationImpl.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverExercisesClassNotFoundBranch() throws Throwable {
        try {
            NoClassDefFoundError error = assertThrows(
                    NoClassDefFoundError.class,
                    () -> RequestSpecificationImplNewFilterContextClosure13DirectAccess
                            .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME));

            assertTrue(error.getMessage().contains(MISSING_CLASS_NAME));
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Error error) throws Error {
        if (NativeImageSupport.isUnsupportedFeatureError(error) || isNativeClosureInitializationFailure(error)) {
            return;
        }
        throw error;
    }

    private static boolean isNativeClosureInitializationFailure(Error error) {
        return error instanceof NoClassDefFoundError
                && ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(error.getMessage());
    }
}
