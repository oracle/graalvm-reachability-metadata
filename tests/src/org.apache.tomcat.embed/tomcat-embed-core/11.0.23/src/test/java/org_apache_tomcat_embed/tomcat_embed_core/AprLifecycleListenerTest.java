/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AprLifecycleListenerTest {

    @Test
    void initializesAprLifecycleThroughStandardServer() throws Exception {
        AprLifecycleListener listener = new AprLifecycleListener();
        listener.setSSLEngine("on");
        listener.setSSLRandomSeed("builtin");
        listener.setFIPSMode("off");

        StandardServer server = new StandardServer();
        server.addLifecycleListener(listener);
        try {
            server.init();

            assertThat(server.getState()).isEqualTo(LifecycleState.INITIALIZED);
            assertThat(listener.getSSLEngine()).isEqualTo("on");
            assertThat(listener.getSSLRandomSeed()).isEqualTo("builtin");
            assertThat(listener.getFIPSMode()).isEqualTo("off");
            if (AprLifecycleListener.isAprAvailable()) {
                assertThat(AprLifecycleListener.getInstalledTcnVersion()).isNotEmpty();
                assertThat(AprLifecycleListener.getInstalledOpenSslVersion()).isNotEmpty();
            }
        } finally {
            server.destroy();
        }

        assertThat(server.getState()).isEqualTo(LifecycleState.DESTROYED);
    }
}
