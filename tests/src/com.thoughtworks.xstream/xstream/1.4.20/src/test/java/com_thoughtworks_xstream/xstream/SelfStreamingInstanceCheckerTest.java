/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.util.SelfStreamingInstanceChecker;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SelfStreamingInstanceCheckerTest {
    @Test
    void delegatesMarshallingThroughConverterLookupForOrdinaryObjects() {
        RecordingConverter converter = new RecordingConverter();
        RecordingConverterLookup lookup = new RecordingConverterLookup(converter);
        Object checkedInstance = new Object();
        Object source = "payload";
        SelfStreamingInstanceChecker checker = new SelfStreamingInstanceChecker(lookup, checkedInstance);

        checker.marshal(source, null, null);

        assertThat(lookup.requestedType).isSameAs(Object.class);
        assertThat(converter.marshalledSource).isSameAs(source);
    }

    @Test
    void rejectsMarshallingTheCheckedInstanceItself() {
        RecordingConverterLookup lookup = new RecordingConverterLookup(new RecordingConverter());
        Object checkedInstance = new Object();
        SelfStreamingInstanceChecker checker = new SelfStreamingInstanceChecker(lookup, checkedInstance);

        assertThatThrownBy(() -> checker.marshal(checkedInstance, null, null))
                .isInstanceOf(ConversionException.class)
                .hasMessage("Cannot marshal the XStream instance in action");
    }

    private static final class RecordingConverterLookup implements ConverterLookup {
        private final Converter converter;
        private Class requestedType;

        private RecordingConverterLookup(Converter converter) {
            this.converter = converter;
        }

        @Override
        public Converter lookupConverterForType(Class type) {
            requestedType = type;
            return converter;
        }
    }

    private static final class RecordingConverter implements Converter {
        private Object marshalledSource;

        @Override
        public boolean canConvert(Class type) {
            return true;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            marshalledSource = source;
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return "unmarshalled";
        }
    }
}
