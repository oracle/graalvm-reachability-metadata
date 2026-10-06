/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JreCompatTest {

    @Test
    void configuresApplicationProtocolsThroughCompatibilityApi() throws Exception {
        JreCompat compat = JreCompat.getInstance();
        SSLParameters parameters = new SSLParameters();
        compat.setApplicationProtocols(parameters, new String[] {"h2", "http/1.1"});
        assertThat(parameters.getApplicationProtocols()).containsExactly("h2", "http/1.1");

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        assertThat(compat.getApplicationProtocol(engine)).isNull();
    }

}
