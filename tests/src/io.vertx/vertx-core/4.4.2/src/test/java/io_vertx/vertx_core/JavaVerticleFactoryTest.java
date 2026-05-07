/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.impl.JavaVerticleFactory;
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
        final Promise<Callable<Verticle>> promise = Promise.promise();
        final JavaVerticleFactory factory = new JavaVerticleFactory();

        factory.createVerticle("java:" + TEST_VERTICLE_CLASS_NAME, testClassLoader(), promise);

        assertSuccessfulCompletion(promise);
        assertNotNull(promise.future().result());
    }

    @Test
    void createsVerticleCallableFromJavaSourceResource() {
        try {
            final Promise<Callable<Verticle>> promise = Promise.promise();
            final JavaVerticleFactory factory = new JavaVerticleFactory();

            factory.createVerticle("java:" + TEST_VERTICLE_SOURCE_RESOURCE, testClassLoader(), promise);

            assertSuccessfulCompletion(promise);
            assertNotNull(promise.future().result());
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static ClassLoader testClassLoader() {
        return JavaVerticleFactoryTest.class.getClassLoader();
    }

    private static void assertSuccessfulCompletion(Promise<Callable<Verticle>> promise) {
        assertTrue(promise.future().succeeded(), () -> failureMessage(promise));
    }

    private static String failureMessage(Promise<Callable<Verticle>> promise) {
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
}
