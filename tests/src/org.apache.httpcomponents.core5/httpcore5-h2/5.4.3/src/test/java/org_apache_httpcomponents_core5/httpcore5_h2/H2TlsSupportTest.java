/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5_h2;

import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.ssl.H2TlsSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class H2TlsSupportTest {
    @Test
    void enforceRequirementsDisablesRetransmissionsAndConfiguresAlpn() {
        SSLParameters parameters = new SSLParameters(
                new String[] {"TLS_AES_128_GCM_SHA256"},
                new String[] {"TLSv1.3"});
        parameters.setEnableRetransmissions(true);

        SSLParameters enforced = H2TlsSupport.enforceRequirements(HttpVersionPolicy.NEGOTIATE, parameters);

        assertThat(enforced).isSameAs(parameters);
        assertThat(parameters.getEnableRetransmissions()).isFalse();
        assertThat(parameters.getApplicationProtocols()).containsExactly("h2", "http/1.1");
    }
}
