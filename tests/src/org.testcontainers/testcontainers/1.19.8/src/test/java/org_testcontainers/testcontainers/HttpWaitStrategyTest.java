/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpWaitStrategyTest {
    @Test
    @Timeout(120)
    void waitsForAHeaderProtectedEndpointAndValidatesItsResponse() {
        String configuration = """
                server {
                    listen 80;
                    location = /health {
                        default_type text/plain;
                        if ($http_x_readiness_token != "coverage-probe") {
                            return 403;
                        }
                        return 200 "container-ready";
                    }
                }
                """;
        List<String> responses = new CopyOnWriteArrayList<>();
        HttpWaitStrategy readiness = new HttpWaitStrategy()
                .forPath("/health")
                .forPort(80)
                .withHeader("X-Readiness-Token", "coverage-probe")
                .forStatusCodeMatching(status -> status == 200)
                .forResponsePredicate(body -> {
                    responses.add(body);
                    return body.equals("container-ready");
                })
                .withReadTimeout(Duration.ofSeconds(10));
        readiness.withStartupTimeout(Duration.ofSeconds(60));

        try (GenericContainer<?> container = new GenericContainer<>("nginx:1-alpine-slim")
                .withExposedPorts(80)
                .withCopyToContainer(Transferable.of(configuration), "/etc/nginx/conf.d/default.conf")
                .waitingFor(readiness)) {
            container.start();

            assertThat(container.isRunning()).isTrue();
            assertThat(responses).isNotEmpty().containsOnly("container-ready");
        }
    }
}
