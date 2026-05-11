/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplSendHttpRequestClosure27DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_sendHttpRequest_closure27Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_sendHttpRequest_closure27";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingSendHttpRequestClosure27Target";
    private static final byte[] CHILD_LOADED_CLOSURE_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQALQEAEGphdmEvbGFuZy9PYmplY3QHAAEBAAY8aW5pdD4BAAMoKVYBAAY8aW5pdD4B
            AAMoKVYMAAUABgoAAgAHAQAPamF2YS9sYW5nL0NsYXNzBwAJAQAHZm9yTmFtZQEAJShMamF2YS9s
            YW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFzczsMAAsADAoACgANAQAgamF2YS9sYW5nL0NsYXNz
            Tm90Rm91bmRFeGNlcHRpb24HAA8BAB5qYXZhL2xhbmcvTm9DbGFzc0RlZkZvdW5kRXJyb3IHABEB
            AApnZXRNZXNzYWdlAQAUKClMamF2YS9sYW5nL1N0cmluZzsMABMAFAoAEAAVAQAGPGluaXQ+AQAV
            KExqYXZhL2xhbmcvU3RyaW5nOylWDAAXABgKABIAGQEAS2lvL3Jlc3Rhc3N1cmVkL2ludGVybmFs
            L1JlcXVlc3RTcGVjaWZpY2F0aW9uSW1wbCRfc2VuZEh0dHBSZXF1ZXN0X2Nsb3N1cmUyNwcAGwEA
            b2lvX3Jlc3RfYXNzdXJlZC9yZXN0X2Fzc3VyZWQvUmVxdWVzdFNwZWNpZmljYXRpb25JbXBsSW5u
            ZXJfc2VuZEh0dHBSZXF1ZXN0X2Nsb3N1cmUyN1Rlc3QkR2VuZXJhdGVkQ2xhc3NSZXNvbHZlcgcA
            HQEABmNsYXNzJAEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFzczsMAB8AIAoA
            HAAhAQAEQ29kZQEADVN0YWNrTWFwVGFibGUBAApFeGNlcHRpb25zAQATamF2YS9sYW5nL1Rocm93
            YWJsZQcAJgEAClNvdXJjZUZpbGUBAB9SZXF1ZXN0U3BlY2lmaWNhdGlvbkltcGwuZ3Jvb3Z5AQAG
            Y2xhc3MkAQAlKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL0NsYXNzOwEAB3Jlc29sdmUA
            IQAcAAIAAQAeAAAAAwABAAMABAABACMAAAARAAEAAQAAAAUqtwAIsQAAAAAAAQAsACsAAgAjAAAA
            EQABAAIAAAAFK7gAIrAAAAAAACUAAAAEAAEAJwAIACoAKwABACMAAAAyAAMAAgAAABIquAAOsEy7
            ABJZK7YAFrcAGr8AAQAAAAUABQAQAAEAJAAAAAYAAUUHABAAAQAoAAAAAgAp
            """);

    public interface GeneratedClassResolver {
        Class<?> resolve(String className) throws Throwable;
    }

    @Test
    void childLoadedClosureClassResolverUsesClassForName() throws Throwable {
        try {
            GeneratedClassResolver resolver = newChildLoadedGeneratedResolver();

            Class<?> resolvedClass = resolver.resolve(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                    RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Expected compiler-generated resolver to reject a missing class");
        } catch (NoClassDefFoundError error) {
            if (!error.getMessage().contains(MISSING_CLASS_NAME)) {
                rethrowUnlessUnsupportedNativeImageError(error);
                return;
            }
            assertTrue(error.getMessage().contains(MISSING_CLASS_NAME));
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void sendHttpRequestClosureConfiguresGetRequestAndResponseHandlers() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/echo", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            byte[] response = (exchange.getRequestMethod() + " " + query).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();
        try {
            Response response = RestAssured.given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .queryParam("from", "query")
                    .formParam("fromForm", "form")
                    .get("/echo");

            assertEquals(200, response.statusCode());
            assertEquals("GET from=query&fromForm=form", response.asString());
        } catch (LinkageError error) {
            assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
        } finally {
            server.stop(0);
            executorService.shutdownNow();
            try {
                assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while stopping test HTTP server", exception);
            }
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplSendHttpRequestClosure27DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    private static GeneratedClassResolver newChildLoadedGeneratedResolver() throws Throwable {
        IsolatedClosureClassLoader classLoader = new IsolatedClosureClassLoader();
        Class<?> generatedClass = classLoader.defineActiveClosureClass(CHILD_LOADED_CLOSURE_CLASS);
        MethodHandle constructor = MethodHandles.publicLookup().findConstructor(
                generatedClass,
                MethodType.methodType(void.class));
        return (GeneratedClassResolver) constructor.invoke();
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Error error) throws Error {
        if (NativeImageSupport.isUnsupportedFeatureError(error) || isNativeClosureInitializationFailure(error)) {
            return;
        }
        throw error;
    }

    private static boolean isNativeClosureInitializationFailure(Error error) {
        return error instanceof NoClassDefFoundError
                && (("Could not initialize class " + CLOSURE_CLASS_NAME).equals(error.getMessage())
                || "Could not initialize class groovy.lang.Closure".equals(error.getMessage()));
    }

    private static final class IsolatedClosureClassLoader extends ClassLoader {
        private IsolatedClosureClassLoader() {
            super(RequestSpecificationImplInner_sendHttpRequest_closure27Test.class.getClassLoader());
        }

        private Class<?> defineActiveClosureClass(byte[] classBytes) {
            return defineClass(CLOSURE_CLASS_NAME, classBytes, 0, classBytes.length);
        }
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
