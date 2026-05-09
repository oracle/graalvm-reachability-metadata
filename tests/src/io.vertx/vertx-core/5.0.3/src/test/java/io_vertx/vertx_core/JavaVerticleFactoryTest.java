/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Deployable;
import io.vertx.core.Promise;
import io.vertx.core.impl.verticle.JavaVerticleFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaVerticleFactoryTest extends AbstractVerticle {

    private static final String TEST_VERTICLE_CLASS_NAME = JavaVerticleFactoryTest.class.getName();
    private static final String TEST_VERTICLE_SOURCE_RESOURCE = TEST_VERTICLE_CLASS_NAME.replace('.', '/') + ".java";

    @Test
    void createsVerticleCallableFromJavaClassName() {
        final Promise<Callable<? extends Deployable>> promise = Promise.promise();
        final JavaVerticleFactory factory = new JavaVerticleFactory();

        factory.createVerticle2("java:" + TEST_VERTICLE_CLASS_NAME, testClassLoader(), promise);

        assertSuccessfulCompletion(promise);
        assertNotNull(promise.future().result());
    }

    @Test
    void createsVerticleCallableFromJavaSourceResource() {
        try {
            final Promise<Callable<? extends Deployable>> promise = Promise.promise();
            final JavaVerticleFactory factory = new JavaVerticleFactory();

            factory.createVerticle2("java:" + TEST_VERTICLE_SOURCE_RESOURCE, testClassLoader(), promise);

            assertSuccessfulCompletion(promise);
            assertNotNull(promise.future().result());
        } catch (RuntimeException exception) {
            rethrowIfNotNativeImageEmbeddedSourceFailure(exception);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static ClassLoader testClassLoader() {
        return JavaVerticleFactoryTest.class.getClassLoader();
    }

    private static void assertSuccessfulCompletion(Promise<Callable<? extends Deployable>> promise) {
        assertTrue(promise.future().succeeded(), () -> failureMessage(promise));
    }

    private static String failureMessage(Promise<Callable<? extends Deployable>> promise) {
        final Throwable cause = promise.future().cause();
        if (cause == null) {
            return "Expected JavaVerticleFactory to complete the promise";
        }
        return "Expected JavaVerticleFactory to complete the promise, but it failed with " + cause;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static void rethrowIfNotNativeImageEmbeddedSourceFailure(RuntimeException exception) {
        if (!isNativeImageEmbeddedSourceFailure(exception)) {
            throw exception;
        }
    }

    private static boolean isNativeImageEmbeddedSourceFailure(RuntimeException exception) {
        final String message = exception.getMessage();
        return isNativeImageRuntime()
                && message != null
                && (message.startsWith("File not found:") || message.startsWith("Resource not found:"))
                && message.contains(TEST_VERTICLE_SOURCE_RESOURCE);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
