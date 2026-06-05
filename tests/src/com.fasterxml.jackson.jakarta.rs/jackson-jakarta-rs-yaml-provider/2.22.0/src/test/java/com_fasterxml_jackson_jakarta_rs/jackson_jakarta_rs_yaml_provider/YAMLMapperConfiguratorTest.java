/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jakarta_rs.jackson_jakarta_rs_yaml_provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.cfg.Annotations;
import com.fasterxml.jackson.jakarta.rs.yaml.YAMLMapperConfigurator;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import jakarta.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Test;

public class YAMLMapperConfiguratorTest {
    @Test
    void defaultMapperResolvesJakartaXmlBindAnnotationIntrospector() throws Exception {
        YAMLMapperConfigurator configurator = new YAMLMapperConfigurator(
                null,
                new Annotations[] {Annotations.JAKARTA_XML_BIND });

        YAMLMapper mapper = configurator.getDefaultMapper();

        AnnotationIntrospector introspector = mapper.getSerializationConfig()
                .getAnnotationIntrospector();
        assertThat(introspector).isInstanceOf(JakartaXmlBindAnnotationIntrospector.class);

        String yaml = mapper.writeValueAsString(new JakartaXmlBindBean("configured"));
        assertThat(yaml).contains("xmlName:", "configured");
    }

    public static final class JakartaXmlBindBean {
        private final String value;

        public JakartaXmlBindBean(String value) {
            this.value = value;
        }

        @XmlElement(name = "xmlName")
        public String getValue() {
            return value;
        }
    }
}
