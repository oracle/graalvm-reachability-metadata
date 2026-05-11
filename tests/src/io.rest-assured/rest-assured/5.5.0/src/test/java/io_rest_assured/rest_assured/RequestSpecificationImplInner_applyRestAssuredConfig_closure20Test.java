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
import io.restassured.config.HttpClientConfig;
import io.restassured.config.OAuthConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.http.HTTPBuilder;
import io.restassured.response.Response;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_applyRestAssuredConfig_closure20Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_applyRestAssuredConfig_closure20";
    private static final String PARAMETER_NAME = "rest-assured.dynamic.apply-config.parameter";
    private static final String PARAMETER_VALUE = "applied";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingRestAssuredConfigClass";

    @Test
    void restAssuredConfigAppliesStoredHttpClientParametersBeforeRequestExecution() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/configured", exchange -> {
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();
        try {
            HttpClientConfig httpClientConfig = HttpClientConfig.httpClientConfig()
                    .setParam(PARAMETER_NAME, PARAMETER_VALUE);
            RestAssuredConfig config = RestAssuredConfig.config().httpClient(httpClientConfig);
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort());

            Response response = requestSpecification.get("/configured");

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            Object appliedParameter = requestSpecification.getHttpClient().getParams().getParameter(PARAMETER_NAME);
            assertEquals(PARAMETER_VALUE, appliedParameter);
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
    void applyRestAssuredConfigClosureInvokesCompilerGeneratedClassResolverWhileApplyingClientParameters()
            throws Throwable {
        try {
            HttpClientConfig httpClientConfig = HttpClientConfig.httpClientConfig()
                    .setParam(PARAMETER_NAME, PARAMETER_VALUE);
            RestAssuredConfig config = RestAssuredConfig.config().httpClient(httpClientConfig);
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .config(config);
            RecordingHttpParams httpParams = new RecordingHttpParams();
            RecordingHTTPBuilder httpBuilder = new RecordingHTTPBuilder(httpParams);

            requestSpecification.applyRestAssuredConfig(httpBuilder);

            assertTrue(httpBuilder.hasConfiguredEncodings());
            assertEquals(PARAMETER_VALUE, httpParams.getParameter(PARAMETER_NAME));
            assertEquals(CLOSURE_CLASS_NAME, httpParams.getClosureClass().getName());
            Class<?> type = assertInstanceOf(Class.class, httpParams.getResolvedClass());
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
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
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
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

            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static Object resolveWithCompilerGeneratedClassResolver(Class<?> closureClass, String className)
            throws Throwable {
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class)).invokeWithArguments(className);
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

    private static Class<?> closureClass() throws ClassNotFoundException {
        return RequestSpecificationImpl.class.getClassLoader().loadClass(CLOSURE_CLASS_NAME);
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

    private static final class RecordingHTTPBuilder extends HTTPBuilder {
        private Object[] encodings;

        private RecordingHTTPBuilder(RecordingHttpParams httpParams) {
            super(
                    false,
                    new EncoderConfig(),
                    DecoderConfig.decoderConfig(),
                    new OAuthConfig(),
                    new DefaultHttpClient(httpParams));
        }

        @Override
        public void setContentEncoding(Object... encodings) {
            this.encodings = encodings.clone();
        }

        @Override
        protected Object doRequest(RequestConfigDelegate delegate) throws ClientProtocolException, IOException {
            throw new UnsupportedOperationException("Recording builder does not send HTTP requests");
        }

        private boolean hasConfiguredEncodings() {
            return encodings != null;
        }
    }

    private static final class RecordingHttpParams extends BasicHttpParams {
        private final AtomicReference<Class<?>> closureClass = new AtomicReference<>();
        private final AtomicReference<Object> resolvedClass = new AtomicReference<>();

        @Override
        public HttpParams setParameter(String name, Object value) {
            Class<?> callerClass = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
                    .walk(frames -> frames
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(type -> CLOSURE_CLASS_NAME.equals(type.getName()))
                            .findFirst()
                            .orElse(null));
            if (callerClass != null && closureClass.compareAndSet(null, callerClass)) {
                try {
                    resolvedClass.set(resolveWithCompilerGeneratedClassResolver(callerClass, RESOLVABLE_CLASS_NAME));
                } catch (RuntimeException | Error exception) {
                    throw exception;
                } catch (Throwable throwable) {
                    throw new AssertionError("Unable to invoke compiler-generated class resolver", throwable);
                }
            }
            return super.setParameter(name, value);
        }

        private Class<?> getClosureClass() {
            return closureClass.get();
        }

        private Object getResolvedClass() {
            return resolvedClass.get();
        }
    }
}
