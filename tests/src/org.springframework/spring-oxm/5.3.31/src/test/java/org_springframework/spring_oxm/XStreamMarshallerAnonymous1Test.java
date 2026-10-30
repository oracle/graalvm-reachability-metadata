/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_oxm;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.xstream.XStreamMarshaller;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XStreamMarshallerAnonymous1Test {
    @Test
    void usesMapperConstructorForCustomElementNames() throws Exception {
        XStreamMarshaller marshaller = new XStreamMarshaller();
        marshaller.setStreamDriver(new DomDriver());
        marshaller.setMapperWrappers(MessageMapper.class);

        StringWriter output = new StringWriter();
        marshaller.marshal("hello & goodbye", new StreamResult(output));

        assertEquals("<message>hello &amp; goodbye</message>", output.toString());
        assertEquals("hello & goodbye",
                marshaller.unmarshal(new StreamSource(new StringReader(output.toString()))));
    }

    @Test
    void chainsMapperAndMapperWrapperConstructorsInConfiguredOrder() throws Exception {
        XStreamMarshaller marshaller = new XStreamMarshaller();
        marshaller.setStreamDriver(new DomDriver());
        marshaller.setMapperWrappers(MessageMapper.class, EnvelopeMapper.class);

        StringWriter output = new StringWriter();
        marshaller.marshal("delivery confirmed", new StreamResult(output));

        assertEquals("<envelope-message>delivery confirmed</envelope-message>", output.toString());
        assertEquals("delivery confirmed",
                marshaller.unmarshal(new StreamSource(new StringReader(output.toString()))));
    }

    public static class MessageMapper extends MapperWrapper {
        public MessageMapper(Mapper wrapped) {
            super(wrapped);
        }

        @Override
        public String serializedClass(Class type) {
            return type == String.class ? "message" : super.serializedClass(type);
        }

        @Override
        public Class realClass(String elementName) {
            return "message".equals(elementName) ? String.class : super.realClass(elementName);
        }
    }

    public static class EnvelopeMapper extends MapperWrapper {
        public EnvelopeMapper(MapperWrapper wrapped) {
            super(wrapped);
        }

        @Override
        public String serializedClass(Class type) {
            String elementName = super.serializedClass(type);
            return type == String.class ? "envelope-" + elementName : elementName;
        }

        @Override
        public Class realClass(String elementName) {
            String unwrappedName = elementName.startsWith("envelope-")
                    ? elementName.substring("envelope-".length()) : elementName;
            return super.realClass(unwrappedName);
        }
    }
}
