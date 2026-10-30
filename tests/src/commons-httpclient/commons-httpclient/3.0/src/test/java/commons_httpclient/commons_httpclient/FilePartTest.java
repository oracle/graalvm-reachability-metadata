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
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class FilePartTest {
    @Test
    void writesUploadNameFilenameAndPayload() throws Exception {
        FilePart part = new FilePart("upload", new ByteArrayPartSource("report.txt", "file payload".getBytes(StandardCharsets.UTF_8)), "text/plain", "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        part.send(output);
        String wire = output.toString(StandardCharsets.UTF_8);
        assertTrue(wire.contains("name=\"upload\"; filename=\"report.txt\""));
        assertTrue(wire.contains("Content-Type: text/plain; charset=UTF-8"));
        assertTrue(wire.contains("file payload"));
        assertEquals(output.size(), part.length());
    }
}
