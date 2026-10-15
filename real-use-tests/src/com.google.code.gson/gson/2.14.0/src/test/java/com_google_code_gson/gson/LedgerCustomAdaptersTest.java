/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Custom type adapters, serializers, and type adapter factories that control the JSON shape of
 * application types.
 *
 * A bookkeeping service exports a ledger whose money amounts are compact strings, whose dates
 * use an annotation-selected adapter, and whose polymorphic entries carry a type discriminator
 * added by an application-defined factory.
 */
public class LedgerCustomAdaptersTest {

    private static final Type ENTRY_LIST = new TypeToken<List<Entry>>() { }.getType();

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Money.class, new MoneyCodec())
            .registerTypeAdapterFactory(new EntryTypeFactory())
            .create();

    @Test
    void writesLedgerInTheShapeTheAdaptersDefine() {
        Ledger ledger = sampleLedger();

        JsonObject document = gson.toJsonTree(ledger).getAsJsonObject();

        assertEquals("ACME Corp", document.get("owner").getAsString());
        assertEquals("2026-01-31", document.get("closedOn").getAsString());
        JsonObject deposit = document.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("deposit", deposit.get("type").getAsString());
        assertEquals("1500.00 EUR", deposit.get("amount").getAsString());
        assertEquals("Invoice 42", deposit.get("reference").getAsString());
        JsonObject transfer = document.getAsJsonArray("entries").get(2).getAsJsonObject();
        assertEquals("transfer", transfer.get("type").getAsString());
        assertEquals("DE89370400440532013000", transfer.get("counterpartyIban").getAsString());
        assertEquals("200.00 EUR", document.getAsJsonObject("limits").get("perEntry").getAsString());
    }

    @Test
    void roundTripsLedgerThroughJsonText() {
        String json = gson.toJson(sampleLedger());

        Ledger restored = gson.fromJson(json, Ledger.class);

        assertEquals(LocalDate.of(2026, 1, 31), restored.closedOn);
        assertEquals(3, restored.entries.size());
        Deposit deposit = assertInstanceOf(Deposit.class, restored.entries.get(0));
        assertEquals("Invoice 42", deposit.reference);
        Withdrawal withdrawal = assertInstanceOf(Withdrawal.class, restored.entries.get(1));
        assertEquals("ATM", withdrawal.channel);
        Transfer transfer = assertInstanceOf(Transfer.class, restored.entries.get(2));
        assertEquals("DE89370400440532013000", transfer.counterpartyIban);
        assertEquals(new Money(new BigDecimal("1140.00"), "EUR"), restored.balance());
        assertEquals(new Money(new BigDecimal("200.00"), "EUR"), restored.limits.get("perEntry"));
    }

    @Test
    void readsEntriesSubmittedByAnotherSystem() {
        String json = """
                [
                  {"type": "withdrawal", "amount": "12.50 USD", "bookedOn": "2026-03-01", "channel": "card"},
                  {"type": "deposit", "amount": "99.99 USD", "bookedOn": "2026-03-02", "reference": "Refund"}
                ]
                """;

        List<Entry> entries = gson.fromJson(json, ENTRY_LIST);

        assertEquals(2, entries.size());
        Withdrawal withdrawal = assertInstanceOf(Withdrawal.class, entries.get(0));
        assertEquals(new Money(new BigDecimal("12.50"), "USD"), withdrawal.amount);
        assertEquals(LocalDate.of(2026, 3, 1), withdrawal.bookedOn);
        assertEquals("card", withdrawal.channel);
        Deposit deposit = assertInstanceOf(Deposit.class, entries.get(1));
        assertEquals(new Money(new BigDecimal("99.99"), "USD"), deposit.amount);
    }

    @Test
    void rejectsEntriesWithUnknownTypeOrMalformedAmount() {
        JsonParseException unknownType = assertThrows(JsonParseException.class, () -> gson.fromJson(
                "{\"type\": \"refund\", \"amount\": \"1.00 EUR\"}", Entry.class));
        assertTrue(unknownType.getMessage().contains("refund"), unknownType.getMessage());

        JsonParseException badAmount = assertThrows(JsonParseException.class, () -> gson.fromJson(
                "{\"type\": \"deposit\", \"amount\": \"one euro\"}", Entry.class));
        assertTrue(badAmount.getMessage().contains("one euro"), badAmount.getMessage());
    }

    private static Ledger sampleLedger() {
        Ledger ledger = new Ledger();
        ledger.owner = "ACME Corp";
        ledger.closedOn = LocalDate.of(2026, 1, 31);
        ledger.entries = new ArrayList<>();

        Deposit deposit = new Deposit();
        deposit.amount = new Money(new BigDecimal("1500.00"), "EUR");
        deposit.bookedOn = LocalDate.of(2026, 1, 3);
        deposit.reference = "Invoice 42";
        ledger.entries.add(deposit);

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.amount = new Money(new BigDecimal("60.00"), "EUR");
        withdrawal.bookedOn = LocalDate.of(2026, 1, 10);
        withdrawal.channel = "ATM";
        ledger.entries.add(withdrawal);

        Transfer transfer = new Transfer();
        transfer.amount = new Money(new BigDecimal("300.00"), "EUR");
        transfer.bookedOn = LocalDate.of(2026, 1, 20);
        transfer.counterpartyIban = "DE89370400440532013000";
        ledger.entries.add(transfer);

        ledger.limits = new LinkedHashMap<>();
        ledger.limits.put("perEntry", new Money(new BigDecimal("200.00"), "EUR"));
        return ledger;
    }

    record Money(BigDecimal value, String currency) {

        Money plus(Money other) {
            return new Money(value.add(other.value), currency);
        }

        Money negate() {
            return new Money(value.negate(), currency);
        }
    }

    /// Writes money as `"<amount> <currency>"` so that exports stay readable to accountants.
    static final class MoneyCodec implements JsonSerializer<Money>, JsonDeserializer<Money> {

        @Override
        public JsonElement serialize(Money money, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(money.value().toPlainString() + " " + money.currency());
        }

        @Override
        public Money deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            String text = json.getAsString();
            String[] parts = text.split(" ");
            if (parts.length != 2) {
                throw new JsonParseException("Malformed amount: " + text);
            }
            try {
                return new Money(new BigDecimal(parts[0]), parts[1]);
            } catch (NumberFormatException e) {
                throw new JsonParseException("Malformed amount: " + text, e);
            }
        }
    }

    /// Selected through `@JsonAdapter`, so Gson instantiates it on first use.
    static final class IsoDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate date) throws IOException {
            out.value(date.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            return LocalDate.parse(in.nextString());
        }
    }

    /// Adds a `type` discriminator to every entry and dispatches on it when reading.
    static final class EntryTypeFactory implements TypeAdapterFactory {

        private static final Map<String, Class<? extends Entry>> SUBTYPES = Map.of(
                "deposit", Deposit.class,
                "withdrawal", Withdrawal.class,
                "transfer", Transfer.class);

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Entry.class) {
                return null;
            }
            TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
            Map<String, TypeAdapter<? extends Entry>> delegates = new LinkedHashMap<>();
            Map<Class<?>, String> names = new LinkedHashMap<>();
            SUBTYPES.forEach((name, subtype) -> {
                delegates.put(name, gson.getDelegateAdapter(this, TypeToken.get(subtype)));
                names.put(subtype, name);
            });
            return (TypeAdapter<T>) new TypeAdapter<Entry>() {
                @Override
                public void write(JsonWriter out, Entry entry) throws IOException {
                    String name = names.get(entry.getClass());
                    TypeAdapter<Entry> delegate = (TypeAdapter<Entry>) delegates.get(name);
                    JsonObject object = new JsonObject();
                    object.addProperty("type", name);
                    delegate.toJsonTree(entry).getAsJsonObject().entrySet()
                            .forEach(field -> object.add(field.getKey(), field.getValue()));
                    elements.write(out, object);
                }

                @Override
                public Entry read(JsonReader in) throws IOException {
                    JsonObject object = elements.read(in).getAsJsonObject();
                    String name = object.remove("type").getAsString();
                    TypeAdapter<? extends Entry> delegate = delegates.get(name);
                    if (delegate == null) {
                        throw new JsonParseException("Unknown entry type: " + name);
                    }
                    return delegate.fromJsonTree(object);
                }
            }.nullSafe();
        }
    }

    abstract static class Entry {
        Money amount;
        @JsonAdapter(IsoDateAdapter.class)
        LocalDate bookedOn;

        abstract Money signedAmount();
    }

    static final class Deposit extends Entry {
        String reference;

        @Override
        Money signedAmount() {
            return amount;
        }
    }

    static final class Withdrawal extends Entry {
        String channel;

        @Override
        Money signedAmount() {
            return amount.negate();
        }
    }

    static final class Transfer extends Entry {
        String counterpartyIban;

        @Override
        Money signedAmount() {
            return amount.negate();
        }
    }

    static final class Ledger {
        String owner;
        @JsonAdapter(IsoDateAdapter.class)
        LocalDate closedOn;
        List<Entry> entries;
        Map<String, Money> limits;

        Money balance() {
            return entries.stream()
                    .map(Entry::signedAmount)
                    .reduce(new Money(new BigDecimal("0.00"), "EUR"), Money::plus);
        }
    }
}
