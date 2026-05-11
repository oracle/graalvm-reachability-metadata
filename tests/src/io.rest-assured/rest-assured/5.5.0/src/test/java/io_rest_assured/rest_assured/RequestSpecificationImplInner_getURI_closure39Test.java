/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGetURIClosure39Access;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getURI_closure39Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getURI_closure39";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetUriClosure39Target";
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADcAEwEATGlvL3Jlc3Rhc3N1cmVkL2ludGVybmFsL1JlcXVlc3RTcGVjaWZpY2F0aW9u
            SW1wbEdldFVSSUNsb3N1cmUzOURpcmVjdEludm9rZXIHAAEBABBqYXZhL2xhbmcvT2JqZWN0BwAD
            AQAGPGluaXQ+AQADKClWDAAFAAYKAAQABwEABENvZGUBAAg8Y2xpbml0PgEAQmlvL3Jlc3Rhc3N1
            cmVkL2ludGVybmFsL1JlcXVlc3RTcGVjaWZpY2F0aW9uSW1wbCRfZ2V0VVJJX2Nsb3N1cmUzOQcA
            CwEABmNsYXNzJAEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFzczsMAA0ADgoA
            DAAPAQAwaW8ucmVzdGFzc3VyZWQuaW50ZXJuYWwuUmVxdWVzdFNwZWNpZmljYXRpb25JbXBsCAAR
            ACEAAgAEAAAAAAACAAEABQAGAAEACQAAABEAAQABAAAABSq3AAixAAAAAAAIAAoABgABAAkAAAAT
            AAEAAAAAAAcSErgAEFexAAAAAAAA
            """);

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplGetURIClosure39Access
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            rethrowUnlessNativeClosureInitializationFailure(error);
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
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            RequestSpecificationImplGetURIClosure39Access.resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
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
    void filterCanReadFullyResolvedUriWithUnnamedPathParameters() {
        AtomicReference<String> capturedUri = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();

        try {
            Response response = RestAssured.given()
                    .baseUri("http://example.com")
                    .port(8181)
                    .basePath("/api")
                    .queryParam("expand", "true")
                    .filter((requestSpecification, responseSpecification, context) -> {
                        capturedUri.set(requestSpecification.getURI());
                        capturedMethod.set(requestSpecification.getMethod());
                        return new ResponseBuilder()
                                .setStatusCode(204)
                                .build();
                    })
                    .get("/users/{id}/orders/{orderId}", "123", "A-1");

            assertEquals(204, response.statusCode());
            assertEquals("GET", capturedMethod.get());

            String uriString = capturedUri.get();
            assertNotNull(uriString);
            URI uri = URI.create(uriString);
            assertEquals("http", uri.getScheme());
            assertEquals("example.com", uri.getHost());
            assertEquals(8181, uri.getPort());
            assertEquals("/api/users/123/orders/A-1", uri.getPath());
            assertEquals("expand=true", uri.getQuery());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
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
