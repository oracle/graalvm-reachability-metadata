/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_legacy;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.jaxp13.Jaxp13XpathEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class Jaxp13XpathEngineTest {
    @BeforeEach
    public void resetConfiguredXPathFactory() {
        XMLUnit.setXPathFactory(null);
        RecordingXPathFactory.reset();
    }

    @AfterEach
    public void clearConfiguredXPathFactory() {
        XMLUnit.setXPathFactory(null);
    }

    @Test
    public void constructsConfiguredXPathFactoryAndEvaluatesXPath() throws Exception {
        XMLUnit.setXPathFactory(RecordingXPathFactory.class.getName());

        Jaxp13XpathEngine engine = new Jaxp13XpathEngine();
        Document document = parseDocument("""
                <orders>
                    <order id="first"><item>coffee</item></order>
                    <order id="second"><item>tea</item></order>
                </orders>
                """);

        assertThat(RecordingXPathFactory.instanceCount()).isEqualTo(1);
        assertThat(engine.evaluate("string(/orders/order[@id='second']/item)", document)).isEqualTo("tea");

        NodeList matchingOrders = engine.getMatchingNodes("/orders/order", document);
        assertThat(matchingOrders.getLength()).isEqualTo(2);
        assertThat(matchingOrders.item(0).getAttributes().getNamedItem("id").getNodeValue()).isEqualTo("first");
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    public static final class RecordingXPathFactory extends XPathFactory {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        private final XPathFactory delegate;

        public RecordingXPathFactory() {
            INSTANCES.incrementAndGet();
            delegate = XPathFactory.newInstance();
        }

        static void reset() {
            INSTANCES.set(0);
        }

        static int instanceCount() {
            return INSTANCES.get();
        }

        @Override
        public boolean isObjectModelSupported(String objectModel) {
            return delegate.isObjectModelSupported(objectModel);
        }

        @Override
        public void setFeature(String name, boolean value) throws XPathFactoryConfigurationException {
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) throws XPathFactoryConfigurationException {
            return delegate.getFeature(name);
        }

        @Override
        public void setXPathVariableResolver(XPathVariableResolver resolver) {
            delegate.setXPathVariableResolver(resolver);
        }

        @Override
        public void setXPathFunctionResolver(XPathFunctionResolver resolver) {
            delegate.setXPathFunctionResolver(resolver);
        }

        @Override
        public XPath newXPath() {
            return delegate.newXPath();
        }
    }
}
