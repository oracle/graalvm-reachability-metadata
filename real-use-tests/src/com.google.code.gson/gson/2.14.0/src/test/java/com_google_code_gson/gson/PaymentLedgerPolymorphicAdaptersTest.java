/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Polymorphic and custom serialization through a `TypeAdapterFactory`, a hierarchy
 * `JsonSerializer`/`JsonDeserializer`, and `@JsonAdapter` field annotations.
 *
 * A payment service stores each account's ledger as JSON. The ledger holds a sealed hierarchy of
 * event records, which a type adapter factory writes with a `type` discriminator and reads back
 * into the right subtype by delegating to Gson's own adapters. Monetary amounts go through a
 * hierarchy adapter as compact `"12.34 EUR"` strings, and timestamps through a field-level
 * `@JsonAdapter` as epoch seconds. Each test asserts on the stored JSON and the restored ledger.
 */
public class PaymentLedgerPolymorphicAdaptersTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(new PaymentEventAdapterFactory())
            .registerTypeHierarchyAdapter(Money.class, new MoneyAdapter())
            .create();

    @Test
    void writesEachEventWithADiscriminatorAndCompactValues() {
        String json = gson.toJson(sampleLedger());

        JsonObject ledger = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("ACC-7781", ledger.get("account").getAsString());
        assertEquals("100.00 EUR", ledger.get("creditLimit").getAsString());

        JsonArray events = ledger.getAsJsonArray("events");
        assertEquals(3, events.size());

        JsonObject authorized = events.get(0).getAsJsonObject();
        assertEquals(List.of("type", "paymentId", "amount", "at", "merchant"),
                List.copyOf(authorized.keySet()), "the discriminator must lead each event");
        assertEquals("authorized", authorized.get("type").getAsString());
        assertEquals("42.50 EUR", authorized.get("amount").getAsString());
        assertEquals(1_760_000_000L, authorized.get("at").getAsLong());
        assertEquals("Corner Bakery", authorized.get("merchant").getAsString());

        JsonObject captured = events.get(1).getAsJsonObject();
        assertEquals("captured", captured.get("type").getAsString());
        assertEquals("40.00 EUR", captured.get("amount").getAsString());

        JsonObject refunded = events.get(2).getAsJsonObject();
        assertEquals("refunded", refunded.get("type").getAsString());
        assertEquals("5.25 EUR", refunded.get("amount").getAsString());
        assertEquals("damaged goods", refunded.get("reason").getAsString());
        assertFalse(refunded.has("merchant"));
    }

    @Test
    void restoresTheLedgerIntoTheOriginalSubtypes() {
        Ledger original = sampleLedger();

        Ledger restored = gson.fromJson(gson.toJson(original), Ledger.class);

        assertEquals(original, restored);
        assertInstanceOf(Authorized.class, restored.events().get(0));
        assertInstanceOf(Captured.class, restored.events().get(1));
        assertInstanceOf(Refunded.class, restored.events().get(2));
        assertEquals(Instant.ofEpochSecond(1_760_000_600L), restored.events().get(1).at());
        assertEquals(new Money(3475, "EUR"), restored.settledBalance());
    }

    @Test
    void readsEventsWrittenByAnotherServiceInAnyKeyOrder() {
        String feed = """
                [
                  {"amount": "19.99 USD", "at": 1760100000, "type": "authorized",
                   "paymentId": "pay-9", "merchant": "Book Nook"},
                  {"paymentId": "pay-9", "type": "captured", "amount": "19.99 USD", "at": 1760100060}
                ]
                """;

        List<PaymentEvent> events = gson.fromJson(feed, new TypeToken<List<PaymentEvent>>() { }.getType());

        assertEquals(List.of(
                new Authorized("pay-9", new Money(1999, "USD"), Instant.ofEpochSecond(1_760_100_000L), "Book Nook"),
                new Captured("pay-9", new Money(1999, "USD"), Instant.ofEpochSecond(1_760_100_060L))),
                events);

        Map<String, PaymentEvent> byType = new LinkedHashMap<>();
        events.forEach(event -> byType.put(PaymentEventAdapterFactory.discriminatorOf(event), event));
        assertEquals(List.of("authorized", "captured"), List.copyOf(byType.keySet()));
    }

    @Test
    void rejectsUnknownEventTypesAndMalformedAmounts() {
        JsonParseException unknownType = assertThrows(JsonParseException.class, () -> gson.fromJson("""
                {"type": "chargeback", "paymentId": "pay-1", "amount": "1.00 EUR", "at": 0}
                """, PaymentEvent.class));
        assertTrue(unknownType.getMessage().contains("chargeback"), unknownType.getMessage());

        JsonParseException badAmount = assertThrows(JsonParseException.class, () -> gson.fromJson("""
                {"type": "captured", "paymentId": "pay-1", "amount": "one euro", "at": 0}
                """, PaymentEvent.class));
        assertTrue(badAmount.getMessage().contains("one euro"), badAmount.getMessage());
    }

    private static Ledger sampleLedger() {
        return new Ledger("ACC-7781", new Money(10000, "EUR"), List.of(
                new Authorized("pay-1", new Money(4250, "EUR"), Instant.ofEpochSecond(1_760_000_000L),
                        "Corner Bakery"),
                new Captured("pay-1", new Money(4000, "EUR"), Instant.ofEpochSecond(1_760_000_600L)),
                new Refunded("pay-1", new Money(525, "EUR"), Instant.ofEpochSecond(1_760_086_400L),
                        "damaged goods")));
    }

    /// An amount in minor units of a currency.
    record Money(long minorUnits, String currency) {

        Money plus(long otherMinorUnits) {
            return new Money(minorUnits + otherMinorUnits, currency);
        }
    }

    sealed interface PaymentEvent permits Authorized, Captured, Refunded {

        String paymentId();

        Money amount();

        Instant at();
    }

    record Authorized(String paymentId, Money amount, @JsonAdapter(EpochSecondsAdapter.class) Instant at,
            String merchant) implements PaymentEvent {
    }

    record Captured(String paymentId, Money amount, @JsonAdapter(EpochSecondsAdapter.class) Instant at)
            implements PaymentEvent {
    }

    record Refunded(String paymentId, Money amount, @JsonAdapter(EpochSecondsAdapter.class) Instant at,
            String reason) implements PaymentEvent {
    }

    record Ledger(String account, Money creditLimit, List<PaymentEvent> events) {

        /// Captured money minus refunds; authorizations do not move money yet.
        Money settledBalance() {
            Money balance = new Money(0, creditLimit.currency());
            for (PaymentEvent event : events) {
                if (event instanceof Captured captured) {
                    balance = balance.plus(captured.amount().minorUnits());
                } else if (event instanceof Refunded refunded) {
                    balance = balance.plus(-refunded.amount().minorUnits());
                }
            }
            return balance;
        }
    }

    /// Writes `Money` as `"<units>.<cents> <currency>"` for every subtype of `Money`.
    static final class MoneyAdapter implements JsonSerializer<Money>, JsonDeserializer<Money> {

        @Override
        public JsonElement serialize(Money money, Type typeOfSrc, JsonSerializationContext context) {
            long units = money.minorUnits();
            String text = "%s%d.%02d %s".formatted(units < 0 ? "-" : "", Math.abs(units) / 100,
                    Math.abs(units) % 100, money.currency());
            return new JsonPrimitive(text);
        }

        @Override
        public Money deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            String text = json.getAsString();
            String[] parts = text.split(" ");
            if (parts.length != 2 || !parts[0].matches("-?\\d+\\.\\d{2}") || !parts[1].matches("[A-Z]{3}")) {
                throw new JsonParseException("not a monetary amount: " + text);
            }
            long minorUnits = Long.parseLong(parts[0].replace(".", ""));
            return new Money(minorUnits, parts[1]);
        }
    }

    /// Stores an `Instant` as whole epoch seconds.
    static final class EpochSecondsAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.getEpochSecond());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.ofEpochSecond(in.nextLong());
        }
    }

    /// Adds a `type` discriminator to every `PaymentEvent` and dispatches on it when reading.
    static final class PaymentEventAdapterFactory implements TypeAdapterFactory {

        private static final Map<String, Class<? extends PaymentEvent>> SUBTYPES = Map.of(
                "authorized", Authorized.class,
                "captured", Captured.class,
                "refunded", Refunded.class);

        static String discriminatorOf(PaymentEvent event) {
            return SUBTYPES.entrySet().stream()
                    .filter(entry -> entry.getValue() == event.getClass())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!PaymentEvent.class.isAssignableFrom(type.getRawType())) {
                return null;
            }
            Map<String, TypeAdapter<? extends PaymentEvent>> delegates = new LinkedHashMap<>();
            SUBTYPES.forEach((name, subtype) ->
                    delegates.put(name, gson.getDelegateAdapter(this, TypeToken.get(subtype))));

            TypeAdapter<PaymentEvent> adapter = new TypeAdapter<PaymentEvent>() {
                @Override
                public void write(JsonWriter out, PaymentEvent event) throws IOException {
                    String name = discriminatorOf(event);
                    JsonObject body = toTree(delegates.get(name), event).getAsJsonObject();
                    JsonObject tagged = new JsonObject();
                    tagged.addProperty("type", name);
                    body.entrySet().forEach(entry -> tagged.add(entry.getKey(), entry.getValue()));
                    gson.toJson(tagged, out);
                }

                @Override
                public PaymentEvent read(JsonReader in) throws IOException {
                    JsonObject tagged = JsonParser.parseReader(in).getAsJsonObject();
                    JsonElement name = tagged.remove("type");
                    if (name == null) {
                        throw new JsonParseException("payment event without a type: " + tagged);
                    }
                    TypeAdapter<? extends PaymentEvent> delegate = delegates.get(name.getAsString());
                    if (delegate == null) {
                        throw new JsonParseException("unknown payment event type: " + name.getAsString());
                    }
                    return delegate.fromJsonTree(tagged);
                }
            }.nullSafe();

            @SuppressWarnings("unchecked")
            TypeAdapter<T> typed = (TypeAdapter<T>) adapter;
            return typed;
        }

        @SuppressWarnings("unchecked")
        private static <E extends PaymentEvent> JsonElement toTree(TypeAdapter<E> delegate, PaymentEvent event) {
            return delegate.toJsonTree((E) event);
        }
    }
}
