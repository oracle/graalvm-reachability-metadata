/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxImplTest {

    @Test
    void deploysVerticleClassUsingPublicNoArgsConstructor() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        ConstructorDeployedVerticle.started = started;
        Vertx vertx = Vertx.vertx();
        try {
            final String deploymentId = vertx.deployVerticle(ConstructorDeployedVerticle.class, new DeploymentOptions())
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertNotNull(deploymentId);
            assertTrue(started.await(10, TimeUnit.SECONDS));
        } finally {
            ConstructorDeployedVerticle.started = null;
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void readsVersionResourceFromClasspath() {
        final String version = VertxImpl.version();

        assertNotNull(version);
        assertFalse(version.isBlank());
    }

    public static class ConstructorDeployedVerticle extends AbstractVerticle {
        private static volatile CountDownLatch started;

        @Override
        public void start() {
            final CountDownLatch latch = started;
            if (latch != null) {
                latch.countDown();
            }
        }
    }
}
