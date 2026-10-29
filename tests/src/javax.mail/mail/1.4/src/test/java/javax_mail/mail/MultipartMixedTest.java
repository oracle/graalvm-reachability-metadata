/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.mail.handlers.multipart_mixed;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

public class MultipartMixedTest {
    @Test
    void transfersAndWritesMultipartBody() throws Exception {
        String wire = """
                --sample-boundary
                Content-Type: text/plain; charset=us-ascii

                First part
                --sample-boundary
                Content-Type: text/plain; charset=us-ascii

                Second part
                --sample-boundary--
                """.replace("\n", "\r\n");
        String type = "multipart/mixed; boundary=sample-boundary";
        multipart_mixed handler = new multipart_mixed();
        DataFlavor flavor = handler.getTransferDataFlavors()[0];
        assertEquals(MimeMultipart.class, flavor.getRepresentationClass());
        ByteArrayDataSource source = new ByteArrayDataSource(wire.getBytes(StandardCharsets.US_ASCII), type);
        MimeMultipart multipart = assertInstanceOf(MimeMultipart.class, handler.getTransferData(flavor, source));
        assertEquals(2, multipart.getCount());
        assertEquals("First part", multipart.getBodyPart(0).getContent());
        assertEquals("Second part", multipart.getBodyPart(1).getContent());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.writeTo(multipart, type, output);
        String serialized = output.toString(StandardCharsets.US_ASCII);
        assertTrue(serialized.contains("First part"));
        assertTrue(serialized.contains("Second part"));
        assertTrue(serialized.contains("--sample-boundary--"));
    }
}
