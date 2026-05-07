/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.VertxWrapper;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.impl.launcher.commands.ClasspathHandler;
import io.vertx.core.spi.launcher.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClasspathHandlerTest {

    @Test
    void deployCreatesAndInvokesIsolatedDeployer() throws Exception {
        Vertx vertx = new CompletingDeployVertx((VertxInternal) Vertx.vertx());
        TestClasspathHandler command = new TestClasspathHandler();
        ExecutionContext context = new ExecutionContext(
                command,
                new VertxCommandLauncher(Collections.emptyList()),
                null);
        CountDownLatch deploymentLatch = new CountDownLatch(1);
        AtomicReference<AsyncResult<String>> deploymentResult = new AtomicReference<>();

        command.setUp(context);
        try {
            command.deploy(
                    "example.MainVerticle",
                    vertx,
                    new DeploymentOptions(),
                    result -> {
                        deploymentResult.set(result);
                        deploymentLatch.countDown();
                    });

            assertTrue(deploymentLatch.await(10, TimeUnit.SECONDS));
            assertNotNull(deploymentResult.get());
            assertTrue(deploymentResult.get().succeeded(), () -> String.valueOf(deploymentResult.get().cause()));
        } finally {
            closeVertx(vertx);
        }
    }

    private static class CompletingDeployVertx extends VertxWrapper {
        CompletingDeployVertx(VertxInternal delegate) {
            super(delegate);
        }

        @Override
        public Future<String> deployVerticle(String name, DeploymentOptions options) {
            return Future.succeededFuture("deployment-id");
        }

        @Override
        public Throwable unavailableNativeTransportCause() {
            return delegate.unavailableNativeTransportCause();
        }

        @Override
        public void deployVerticle(
                String name,
                DeploymentOptions options,
                Handler<AsyncResult<String>> completionHandler) {
            completionHandler.handle(Future.succeededFuture("deployment-id"));
        }
    }

    private static class TestClasspathHandler extends ClasspathHandler {
        @Override
        public void run() {
        }
    }

    private static void closeVertx(Vertx vertx) throws InterruptedException {
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicReference<AsyncResult<Void>> closeResult = new AtomicReference<>();

        vertx.close(result -> {
            closeResult.set(result);
            closeLatch.countDown();
        });

        assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        assertNotNull(closeResult.get());
        assertTrue(closeResult.get().succeeded(), () -> String.valueOf(closeResult.get().cause()));
    }
}
