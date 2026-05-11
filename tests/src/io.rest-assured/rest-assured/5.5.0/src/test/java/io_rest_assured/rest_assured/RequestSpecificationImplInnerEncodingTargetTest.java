/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInnerEncodingTargetTest {
    private static final String ENCODING_TARGET_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$EncodingTarget";

    @Test
    void invokesCompilerGeneratedClassResolverForEncodingTarget() throws Throwable {
        try {
            Class<?> encodingTargetClass = RequestSpecificationImpl.class.getClassLoader()
                    .loadClass(ENCODING_TARGET_CLASS_NAME);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    encodingTargetClass,
                    MethodHandles.lookup());
            MethodHandle classResolver = lookup.findStatic(
                    encodingTargetClass,
                    "class$",
                    MethodType.methodType(Class.class, String.class));

            Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(ENCODING_TARGET_CLASS_NAME);

            assertSame(encodingTargetClass, resolvedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reflectionInvocationReachesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> encodingTargetClass = RequestSpecificationImpl.class.getClassLoader()
                    .loadClass(ENCODING_TARGET_CLASS_NAME);
            Method classResolver = encodingTargetClass.getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, ENCODING_TARGET_CLASS_NAME);

            assertSame(encodingTargetClass, resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void groovyRuntimeInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> encodingTargetClass = RequestSpecificationImpl.class.getClassLoader()
                    .loadClass(ENCODING_TARGET_CLASS_NAME);

            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    encodingTargetClass,
                    "class$",
                    new Object[] {ENCODING_TARGET_CLASS_NAME});

            assertSame(encodingTargetClass, resolvedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void groovyRuntimeUsesEncodingTargetEnumRangeHelpers() throws Throwable {
        try {
            Class<?> encodingTargetClass = RequestSpecificationImpl.class.getClassLoader()
                    .loadClass(ENCODING_TARGET_CLASS_NAME);
            Object[] constants = encodingTargetClass.getEnumConstants();

            Object nextAfterBody = InvokerHelper.invokeMethod(constants[0], "next", new Object[0]);
            Object previousBeforeQuery = InvokerHelper.invokeMethod(constants[1], "previous", new Object[0]);

            assertSame(constants[1], nextAfterBody);
            assertSame(constants[0], previousBeforeQuery);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void requestEncodingUsesBodyAndQueryEncodingTargets() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> rawPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.setExecutor(executorService);
        server.createContext("/resource", exchange -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
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
                    .pathParam("value", "space value")
                    .formParam("field name", "field value")
                    .post("/resource/{value}");

            assertEquals(200, response.statusCode());
            assertEquals("/resource/space%20value", rawPath.get());
            assertEquals("field%20name=field%20value", requestBody.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
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
