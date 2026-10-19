/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Polymorphic type handling: `@JsonTypeInfo` with logical type names from `@JsonSubTypes`
 * and `@JsonTypeName`, included as a property or as a wrapper object, plus subtypes
 * registered at runtime through `MapperBuilder.registerSubtypes`.
 *
 * A parcel-tracking service stores a shipment's history as a JSON array of heterogeneous
 * events, reads it back into the right event classes, rejects unknown event kinds, accepts a
 * carrier plug-in event that only the runtime registration knows about, and stores the
 * notification channel of a parcel keyed by the channel's type name.
 */
public class ShipmentEventPolymorphicTypingTest {

    private static final String HISTORY = """
            [
              {"kind": "picked-up", "parcel": "PX-100", "depot": "Rotterdam"},
              {"kind": "in-transit", "parcel": "PX-100", "from": "Rotterdam", "to": "Antwerp", "hours": 3},
              {"kind": "delivered", "parcel": "PX-100", "signedBy": "J. Peeters"}
            ]
            """;

    private static final TypeReference<List<ShipmentEvent>> HISTORY_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void readsHeterogeneousHistoryIntoConcreteEvents() throws Exception {
        List<ShipmentEvent> history = mapper.readValue(HISTORY, HISTORY_TYPE);

        assertEquals(3, history.size());
        PickedUp pickedUp = assertInstanceOf(PickedUp.class, history.get(0));
        assertEquals("Rotterdam", pickedUp.depot());
        InTransit leg = assertInstanceOf(InTransit.class, history.get(1));
        assertEquals("Antwerp", leg.to());
        assertEquals(3, leg.hours());
        Delivered delivered = assertInstanceOf(Delivered.class, history.get(2));
        assertEquals("J. Peeters", delivered.signedBy());
        assertTrue(history.stream().allMatch(event -> event.parcel().equals("PX-100")));
        assertEquals("delivered to J. Peeters", describe(history.get(history.size() - 1)));
    }

    @Test
    void writesTypeIdsSoTheHistoryRoundTrips() throws Exception {
        List<ShipmentEvent> history = List.of(
                new PickedUp("PX-200", "Hamburg"),
                new InTransit("PX-200", "Hamburg", "Bremen", 2),
                new Delivered("PX-200", "K. Vogel"));

        String json = mapper.writerFor(HISTORY_TYPE).writeValueAsString(history);
        JsonNode tree = mapper.readTree(json);

        assertEquals("picked-up", tree.get(0).get("kind").asText());
        assertEquals("in-transit", tree.get(1).get("kind").asText());
        assertEquals("delivered", tree.get(2).get("kind").asText());
        assertEquals("Bremen", tree.get(1).get("to").asText());
        assertEquals(history, mapper.readValue(json, HISTORY_TYPE));
    }

    @Test
    void rejectsAnUnknownEventKind() {
        String json = """
                {"kind": "teleported", "parcel": "PX-300"}
                """;

        InvalidTypeIdException error = assertThrows(InvalidTypeIdException.class,
                () -> mapper.readValue(json, ShipmentEvent.class));

        assertEquals("teleported", error.getTypeId());
        assertEquals(ShipmentEvent.class, error.getBaseType().getRawClass());
    }

    @Test
    void acceptsCarrierEventRegisteredAtRuntime() throws Exception {
        ObjectMapper carrierMapper = JsonMapper.builder()
                .registerSubtypes(new NamedType(CustomsHold.class, "customs-hold"))
                .build();
        String json = """
                {"kind": "customs-hold", "parcel": "PX-400", "office": "Felixstowe", "reason": "missing invoice"}
                """;

        ShipmentEvent event = carrierMapper.readValue(json, ShipmentEvent.class);

        CustomsHold hold = assertInstanceOf(CustomsHold.class, event);
        assertEquals("Felixstowe", hold.office());
        assertEquals("held at Felixstowe: missing invoice", describe(hold));
        assertEquals("customs-hold", carrierMapper.valueToTree(event).get("kind").asText());
        assertThrows(InvalidTypeIdException.class, () -> mapper.readValue(json, ShipmentEvent.class));
    }

    @Test
    void wrapsNotificationChannelInObjectKeyedByTypeName() throws Exception {
        Notification email = new Notification("PX-500", new EmailChannel("ops@example.org"));
        Notification sms = new Notification("PX-500", new SmsChannel("+3212345678"));

        String emailJson = mapper.writeValueAsString(email);
        String smsJson = mapper.writeValueAsString(sms);

        assertEquals("""
                {"parcel":"PX-500","channel":{"email":{"address":"ops@example.org"}}}""", emailJson);
        assertEquals("""
                {"parcel":"PX-500","channel":{"sms":{"number":"+3212345678"}}}""", smsJson);
        assertEquals(email, mapper.readValue(emailJson, Notification.class));
        assertEquals(sms, mapper.readValue(smsJson, Notification.class));
    }

    private static String describe(ShipmentEvent event) {
        if (event instanceof PickedUp pickedUp) {
            return "picked up at " + pickedUp.depot();
        } else if (event instanceof InTransit leg) {
            return leg.from() + " -> " + leg.to();
        } else if (event instanceof Delivered delivered) {
            return "delivered to " + delivered.signedBy();
        } else if (event instanceof CustomsHold hold) {
            return "held at " + hold.office() + ": " + hold.reason();
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    /// A tracking event; the `kind` property carries its logical type name.
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PickedUp.class, name = "picked-up"),
        @JsonSubTypes.Type(value = InTransit.class, name = "in-transit"),
        @JsonSubTypes.Type(value = Delivered.class, name = "delivered")
    })
    public interface ShipmentEvent {

        String parcel();
    }

    public record PickedUp(String parcel, String depot) implements ShipmentEvent {
    }

    public record InTransit(String parcel, String from, String to, int hours) implements ShipmentEvent {
    }

    public record Delivered(String parcel, String signedBy) implements ShipmentEvent {
    }

    /// An event a carrier plug-in contributes without touching `ShipmentEvent`.
    public record CustomsHold(String parcel, String office, String reason) implements ShipmentEvent {
    }

    /// A notification whose channel is wrapped in an object keyed by the channel's type name.
    public record Notification(String parcel, Channel channel) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes({@JsonSubTypes.Type(EmailChannel.class), @JsonSubTypes.Type(SmsChannel.class)})
    public interface Channel {
    }

    @JsonTypeName("email")
    public static final class EmailChannel implements Channel {

        private final String address;

        @JsonCreator
        public EmailChannel(@JsonProperty("address") String address) {
            this.address = address;
        }

        public String getAddress() {
            return address;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EmailChannel channel && channel.address.equals(address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }

    @JsonTypeName("sms")
    public static final class SmsChannel implements Channel {

        private final String number;

        @JsonCreator
        public SmsChannel(@JsonProperty("number") String number) {
            this.number = number;
        }

        public String getNumber() {
            return number;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SmsChannel channel && channel.number.equals(number);
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }
    }
}
