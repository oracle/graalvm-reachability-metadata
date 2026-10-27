/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Bean (de)serializer modification hooks: a `BeanSerializerModifier` that drops and reorders
 * property writers, a `BeanDeserializerModifier` that wraps built bean deserializers in a
 * validating `DelegatingDeserializer`, and `ContextualSerializer`/`ContextualDeserializer`
 * handlers configured per property from the application's own annotations.
 *
 * A cardholder registry exports account summaries to a partner with card data masked and
 * internal risk fields removed, and accepts registrations whose free-text fields are
 * normalized and whose content is validated before the application ever sees the object.
 */
public class CardholderRegistryBeanModifiersTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new RegistryModule())
            .build();

    @Test
    void exportMasksAnnotatedPropertiesAndDropsInternalOnes() throws Exception {
        AccountSummary summary = new AccountSummary("ACC-1001", "4111111111111111", "Ada Lovelace",
                "0.82", List.of("+441632960001", "+441632960002"), List.of("vip", "eu"),
                Map.of("tier", "gold"));

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(summary));

        assertEquals("************1111", tree.get("cardNumber").asText());
        assertEquals("*********ace", tree.get("holder").asText());
        assertEquals("*".repeat(11) + "01", tree.get("backupPhones").get(0).asText());
        assertEquals("*".repeat(11) + "02", tree.get("backupPhones").get(1).asText());
        assertEquals("ACC-1001", tree.get("accountId").asText());
        assertEquals("vip", tree.get("tags").get(0).asText());
        assertEquals("gold", tree.get("labels").get("tier").asText());
        assertFalse(tree.has("riskScore"));

        List<String> names = new ArrayList<>();
        Iterator<String> fieldNames = tree.fieldNames();
        fieldNames.forEachRemaining(names::add);
        assertEquals(List.of("accountId", "backupPhones", "cardNumber", "holder", "labels", "tags"), names);
    }

    @Test
    void rootStringsAndPlainMappersAreUntouched() throws Exception {
        assertEquals("\"4111111111111111\"", mapper.writeValueAsString("4111111111111111"));

        ObjectMapper plain = new ObjectMapper();
        AccountSummary summary = new AccountSummary("ACC-7", "5500000000000004", "Grace Hopper",
                "0.10", List.of(), List.of(), Map.of());
        JsonNode tree = plain.readTree(plain.writeValueAsString(summary));

        assertEquals("5500000000000004", tree.get("cardNumber").asText());
        assertEquals("0.10", tree.get("riskScore").asText());
    }

    @Test
    void registrationTextIsNormalizedPerAnnotatedProperty() throws Exception {
        String json = """
                {
                  "fullName": "   ada    lovelace  ",
                  "countryCode": " gb ",
                  "note": "keep  these  spaces ",
                  "age": 36
                }
                """;

        Registration registration = mapper.readValue(json, Registration.class);

        assertEquals("ada lovelace", registration.fullName);
        assertEquals("GB", registration.countryCode);
        assertEquals("keep  these  spaces ", registration.note);
        assertEquals(36, registration.age);
        assertEquals("  raw root  ", mapper.readValue("\"  raw root  \"", String.class));
    }

    @Test
    void invalidRegistrationIsRejectedByWrappedDeserializer() {
        String json = """
                {"fullName": "Bob", "countryCode": "usa", "note": "", "age": 15}
                """;

        MismatchedInputException failure = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(json, Registration.class));

        assertTrue(failure.getOriginalMessage().contains("countryCode must have 2 letters"),
                failure.getOriginalMessage());
        assertTrue(failure.getOriginalMessage().contains("age must be at least 18"),
                failure.getOriginalMessage());
    }

    @Test
    void nestedInvalidRegistrationReportsItsPath() throws Exception {
        String valid = """
                {"reference": "RQ-1", "applicant": {"fullName": " Linus ", "countryCode": "fi", "age": 54}}
                """;
        CardRequest request = mapper.readValue(valid, CardRequest.class);
        assertEquals("Linus", request.applicant.fullName);
        assertEquals("FI", request.applicant.countryCode);

        String invalid = """
                {"reference": "RQ-2", "applicant": {"fullName": "Kid", "countryCode": "se", "age": 9}}
                """;
        MismatchedInputException failure = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(invalid, CardRequest.class));

        assertEquals("applicant", failure.getPath().get(0).getFieldName());
        assertTrue(failure.getOriginalMessage().contains("age must be at least 18"),
                failure.getOriginalMessage());
    }

    /** Masks all but the last `visible` characters of the annotated text, or each list element. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface Masked {
        int visible() default 4;
    }

    /** Marks a property that never leaves the registry. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface InternalOnly {
    }

    /** Trims and collapses whitespace in the annotated text, optionally upper-casing it. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface Normalized {
        boolean upperCase() default false;
    }

    /** An inbound object that can report what is wrong with it. */
    interface Validated {
        List<String> violations();
    }

    record AccountSummary(String accountId,
            @Masked String cardNumber,
            @Masked(visible = 3) String holder,
            @InternalOnly String riskScore,
            @Masked(visible = 2) List<String> backupPhones,
            List<String> tags,
            Map<String, String> labels) {
    }

    static class Registration implements Validated {
        @Normalized
        public String fullName;

        @Normalized(upperCase = true)
        public String countryCode;

        public String note;

        public int age;

        @Override
        public List<String> violations() {
            List<String> problems = new ArrayList<>();
            if (countryCode == null || countryCode.length() != 2) {
                problems.add("countryCode must have 2 letters");
            }
            if (age < 18) {
                problems.add("age must be at least 18");
            }
            return problems;
        }
    }

    static class CardRequest {
        public String reference;

        public Registration applicant;
    }

    static final class RegistryModule extends SimpleModule {
        RegistryModule() {
            super("cardholder-registry");
            addSerializer(String.class, new MaskingSerializer(-1));
            addDeserializer(String.class, new NormalizingDeserializer(false, false));
            setSerializerModifier(new ExportModifier());
            setDeserializerModifier(new ValidationModifier());
        }
    }

    static final class MaskingSerializer extends StdSerializer<String> implements ContextualSerializer {
        private final int visible;

        MaskingSerializer(int visible) {
            super(String.class);
            this.visible = visible;
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
            // Checkstyle: stop direct annotation access check
            Masked masked = property == null ? null : property.getAnnotation(Masked.class);
            // Checkstyle: resume direct annotation access check
            return masked == null ? this : new MaskingSerializer(masked.visible());
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (visible < 0 || value.length() <= visible) {
                gen.writeString(value);
                return;
            }
            int hidden = value.length() - visible;
            gen.writeString("*".repeat(hidden) + value.substring(hidden));
        }
    }

    static final class NormalizingDeserializer extends StdDeserializer<String> implements ContextualDeserializer {
        private final boolean normalize;
        private final boolean upperCase;

        NormalizingDeserializer(boolean normalize, boolean upperCase) {
            super(String.class);
            this.normalize = normalize;
            this.upperCase = upperCase;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            // Checkstyle: stop direct annotation access check
            Normalized normalized = property == null ? null : property.getAnnotation(Normalized.class);
            // Checkstyle: resume direct annotation access check
            return normalized == null ? this : new NormalizingDeserializer(true, normalized.upperCase());
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null) {
                return (String) ctxt.handleUnexpectedToken(String.class, p);
            }
            if (!normalize) {
                return text;
            }
            String collapsed = text.trim().replaceAll("\\s+", " ");
            return upperCase ? collapsed.toUpperCase(Locale.ROOT) : collapsed;
        }
    }

    static final class ExportModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties) {
            List<BeanPropertyWriter> exported = new ArrayList<>();
            for (BeanPropertyWriter writer : beanProperties) {
                // Checkstyle: stop direct annotation access check
                if (writer.getAnnotation(InternalOnly.class) == null) {
                    exported.add(writer);
                }
                // Checkstyle: resume direct annotation access check
            }
            return exported;
        }

        @Override
        public List<BeanPropertyWriter> orderProperties(SerializationConfig config, BeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties) {
            List<BeanPropertyWriter> ordered = new ArrayList<>(beanProperties);
            ordered.sort(Comparator.comparing(BeanPropertyWriter::getName));
            return ordered;
        }
    }

    static final class ValidationModifier extends BeanDeserializerModifier {
        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc,
                JsonDeserializer<?> deserializer) {
            if (Validated.class.isAssignableFrom(beanDesc.getBeanClass())) {
                return new ValidatingDeserializer(deserializer);
            }
            return deserializer;
        }
    }

    static final class ValidatingDeserializer extends DelegatingDeserializer {
        ValidatingDeserializer(JsonDeserializer<?> delegatee) {
            super(delegatee);
        }

        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
            return new ValidatingDeserializer(newDelegatee);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Object value = super.deserialize(p, ctxt);
            List<String> violations = ((Validated) value).violations();
            if (!violations.isEmpty()) {
                return ctxt.reportInputMismatch(this, "Rejected %s: %s",
                        value.getClass().getSimpleName(), String.join("; ", violations));
            }
            return value;
        }
    }
}
