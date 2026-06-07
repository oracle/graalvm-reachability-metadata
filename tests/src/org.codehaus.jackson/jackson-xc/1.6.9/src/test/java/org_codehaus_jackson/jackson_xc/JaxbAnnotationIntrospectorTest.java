/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_xc;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnumValue;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbAnnotationIntrospectorTest {
    @Test
    void serializesEnumUsingXmlEnumValue() throws Exception {
        ObjectMapper mapper = newMapper();

        String json = mapper.writeValueAsString(Status.ENABLED);

        assertThat(json).isEqualTo("\"enabled-for-xml\"");
    }

    @Test
    void deserializesSetterPropertyUsingTypeDeclaredOnMatchingField() throws Exception {
        ObjectMapper mapper = newMapper();

        Kennel kennel = mapper.readValue("{\"resident\":{\"name\":\"Rex\"}}", Kennel.class);

        assertThat(kennel.getResident()).isInstanceOf(Dog.class);
        assertThat(kennel.getResident().getName()).isEqualTo("Rex");
    }

    private ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JaxbAnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
        mapper.getDeserializationConfig().setAnnotationIntrospector(introspector);
        return mapper;
    }

    private enum Status {
        @XmlEnumValue("enabled-for-xml")
        ENABLED,
        DISABLED
    }

    public interface Animal {
        String getName();
    }

    public static class Dog implements Animal {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Kennel {
        @XmlElement(type = Dog.class)
        private Animal resident;

        public Animal getResident() {
            return resident;
        }

        public void setResident(Animal resident) {
            this.resident = resident;
        }
    }
}
