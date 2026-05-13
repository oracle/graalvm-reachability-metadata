/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.OptionsResponse;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OptionsResponseTest {
    @Test
    public void addEntryStoresHrefsAndSerializesOptionsResponse() throws Exception {
        OptionsResponse optionsResponse = new OptionsResponse();
        optionsResponse.addEntry(
                DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                DeltaVConstants.NAMESPACE,
                new String[] {"/activities", "/more-activities"});

        assertThat(optionsResponse.getHrefs(
                        DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                        DeltaVConstants.NAMESPACE))
                .containsExactly("/activities", "/more-activities");
        assertThat(optionsResponse.getHrefs(
                        DeltaVConstants.XML_WSP_COLLECTION_SET,
                        DeltaVConstants.NAMESPACE))
                .isEmpty();

        Element optionsResponseElement = optionsResponse.toXml(newDocument());

        assertThat(optionsResponseElement.getLocalName()).isEqualTo(DeltaVConstants.XML_OPTIONS_RESPONSE);
        assertThat(optionsResponseElement.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        Element entryElement = getFirstChild(optionsResponseElement, DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                DeltaVConstants.NAMESPACE);
        assertThat(entryElement).isNotNull();
        assertThat(entryElement.getElementsByTagNameNS(DavConstants.NAMESPACE.getURI(), DavConstants.XML_HREF).getLength())
                .isEqualTo(2);
    }

    @Test
    public void createFromXmlReadsEachResponseEntryHrefSet() throws Exception {
        Document responseDocument = parseXml("""
                <D:options-response xmlns:D="DAV:" xmlns:jcr="http://jackrabbit.apache.org/webdav">
                    <D:workspace-collection-set>
                        <D:href>/workspaces/default</D:href>
                    </D:workspace-collection-set>
                    <jcr:custom-response>
                        <D:href>/custom/one</D:href>
                        <D:href>/custom/two</D:href>
                    </jcr:custom-response>
                </D:options-response>
                """);

        OptionsResponse optionsResponse = OptionsResponse.createFromXml(responseDocument.getDocumentElement());

        assertThat(optionsResponse.getHrefs(
                        DeltaVConstants.XML_WSP_COLLECTION_SET,
                        DeltaVConstants.NAMESPACE))
                .containsExactly("/workspaces/default");
        assertThat(optionsResponse.getHrefs(
                        "custom-response",
                        Namespace.getNamespace("jcr", "http://jackrabbit.apache.org/webdav")))
                .containsExactly("/custom/one", "/custom/two");
    }

    @Test
    public void createFromXmlRejectsNonOptionsResponseElement() throws Exception {
        Document requestDocument = parseXml("""
                <D:options xmlns:D="DAV:"/>
                """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OptionsResponse.createFromXml(requestDocument.getDocumentElement()))
                .withMessage("DAV:options-response element expected");
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = newDocumentBuilderFactory();
        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        return factory.newDocumentBuilder().parse(input);
    }

    private static Document newDocument() throws Exception {
        return newDocumentBuilderFactory().newDocumentBuilder().newDocument();
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private static Element getFirstChild(Element parent, String localName, Namespace namespace) {
        if (parent.getElementsByTagNameNS(namespace.getURI(), localName).getLength() == 0) {
            return null;
        }
        return (Element) parent.getElementsByTagNameNS(namespace.getURI(), localName).item(0);
    }
}
