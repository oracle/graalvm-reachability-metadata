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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Enum value representation: constants renamed with `@JsonProperty`, external codes through
 * `@JsonValue` and a delegating `@JsonCreator` factory, `toString`- and index-based enum
 * features, object-shaped enums via `@JsonFormat(shape = OBJECT)`, `EnumSet` values and
 * `EnumMap` keys, and rejection or nulling of unknown constants.
 *
 * A helpdesk exchanges tickets with a partner whose wire format names statuses in kebab case,
 * encodes priorities as `P1`..`P4`, publishes the SLA catalogue as objects, and counts tickets
 * per channel. Legacy exports use ordinal indexes, and newer partners may send values that the
 * helpdesk does not know yet.
 */
public class HelpdeskTicketEnumRepresentationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void writesAndReadsRenamedAndCodedConstants() throws Exception {
        Ticket ticket = new Ticket("HD-1001", Status.WAITING_ON_CUSTOMER, Priority.HIGH,
                EnumSet.of(Channel.EMAIL, Channel.CHAT));

        String json = mapper.writeValueAsString(ticket);

        assertEquals("""
                {"id":"HD-1001","status":"waiting-on-customer","priority":"P2","channels":["EMAIL","CHAT"]}""",
                json);

        Ticket read = mapper.readValue("""
                {"id":"HD-1002","status":"in-progress","priority":"p1","channels":["PHONE"]}""",
                Ticket.class);

