/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.support.jsse.FilterParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSSLContextParametersTest {
    @Test
    void configuresSslEngineNamedGroupsAndSignatureSchemesThroughSslContextParameters() throws Exception {
        SSLContextParameters parameters = new SSLContextParameters();
        parameters.setNamedGroupsFilter(includeAllFilter());
        parameters.setSignatureSchemesFilter(includeAllFilter());

        SSLContext context = parameters.createSSLContext(null);
        SSLEngine engine = context.createSSLEngine("localhost", 443);

        assertThat(context.getProtocol()).isNotBlank();
        assertThat(engine.getEnabledProtocols()).isNotEmpty();
        assertThat(engine.getEnabledCipherSuites()).isNotEmpty();
    }

    private static FilterParameters includeAllFilter() {
        FilterParameters filter = new FilterParameters();
        filter.addInclude(".*");
        return filter;
    }
}
