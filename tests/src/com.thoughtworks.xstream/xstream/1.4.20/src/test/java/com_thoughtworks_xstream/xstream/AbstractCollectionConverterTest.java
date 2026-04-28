/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.StringWriter;
import java.util.Iterator;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCollectionConverterTest {
    @Test
    void writesNullCollectionItemsUsingMapperNullNodeType() {
        StringWriter buffer = new StringWriter();
        HierarchicalStreamWriter writer = new PrettyPrintWriter(buffer);
        TestCollectionConverter converter = new TestCollectionConverter(new XStream().getMapper());

        converter.writeNullCollectionItem(writer);
        writer.flush();

        assertThat(buffer.toString()).contains("<null/>");
    }

    private static final class TestCollectionConverter extends AbstractCollectionConverter {
        private TestCollectionConverter(Mapper mapper) {
            super(mapper);
        }

        private void writeNullCollectionItem(HierarchicalStreamWriter writer) {
            writeCompleteItem(null, new NoopMarshallingContext(), writer);
        }

        @Override
        public boolean canConvert(Class type) {
            return false;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            throw new UnsupportedOperationException("Only protected item writing is exercised by this test");
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            throw new UnsupportedOperationException("Only protected item writing is exercised by this test");
        }
    }

    private static final class NoopMarshallingContext implements MarshallingContext {
        @Override
        public void convertAnother(Object item) {
            throw new UnsupportedOperationException("Null item writing should not marshal nested values");
        }

        @Override
        public void convertAnother(Object item, Converter converter) {
            throw new UnsupportedOperationException("Null item writing should not marshal nested values");
        }

        @Override
        public Object get(Object key) {
            throw new UnsupportedOperationException("Null item writing should not read context values");
        }

        @Override
        public void put(Object key, Object value) {
            throw new UnsupportedOperationException("Null item writing should not store context values");
        }

        @Override
        public Iterator keys() {
            throw new UnsupportedOperationException("Null item writing should not enumerate context values");
        }
    }
}
