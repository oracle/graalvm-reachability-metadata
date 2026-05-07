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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VertxImplTest {

    @Test
    void deployVerticleClassInstantiatesDefaultConstructor() throws TimeoutException {
        Vertx vertx = null;
        try {
            vertx = Vertx.vertx();

            String deploymentId = vertx.deployVerticle(ConstructedVerticle.class, new DeploymentOptions())
                    .await(10, TimeUnit.SECONDS);

            assertNotNull(deploymentId);
            assertFalse(deploymentId.isBlank());
        } finally {
            if (vertx != null) {
                vertx.close().await(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void versionReadsBundledVersionResource() {
        String version = VertxImpl.version();

        assertNotNull(version);
        assertFalse(version.isBlank());
    }

    public static class ConstructedVerticle extends AbstractVerticle {
    }
}
