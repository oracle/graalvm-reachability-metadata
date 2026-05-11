/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.HostConfiguration;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestS3ServiceTest {
    @Test
    public void constructsAnonymousServiceWithExplicitHttpClientProperties() throws Exception {
        Jets3tProperties properties = Jets3tProperties.getInstance(new ByteArrayInputStream("""
            s3service.https-only=false
            s3service.internal-error-retry-max=3
            httpclient.proxy-autodetect=false
            httpclient.connection-timeout-ms=1000
            httpclient.socket-timeout-ms=1000
            httpclient.max-connections=2
            httpclient.stale-checking-enabled=false
            httpclient.retry-max=1
            httpclient.useragent=coverage-agent
            """.getBytes(StandardCharsets.ISO_8859_1)), "rest-s3-service-test-properties");

        RestS3Service service = new RestS3Service(
            null,
            "RestS3ServiceTest/1.0",
            null,
            properties,
            new HostConfiguration());

        assertThat(service.isAuthenticatedConnection()).isFalse();
        assertThat(service.isHttpsOnly()).isFalse();
        assertThat(service.getInternalErrorRetryMax()).isEqualTo(3);
        assertThat(service.getInvokingApplicationDescription()).isEqualTo("RestS3ServiceTest/1.0");
        assertThat(service.getJetS3tProperties()).isSameAs(properties);
    }
}
