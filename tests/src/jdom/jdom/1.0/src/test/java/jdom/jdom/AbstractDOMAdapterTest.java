/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.DocType;
import org.jdom.JDOMException;
import org.jdom.adapters.AbstractDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractDOMAdapterTest {
    @Test
    void createDocumentWithDocTypeAttemptsToApplyInternalSubset() throws Exception {
        ConcreteDOMAdapter adapter = new ConcreteDOMAdapter();
        DocType docType = new DocType("catalog", "catalog.dtd");
        docType.setInternalSubset("<!ELEMENT catalog (book*)>\n<!ELEMENT book (#PCDATA)>");

        Document document = adapter.createDocument(docType);
        DocumentType domDocType = document.getDoctype();

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("catalog");
        assertThat(domDocType).isNotNull();
        assertThat(domDocType.getName()).isEqualTo("catalog");
        assertThat(domDocType.getSystemId()).isEqualTo("catalog.dtd");
        assertThat(domDocType.getInternalSubset() == null
                || domDocType.getInternalSubset().equals(docType.getInternalSubset())).isTrue();
    }

    public static final class ConcreteDOMAdapter extends AbstractDOMAdapter {
        @Override
        public Document getDocument(InputStream in, boolean validate) throws IOException, JDOMException {
            throw new UnsupportedOperationException("Parsing is not needed by this test");
        }

        @Override
        public Document createDocument() throws JDOMException {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
                factory.setNamespaceAware(true);
                return factory.newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                throw new JDOMException("Could not create DOM document", e);
            }
        }
    }
}
