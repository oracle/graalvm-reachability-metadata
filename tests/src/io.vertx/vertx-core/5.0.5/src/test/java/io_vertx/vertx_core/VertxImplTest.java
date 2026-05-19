/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Deployable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxImplTest {

    @Test
    void deployVerticleClassInstantiatesDeployableWithNoArgConstructor() throws Exception {
        ConstructorDeployable.reset();
        Vertx vertx = Vertx.vertx();
        try {
            String deploymentId = await(vertx.deployVerticle(ConstructorDeployable.class, new DeploymentOptions()));

            assertNotNull(deploymentId);
            assertFalse(deploymentId.isBlank());
            assertTrue(ConstructorDeployable.deployed());
        } finally {
            await(vertx.close());
        }
    }

    private static <T> T await(Future<T> future) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AsyncResult<T>> result = new AtomicReference<>();

        future.onComplete(completion -> {
            result.set(completion);
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertTrue(result.get().succeeded(), () -> String.valueOf(result.get().cause()));
        return result.get().result();
    }

    public static class ConstructorDeployable implements Deployable {
        private static final AtomicBoolean DEPLOYED = new AtomicBoolean();

        public ConstructorDeployable() {
        }

        static void reset() {
            DEPLOYED.set(false);
        }

        static boolean deployed() {
            return DEPLOYED.get();
        }

        @Override
        public Future<?> deploy(Context context) {
            DEPLOYED.set(true);
            return Future.succeededFuture();
        }
    }
}
