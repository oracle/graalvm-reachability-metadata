/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.xml.parser;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XMLParser {
    private Document document;

    public void parse(InputSource inputSource) throws SAXException, IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            document = factory.newDocumentBuilder().parse(inputSource);
        } catch (ParserConfigurationException e) {
            throw new SAXException("Could not create DOM document builder", e);
        }
    }

    public Document getDocument() {
        return document;
    }
}
