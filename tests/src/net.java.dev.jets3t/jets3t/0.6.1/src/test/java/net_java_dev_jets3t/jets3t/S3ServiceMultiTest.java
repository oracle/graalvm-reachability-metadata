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
import org.jets3t.service.multithread.S3ServiceMulti;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ServiceMultiTest {
    @Test
    public void constructsMultiServiceAroundThreadSafeS3Service() throws Exception {
        Jets3tProperties properties = Jets3tProperties.getInstance(new ByteArrayInputStream("""
            s3service.admin-max-thread-count=2
            s3service.max-thread-count=2
            httpclient.max-connections=2
            httpclient.proxy-autodetect=false
            httpclient.connection-timeout-ms=1000
            httpclient.socket-timeout-ms=1000
            """.getBytes(StandardCharsets.ISO_8859_1)), "s3-service-multi-test-properties");
        RestS3Service s3Service = new RestS3Service(
            null,
            "S3ServiceMultiTest/1.0",
            null,
            properties,
            new HostConfiguration());

        S3ServiceMulti multiService = new S3ServiceMulti(s3Service, null, 1L);

        assertThat(multiService.getS3Service()).isSameAs(s3Service);
        assertThat(multiService.isAuthenticatedConnection()).isFalse();
        assertThat(multiService.getAWSCredentials()).isNull();
    }
}
