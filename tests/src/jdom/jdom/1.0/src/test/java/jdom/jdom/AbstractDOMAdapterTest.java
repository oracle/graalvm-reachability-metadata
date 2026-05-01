/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jdom.DocType;
import org.jdom.JDOMException;
import org.jdom.adapters.AbstractDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

public class AbstractDOMAdapterTest {
    @Test
    void createDocumentWithDocTypeAttemptsToApplyInternalSubset() throws Exception {
        AbstractDOMAdapter adapter = new StandardDocumentAdapter();
        String internalSubset = """
                <!ELEMENT book (title)>
                <!ELEMENT title (#PCDATA)>
                """.trim();
        DocType docType = new DocType("book", "-//JDOM Test//DTD Book//EN", "book.dtd");
        docType.setInternalSubset(internalSubset);

        Document document = adapter.createDocument(docType);

        DocumentType documentType = document.getDoctype();
        assertThat(documentType).isNotNull();
        assertThat(documentType.getName()).isEqualTo("book");
        assertThat(documentType.getPublicId()).isEqualTo("-//JDOM Test//DTD Book//EN");
        assertThat(documentType.getSystemId()).isEqualTo("book.dtd");
        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("http://temporary");
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("book");
    }

    private static final class StandardDocumentAdapter extends AbstractDOMAdapter {
        @Override
        public Document getDocument(InputStream in, boolean validate) throws IOException, JDOMException {
            throw new UnsupportedOperationException("Parsing is not needed for this AbstractDOMAdapter coverage test");
        }

        @Override
        public Document createDocument() throws JDOMException {
            try {
                return DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                throw new JDOMException("Unable to create a DOM document", e);
            }
        }
    }
}
