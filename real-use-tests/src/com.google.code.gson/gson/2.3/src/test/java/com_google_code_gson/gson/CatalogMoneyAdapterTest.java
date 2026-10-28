/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Gson's custom type-adapter subsystem to import catalog prices and export a checkout total.
public class CatalogMoneyAdapterTest {

    @Test
    @Timeout(60)
    public void convertsScalarPricesToMoneyAndPreservesTheCatalogWireFormat() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Money.class, new MoneyAdapter().nullSafe())
                .serializeNulls()
                .create();
        Type catalogType = new TypeToken<Map<String, Money>>() { }.getType();
        Map<String, Money> catalog = gson.fromJson("""
                {"notebook":"USD 12.50","pencil":"USD 1.25","retired":null}
                """, catalogType);

        assertEquals(new Money("USD", 1250), catalog.get("notebook"));
        assertEquals(new Money("USD", 125), catalog.get("pencil"));
        assertNull(catalog.get("retired"));

        Money notebook = catalog.get("notebook");
        Money pencil = catalog.get("pencil");
        assertEquals(notebook.currency(), pencil.currency());
        Money total = new Money(notebook.currency(), 2 * notebook.minorUnits() + pencil.minorUnits());

        assertEquals("\"USD 26.25\"", gson.toJson(total, Money.class));
        assertEquals(total, gson.fromJson("\"USD 26.25\"", Money.class));
        assertEquals("""
                {"notebook":"USD 12.50","pencil":"USD 1.25","retired":null}""",
                gson.toJson(catalog, catalogType));
        assertEquals("null", gson.toJson(null, Money.class));
        assertThrows(JsonParseException.class,
                () -> gson.fromJson("\"USD 1.005\"", Money.class));
    }

    public record Money(String currency, long minorUnits) {
    }

    private static class MoneyAdapter extends TypeAdapter<Money> {
        @Override
        public void write(JsonWriter writer, Money money) throws IOException {
            writer.value(money.currency() + " " + BigDecimal.valueOf(money.minorUnits(), 2).toPlainString());
        }

        @Override
        public Money read(JsonReader reader) throws IOException {
            String value = reader.nextString();
            String[] parts = value.split(" ", 2);
            if (parts.length != 2 || !parts[0].matches("[A-Z]{3}")) {
                throw new JsonParseException("A catalog price requires a currency and amount");
            }
            try {
                long minorUnits = new BigDecimal(parts[1]).movePointRight(2).longValueExact();
                return new Money(parts[0], minorUnits);
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new JsonParseException("A catalog price must be an exact number of cents", exception);
            }
        }
    }
}
