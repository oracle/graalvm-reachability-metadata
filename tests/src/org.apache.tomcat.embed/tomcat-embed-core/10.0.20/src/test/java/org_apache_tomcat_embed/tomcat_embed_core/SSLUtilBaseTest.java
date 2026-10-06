/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.net.ssl.TrustManager;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLUtilBaseTest {

    @Test
    void createsConfiguredTrustManager() throws Exception {
        SSLHostConfig hostConfig = new SSLHostConfig();
        hostConfig.setTrustManagerClassName(RecordingTrustManager.class.getName());
        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(hostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        SSLUtil sslUtil = new JSSEImplementation().getSSLUtil(certificate);

        TrustManager[] trustManagers = sslUtil.getTrustManagers();

        assertThat(trustManagers).hasSize(1);
        assertThat(trustManagers[0]).isInstanceOf(RecordingTrustManager.class);
    }

    public static final class RecordingTrustManager implements TrustManager {
        public RecordingTrustManager() {
        }
    }
}
