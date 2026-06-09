/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.Validator;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.validation.Schema;

import io.restassured.internal.mapping.JaxbMapper;
import io.restassured.mapper.ObjectMapperSerializationContext;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbMapperTest {
    @Test
    void resolvesGeneratedGroovyClassLiteralHelper() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(JaxbMapper.class.getName());

        assertThat(resolvedClass).isEqualTo(JaxbMapper.class);
    }

    @Test
    void serializesXmlWithExplicitJaxbMapper() {
        JaxbMapper mapper = new JaxbMapper((type, charset) -> new TestJaxbContext());
        Payload payload = new Payload("request", 7);

        Object xml = mapper.serialize(new TestSerializationContext(payload, "UTF-8"));

        assertThat(xml).isEqualTo("""
                <payload encoding="UTF-8"><message>request</message><count>7</count></payload>""");
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(JaxbMapper.class, MethodHandles.lookup());
        MethodHandle classHelper = privateLookup.findStatic(
                JaxbMapper.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    public static class Payload {
        public final String message;
        public final int count;

        Payload(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    private static final class TestSerializationContext implements ObjectMapperSerializationContext {
        private final Object objectToSerialize;
        private final String charset;

        private TestSerializationContext(Object objectToSerialize, String charset) {
            this.objectToSerialize = objectToSerialize;
            this.charset = charset;
        }

        @Override
        public Object getObjectToSerialize() {
            return objectToSerialize;
        }

        @Override
        public <T> T getObjectToSerializeAs(Class<T> type) {
            return type.cast(objectToSerialize);
        }

        @Override
        public String getContentType() {
            return "application/xml";
        }

        @Override
        public String getCharset() {
            return charset;
        }
    }

    private static final class TestJaxbContext extends JAXBContext {
        @Override
        public Unmarshaller createUnmarshaller() {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public Marshaller createMarshaller() {
            return new TestMarshaller();
        }

        @Override
        public Validator createValidator() {
            throw new UnsupportedOperationException("Validation is not used by this test");
        }
    }

    private static final class TestMarshaller implements Marshaller {
        private final Map<String, Object> properties = new HashMap<>();

        @Override
        public void marshal(Object object, Result result) {
            throw new UnsupportedOperationException("Result marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, OutputStream outputStream) throws JAXBException {
            try {
                outputStream.write(xmlFor((Payload) object).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new JAXBException(e);
            }
        }

        @Override
        public void marshal(Object object, File file) {
            throw new UnsupportedOperationException("File marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, Writer writer) throws JAXBException {
            try {
                writer.write(xmlFor((Payload) object));
            } catch (IOException e) {
                throw new JAXBException(e);
            }
        }

        @Override
        public void marshal(Object object, ContentHandler handler) {
            throw new UnsupportedOperationException("SAX marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, Node node) {
            throw new UnsupportedOperationException("DOM node access is not used by this test");
        }

        @Override
        public void marshal(Object object, XMLStreamWriter writer) {
            throw new UnsupportedOperationException("XMLStreamWriter marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, XMLEventWriter writer) {
            throw new UnsupportedOperationException("XMLEventWriter marshalling is not used by this test");
        }

        @Override
        public Node getNode(Object object) {
            throw new UnsupportedOperationException("DOM node access is not used by this test");
        }

        @Override
        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }

        @Override
        public Object getProperty(String name) throws PropertyException {
            if (properties.containsKey(name)) {
                return properties.get(name);
            }
            throw new PropertyException(name);
        }

        @Override
        public void setEventHandler(ValidationEventHandler handler) {
        }

        @Override
        public ValidationEventHandler getEventHandler() {
            return null;
        }

        @Override
        public void setAdapter(XmlAdapter adapter) {
        }

        @Override
        public <A extends XmlAdapter> void setAdapter(Class<A> type, A adapter) {
        }

        @Override
        public <A extends XmlAdapter> A getAdapter(Class<A> type) {
            return null;
        }

        @Override
        public void setAttachmentMarshaller(AttachmentMarshaller attachmentMarshaller) {
        }

        @Override
        public AttachmentMarshaller getAttachmentMarshaller() {
            return null;
        }

        @Override
        public void setSchema(Schema schema) {
        }

        @Override
        public Schema getSchema() {
            return null;
        }

        @Override
        public void setListener(Marshaller.Listener listener) {
        }

        @Override
        public Marshaller.Listener getListener() {
            return null;
        }

        private String xmlFor(Payload payload) {
            return "<payload encoding=\"" + properties.get(JAXB_ENCODING) + "\">"
                    + "<message>" + payload.message + "</message>"
                    + "<count>" + payload.count + "</count>"
                    + "</payload>";
        }
    }
}
