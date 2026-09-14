/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_jaxb_annotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.junit.jupiter.api.Test;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbAnnotationIntrospectorTest {

    @Test
    void serializesAndDeserializesJaxbAnnotatedProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JaxbAnnotationModule());
        Contact expected = new Contact("contact-7", "Ada");

        String json = mapper.writeValueAsString(expected);
        Contact restored = mapper.readValue(json, Contact.class);

        assertThat(json).isEqualTo("{\"identifier\":\"contact-7\",\"display-name\":\"Ada\"}");
        assertThat(restored.identifier).isEqualTo(expected.identifier);
        assertThat(restored.displayName).isEqualTo(expected.displayName);
    }

    @Test
    void serializesAndDeserializesDataHandlerContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JaxbAnnotationModule());
        byte[] payload = "jaxb-attachment".getBytes(StandardCharsets.UTF_8);
        DataHandler expected = new DataHandler(new ByteArrayDataSource(payload));

        String json = mapper.writeValueAsString(expected);
        DataHandler restored = mapper.readValue(json, DataHandler.class);

        assertThat(json).isEqualTo("\"amF4Yi1hdHRhY2htZW50\"");
        assertThat(restored.getContentType()).isEqualTo("application/octet-stream");
        try (InputStream input = restored.getInputStream()) {
            assertThat(input.readAllBytes()).containsExactly(payload);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void appliesXmlEnumValueNamesThroughLegacyIntrospectorApi() {
        JaxbAnnotationIntrospector introspector = new JaxbAnnotationIntrospector(
                TypeFactory.defaultInstance());
        String[] names = {"PENDING", "CONFIRMED", "CANCELLED"};

        String[] resolvedNames = introspector.findEnumValues(BookingStatus.class,
                BookingStatus.values(), names);

        assertThat(resolvedNames).containsExactly("pending-booking", "confirmed-booking", "CANCELLED");
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Contact {
        @XmlAttribute(name = "identifier")
        public String identifier;

        @XmlElement(name = "display-name")
        public String displayName;

        public Contact() {
        }

        private Contact(String identifier, String displayName) {
            this.identifier = identifier;
            this.displayName = displayName;
        }
    }

    private enum BookingStatus {
        @XmlEnumValue("pending-booking")
        PENDING,

        @XmlEnumValue("confirmed-booking")
        CONFIRMED,

        CANCELLED
    }

    private static final class ByteArrayDataSource implements DataSource {
        private final byte[] content;

        private ByteArrayDataSource(byte[] content) {
            this.content = content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Read-only data source");
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public String getName() {
            return "jaxb-attachment";
        }
    }
}
