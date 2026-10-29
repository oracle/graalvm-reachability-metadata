/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.mail.handlers.text_html;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

public class TextHtmlTest {
    @Test
    void transfersAndWritesHtml() throws Exception {
        String html = "<p>Ol\u00e1, reader</p>";
        String type = "text/html; charset=utf-8";
        text_html handler = new text_html();
        DataFlavor flavor = handler.getTransferDataFlavors()[0];
        assertEquals(String.class, flavor.getRepresentationClass());
        assertEquals("html", flavor.getSubType());
        ByteArrayDataSource source = new ByteArrayDataSource(html.getBytes(StandardCharsets.UTF_8), type);
        assertEquals(html, handler.getTransferData(flavor, source));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.writeTo(html, type, output);
        assertEquals(html, output.toString(StandardCharsets.UTF_8));
    }
}
