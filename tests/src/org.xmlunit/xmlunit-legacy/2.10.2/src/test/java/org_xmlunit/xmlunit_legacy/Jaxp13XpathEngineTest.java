/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_legacy;

import java.util.Map;

import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.jaxp13.Jaxp13XpathEngine;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

public class Jaxp13XpathEngineTest {
    @Test
    void selectsAndEvaluatesNamespacedXmlWithConfiguredXPathFactory() throws Exception {
        String previousFactory = XMLUnit.getXPathFactory();
        try {
            XMLUnit.setXPathFactory("org.apache.xpath.jaxp.XPathFactoryImpl");
            XpathEngine engine = new Jaxp13XpathEngine();
            engine.setNamespaceContext(new SimpleNamespaceContext(Map.of("c", "urn:catalog")));
            Document catalog = XMLUnit.buildControlDocument("""
                    <catalog xmlns="urn:catalog">
                        <item id="pencil" price="2.5">Pencil</item>
                        <item id="notebook" price="40">Notebook</item>
                    </catalog>
                    """);

            NodeList items = engine.getMatchingNodes("/c:catalog/c:item", catalog);
            assertThat(items.getLength()).isEqualTo(2);
            assertThat(items.item(0).getTextContent()).isEqualTo("Pencil");
            assertThat(items.item(1).getTextContent()).isEqualTo("Notebook");
            assertThat(engine.evaluate("sum(/c:catalog/c:item/@price)", catalog)).isEqualTo("42.5");
            assertThat(engine.evaluate("/c:catalog/c:item[@id='notebook']/text()", catalog))
                    .isEqualTo("Notebook");
            assertThat(engine.getMatchingNodes("/c:catalog/c:item[@price > 100]", catalog).getLength())
                    .isZero();
        } finally {
            XMLUnit.setXPathFactory(previousFactory);
        }
    }
}
