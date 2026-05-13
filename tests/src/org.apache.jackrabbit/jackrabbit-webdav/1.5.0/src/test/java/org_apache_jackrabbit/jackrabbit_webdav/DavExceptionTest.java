/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DavExceptionTest {
    private static final int SC_NOT_FOUND = 404;
    private static final int SC_CONFLICT = 409;

    @Test
    public void constructorLoadsStatusPhrasesResource() {
        DavException exception = new DavException(SC_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(SC_NOT_FOUND);
        assertThat(exception.getStatusPhrase()).isEqualTo("Not Found");
        assertThat(exception.getMessage()).isEqualTo("Not Found");
        assertThat(exception.hasErrorCondition()).isFalse();
    }

    @Test
    public void toXmlWrapsSpecificErrorConditionInDavErrorElement() throws Exception {
        Document document = newDocument();
        Element condition = document.createElementNS(DavConstants.NAMESPACE.getURI(), "D:missing-resource");
        DavException exception = new DavException(
                SC_CONFLICT,
                "conflict",
                new IllegalStateException("cause"),
                condition);

        Element error = exception.toXml(newDocument());

        assertThat(exception.getStatusPhrase()).isEqualTo("Conflict");
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(error).isNotNull();
        assertThat(error.getLocalName()).isEqualTo(DavException.XML_ERROR);
        assertThat(error.getNamespaceURI()).isEqualTo(DavConstants.NAMESPACE.getURI());
        assertThat(error.getFirstChild().getLocalName()).isEqualTo("missing-resource");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }
}
