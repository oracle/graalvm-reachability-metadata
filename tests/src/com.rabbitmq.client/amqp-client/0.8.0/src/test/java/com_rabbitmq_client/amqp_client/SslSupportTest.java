/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.qpid.protonj2.client.SslOptions;
import com.rabbitmq.qpid.protonj2.client.transport.netty4.SslSupport;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

public class SslSupportTest {

    private static final String TRUST_STORE_RESOURCE = "ssl/empty-truststore.jks";
    private static final String TRUST_STORE_PASSWORD = "secret";

    @Test
    void createsJdkSslContextFromClasspathTrustStore() throws Exception {
        SslOptions sslOptions =
                new SslOptions()
                        .trustStoreLocation("classpath:" + TRUST_STORE_RESOURCE)
                        .trustStorePassword(TRUST_STORE_PASSWORD)
                        .trustStoreType("jks");

        SSLContext sslContext = SslSupport.createJdkSslContext(sslOptions);

        assertThat(sslContext).isNotNull();
        assertThat(sslContext.getProtocol()).isEqualTo(sslOptions.contextProtocol());
    }
}
