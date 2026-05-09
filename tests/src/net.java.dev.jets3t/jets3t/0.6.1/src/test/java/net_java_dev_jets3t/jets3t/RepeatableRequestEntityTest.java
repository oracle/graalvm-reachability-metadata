/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.jets3t.service.impl.rest.httpclient.RepeatableRequestEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RepeatableRequestEntityTest {
    @Test
    public void writesRepeatableRequestBodyAndComputesDigest() throws Exception {
        byte[] payload = "repeatable request payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(payload);
        RepeatableRequestEntity entity = new RepeatableRequestEntity(
            "payload.txt", inputStream, "text/plain", payload.length);

        ByteArrayOutputStream firstWrite = new ByteArrayOutputStream();
        entity.writeRequest(firstWrite);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.getContentType()).isEqualTo("text/plain");
        assertThat(entity.getContentLength()).isEqualTo(payload.length);
        assertThat(firstWrite.toByteArray()).containsExactly(payload);
        assertThat(entity.getMD5DigestOfData()).containsExactly(md5(payload));

        ByteArrayOutputStream repeatedWrite = new ByteArrayOutputStream();
        entity.writeRequest(repeatedWrite);

        assertThat(repeatedWrite.toByteArray()).containsExactly(payload);
        assertThat(entity.getMD5DigestOfData()).containsExactly(md5(payload));
    }

    private static byte[] md5(byte[] payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return digest.digest(payload);
    }
}
