/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.provider.jaxb.AbstractListElementProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.helpers.AbstractUnmarshallerImpl;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractListElementProviderTest {
    @BeforeAll
    static void installRuntimeDelegate() {
        RuntimeDelegate.setInstance(new TestRuntimeDelegate());
    }

    @Test
    void readsXmlRootElementsIntoArray() throws IOException {
        AbstractListElementProvider provider = new TestListElementProvider(new TestProviders());
        byte[] xml = "<items><item>alpha</item><item>beta</item></items>"
                .getBytes(StandardCharsets.UTF_8);

        Object result = provider.readFrom(
                arrayType(),
                Item[].class,
                new Annotation[0],
                MediaType.APPLICATION_XML_TYPE,
                null,
                new ByteArrayInputStream(xml));

        assertThat(result).isInstanceOf(Item[].class);
        assertThat((Item[]) result).extracting(Item::getName).containsExactly("alpha", "beta");
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> arrayType() {
        return (Class<Object>) (Class<?>) Item[].class;
    }

    private static final class TestListElementProvider extends AbstractListElementProvider {
        private TestListElementProvider(Providers providers) {
            super(providers, MediaType.APPLICATION_XML_TYPE);
        }

        @Override
        public void writeList(
                Class<?> elementType,
                Collection<?> values,
                MediaType mediaType,
                Charset charset,
                Marshaller marshaller,
                OutputStream entityStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected XMLStreamReader getXMLStreamReader(
                Class<?> elementType,
                MediaType mediaType,
                Unmarshaller unmarshaller,
                InputStream entityStream) throws XMLStreamException {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            return factory.createXMLStreamReader(entityStream);
        }
    }

    private static final class TestProviders implements Providers {
        private final Unmarshaller unmarshaller = new ItemUnmarshaller();

        @Override
        public <T> MessageBodyReader<T> getMessageBodyReader(
                Class<T> type,
                Type genericType,
                Annotation[] annotations,
                MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyWriter<T> getMessageBodyWriter(
                Class<T> type,
                Type genericType,
                Annotation[] annotations,
                MediaType mediaType) {
            return null;
        }

        @Override
        public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ContextResolver<T> getContextResolver(
                Class<T> contextType,
                MediaType mediaType) {
            if (contextType == Unmarshaller.class) {
                return type -> (T) unmarshaller;
            }
            return null;
        }
    }

    private static final class TestRuntimeDelegate extends RuntimeDelegate {
        @Override
        public UriBuilder createUriBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseBuilder createResponseBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VariantListBuilder createVariantListBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            return new HeaderDelegate<T>() {
                @Override
                public T fromString(String value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String toString(T value) {
                    MediaType mediaType = (MediaType) value;
                    return mediaType.getType() + "/" + mediaType.getSubtype();
                }
            };
        }
    }

    private static final class ItemUnmarshaller extends AbstractUnmarshallerImpl {
        @Override
        public Object unmarshal(XMLStreamReader reader) throws JAXBException {
            try {
                String name = reader.getElementText();
                advanceBeyondElement(reader);
                return new Item(name);
            } catch (XMLStreamException ex) {
                throw new JAXBException(ex);
            }
        }

        @Override
        public <T> JAXBElement<T> unmarshal(XMLStreamReader reader, Class<T> expectedType)
                throws JAXBException {
            T value = expectedType.cast(unmarshal(reader));
            return new JAXBElement<T>(new QName("item"), expectedType, value);
        }

        @Override
        protected Object unmarshal(XMLReader reader, InputSource source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object unmarshal(Node node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnmarshallerHandler getUnmarshallerHandler() {
            throw new UnsupportedOperationException();
        }

        private static void advanceBeyondElement(XMLStreamReader reader) throws XMLStreamException {
            if (reader.hasNext() && reader.getEventType() == XMLStreamConstants.END_ELEMENT) {
                reader.next();
            }
        }
    }

    @XmlRootElement(name = "item")
    public static final class Item {
        private final String name;

        public Item(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
