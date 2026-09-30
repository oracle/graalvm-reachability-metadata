/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PrivateLoomSupportTest {
    @Test
    @Timeout(55)
    void startsAnEmbeddedServerWithTheConfiguredVirtualThreadEventLoop() {
        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "micronaut.netty.event-loops.default.executor", "virtual",
                "micronaut.netty.event-loops.default.loom-carrier", true,
                "micronaut.executors.virtual.type", "thread-per-task",
                "micronaut.executors.virtual.virtual", true);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST)) {
            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isPositive();
        }
    }
}
