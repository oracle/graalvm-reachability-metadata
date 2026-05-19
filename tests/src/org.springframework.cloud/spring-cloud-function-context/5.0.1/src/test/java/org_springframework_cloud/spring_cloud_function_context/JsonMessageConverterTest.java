/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMessageConverterTest {
    @Test
    void fromMessageLoadsTargetTypeFromContentTypeParameter() {
        RecordingJsonMapper jsonMapper = new RecordingJsonMapper();
        JsonMessageConverter converter = new JsonMessageConverter(jsonMapper);
        String payload = "{\"message\":\"hello\"}";
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(MessageHeaders.CONTENT_TYPE, applicationJsonFor(JsonMessageConverterPayload.class))
                .build();

        Object converted = converter.fromMessage(message, Object.class);

        assertThat(converted).isInstanceOf(JsonMessageConverterPayload.class);
        assertThat(((JsonMessageConverterPayload) converted).rawJson()).isEqualTo(payload);
        assertThat(jsonMapper.convertedJson()).isEqualTo(payload);
        assertThat(jsonMapper.convertedType()).isEqualTo(JsonMessageConverterPayload.class);
    }

    private static MimeType applicationJsonFor(Class<?> type) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("type", type.getName());
        return new MimeType("application", "json", parameters);
    }

    private static final class RecordingJsonMapper extends JsonMapper {
        private Object convertedJson;

        private Type convertedType;

        @SuppressWarnings("unchecked")
        @Override
        protected <T> T doFromJson(Object json, Type type) {
            this.convertedJson = json;
            this.convertedType = type;
            assertThat(type).isEqualTo(JsonMessageConverterPayload.class);
            return (T) new JsonMessageConverterPayload(String.valueOf(json));
        }

        @Override
        public String toString(Object value) {
            return String.valueOf(value);
        }

        Object convertedJson() {
            return this.convertedJson;
        }

        Type convertedType() {
            return this.convertedType;
        }
    }
}

final class JsonMessageConverterPayload {
    private final String rawJson;

    JsonMessageConverterPayload(String rawJson) {
        this.rawJson = rawJson;
    }

    String rawJson() {
        return this.rawJson;
    }
}
