/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jaxrs.jackson_jaxrs_json_provider;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JsonMapperConfigurator;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMapperConfiguratorTest {
    @Test
    public void getDefaultMapperConfiguresJacksonAnnotationIntrospector() throws Exception {
        JsonMapperConfigurator configurator = new JsonMapperConfigurator(
                null,
                new Annotations[] {Annotations.JACKSON});

        ObjectMapper mapper = configurator.getDefaultMapper();

        AnnotationIntrospector introspector = mapper.getSerializationConfig()
                .getAnnotationIntrospector();
        assertThat(introspector).isInstanceOf(JacksonAnnotationIntrospector.class);
        assertThat(mapper.writeValueAsString(Map.of("message", "hello"))).isEqualTo("{\"message\":\"hello\"}");
        assertThat(configurator.getDefaultMapper()).isSameAs(mapper);
    }

    @Test
    public void getDefaultMapperConfiguresJaxbAnnotationIntrospector() throws Exception {
        JsonMapperConfigurator configurator = new JsonMapperConfigurator(
                null,
                new Annotations[] {Annotations.JAXB});

        ObjectMapper mapper = configurator.getDefaultMapper();

        AnnotationIntrospector introspector = mapper.getSerializationConfig()
                .getAnnotationIntrospector();
        assertThat(introspector).isInstanceOf(JaxbAnnotationIntrospector.class);
        assertThat(mapper.writeValueAsString(new JaxbPayload("hello")))
                .isEqualTo("{\"jaxbMessage\":\"hello\"}");
        assertThat(configurator.getDefaultMapper()).isSameAs(mapper);
    }

    public static class JaxbPayload {
        @XmlElement(name = "jaxbMessage")
        public String message;

        public JaxbPayload(String message) {
            this.message = message;
        }
    }
}
