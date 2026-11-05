/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Providers;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.jaxb.CollectionProvider;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlTypeProvider;
import org.jboss.resteasy.plugins.providers.jaxb.XmlJAXBContextFinder;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

final class ProviderSession implements AutoCloseable {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private final ResteasyProviderFactoryImpl factory = new ResteasyProviderFactoryImpl();

    ProviderSession() {
        ResteasyContext.pushContext(ResteasyProviderFactory.class, factory);
        ResteasyContext.pushContext(Providers.class, factory);
        factory.registerProvider(XmlJAXBContextFinder.class);
        factory.registerProvider(CollectionProvider.class);
        factory.registerProvider(JAXBXmlTypeProvider.class);
    }

    <T> T read(Class<T> type, Type genericType, String xml) throws Exception {
        MessageBodyReader<T> reader = factory.getMessageBodyReader(type, genericType, NO_ANNOTATIONS,
                MediaType.APPLICATION_XML_TYPE);
        assertThat(reader).isNotNull();
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return reader.readFrom(type, genericType, NO_ANNOTATIONS, MediaType.APPLICATION_XML_TYPE,
                    new MultivaluedHashMap<>(), input);
        }
    }

    <T> String write(T value, Class<T> type, Type genericType) throws Exception {
        MessageBodyWriter<T> writer = factory.getMessageBodyWriter(type, genericType, NO_ANNOTATIONS,
                MediaType.APPLICATION_XML_TYPE);
        assertThat(writer).isNotNull();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeTo(value, type, genericType, NO_ANNOTATIONS, MediaType.APPLICATION_XML_TYPE,
                new MultivaluedHashMap<>(), output);
        return output.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        ResteasyContext.clearContextData();
    }
}
