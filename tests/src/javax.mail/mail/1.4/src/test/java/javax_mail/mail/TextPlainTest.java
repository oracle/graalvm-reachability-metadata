/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.mail.handlers.text_plain;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

public class TextPlainTest {
    @Test
    void transfersAndWritesUtf8Text() throws Exception {
        String text = "Message for Andr\u00e9";
        String type = "text/plain; charset=utf-8";
        text_plain handler = new text_plain();
        DataFlavor flavor = handler.getTransferDataFlavors()[0];
        assertEquals(String.class, flavor.getRepresentationClass());
        assertEquals("plain", flavor.getSubType());
        ByteArrayDataSource source = new ByteArrayDataSource(text.getBytes(StandardCharsets.UTF_8), type);
        assertEquals(text, handler.getTransferData(flavor, source));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.writeTo(text, type, output);
        assertEquals(text, output.toString(StandardCharsets.UTF_8));
    }
}
