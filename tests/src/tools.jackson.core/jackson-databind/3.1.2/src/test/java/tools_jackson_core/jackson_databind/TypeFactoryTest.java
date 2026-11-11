/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.OptBoolean;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class TypeFactoryTest {
    @Test
    void resolvesInjectedJacksonServiceTypesAndExecutesConfiguredMappingJob() {
        ObjectMapper payloadReader = JsonMapper.builder().build();
        MapperBuilder<?, ?> outputBuilder = JsonMapper.builder()
                .enable(SerializationFeature.WRAP_ROOT_VALUE);
        JacksonModule commonModule = new SimpleModule("request-names")
                .setMixInAnnotation(Payload.class, PayloadMixin.class);
        SimpleModule domainModule = new SimpleModule("trace-values")
                .setMixInAnnotation(TraceId.class, TraceIdMixin.class);
        Map<Class<?>, String> serviceBindings = payloadReader.readValue(
                """
                {
                  "tools.jackson.databind.ObjectMapper": "reader",
                  "tools.jackson.databind.cfg.MapperBuilder": "builder",
                  "tools.jackson.databind.JacksonModule": "common",
                  "tools.jackson.databind.module.SimpleModule": "domain"
                }
                """,
                new TypeReference<Map<Class<?>, String>>() {
                });
        Map<String, Object> serviceInstances = Map.of(
                "reader", payloadReader,
                "builder", outputBuilder,
                "common", commonModule,
                "domain", domainModule);
        InjectableValues.Std services = new InjectableValues.Std();
        serviceBindings.forEach((type, name) -> services.addValue(type, serviceInstances.get(name)));
        ObjectMapper jobReader = JsonMapper.builder()
                .injectableValues(services)
                .build();

        MappingJob job = jobReader.readValue(
                """
                {
                  "input": {
                    "requestId": "request-7",
                    "traceId": {"value": "trace-8"}
                  }
                }
                """,
                MappingJob.class);

        JsonNode result = payloadReader.readTree(job.execute());
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.at("/job/request_id").asText()).isEqualTo("request-7");
        assertThat(result.at("/job/traceId").asText()).isEqualTo("trace-8");
        assertThat(result.path("job").has("requestId")).isFalse();

        Payload original = payloadReader.treeToValue(job.input, Payload.class);
        assertThat(original).isEqualTo(new Payload("request-7", new TraceId("trace-8")));
        assertThat(payloadReader.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)).isFalse();
    }

    public static final class MappingJob {
        private final ObjectMapper reader;
        private final MapperBuilder<?, ?> builder;
        private final JacksonModule commonModule;
        private final SimpleModule domainModule;
        private final JsonNode input;

        @JsonCreator
        public MappingJob(
                @JacksonInject(useInput = OptBoolean.FALSE) ObjectMapper reader,
                @JacksonInject(useInput = OptBoolean.FALSE) MapperBuilder<?, ?> builder,
                @JacksonInject(useInput = OptBoolean.FALSE) JacksonModule commonModule,
                @JacksonInject(useInput = OptBoolean.FALSE) SimpleModule domainModule,
                @JsonProperty("input") JsonNode input) {
            this.reader = reader;
            this.builder = builder;
            this.commonModule = commonModule;
            this.domainModule = domainModule;
            this.input = input;
        }

        public String execute() {
            Payload value = reader.treeToValue(input, Payload.class);
            ObjectMapper writer = builder.addModules(commonModule, domainModule).build();
            return writer.writeValueAsString(value);
        }
    }

    @JsonRootName("job")
    public record Payload(String requestId, TraceId traceId) {
    }

    public record TraceId(String value) {
    }

    public abstract static class PayloadMixin {
        @JsonProperty("request_id")
        public abstract String requestId();
    }

    public abstract static class TraceIdMixin {
        @JsonValue
        public abstract String value();
    }
}
