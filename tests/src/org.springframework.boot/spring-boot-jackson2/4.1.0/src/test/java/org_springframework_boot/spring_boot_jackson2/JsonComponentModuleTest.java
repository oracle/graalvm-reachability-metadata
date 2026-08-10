/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jackson2.JsonComponent;
import org.springframework.boot.jackson2.JsonComponentModule;

public class JsonComponentModuleTest {
    @Test
    void registersJsonComponentSerializerBean() throws Exception {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("exampleSerializer", new ExampleSerializer());

        JsonComponentModule module = new JsonComponentModule();
        module.setBeanFactory(beanFactory);
        module.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(module);

        assertThat(objectMapper.writeValueAsString(new Example("boot"))).isEqualTo("\"json-component:boot\"");
    }

    private record Example(String value) {
    }

    @JsonComponent
    static class ExampleSerializer extends JsonSerializer<Example> {
        @Override
        public void serialize(Example value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            generator.writeString("json-component:" + value.value());
        }
    }
}
