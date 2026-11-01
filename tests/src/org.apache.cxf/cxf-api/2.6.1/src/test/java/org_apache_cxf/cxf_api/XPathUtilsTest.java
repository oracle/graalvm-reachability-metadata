/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_api;

import java.util.Map;

import javax.xml.xpath.XPathConstants;

import org.apache.cxf.helpers.XMLUtils;
import org.apache.cxf.helpers.XPathUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class XPathUtilsTest {
    @Test
    void selectsNamespacedElementsAndEvaluatesValues() throws Exception {
        Document document = XMLUtils.parse("""
                <catalog xmlns="urn:catalog">
                    <item id="tea" available="true">Tea</item>
                    <item id="coffee" available="false">Coffee</item>
                </catalog>
                """);
        XPathUtils xpath = new XPathUtils(Map.of("c", "urn:catalog"));
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        assertEquals("Tea", xpath.getValueString("/c:catalog/c:item[@id='tea']", document));
        NodeList items = xpath.getValueList("/c:catalog/c:item", document);
        assertNotNull(items);
        assertEquals(2, items.getLength());
        assertEquals("coffee", xpath.getValueNode("/c:catalog/c:item[2]", document)
                .getAttributes().getNamedItem("id").getNodeValue());
        assertEquals(2.0, xpath.getValue("count(/c:catalog/c:item)", document, XPathConstants.NUMBER));
        assertEquals(Boolean.TRUE, xpath.getValue("/c:catalog/c:item[@available='true']",
                document, XPathConstants.BOOLEAN));
        assertEquals(Boolean.FALSE, xpath.getValue("/c:catalog/c:item[@id='missing']",
                document, XPathConstants.BOOLEAN));
        assertSame(original, Thread.currentThread().getContextClassLoader());
    }
}
