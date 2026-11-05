/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class InputStreamRequestEntityTest {
    @Test
    void buffersUnknownLengthAndReplaysPayload() throws Exception {
        byte[] payload = "streamed payload".getBytes(StandardCharsets.UTF_8);
        InputStreamRequestEntity entity = new InputStreamRequestEntity(new ByteArrayInputStream(payload), "text/plain");
        assertEquals(payload.length, entity.getContentLength());
        assertTrue(entity.isRepeatable());
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        entity.writeRequest(first);
        entity.writeRequest(second);
        assertArrayEquals(payload, first.toByteArray());
        assertArrayEquals(payload, second.toByteArray());
        assertEquals("text/plain", entity.getContentType());
    }
}
