/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_jaxrs;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.jaxrs.Annotations;
import org.codehaus.jackson.jaxrs.MapperConfigurator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperConfiguratorTest {
    @Test
    void defaultMapperBindsPropertiesUsingJaxbAnnotations() throws Exception {
        MapperConfigurator configurator = new MapperConfigurator(null, new Annotations[]{Annotations.JAXB});
        ObjectMapper mapper = configurator.getDefaultMapper();
        Contact contact = new Contact();
        contact.name = "Ada";

        assertThat(mapper.writeValueAsString(contact)).isEqualTo("{\"xml-name\":\"Ada\"}");
        Contact restored = mapper.readValue("""
                {"xml-name":"Grace"}
                """, Contact.class);
        assertThat(restored.name).isEqualTo("Grace");
        assertThat(configurator.getDefaultMapper()).isSameAs(mapper);
        assertThat(configurator.getConfiguredMapper()).isNull();
    }

    @Test
    void configuredMapperGivesJacksonAnnotationsPriorityOverJaxb() throws Exception {
        MapperConfigurator configurator = new MapperConfigurator(null, new Annotations[]{Annotations.JACKSON});
        configurator.setAnnotationsToUse(new Annotations[]{Annotations.JACKSON, Annotations.JAXB});
        ObjectMapper mapper = configurator.getConfiguredMapper();
        Contact contact = new Contact();
        contact.name = "Lin";

        assertThat(mapper.writeValueAsString(contact)).isEqualTo("{\"json-name\":\"Lin\"}");
        Contact restored = mapper.readValue("""
                {"json-name":"Sam"}
                """, Contact.class);
        assertThat(restored.name).isEqualTo("Sam");
    }

    public static class Contact {
        @XmlElement(name = "xml-name")
        @JsonProperty("json-name")
        public String name;
    }
}
