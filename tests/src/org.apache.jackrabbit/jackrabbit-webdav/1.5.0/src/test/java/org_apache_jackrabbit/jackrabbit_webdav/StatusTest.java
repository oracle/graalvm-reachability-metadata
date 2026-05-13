/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.Status;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class StatusTest {
    private static final int SC_CREATED = 201;
    private static final int SC_NOT_FOUND = 404;

    @Test
    public void constructorInitializesClassLoggerAndSerializesStatusElement() throws Exception {
        Status status = new Status(SC_NOT_FOUND);

        Element element = status.toXml(newDocument());

        assertThat(status.getStatusCode()).isEqualTo(SC_NOT_FOUND);
        assertThat(element.getLocalName()).isEqualTo(DavConstants.XML_STATUS);
        assertThat(element.getNamespaceURI()).isEqualTo(DavConstants.NAMESPACE.getURI());
        assertThat(element.getTextContent()).isEqualTo("HTTP/1.1 404 Not Found");
    }

    @Test
    public void parseAcceptsWhitespaceAndPreservesReasonPhrase() throws Exception {
        Status status = Status.parse("  HTTP/1.1 201 Created by coverage test  ");

        Element element = status.toXml(newDocument());

        assertThat(status.getStatusCode()).isEqualTo(SC_CREATED);
        assertThat(element.getTextContent()).isEqualTo("HTTP/1.1 201 Created by coverage test");
    }

    @Test
    public void parseRejectsMissingStatusLine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Status.parse(null))
                .withMessage("Unable to parse status line from null xml element.");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }
}
