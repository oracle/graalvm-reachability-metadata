/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webflux;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.Jetty10RequestUpgradeStrategy;

public class HandshakeWebSocketServiceTest {
    @Test
    void defaultConstructorInstantiatesDetectedUpgradeStrategy() {
        HandshakeWebSocketService service = new HandshakeWebSocketService();

        assertThat(service.getUpgradeStrategy()).isInstanceOf(Jetty10RequestUpgradeStrategy.class);
        assertThat(service.isRunning()).isFalse();
    }
}
