/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.XMLReaderUtils;

public class ConfigBaseTest {

    @Test
    public void configureSetsPrimitiveComplexListAndMapParameters() throws Exception {
        ConfigurableConfig config = new ConfigurableConfig();
        String xml = """
                <properties>
                  <config>
                    <intValue>7</intValue>
                    <longValue>123456789</longValue>
                    <floatValue>1.5</floatValue>
                    <doubleValue>2.25</doubleValue>
                    <enabled>true</enabled>
                    <stringValue>configured</stringValue>
                    <alias>adder-value</alias>
                    <names>
                      <name>alpha</name>
                      <name>beta</name>
                    </names>
                    <mappings>
                      <entry from="first" to="one"/>
                      <entry k="second" v="two"/>
                    </mappings>
                    <nested class="%s">
                      <value>nested-value</value>
                    </nested>
                  </config>
                </properties>
                """.formatted(NestedConfig.class.getName());

        Set<String> settings = config.configureFrom(xml);

        assertThat(settings).contains("intValue", "longValue", "floatValue", "doubleValue",
                "enabled", "stringValue", "alias", "names", "mappings", "nested");
        assertThat(config.intValue).isEqualTo(7);
        assertThat(config.longValue).isEqualTo(123456789L);
        assertThat(config.floatValue).isEqualTo(1.5f);
        assertThat(config.doubleValue).isEqualTo(2.25d);
        assertThat(config.enabled).isTrue();
        assertThat(config.stringValue).isEqualTo("configured");
        assertThat(config.aliases).containsExactly("adder-value");
        assertThat(config.names).containsExactly("alpha", "beta");
        assertThat(config.mappings).containsEntry("first", "one").containsEntry("second", "two");
        assertThat(config.nested.value).isEqualTo("nested-value");
    }

    @Test
    public void configureSetsClassListParameter() throws Exception {
        String xml = """
                <properties>
                  <group>
                    <plugins class="%s">
                      <plugin class="%s">
                        <name>plugin-one</name>
                      </plugin>
                    </plugins>
                  </group>
                </properties>
                """.formatted(Plugin.class.getName(), PluginImpl.class.getName());

        CompositeGroup group = ConfigurableConfig.buildCompositeFrom(wrapClassListElement(xml));

        assertThat(group.plugins).hasSize(1);
        assertThat(group.plugins.get(0)).isInstanceOf(PluginImpl.class);
        assertThat(((PluginImpl) group.plugins.get(0)).name).isEqualTo("plugin-one");
    }

    @Test
    public void buildCompositeCreatesCompositeWithConfiguredItems() throws Exception {
        String xml = """
                <properties>
                  <group>
                    <item class="%s">
                      <stringValue>first</stringValue>
                    </item>
                    <item class="%s">
                      <stringValue>second</stringValue>
                    </item>
                    <description>loaded-composite</description>
                  </group>
                </properties>
                """.formatted(SimpleItem.class.getName(), SimpleItem.class.getName());

        CompositeGroup group = ConfigurableConfig.buildCompositeFrom(xml);

        assertThat(group.items)
                .extracting(item -> item.stringValue)
                .containsExactly("first", "second");
        assertThat(group.description).isEqualTo("loaded-composite");
    }

    private static InputStream xmlStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static Element wrapClassListElement(String xml) throws Exception {
        Document document = XMLReaderUtils.buildDOM(xmlStream(xml));
        return new WrappingElement(document.getDocumentElement());
    }

    public static class ConfigurableConfig extends ConfigBase {
        private int intValue;
        private long longValue;
        private float floatValue;
        private double doubleValue;
        private boolean enabled;
        private String stringValue;
        private List<String> names;
        private Map<String, String> mappings;
        private NestedConfig nested;
        private final List<String> aliases = new ArrayList<>();

        public Set<String> configureFrom(String xml) throws TikaConfigException, IOException {
            return configure("config", xmlStream(xml));
        }

        public static CompositeGroup buildCompositeFrom(String xml)
                throws TikaConfigException, IOException {
            return buildComposite("group", CompositeGroup.class, "item", SimpleItem.class,
                    xmlStream(xml));
        }

