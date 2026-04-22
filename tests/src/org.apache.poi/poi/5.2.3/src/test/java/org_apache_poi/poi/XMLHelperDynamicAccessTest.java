/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.util.XMLHelper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLHelperDynamicAccessTest {

    @Test
    void createsSecuredXmlBuildersAndReaders() throws Exception {
        Document document = XMLHelper.newDocumentBuilder()
                .parse(new InputSource(new StringReader("<root><child>value</child></root>")));

        List<String> elementNames = new ArrayList<>();
        XMLReader xmlReader = XMLHelper.newXMLReader();
        xmlReader.setContentHandler(new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
                elementNames.add(localName.isEmpty() ? qName : localName);
            }
        });
        xmlReader.parse(new InputSource(new StringReader("<alpha><beta/></alpha>")));

        assertThat(document.getDocumentElement().getTagName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getTextContent()).isEqualTo("value");
        assertThat(elementNames).containsExactly("alpha", "beta");
    }
}
