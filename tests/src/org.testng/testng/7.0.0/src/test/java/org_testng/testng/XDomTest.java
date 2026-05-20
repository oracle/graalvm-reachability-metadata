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
                <constructor-child/><default-child>body text</default-child><unknown-child value="handled"/></root>
                """;
        Document document = parse(xml);
        ParentSetterChild.lastInstance = null;
        ConstructorChild.lastInstance = null;
        DefaultChild.lastInstance = null;

        RootElement root = (RootElement) new XDom(new TestTagFactory(), document).parse();

        assertThat(root.booleanAttribute).isTrue();
        assertThat(root.intAttribute).isEqualTo(42);
        assertThat(root.stringAttribute).isEqualTo("alpha");
        assertThat(root.unknownChildValue).isEqualTo("handled");
        assertThat(ParentSetterChild.lastInstance.parent).isSameAs(root);
        assertThat(ConstructorChild.lastInstance.parent).isSameAs(root);
        assertThat(DefaultChild.lastInstance.text).isEqualTo("body text");
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
        private String unknownChildValue;

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

        @OnElement(tag = "unknown-child", attributes = {"value"})
        public void setUnknownChildValue(String value) {
            this.unknownChildValue = value;
        }
    }

    public static final class ParentSetterChild {
        private static ParentSetterChild lastInstance;

        private RootElement parent;

        public ParentSetterChild() {
            lastInstance = this;
        }

        @ParentSetter
        public void setParent(RootElement parent) {
            this.parent = parent;
        }
    }

    public static final class ConstructorChild {
        private static ConstructorChild lastInstance;

        private final RootElement parent;

        public ConstructorChild(RootElement parent) {
            this.parent = parent;
            lastInstance = this;
        }
    }

    public static final class DefaultChild {
        private static DefaultChild lastInstance;

        private String text;

        public DefaultChild() {
            lastInstance = this;
        }

        @TagContent(name = "default-child")
        public void setText(String text) {
            this.text = text;
        }
    }
}
