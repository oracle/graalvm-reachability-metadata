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

import com.sun.mail.handlers.message_rfc822;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.Message;
import javax.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

public class MessageRfc822Test {
    @Test
    void transfersAndWritesAttachedMessage() throws Exception {
        String wire = """
                From: sender@example.org
                To: reader@example.org
                Subject: Forwarded greeting
                Content-Type: text/plain; charset=us-ascii

                Hello from the attachment
                """.replace("\n", "\r\n");
        message_rfc822 handler = new message_rfc822();
        DataFlavor flavor = handler.getTransferDataFlavors()[0];
        assertEquals(Message.class, flavor.getRepresentationClass());
        ByteArrayDataSource source = new ByteArrayDataSource(wire.getBytes(StandardCharsets.US_ASCII), "message/rfc822");
        Message message = assertInstanceOf(Message.class, handler.getTransferData(flavor, source));
        assertEquals("Forwarded greeting", message.getSubject());
        assertEquals("Hello from the attachment\r\n", message.getContent());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.writeTo(message, "message/rfc822", output);
        String serialized = output.toString(StandardCharsets.US_ASCII);
        assertTrue(serialized.contains("Subject: Forwarded greeting"));
        assertTrue(serialized.contains("Hello from the attachment"));
    }
}
