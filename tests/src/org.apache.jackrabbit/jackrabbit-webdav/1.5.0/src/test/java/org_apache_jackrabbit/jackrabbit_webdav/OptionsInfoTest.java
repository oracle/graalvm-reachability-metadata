/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OptionsInfoTest {
    @Test
    void serializesRequestedDeltaVOptionElements() throws Exception {
        OptionsInfo optionsInfo = new OptionsInfo(new String[] {
                DeltaVConstants.XML_VH_COLLECTION_SET,
                DeltaVConstants.XML_WSP_COLLECTION_SET,
                DeltaVConstants.XML_ACTIVITY_COLLECTION_SET
        });

        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                Namespace.getNamespace("urn:example:other"))).isFalse();

        Document document = newDocument();
        Element optionsElement = optionsInfo.toXml(document);

        assertThat(DomUtil.matches(optionsElement, DeltaVConstants.XML_OPTIONS, DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(optionsElement, DeltaVConstants.XML_VH_COLLECTION_SET,
                DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(optionsElement, DeltaVConstants.XML_WSP_COLLECTION_SET,
                DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(optionsElement, DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                DeltaVConstants.NAMESPACE)).isTrue();
    }

    @Test
    void parsesOptionsXmlAndRejectsUnexpectedRootElement() throws Exception {
        Document requestDocument = parse("""
                <D:options xmlns:D="DAV:">
                    <D:version-history-collection-set/>
                    <D:workspace-collection-set/>
                </D:options>
                """);

        OptionsInfo optionsInfo = OptionsInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isFalse();

        Document invalidDocument = parse("""
                <D:not-options xmlns:D="DAV:"/>
                """);

        assertThatThrownBy(() -> OptionsInfo.createFromXml(invalidDocument.getDocumentElement()))
                .isInstanceOfSatisfying(DavException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DavServletResponse.SC_BAD_REQUEST));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Document newDocument() throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.newDocument();
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
