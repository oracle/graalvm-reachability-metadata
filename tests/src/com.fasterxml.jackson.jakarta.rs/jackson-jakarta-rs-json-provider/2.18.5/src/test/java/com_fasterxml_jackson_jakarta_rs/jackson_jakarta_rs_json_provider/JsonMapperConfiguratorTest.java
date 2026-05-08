/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jakarta_rs.jackson_jakarta_rs_json_provider;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.cfg.Annotations;
import com.fasterxml.jackson.jakarta.rs.json.JsonMapperConfigurator;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import jakarta.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMapperConfiguratorTest {
    @Test
    void configuresDefaultMapperWithJakartaXmlBindAnnotations() throws Exception {
        JsonMapperConfigurator configurator = new JsonMapperConfigurator(null,
                new Annotations[] {Annotations.JAKARTA_XML_BIND});

        ObjectMapper mapper = configurator.getDefaultMapper();

        assertThat(mapper.getSerializationConfig().getAnnotationIntrospector().allIntrospectors())
                .extracting(AnnotationIntrospector::getClass)
                .contains(JakartaXmlBindAnnotationIntrospector.class);
        assertThat(mapper.writeValueAsString(new XmlElementBean("configured")))
                .isEqualTo("{\"xml_name\":\"configured\"}");
    }

    public static final class XmlElementBean {
        @XmlElement(name = "xml_name")
        public final String name;

        public XmlElementBean(String name) {
            this.name = name;
        }
    }
}
