/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLXML;
import java.util.List;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.hsqldb.jdbc.JDBCSQLXML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDBCSQLXMLTest {
    private static final String XML = "<message><body>Hello XML</body></message>";

    @Test
    void exposesStreamDomSaxAndStaxSources() throws Exception {
        List<Class<? extends Source>> formats = List.of(StreamSource.class, DOMSource.class,
                SAXSource.class, StAXSource.class);
        for (Class<? extends Source> format : formats) {
            SQLXML value = new JDBCSQLXML(new StreamSource(new StringReader(XML)));
            try {
                assertMessage(value.getSource(format));
            } finally {
                value.free();
            }
        }
    }

    @Test
    void acceptsStreamDomSaxAndCustomStaxResults() throws Exception {
        List<Class<? extends Result>> formats = List.of(StreamResult.class, DOMResult.class,
                SAXResult.class, MessageStaxResult.class);
        try (Connection connection = DatabaseSupport.memory()) {
            for (Class<? extends Result> format : formats) {
                SQLXML value = connection.createSQLXML();
                try {
                    Result output = value.setResult(format);
                    TransformerFactory.newInstance().newTransformer().transform(
                            new StreamSource(new StringReader(XML)), output);
                    if (output instanceof StreamResult stream) {
                        stream.getOutputStream().close();
                    }
                    assertMessage(new StreamSource(new StringReader(value.getString())));
                } finally {
                    value.free();
                }
            }
        }
    }

    private static void assertMessage(Source source) throws Exception {
        DOMResult document = new DOMResult();
        TransformerFactory.newInstance().newTransformer().transform(source, document);
        Document dom = (Document) document.getNode();
        assertEquals("message", dom.getDocumentElement().getTagName());
        assertEquals("Hello XML", dom.getDocumentElement().getTextContent());
    }

    public static class MessageStaxResult extends StAXResult {
        public MessageStaxResult(XMLStreamWriter writer) {
            super(writer);
        }
    }
}
