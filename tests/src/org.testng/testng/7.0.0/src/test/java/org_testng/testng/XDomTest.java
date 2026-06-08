/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.testng.xml.dom.ITagFactory;
import org.testng.xml.dom.OnElement;
import org.testng.xml.dom.ParentSetter;
import org.testng.xml.dom.TagContent;
import org.testng.xml.dom.XDom;
import org.w3c.dom.Document;

public class XDomTest {
    @Test
    void parsesElementsAttributesChildrenAndTextThroughDomReflectionHooks() throws Exception {
        String xml = """
                <root boolean-attribute="true" int-attribute="42" string-attribute="alpha"><parent-setter-child/>\
                <constructor-child/><default-child>body text</default-child></root>
                """;
        Document document = parse(xml);
        ParentSetterChild.parent = null;
        ConstructorChild.parent = null;
        DefaultChild.text = null;

        RootElement root = (RootElement) new XDom(new TestTagFactory(), document).parse();

        assertThat(root.booleanAttribute).isTrue();
        assertThat(root.intAttribute).isEqualTo(42);
        assertThat(root.stringAttribute).isEqualTo("alpha");
        assertThat(ParentSetterChild.parent).isSameAs(root);
        assertThat(ConstructorChild.parent).isSameAs(root);
        assertThat(DefaultChild.text).isEqualTo("body text");
    }

    @Test
    void invokesAnnotatedSetterForChildWithoutRegisteredTagClass() throws Exception {
        String xml = """
                <root><unmapped-child name="timeout" value="60"/></root>
                """;
        Document document = parse(xml);

        RootElement root = (RootElement) new XDom(new TestTagFactory(), document).parse();

        assertThat(root.unmappedChildName).isEqualTo("timeout");
        assertThat(root.unmappedChildValue).isEqualTo("60");
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static final class TestTagFactory implements ITagFactory {
        private final Map<String, Class<?>> classesByTag = new HashMap<>();

        public TestTagFactory() {
            classesByTag.put("root", RootElement.class);
            classesByTag.put("parent-setter-child", ParentSetterChild.class);
            classesByTag.put("constructor-child", ConstructorChild.class);
            classesByTag.put("default-child", DefaultChild.class);
        }

        @Override
        public Class<?> getClassForTag(String tag) {
            return classesByTag.get(tag);
        }
    }

    public static final class RootElement {
        private boolean booleanAttribute;
        private int intAttribute;
        private String stringAttribute;
        private ParentSetterChild parentSetterChild;
        private ConstructorChild constructorChild;
        private DefaultChild defaultChild;
        private String unmappedChildName;
        private String unmappedChildValue;

        public RootElement() {
        }

        public void setBooleanAttribute(boolean booleanAttribute) {
            this.booleanAttribute = booleanAttribute;
        }

        public void setIntAttribute(int intAttribute) {
            this.intAttribute = intAttribute;
        }

        public void setStringAttribute(String stringAttribute) {
            this.stringAttribute = stringAttribute;
        }

        public void addParentSetterChild(ParentSetterChild parentSetterChild) {
            this.parentSetterChild = parentSetterChild;
        }

        public void addConstructorChild(ConstructorChild constructorChild) {
            this.constructorChild = constructorChild;
        }

        public void addDefaultChild(DefaultChild defaultChild) {
            this.defaultChild = defaultChild;
        }

        @OnElement(tag = "unmapped-child", attributes = {"name", "value"})
        public void onUnmappedChild(String name, String value) {
            this.unmappedChildName = name;
            this.unmappedChildValue = value;
        }
    }

    public static final class ParentSetterChild {
        private static RootElement parent;

        public ParentSetterChild() {
        }

        @ParentSetter
        public void setParent(RootElement parent) {
            ParentSetterChild.parent = parent;
        }
    }

    public static final class ConstructorChild {
        private static RootElement parent;

        public ConstructorChild(RootElement parent) {
            ConstructorChild.parent = parent;
        }
    }

    public static final class DefaultChild {
        private static String text;

        public DefaultChild() {
        }

        @TagContent(name = "default-child")
        public void setText(String text) {
            DefaultChild.text = text;
        }
    }
}
