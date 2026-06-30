/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.bind.BindConstants;
import org.apache.jackrabbit.webdav.bind.UnbindInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class UnbindInfoTest {
    @Test
    void parsesUnbindRequestXmlAndSerializesItBackToWebdavXml() throws Exception {
        Document requestDocument = parse("""
                <D:unbind xmlns:D="DAV:">
                    <D:segment>removed-binding-name</D:segment>
                </D:unbind>
                """);

        UnbindInfo unbindInfo = UnbindInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(unbindInfo.getSegment()).isEqualTo("removed-binding-name");

        Document responseDocument = newDocument();
        Element unbindElement = unbindInfo.toXml(responseDocument);

        assertThat(DomUtil.matches(unbindElement, BindConstants.XML_UNBIND, BindConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(unbindElement, BindConstants.XML_SEGMENT, BindConstants.NAMESPACE))
                .isEqualTo("removed-binding-name");
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
