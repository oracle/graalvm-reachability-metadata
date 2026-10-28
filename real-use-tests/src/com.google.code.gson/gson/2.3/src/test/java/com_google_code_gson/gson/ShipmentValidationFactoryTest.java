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
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's delegating type-adapter factories to validate shipment imports and their nested parcels.
public class ShipmentValidationFactoryTest {

    @Test
    @Timeout(60)
    public void rejectsInvalidImportsBeforeDispatchAndPreservesTheStandardShipmentFormat() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new ValidationFactory())
                .create();
        String incoming = """
                {
                  "shipmentId": "S-503",
                  "parcels": [
                    {"sku": "book", "weightGrams": 1250},
                    {"sku": "stationery", "weightGrams": 750}
                  ],
                  "dispatched": false
                }
                """;

        Shipment shipment = gson.fromJson(incoming, Shipment.class);
        assertEquals("S-503", shipment.shipmentId);
        assertEquals(2, shipment.parcels.size());
        assertEquals("book", shipment.parcels.get(0).sku);
        assertEquals("stationery", shipment.parcels.get(1).sku);
        int totalWeight = shipment.parcels.stream().mapToInt(parcel -> parcel.weightGrams).sum();
        assertEquals(2000, totalWeight);

        JsonParseException invalidParcel = assertThrows(JsonParseException.class,
                () -> gson.fromJson(incoming.replace("1250", "0"), Shipment.class));
        assertEquals("A parcel requires a SKU and a positive weight", invalidParcel.getMessage());
        JsonParseException invalidShipment = assertThrows(JsonParseException.class,
                () -> gson.fromJson(incoming.replace("S-503", ""), Shipment.class));
        assertEquals("A shipment requires an ID and at least one parcel", invalidShipment.getMessage());

        shipment.dispatched = true;
        String acknowledgement = gson.toJson(shipment);
        assertEquals(new JsonParser().parse("""
                {
                  "shipmentId": "S-503",
                  "parcels": [
                    {"sku": "book", "weightGrams": 1250},
                    {"sku": "stationery", "weightGrams": 750}
                  ],
                  "dispatched": true
                }
                """), new JsonParser().parse(acknowledgement));
        Shipment restored = gson.fromJson(acknowledgement, Shipment.class);
        assertEquals("S-503", restored.shipmentId);
        assertTrue(restored.dispatched);
        assertEquals(2000, restored.parcels.stream().mapToInt(parcel -> parcel.weightGrams).sum());
    }

    private interface Validated {
        void validate();
    }

    private static class ValidationFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!Validated.class.isAssignableFrom(type.getRawType())) {
                return null;
            }
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public T read(JsonReader reader) throws IOException {
                    T value = delegate.read(reader);
                    if (value instanceof Validated validated) {
                        validated.validate();
                    }
                    return value;
                }

                @Override
                public void write(JsonWriter writer, T value) throws IOException {
                    delegate.write(writer, value);
                }
            };
        }
    }

    public static class Shipment implements Validated {
        private String shipmentId;
        private List<Parcel> parcels;
        private boolean dispatched;

        @Override
        public void validate() {
            if (shipmentId == null || shipmentId.isBlank()
                    || parcels == null || parcels.isEmpty() || parcels.contains(null)) {
                throw new JsonParseException("A shipment requires an ID and at least one parcel");
            }
        }
    }

    public static class Parcel implements Validated {
        private String sku;
        private int weightGrams;

        @Override
        public void validate() {
            if (sku == null || sku.isBlank() || weightGrams <= 0) {
                throw new JsonParseException("A parcel requires a SKU and a positive weight");
            }
        }
    }
}
