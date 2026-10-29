/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.mail.handlers.text_xml;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

public class TextXmlTest {
    @Test
    void transfersAndWritesXml() throws Exception {
        String xml = "<message>Gr\u00fc\u00dfe</message>";
        String type = "text/xml; charset=utf-8";
        text_xml handler = new text_xml();
        DataFlavor flavor = handler.getTransferDataFlavors()[0];
        assertEquals(String.class, flavor.getRepresentationClass());
        assertEquals("xml", flavor.getSubType());
        ByteArrayDataSource source = new ByteArrayDataSource(xml.getBytes(StandardCharsets.UTF_8), type);
        assertEquals(xml, handler.getTransferData(flavor, source));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.writeTo(xml, type, output);
        assertEquals(xml, output.toString(StandardCharsets.UTF_8));
    }
}
