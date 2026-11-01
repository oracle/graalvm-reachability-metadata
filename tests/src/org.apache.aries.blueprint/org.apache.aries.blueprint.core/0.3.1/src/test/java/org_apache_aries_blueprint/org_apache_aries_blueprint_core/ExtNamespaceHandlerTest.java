/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.io.InputStream;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.aries.blueprint.ext.ExtNamespaceHandler;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtNamespaceHandlerTest {
    @Test
    void suppliesTheExtensionSchema() throws Exception {
        ExtNamespaceHandler handler = new ExtNamespaceHandler();
        URL schema = handler.getSchemaLocation(ExtNamespaceHandler.BLUEPRINT_EXT_NAMESPACE);
        assertThat(schema).isNotNull();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try (InputStream stream = schema.openStream()) {
            Document document = factory.newDocumentBuilder().parse(stream);
            assertThat(document.getDocumentElement().getAttribute("targetNamespace"))
                    .isEqualTo(ExtNamespaceHandler.BLUEPRINT_EXT_NAMESPACE);
            assertThat(document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element")
                    .item(0).getAttributes().getNamedItem("name").getNodeValue())
                    .isEqualTo("property-placeholder");
        }
    }
}
