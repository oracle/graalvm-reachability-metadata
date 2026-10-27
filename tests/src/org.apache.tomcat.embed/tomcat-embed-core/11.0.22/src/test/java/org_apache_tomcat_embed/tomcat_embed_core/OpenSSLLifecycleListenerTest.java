/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.OpenSSLLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSSLLifecycleListenerTest {

    @Test
    void disablesAndManagesFfmOpenSslLifecycle() throws Exception {
        OpenSSLLifecycleListener listener = new OpenSSLLifecycleListener();
        listener.setSSLEngine("off");
        listener.setSSLRandomSeed("builtin");
        listener.setFIPSMode("off");

        assertThat(listener.getSSLEngine()).isEqualTo("off");
        assertThat(listener.getSSLRandomSeed()).isEqualTo("builtin");
        assertThat(listener.getFIPSMode()).isEqualTo("off");
        assertThat(listener.isFIPSModeActive()).isFalse();

        StandardServer server = new StandardServer();
        server.addLifecycleListener(listener);
        try {
            server.init();
            assertThat(server.getState()).isEqualTo(LifecycleState.INITIALIZED);
            assertThat(OpenSSLLifecycleListener.isAvailable()).isFalse();
            assertThat(OpenSSLLifecycleListener.getInstalledOpenSslVersion()).isNull();
        } finally {
            server.destroy();
        }

        assertThat(server.getState()).isEqualTo(LifecycleState.DESTROYED);
    }
}
