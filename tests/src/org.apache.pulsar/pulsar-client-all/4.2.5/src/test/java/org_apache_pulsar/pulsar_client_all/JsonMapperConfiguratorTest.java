/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.jaxrs.cfg.Annotations;
import org.apache.pulsar.shade.com.fasterxml.jackson.jaxrs.json.JsonMapperConfigurator;
import org.apache.pulsar.shade.javax.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Test;

public class JsonMapperConfiguratorTest {
    @Test
    void createsJaxbIntrospectorForDefaultMapper() throws Exception {
        JsonMapperConfigurator configurator =
                new JsonMapperConfigurator(null, new Annotations[] {Annotations.JAXB});
        ObjectMapper mapper = configurator.getDefaultMapper();

        String json = mapper.writeValueAsString(new JaxbValue("persistent"));

        assertThat(json).contains("\"wireName\":\"persistent\"");
    }

    public static class JaxbValue {
        @XmlElement(name = "wireName")
        public String name;

        public JaxbValue() {
        }

        public JaxbValue(String name) {
            this.name = name;
        }
    }
}
