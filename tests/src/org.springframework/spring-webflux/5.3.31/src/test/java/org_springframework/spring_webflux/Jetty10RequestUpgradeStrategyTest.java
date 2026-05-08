/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webflux;

import org.junit.jupiter.api.Test;

import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.upgrade.Jetty10RequestUpgradeStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class Jetty10RequestUpgradeStrategyTest {
    @Test
    void constructsStrategyWhenJetty10WebSocketServerApiIsAvailable() {
        RequestUpgradeStrategy strategy = new Jetty10RequestUpgradeStrategy();

        assertThat(strategy).isInstanceOf(Jetty10RequestUpgradeStrategy.class);
    }
}
