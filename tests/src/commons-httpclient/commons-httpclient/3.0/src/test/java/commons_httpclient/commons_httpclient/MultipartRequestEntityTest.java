/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class MultipartRequestEntityTest {
    @Test
    void serializesMultipartEntity() throws Exception {
        HttpMethodParams params = new HttpMethodParams();
        params.setParameter(HttpMethodParams.MULTIPART_BOUNDARY, "application-boundary");
        MultipartRequestEntity entity = new MultipartRequestEntity(new Part[] {new StringPart("field", "value")}, params);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeRequest(output);
        assertEquals("multipart/form-data; boundary=application-boundary", entity.getContentType());
        assertEquals(output.size(), entity.getContentLength());
        assertTrue(entity.isRepeatable());
        assertTrue(output.toString(StandardCharsets.US_ASCII).contains("value"));
    }
}