        public static CompositeGroup buildCompositeFrom(Element element)
                throws TikaConfigException, IOException {
            return buildComposite("group", CompositeGroup.class, "item", SimpleItem.class,
                    element);
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public void addAlias(String alias) {
            aliases.add(alias);
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        public void setNested(NestedConfig nested) {
            this.nested = nested;
        }
    }

    public interface Plugin {
    }

    public static class PluginImpl implements Plugin {
        private String name;

        public PluginImpl() {
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class NestedConfig {
        private String value;

        public NestedConfig() {
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class SimpleItem {
        private String stringValue;

        public SimpleItem() {
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }

    public static class CompositeGroup {
        private final List<SimpleItem> items;
        private String description;
        private List<Plugin> plugins;

        public CompositeGroup(List<SimpleItem> items) {
            this.items = items;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setPlugins(List<Plugin> plugins) {
            this.plugins = plugins;
        }
    }

    private static class WrappingElement implements Element {
        private final Element delegate;

        WrappingElement(Element delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getTagName() {
            return delegate.getTagName();
        }

        @Override
        public String getAttribute(String name) {
            return delegate.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, String value) {
            delegate.setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            delegate.removeAttribute(name);
        }

        @Override
        public Attr getAttributeNode(String name) {
            return delegate.getAttributeNode(name);
        }

        @Override
        public Attr setAttributeNode(Attr newAttr) {
            return delegate.setAttributeNode(newAttr);
        }

        @Override
        public Attr removeAttributeNode(Attr oldAttr) {
            return delegate.removeAttributeNode(oldAttr);
        }

        @Override
        public NodeList getElementsByTagName(String name) {
            return delegate.getElementsByTagName(name);
        }

        @Override
        public String getAttributeNS(String namespaceURI, String localName) {
            return delegate.getAttributeNS(namespaceURI, localName);
        }

        @Override
        public void setAttributeNS(String namespaceURI, String qualifiedName, String value) {
            delegate.setAttributeNS(namespaceURI, qualifiedName, value);
        }

        @Override
        public void removeAttributeNS(String namespaceURI, String localName) {
            delegate.removeAttributeNS(namespaceURI, localName);
        }

        @Override
        public Attr getAttributeNodeNS(String namespaceURI, String localName) {
            return delegate.getAttributeNodeNS(namespaceURI, localName);
        }

        @Override
        public Attr setAttributeNodeNS(Attr newAttr) {
            return delegate.setAttributeNodeNS(newAttr);
        }

        @Override
        public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
            return delegate.getElementsByTagNameNS(namespaceURI, localName);
        }

        @Override
        public boolean hasAttribute(String name) {
            return delegate.hasAttribute(name);
        }

        @Override
        public boolean hasAttributeNS(String namespaceURI, String localName) {
            return delegate.hasAttributeNS(namespaceURI, localName);
        }

        @Override
        public TypeInfo getSchemaTypeInfo() {
            return delegate.getSchemaTypeInfo();
        }

        @Override
        public void setIdAttribute(String name, boolean isId) {
            delegate.setIdAttribute(name, isId);
        }

        @Override
        public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) {
            delegate.setIdAttributeNS(namespaceURI, localName, isId);
        }

        @Override
        public void setIdAttributeNode(Attr idAttr, boolean isId) {
            delegate.setIdAttributeNode(idAttr, isId);
        }

        @Override
        public String getNodeName() {
            return delegate.getNodeName();
        }

        @Override
        public String getNodeValue() {
            return delegate.getNodeValue();
        }

        @Override
        public void setNodeValue(String nodeValue) {
            delegate.setNodeValue(nodeValue);
        }

        @Override
        public short getNodeType() {
            return delegate.getNodeType();
        }

        @Override
        public Node getParentNode() {
            return delegate.getParentNode();
        }

        @Override
        public NodeList getChildNodes() {
            return new WrappingNodeList(delegate.getChildNodes());
        }

        @Override
        public Node getFirstChild() {
            return wrapNode(delegate.getFirstChild());
        }

        @Override
        public Node getLastChild() {
            return wrapNode(delegate.getLastChild());
        }

        @Override
        public Node getPreviousSibling() {
            return wrapNode(delegate.getPreviousSibling());
        }

        @Override
        public Node getNextSibling() {
            return wrapNode(delegate.getNextSibling());
        }

        @Override
        public NamedNodeMap getAttributes() {
            return delegate.getAttributes();
        }

        @Override
        public Document getOwnerDocument() {
            return delegate.getOwnerDocument();
        }

        @Override
        public Node insertBefore(Node newChild, Node refChild) {
            return delegate.insertBefore(newChild, refChild);
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) {
            return delegate.replaceChild(newChild, oldChild);
        }

        @Override
        public Node removeChild(Node oldChild) {
            return delegate.removeChild(oldChild);
        }

        @Override
        public Node appendChild(Node newChild) {
            return delegate.appendChild(newChild);
        }

        @Override
        public boolean hasChildNodes() {
            return delegate.hasChildNodes();
        }

        @Override
        public Node cloneNode(boolean deep) {
            return delegate.cloneNode(deep);
        }

        @Override
        public void normalize() {
            delegate.normalize();
        }

        @Override
        public boolean isSupported(String feature, String version) {
            return delegate.isSupported(feature, version);
        }

        @Override
        public String getNamespaceURI() {
            return delegate.getNamespaceURI();
        }

        @Override
        public String getPrefix() {
            return delegate.getPrefix();
        }

        @Override
        public void setPrefix(String prefix) {
            delegate.setPrefix(prefix);
        }

        @Override
        public String getLocalName() {
            return delegate.getLocalName();
        }

        @Override
        public boolean hasAttributes() {
            return delegate.hasAttributes();
        }

        @Override
        public String getBaseURI() {
            return delegate.getBaseURI();
        }

        @Override
        public short compareDocumentPosition(Node other) {
            return delegate.compareDocumentPosition(other);
        }

        @Override
        public String getTextContent() {
            return delegate.getTextContent();
        }

        @Override
        public void setTextContent(String textContent) {
            delegate.setTextContent(textContent);
        }

        @Override
        public boolean isSameNode(Node other) {
            return delegate.isSameNode(other);
        }

        @Override
        public String lookupPrefix(String namespaceURI) {
            return delegate.lookupPrefix(namespaceURI);
        }

        @Override
        public boolean isDefaultNamespace(String namespaceURI) {
            return delegate.isDefaultNamespace(namespaceURI);
        }

        @Override
        public String lookupNamespaceURI(String prefix) {
            return delegate.lookupNamespaceURI(prefix);
        }

        @Override
        public boolean isEqualNode(Node arg) {
            return delegate.isEqualNode(arg);
        }

        @Override
        public Object getFeature(String feature, String version) {
            return delegate.getFeature(feature, version);
        }

        @Override
        public Object setUserData(String key, Object data, UserDataHandler handler) {
            return delegate.setUserData(key, data, handler);
        }

        @Override
        public Object getUserData(String key) {
            return delegate.getUserData(key);
        }
    }

    private static final class FlappingClassAttributeElement extends WrappingElement {
        private final NamedNodeMap attributes;

        FlappingClassAttributeElement(Element delegate) {
            super(delegate);
            attributes = new FlappingClassAttributeMap(delegate.getAttributes());
        }

        @Override
        public NamedNodeMap getAttributes() {
            return attributes;
        }
    }

    private static class WrappingNodeList implements NodeList {
        private final NodeList delegate;

        WrappingNodeList(NodeList delegate) {
            this.delegate = delegate;
        }

        @Override
        public Node item(int index) {
            return wrapNode(delegate.item(index));
        }

        @Override
        public int getLength() {
            return delegate.getLength();
        }
    }

    private static class FlappingClassAttributeMap implements NamedNodeMap {
        private final NamedNodeMap delegate;
        private boolean classAttributeHidden = true;

        FlappingClassAttributeMap(NamedNodeMap delegate) {
            this.delegate = delegate;
        }

        @Override
        public Node getNamedItem(String name) {
            if ("class".equals(name) && classAttributeHidden) {
                classAttributeHidden = false;
                return null;
            }
            return delegate.getNamedItem(name);
        }

        @Override
        public Node setNamedItem(Node arg) {
            return delegate.setNamedItem(arg);
        }

        @Override
        public Node removeNamedItem(String name) {
            return delegate.removeNamedItem(name);
        }

        @Override
        public Node item(int index) {
            return delegate.item(index);
        }

        @Override
        public int getLength() {
            return delegate.getLength();
        }

        @Override
        public Node getNamedItemNS(String namespaceURI, String localName) {
            return delegate.getNamedItemNS(namespaceURI, localName);
        }

        @Override
        public Node setNamedItemNS(Node arg) {
            return delegate.setNamedItemNS(arg);
        }

        @Override
        public Node removeNamedItemNS(String namespaceURI, String localName) {
            return delegate.removeNamedItemNS(namespaceURI, localName);
        }
    }

    private static Node wrapNode(Node node) {
        if (node instanceof Element element) {
            if ("plugins".equals(element.getLocalName())) {
                return new FlappingClassAttributeElement(element);
            }
            return new WrappingElement(element);
        }
        return node;
    }
}