        assertEquals(Status.IN_PROGRESS, read.status);
        assertEquals(Priority.URGENT, read.priority);
        assertEquals(EnumSet.of(Channel.PHONE), read.channels);
    }

    @Test
    void rejectsUnknownStatusAndPriorityCode() {
        InvalidFormatException unknownStatus = assertThrows(InvalidFormatException.class,
                () -> mapper.readValue("""
                        {"id":"HD-1003","status":"escalated","priority":"P3","channels":[]}""",
                        Ticket.class));

        assertEquals(Status.class, unknownStatus.getTargetType());
        assertEquals("escalated", unknownStatus.getValue());
        assertEquals("status", unknownStatus.getPath().get(0).getFieldName());

        ValueInstantiationException unknownPriority = assertThrows(ValueInstantiationException.class,
                () -> mapper.readValue("""
                        {"id":"HD-1004","status":"open","priority":"P9","channels":[]}""",
                        Ticket.class));

        assertTrue(unknownPriority.getCause() instanceof IllegalArgumentException);
        assertTrue(unknownPriority.getCause().getMessage().contains("P9"));
    }

    @Test
    void mapsUnknownConstantsToNullForForwardCompatiblePartners() throws Exception {
        ObjectMapper lenient = mapper.rebuild()
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .build();

        List<Channel> channels = lenient.readValue("""
                ["EMAIL","WHATSAPP","PHONE"]""", new TypeReference<List<Channel>>() { });

        assertEquals(3, channels.size());
        assertEquals(Channel.EMAIL, channels.get(0));
        assertNull(channels.get(1));
        assertEquals(Channel.PHONE, channels.get(2));
    }

    @Test
    void usesToStringForLabelsAndIndexesForLegacyExports() throws Exception {
        ObjectMapper labels = JsonMapper.builder()
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .build();

        assertEquals("""
                ["Self-service portal","Live chat"]""",
                labels.writeValueAsString(List.of(Channel.PORTAL, Channel.CHAT)));
        assertEquals(Channel.EMAIL, labels.readValue("\"E-mail\"", Channel.class));

        ObjectMapper legacy = JsonMapper.builder()
                .enable(SerializationFeature.WRITE_ENUMS_USING_INDEX)
                .build();

        assertEquals("[0,3]", legacy.writeValueAsString(List.of(Channel.EMAIL, Channel.PORTAL)));
        assertEquals(Channel.CHAT, mapper.readValue("2", Channel.class));

        ObjectMapper strict = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .build();

        assertThrows(InvalidFormatException.class, () -> strict.readValue("2", Channel.class));
    }

    @Test
    void writesSlaCatalogueAsObjectShapedEnums() throws Exception {
        JsonNode catalogue = mapper.readTree(mapper.writeValueAsString(SlaTier.values()));

        assertEquals(3, catalogue.size());
        assertEquals("Gold", catalogue.get(0).get("displayName").asText());
        assertEquals(1, catalogue.get(0).get("responseHours").asInt());
        assertEquals("Bronze", catalogue.get(2).get("displayName").asText());
        assertEquals(24, catalogue.get(2).get("responseHours").asInt());
        assertEquals(2, catalogue.get(0).size());
    }

    @Test
    void countsTicketsPerChannelInEnumMapKeyedReport() throws Exception {
        EnumMap<Channel, Integer> counts = new EnumMap<>(Channel.class);
        counts.put(Channel.PORTAL, 4);
        counts.put(Channel.EMAIL, 12);
        counts.put(Channel.PHONE, 7);
        ChannelReport report = new ChannelReport("2026-W42", counts);

        String json = mapper.writeValueAsString(report);

        assertEquals("""
                {"week":"2026-W42","countsByChannel":{"EMAIL":12,"PHONE":7,"PORTAL":4}}""", json);

        ChannelReport read = mapper.readValue(json, ChannelReport.class);

        assertTrue(read.countsByChannel instanceof EnumMap);
        assertEquals(List.of(Channel.EMAIL, Channel.PHONE, Channel.PORTAL),
                List.copyOf(read.countsByChannel.keySet()));
        assertEquals(12, read.countsByChannel.get(Channel.EMAIL));

        ObjectMapper indexedKeys = mapper.rebuild()
                .enable(SerializationFeature.WRITE_ENUM_KEYS_USING_INDEX)
                .build();

        assertEquals("""
                {"0":12,"1":7,"3":4}""", indexedKeys.writeValueAsString(counts));
    }

    /** Ticket lifecycle status, named in kebab case on the wire. */
    enum Status {
        @JsonProperty("open")
        OPEN,
        @JsonProperty("in-progress")
        IN_PROGRESS,
        @JsonProperty("waiting-on-customer")
        WAITING_ON_CUSTOMER,
        @JsonProperty("resolved")
        RESOLVED
    }

    /** Ticket priority, exchanged as the partner's `P1`..`P4` code. */
    enum Priority {
        URGENT("P1"),
        HIGH("P2"),
        NORMAL("P3"),
        LOW("P4");

        private final String code;

        Priority(String code) {
            this.code = code;
        }

        @JsonValue
        String code() {
            return code;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Priority fromCode(String code) {
            for (Priority priority : values()) {
                if (priority.code.equalsIgnoreCase(code)) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority code " + code);
        }
    }

    /** Intake channel with a human label returned by `toString`. */
    enum Channel {
        EMAIL("E-mail"),
        PHONE("Phone"),
        CHAT("Live chat"),
        PORTAL("Self-service portal");

        private final String label;

        Channel(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Support tier published to partners as an object rather than a name. */
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    @JsonPropertyOrder({"displayName", "responseHours"})
    enum SlaTier {
        GOLD("Gold", 1),
        SILVER("Silver", 4),
        BRONZE("Bronze", 24);

        private final String displayName;
        private final int responseHours;

        SlaTier(String displayName, int responseHours) {
            this.displayName = displayName;
            this.responseHours = responseHours;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getResponseHours() {
            return responseHours;
        }
    }

    /** Ticket exchanged with the partner helpdesk. */
    @JsonPropertyOrder({"id", "status", "priority", "channels"})
    static class Ticket {
        public String id;
        public Status status;
        public Priority priority;
        public Set<Channel> channels;

        Ticket() {
        }

        Ticket(String id, Status status, Priority priority, Set<Channel> channels) {
            this.id = id;
            this.status = status;
            this.priority = priority;
            this.channels = channels;
        }
    }

    /** Weekly ticket counts keyed by intake channel. */
    @JsonPropertyOrder({"week", "countsByChannel"})
    static class ChannelReport {
        public String week;
        public EnumMap<Channel, Integer> countsByChannel;

        ChannelReport() {
        }

        ChannelReport(String week, EnumMap<Channel, Integer> countsByChannel) {
            this.week = week;
            this.countsByChannel = countsByChannel;
        }
    }
}
