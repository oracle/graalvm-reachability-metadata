/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lenient input handling: logical-type and per-class coercion configs, `@JsonSetter(nulls = ...)`
 * null handling, single values accepted as arrays, case-insensitive enums with a
 * `@JsonEnumDefaultValue` fallback, and recovery from bad input through a
 * `DeserializationProblemHandler`.
 *
 * A CRM imports contacts exported by a legacy system whose JSON is sloppy: tags come as a single
 * string or a list, unknown columns appear, empty strings stand for missing objects, revenue is
 * sometimes `"n/a"`, and enum values use any case. The importer tolerates what it can, records
 * what it repaired, and still rejects the inputs the business refuses to guess about.
 */
public class CrmContactImportCoercionTest {

    @Test
    void importsSloppyContactAndRecordsRepairs() throws Exception {
        ImportProblemCollector problems = new ImportProblemCollector();
        ObjectMapper mapper = importMapper(problems);
        String json = """
                {
                  "externalId": "C-17",
                  "employees": 250,
                  "annualRevenue": "n/a",
                  "tier": "gold",
                  "tags": "vip",
                  "emails": null,
                  "region": null,
                  "owner": "dana",
                  "address": "",
                  "legacyRowNumber": 99,
                  "legacyAudit": {"by": "batch", "at": [1, 2]}
                }
                """;

        Contact contact = mapper.readValue(json, Contact.class);

        assertEquals("C-17", contact.externalId);
        assertEquals(250, contact.employees);
        assertEquals(0.0d, contact.annualRevenue);
        assertEquals(Tier.GOLD, contact.tier);
        assertEquals(List.of("vip"), contact.tags);
        assertEquals(List.of(), contact.emails);
        assertEquals("EMEA", contact.region);
        assertEquals("dana", contact.owner);
        assertNull(contact.address);
        assertEquals(List.of("legacyRowNumber", "legacyAudit"), problems.skippedProperties);
        assertEquals(List.of("n/a"), problems.unparsableNumbers);
    }

    @Test
    void wellFormedContactNeedsNoRepairs() throws Exception {
        ImportProblemCollector problems = new ImportProblemCollector();
        ObjectMapper mapper = importMapper(problems);
        String json = """
                {
                  "externalId": "C-18",
                  "employees": 12,
                  "annualRevenue": 1250000.5,
                  "tier": "SILVER",
                  "tags": ["partner", "reseller"],
                  "emails": ["ops@example.com"],
                  "region": "APAC",
                  "address": {"city": "Singapore", "postalCode": "018989"}
                }
                """;

        Contact contact = mapper.readValue(json, Contact.class);

        assertEquals(1250000.5d, contact.annualRevenue);
        assertEquals(Tier.SILVER, contact.tier);
        assertEquals(List.of("partner", "reseller"), contact.tags);
        assertEquals(List.of("ops@example.com"), contact.emails);
        assertEquals("APAC", contact.region);
        assertEquals("Singapore", contact.address.city);
        assertEquals("018989", contact.address.postalCode);
        assertNull(contact.owner);
        assertTrue(problems.skippedProperties.isEmpty());
        assertTrue(problems.unparsableNumbers.isEmpty());
    }

    @Test
    void unknownTierFallsBackToDefaultValue() throws Exception {
        ObjectMapper mapper = importMapper(new ImportProblemCollector());

        Contact contact = mapper.readValue("""
                {"externalId": "C-19", "tier": "platinum-plus"}
                """, Contact.class);

        assertEquals(Tier.UNCLASSIFIED, contact.tier);
    }

    @Test
    void employeeCountGivenAsTextIsRejected() {
        ObjectMapper mapper = importMapper(new ImportProblemCollector());

        MismatchedInputException failure = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue("""
                        {"externalId": "C-20", "employees": "250"}
                        """, Contact.class));

        assertEquals("employees", failure.getPath().get(0).getFieldName());
    }

    @Test
    void defaultMapperStillCoercesEmployeeCountFromText() throws Exception {
        ObjectMapper lenient = JsonMapper.builder().build();

        Contact contact = lenient.readValue("""
                {"externalId": "C-21", "employees": "250"}
                """, Contact.class);

        assertEquals(250, contact.employees);
    }

    @Test
    void explicitNullOwnerIsRejected() {
        ObjectMapper mapper = importMapper(new ImportProblemCollector());

        InvalidNullException failure = assertThrows(InvalidNullException.class,
                () -> mapper.readValue("""
                        {"externalId": "C-22", "owner": null}
                        """, Contact.class));

        assertEquals("owner", failure.getPropertyName().getSimpleName());
    }

    /// Builds the importer's mapper: strict about head counts in text, lenient about the rest.
    /// The `int` rule is scoped to the class because a `LogicalType.Integer` rule also governs
    /// `double` targets in this Jackson line.
    private static ObjectMapper importMapper(ImportProblemCollector problems) {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .withCoercionConfig(LogicalType.POJO,
                        config -> config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull))
                .withCoercionConfig(int.class,
                        config -> config.setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .addHandler(problems)
                .build();
    }

    /// Skips columns the CRM does not know and zeroes revenue values that are not numbers.
    static final class ImportProblemCollector extends DeserializationProblemHandler {

        final List<String> skippedProperties = new ArrayList<>();

        final List<String> unparsableNumbers = new ArrayList<>();

        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                JsonDeserializer<?> deserializer, Object beanOrClass, String propertyName) throws IOException {
            skippedProperties.add(propertyName);
            p.skipChildren();
            return true;
        }

        @Override
        public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType,
                String valueToConvert, String failureMsg) {
            if (targetType == double.class) {
                unparsableNumbers.add(valueToConvert);
                return 0.0d;
            }
            return NOT_HANDLED;
        }
    }

    enum Tier {
        BRONZE,
        SILVER,
        GOLD,
        @JsonEnumDefaultValue
        UNCLASSIFIED
    }

    public static class Address {

        public String city;

        public String postalCode;
    }

    public static class Contact {

        public String externalId;

        public int employees;

        public double annualRevenue;

        public Tier tier;

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        public List<String> tags = new ArrayList<>();

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public List<String> emails = new ArrayList<>();

        @JsonSetter(nulls = Nulls.SKIP)
        public String region = "EMEA";

        @JsonSetter(nulls = Nulls.FAIL)
        public String owner;

        public Address address;
    }
}
