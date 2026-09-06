/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webflux;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.web.reactive.socket.client.JettyWebSocketClient;

public class JettyWebSocketClientInnerJetty10UpgradeHelperTest {
    @Test
    void constructionInitializesJetty10UpgradeHelper() {
        JettyWebSocketClient client = new JettyWebSocketClient();
        try {
            assertThat(client.getJettyClient()).isNotNull();
            assertThat(client.isRunning()).isFalse();
        } finally {
            client.stop();
        }
    }
}
