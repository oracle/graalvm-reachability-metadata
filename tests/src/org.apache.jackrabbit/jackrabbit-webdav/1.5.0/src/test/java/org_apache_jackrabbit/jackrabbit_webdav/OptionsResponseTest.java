/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.OptionsResponse;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class OptionsResponseTest {
    @Test
    void serializesAndParsesOptionsResponseEntries() throws Exception {
        OptionsResponse response = new OptionsResponse();
        response.addEntry(
                DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
                DeltaVConstants.NAMESPACE,
                new String[] {"/activities", "/archive/activities"});
        response.addEntry(
                DeltaVConstants.XML_WSP_COLLECTION_SET,
                DeltaVConstants.NAMESPACE,
                new String[] {"/workspaces"});

        Element xml = response.toXml(newDocument());
        OptionsResponse parsed = OptionsResponse.createFromXml(xml);

        assertThat(DomUtil.matches(xml, DeltaVConstants.XML_OPTIONS_RESPONSE, DavConstants.NAMESPACE)).isTrue();
        assertThat(parsed.getHrefs(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET, DavConstants.NAMESPACE))
                .containsExactly("/activities", "/archive/activities");
        assertThat(parsed.getHrefs(DeltaVConstants.XML_WSP_COLLECTION_SET, DavConstants.NAMESPACE))
                .containsExactly("/workspaces");
        assertThat(parsed.getHrefs(DeltaVConstants.XML_VH_COLLECTION_SET, DavConstants.NAMESPACE)).isEmpty();
    }

    @Test
    void rejectsNonOptionsResponseElement() throws Exception {
        Element element = DomUtil.createElement(newDocument(), DeltaVConstants.XML_OPTIONS, DavConstants.NAMESPACE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OptionsResponse.createFromXml(element))
                .withMessage("DAV:options-response element expected");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
