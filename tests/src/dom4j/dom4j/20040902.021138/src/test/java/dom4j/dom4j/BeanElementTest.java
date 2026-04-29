/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.Element;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.dom4j.io.SAXContentHandler;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanElementTest {
    @Test
    void saxContentHandlerInitializesBackingBeanFromClassAttribute() throws Exception {
        SAXContentHandler handler = new SAXContentHandler(BeanDocumentFactory.getInstance());
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", BeanDocumentFactory.class.getName());

        handler.startDocument();
        handler.startElement("", "configuredBean", "configuredBean", attributes);
        handler.endElement("", "configuredBean", "configuredBean");
        handler.endDocument();

        Element rootElement = handler.getDocument().getRootElement();
        assertThat(rootElement).isInstanceOf(BeanElement.class);
        assertThat(((BeanElement) rootElement).getData()).isInstanceOf(BeanDocumentFactory.class);
    }
}
