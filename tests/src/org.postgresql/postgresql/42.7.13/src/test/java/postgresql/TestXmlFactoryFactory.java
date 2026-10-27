/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.postgresql.xml.PGXmlFactoryFactory;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class TestXmlFactoryFactory implements PGXmlFactoryFactory {

    @Override
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    @Override
    public TransformerFactory newTransformerFactory() {
        return TransformerFactory.newInstance();
    }

    @Override
    public SAXTransformerFactory newSAXTransformerFactory() {
        return (SAXTransformerFactory) SAXTransformerFactory.newInstance();
    }

    @Override
    public XMLInputFactory newXMLInputFactory() {
        return XMLInputFactory.newFactory();
    }

    @Override
    public XMLOutputFactory newXMLOutputFactory() {
        return XMLOutputFactory.newFactory();
    }

    @Override
    public XMLReader createXMLReader() throws SAXException {
        return XMLReaderFactory.createXMLReader();
    }
}
