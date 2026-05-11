/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.DecoderConfig;
import io.restassured.config.EncoderConfig;
import io.restassured.config.OAuthConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.http.ContentEncoding;
import io.restassured.internal.http.HTTPBuilder;
import io.restassured.response.Response;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_applyContentDecoders_closure21Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_applyContentDecoders_closure21";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";

    @Test
    void requestConfigurationAppliesContentDecoderClosureToDecodeGzipResponse() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> acceptEncoding = new AtomicReference<>();
        server.setExecutor(executorService);
        server.createContext("/compressed", exchange -> {
            acceptEncoding.set(exchange.getRequestHeaders().getFirst(ContentEncoding.ACCEPT_ENC_HDR));
            byte[] response = gzip("decoded response");
            exchange.getResponseHeaders().add(ContentEncoding.CONTENT_ENC_HDR, "gzip");
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();
        try {
            RestAssuredConfig config = RestAssuredConfig.config().decoderConfig(
                    DecoderConfig.decoderConfig().contentDecoders(DecoderConfig.ContentDecoder.GZIP));

            Response response = RestAssured.given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .get("/compressed");

            assertEquals(200, response.statusCode());
            assertEquals("decoded response", response.asString());
            assertTrue(acceptEncoding.get().toLowerCase(Locale.ROOT).contains("gzip"));
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

    @Test
    void applyContentDecodersCapturesClosureClassAndInvokesItsClassResolver() throws Throwable {
        try {
            RecordingDecoder decoder = new RecordingDecoder();
            RecordingHTTPBuilder httpBuilder = new RecordingHTTPBuilder();

            applyContentDecoders().invoke(RestAssured.given(), httpBuilder, List.of(decoder));

            Class<?> closureClass = decoder.getClosureClass();
            assertNotNull(closureClass);
            assertEquals(CLOSURE_CLASS_NAME, closureClass.getName());
            assertArrayEquals(new Object[] {ContentEncoding.Type.GZIP}, httpBuilder.getEncodings());

            Method classResolver = closureClass.getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);
            Object resolvedClass = classResolver.invoke(null, RESOLVABLE_CLASS_NAME);

            assertResolvedClassName(resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (LinkageError error) {
            if (!isNativeGroovyInitializationFailure(error)) {
                rethrowUnlessUnsupportedNativeImageError(error);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverResolvesUnloadedJdkClass() throws Throwable {
        try {
            MethodHandle classResolver = classResolver();

            Object resolvedClass = classResolver.invokeWithArguments(RESOLVABLE_CLASS_NAME);

            assertResolvedClassName(resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, RESOLVABLE_CLASS_NAME);

            assertResolvedClassName(resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsUnknownClasses() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            InvocationTargetException exception = assertThrows(
                    InvocationTargetException.class,
                    () -> classResolver.invoke(null, "io.restassured.internal.http.DoesNotExist"));

            assertInstanceOf(NoClassDefFoundError.class, exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertResolvedClassName(Object resolvedClass) {
        Class<?> type = assertInstanceOf(Class.class, resolvedClass);
        assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
    }

    private static MethodHandle applyContentDecoders() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findVirtual(
                RequestSpecificationImpl.class,
                "applyContentDecoders",
                MethodType.methodType(Object.class, HTTPBuilder.class, List.class));
    }

    private static MethodHandle classResolver()
            throws IllegalAccessException, NoSuchMethodException, ClassNotFoundException {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> closureClass() throws IllegalAccessException, ClassNotFoundException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findClass(CLOSURE_CLASS_NAME);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
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

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static final class RecordingHTTPBuilder extends HTTPBuilder {
        private Object[] encodings;

        private RecordingHTTPBuilder() {
            super(
                    false,
                    new EncoderConfig(),
                    DecoderConfig.decoderConfig(),
                    new OAuthConfig(),
                    new DefaultHttpClient());
        }

        @Override
        public void setContentEncoding(Object... encodings) {
            this.encodings = encodings.clone();
        }

        @Override
        protected Object doRequest(RequestConfigDelegate delegate) throws ClientProtocolException, IOException {
            throw new UnsupportedOperationException("Recording builder does not send HTTP requests");
        }

        private Object[] getEncodings() {
            return encodings.clone();
        }
    }

    private static final class RecordingDecoder {
        private final AtomicReference<Class<?>> closureClass = new AtomicReference<>();

        @Override
        public String toString() {
            Class<?> callerClass = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
                    .walk(frames -> frames
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(type -> CLOSURE_CLASS_NAME.equals(type.getName()))
                            .findFirst()
                            .orElse(null));
            if (callerClass != null) {
                closureClass.set(callerClass);
            }
            return DecoderConfig.ContentDecoder.GZIP.name();
        }

        private Class<?> getClosureClass() {
            return closureClass.get();
        }
    }
}
