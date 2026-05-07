/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.xml.parser;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XMLParser {
    private static int instanceCount;
    private static int parseCount;
    private static int getDocumentCount;

    private Document document;

    public XMLParser() {
        instanceCount++;
    }

    public static void resetInvocationCounts() {
        instanceCount = 0;
        parseCount = 0;
        getDocumentCount = 0;
    }

    public static int getInstanceCount() {
        return instanceCount;
    }

    public static int getParseCount() {
        return parseCount;
    }

    public static int getDocumentAccessCount() {
        return getDocumentCount;
    }

    public void parse(InputSource inputSource) throws IOException, SAXException {
        parseCount++;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        try {
            document = factory.newDocumentBuilder().parse(inputSource);
        } catch (ParserConfigurationException e) {
            throw new SAXException("Unable to create a namespace-aware document builder", e);
        }
    }

    public Document getDocument() {
        getDocumentCount++;
        return document;
    }
}
