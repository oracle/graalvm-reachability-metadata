/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class XPathTest {
    private static final String XPATH_CLASS_PROPERTY = "org.jdom.xpath.class";

    private String previousXPathClass;

    @BeforeEach
    void captureXPathClassProperty() {
        previousXPathClass = System.getProperty(XPATH_CLASS_PROPERTY);
    }

    @AfterEach
    void restoreXPathClassProperty() {
        if (previousXPathClass == null) {
            System.clearProperty(XPATH_CLASS_PROPERTY);
        } else {
            System.setProperty(XPATH_CLASS_PROPERTY, previousXPathClass);
        }
    }

    @Test
    void newInstanceCreatesConfiguredXPathImplementation() throws Exception {
        System.setProperty(XPATH_CLASS_PROPERTY, ConfiguredXPath.class.getName());
        Element root = new Element("root").setText("payload");

        XPath xpath = XPath.newInstance("/root[@id=$target]");

        assertThat(xpath).isInstanceOf(ConfiguredXPath.class);
        assertThat(xpath.getXPath()).isEqualTo("/root[@id=$target]");
        assertThat(xpath.selectNodes(root)).containsExactly(root);
        assertThat(xpath.selectSingleNode(root)).isSameAs(root);
        assertThat(xpath.valueOf(root)).isEqualTo("payload");
        assertThat(xpath.numberValueOf(root)).isEqualTo(Integer.valueOf("/root[@id=$target]".length()));

        ConfiguredXPath configuredXPath = (ConfiguredXPath) xpath;
        configuredXPath.setVariable("target", "root-1");
        configuredXPath.addNamespace("sample", "urn:jdom-xpath");

        assertThat(configuredXPath.variables).containsEntry("target", "root-1");
        assertThat(configuredXPath.namespaces)
                .extracting(Namespace::getPrefix, Namespace::getURI)
                .containsExactly(tuple("sample", "urn:jdom-xpath"));
    }

    public static final class ConfiguredXPath extends XPath {
        private final String expression;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private final List<Namespace> namespaces = new ArrayList<>();

        public ConfiguredXPath(String expression) {
            this.expression = expression;
        }

        @Override
        public List selectNodes(Object context) throws JDOMException {
            return context == null ? Collections.emptyList() : Collections.singletonList(context);
        }

        @Override
        public Object selectSingleNode(Object context) throws JDOMException {
            return context;
        }

        @Override
        public String valueOf(Object context) throws JDOMException {
            if (context instanceof Element) {
                return ((Element) context).getText();
            }
            return String.valueOf(context);
        }

        @Override
        public Number numberValueOf(Object context) throws JDOMException {
            return Integer.valueOf(expression.length());
        }

        @Override
        public void setVariable(String name, Object value) {
            variables.put(name, value);
        }

        @Override
        public void addNamespace(Namespace namespace) {
            namespaces.add(namespace);
        }

        @Override
        public String getXPath() {
            return expression;
        }
    }
}
