/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.extended.FontConverter;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FontConverterTest {
    @Test
    void marshalsNullTextAttributesWithMapperNullType() {
        FontConverter converter = new FontConverter(new XStream().getMapper());
        RecordingWriter writer = new RecordingWriter();

        converter.marshal(new FontWithNullTextAttribute(), writer, new NoOpMarshallingContext());

        assertThat(writer.nodeNames()).containsExactly("family");
        assertThat(writer.nodeTypes()).containsExactly("com.thoughtworks.xstream.mapper.Mapper$Null");
        assertThat(writer.attributes()).containsEntry("class", "null");
    }

    public static final class FontWithNullTextAttribute extends Font {
        private static final long serialVersionUID = 1L;

        FontWithNullTextAttribute() {
            super(Font.SANS_SERIF, Font.PLAIN, 12);
        }

        @Override
        public Map<TextAttribute, ?> getAttributes() {
            Map<TextAttribute, Object> attributes = new LinkedHashMap<>();
            attributes.put(TextAttribute.FAMILY, null);
            return attributes;
        }
    }

    private static final class RecordingWriter implements ExtendedHierarchicalStreamWriter {
        private final List<String> nodeNames = new ArrayList<>();
        private final List<String> nodeTypes = new ArrayList<>();
        private final Map<String, String> attributes = new LinkedHashMap<>();

        @Override
        public void startNode(String name, Class clazz) {
            nodeNames.add(name);
            nodeTypes.add(clazz.getName());
        }

        @Override
        public void startNode(String name) {
            nodeNames.add(name);
        }

        @Override
        public void addAttribute(String name, String value) {
            attributes.put(name, value);
        }

        @Override
        public void setValue(String text) {
        }

        @Override
        public void endNode() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public ExtendedHierarchicalStreamWriter underlyingWriter() {
            return this;
        }

        private List<String> nodeNames() {
            return nodeNames;
        }

        private List<String> nodeTypes() {
            return nodeTypes;
        }

        private Map<String, String> attributes() {
            return attributes;
        }
    }

    private static final class NoOpMarshallingContext implements MarshallingContext {
        @Override
        public void convertAnother(Object nextItem) {
        }

        @Override
        public void convertAnother(Object nextItem, Converter converter) {
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public void put(Object key, Object value) {
        }

        @Override
        public Iterator keys() {
            return Collections.emptyIterator();
        }
    }
}
