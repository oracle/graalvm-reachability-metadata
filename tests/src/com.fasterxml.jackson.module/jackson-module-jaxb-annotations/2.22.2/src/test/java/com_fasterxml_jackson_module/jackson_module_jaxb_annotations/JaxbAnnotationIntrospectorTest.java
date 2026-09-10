/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_jaxb_annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.junit.jupiter.api.Test;

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
}
