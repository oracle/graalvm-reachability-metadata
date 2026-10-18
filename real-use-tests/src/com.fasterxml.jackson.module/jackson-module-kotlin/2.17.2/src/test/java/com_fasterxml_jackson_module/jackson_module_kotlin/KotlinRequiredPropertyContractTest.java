/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.Map;
import java.util.TreeMap;
import kotlin.KotlinVersion;
import kotlin.reflect.KTypeProjection;
import kotlin.text.MatchGroup;
import org.junit.jupiter.api.Test;

/// Required-property detection from Kotlin nullability by the Kotlin module's annotation
/// introspector (`KotlinAnnotationIntrospector.hasRequiredMarker`).
///
/// A Java API gateway publishes a field contract for every payload type it forwards, marking
/// each field as required or optional. Several payloads are Kotlin data classes from the
/// Kotlin standard library, whose non-null properties must be reported as required and whose
/// nullable properties as optional, without anyone annotating them. The gateway builds the
/// contract by walking each type with Jackson's JSON-format visitor, the way schema generators
/// do, and checks that its own Java DTOs keep the explicit `@JsonProperty(required = true)`
/// contract they declare.
class KotlinRequiredPropertyContractTest {

    private static final ObjectMapper KOTLIN_MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// A gateway-owned Java DTO that declares its contract with annotations.
    public static final class RouteDescriptor {
        @JsonProperty(required = true)
        public String path;
        public String description;
        public KotlinVersion minimumClientVersion;
    }

    /// Collects `field -> required` for one object type through the format visitor.
    static final class ContractCollector extends JsonFormatVisitorWrapper.Base {
        private final Map<String, Boolean> fields = new TreeMap<>();

        @Override
        public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
            return new JsonObjectFormatVisitor.Base(getProvider()) {
                @Override
                public void property(BeanProperty writer) {
                    fields.put(writer.getName(), true);
                }

                @Override
                public void optionalProperty(BeanProperty writer) {
                    fields.put(writer.getName(), false);
                }
            };
        }

        Map<String, Boolean> fields() {
            return fields;
        }
    }

    private static Map<String, Boolean> writeContract(ObjectMapper mapper, Class<?> type)
            throws JsonMappingException {
        ContractCollector collector = new ContractCollector();
        mapper.acceptJsonFormatVisitor(type, collector);
        return collector.fields();
    }

    private static Map<String, Boolean> readContract(ObjectMapper mapper, Class<?> type) {
        BeanDescription description = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(type));
        Map<String, Boolean> fields = new TreeMap<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            fields.put(property.getName(), property.getMetadata().isRequired());
        }
        return fields;
    }

    @Test
    void reportsNonNullKotlinPropertiesAsRequired() throws Exception {
        assertEquals(Map.of("major", true, "minor", true, "patch", true),
                writeContract(KOTLIN_MAPPER, KotlinVersion.class));
        assertEquals(Map.of("range", true, "value", true),
                writeContract(KOTLIN_MAPPER, MatchGroup.class));
    }

    @Test
    void reportsNullableKotlinPropertiesAsOptional() throws Exception {
        assertEquals(Map.of("type", false, "variance", false),
                writeContract(KOTLIN_MAPPER, KTypeProjection.class));
    }

    @Test
    void keepsAnnotatedContractOfJavaDtos() throws Exception {
        assertEquals(Map.of("description", false, "minimumClientVersion", false, "path", true),
                writeContract(KOTLIN_MAPPER, RouteDescriptor.class));
    }

    @Test
    void reportsKotlinNullabilityForIncomingPayloads() {
        assertEquals(Map.of("range", true, "value", true),
                readContract(KOTLIN_MAPPER, MatchGroup.class));
        assertEquals(Map.of("type", false, "variance", false),
                readContract(KOTLIN_MAPPER, KTypeProjection.class));
    }

    @Test
    void plainMapperSeesNoKotlinNullability() throws Exception {
        ObjectMapper plain = new ObjectMapper();

        assertEquals(Map.of("major", false, "minor", false, "patch", false),
                writeContract(plain, KotlinVersion.class));
        assertEquals(Map.of("range", false, "value", false),
                writeContract(plain, MatchGroup.class));
    }
}
