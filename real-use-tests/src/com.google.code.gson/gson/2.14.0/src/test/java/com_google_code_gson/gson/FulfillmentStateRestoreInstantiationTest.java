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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonIOException;
import com.google.gson.ReflectionAccessFilter;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Object instantiation during deserialization: `InstanceCreator` registration, default
 * implementations for collection interfaces, Unsafe allocation of constructor-less classes,
 * `disableJdkUnsafe`, and `ReflectionAccessFilter` policies.
 *
 * A fulfillment service checkpoints its in-memory state as JSON and restores it after a restart.
 * Restoring declares its fields through interfaces such as `Queue` and `SortedSet`, injects the
 * running warehouse context into shipments through an `InstanceCreator`, and keeps reflection away
 * from a vendor SDK type by an access filter that forces an explicit `TypeAdapter`.
 */
public class FulfillmentStateRestoreInstantiationTest {

    private static final String CHECKPOINT = """
            {
              "pendingOrders": ["ord-17", "ord-03", "ord-42"],
              "carriers": ["ups", "dhl", "fedex"],
              "completedStages": ["PICKED", "PACKED"],
              "countsByStage": {"PACKED": 4, "PICKED": 9},
              "slaBreachesByCarrier": {"ups": 1, "dhl": 0},
              "retryBudget": {"ord-03": 2}
            }
            """;

    @Test
    void interfaceTypedFieldsAreRestoredIntoGsonDefaultImplementations() {
        Gson gson = new Gson();

        FulfillmentState state = gson.fromJson(CHECKPOINT, FulfillmentState.class);

        assertInstanceOf(ArrayDeque.class, state.pendingOrders);
        assertEquals("ord-17", state.pendingOrders.poll());
        assertEquals("ord-03", state.pendingOrders.poll());
        assertEquals(1, state.pendingOrders.size());

        assertInstanceOf(TreeSet.class, state.carriers);
        assertEquals("dhl", state.carriers.first());
        assertEquals(List.of("dhl", "fedex", "ups"), new ArrayList<>(state.carriers));

        assertEquals(EnumSet.of(Stage.PICKED, Stage.PACKED), state.completedStages);
        assertFalse(state.completedStages.contains(Stage.SHIPPED));

        assertInstanceOf(EnumMap.class, state.countsByStage);
        assertEquals(List.of(Stage.PICKED, Stage.PACKED), new ArrayList<>(state.countsByStage.keySet()));
        assertEquals(9, state.countsByStage.get(Stage.PICKED));

        assertInstanceOf(TreeMap.class, state.slaBreachesByCarrier);
        assertEquals("dhl", state.slaBreachesByCarrier.firstKey());

        assertInstanceOf(ConcurrentHashMap.class, state.retryBudget);
        state.retryBudget.merge("ord-03", -1, Integer::sum);
        assertEquals(1, state.retryBudget.get("ord-03"));
    }

    @Test
    void instanceCreatorInjectsTheRunningWarehouseContextIntoRestoredShipments() {
        WarehouseContext context = new WarehouseContext("berlin-2");
        AtomicInteger created = new AtomicInteger();
        InstanceCreator<Shipment> shipmentCreator = type -> {
            created.incrementAndGet();
            return new Shipment(context);
        };
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Shipment.class, shipmentCreator)
                .create();
        String json = """
                [
                  {"orderId": "ord-17", "parcels": 2},
                  {"orderId": "ord-42", "parcels": 1}
                ]
                """;
        Type shipmentList = new TypeToken<List<Shipment>>() { }.getType();

        List<Shipment> shipments = gson.fromJson(json, shipmentList);

        assertEquals(2, created.get());
        assertEquals("ord-17", shipments.get(0).orderId);
        assertEquals(2, shipments.get(0).parcels);
        assertSame(context, shipments.get(0).context);
        assertSame(context, shipments.get(1).context);
        assertEquals("berlin-2/ord-42", shipments.get(1).label());
        assertEquals("""
                {"orderId":"ord-17","parcels":2}""", gson.toJson(shipments.get(0)));
    }

    @Test
    void abstractNotificationChannelNeedsAnInstanceCreatorToBeRestored() {
        String json = """
                {"target": "ops@example.com", "retries": 3}""";

        JsonIOException rejected = assertThrows(JsonIOException.class,
                () -> new Gson().fromJson(json, NotificationChannel.class));
        assertTrue(rejected.getMessage().startsWith("Abstract classes can't be instantiated!"),
                rejected.getMessage());
        assertTrue(rejected.getMessage().contains("Class name: " + NotificationChannel.class.getName()),
                rejected.getMessage());

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(NotificationChannel.class,
                        (InstanceCreator<NotificationChannel>) type -> new EmailChannel())
                .create();
        NotificationChannel channel = gson.fromJson(json, NotificationChannel.class);

        EmailChannel email = assertInstanceOf(EmailChannel.class, channel);
        assertEquals("ops@example.com", email.target);
        assertEquals(3, email.retries);
        assertEquals("mailto:ops@example.com", email.address());
    }

    @Test
    void constructorlessCarrierIsAllocatedWithoutRunningFieldInitializers() {
        String json = """
                {"code": "dhl", "slaHours": 48}""";

        Carrier restored = new Gson().fromJson(json, Carrier.class);

        assertEquals("dhl", restored.code);
        assertEquals(48, restored.slaHours);
        assertFalse(restored.active, "Unsafe allocation skips the `active = true` initializer");
        assertTrue(new Carrier("dhl", 48).active);

        Carrier explicit = new Gson().fromJson("""
                {"code": "ups", "slaHours": 24, "active": true}""", Carrier.class);
        assertTrue(explicit.active);
    }

    @Test
    void disablingJdkUnsafeRejectsConstructorlessClassesUntilACreatorIsRegistered() {
        String json = """
                {"code": "fedex", "slaHours": 72}""";
        Gson strict = new GsonBuilder().disableJdkUnsafe().create();

        JsonIOException rejected = assertThrows(JsonIOException.class,
                () -> strict.fromJson(json, Carrier.class));
        assertTrue(rejected.getMessage().contains("usage of JDK Unsafe is disabled"),
                rejected.getMessage());

        FulfillmentState state = strict.fromJson(CHECKPOINT, FulfillmentState.class);
        assertEquals(3, state.carriers.size());

        Gson withCreator = strict.newBuilder()
                .registerTypeAdapter(Carrier.class,
                        (InstanceCreator<Carrier>) type -> new Carrier("unknown", 0))
                .create();
        Carrier restored = withCreator.fromJson(json, Carrier.class);
        assertEquals("fedex", restored.code);
        assertEquals(72, restored.slaHours);
        assertTrue(restored.active, "the creator's constructor ran the initializer");
    }

    @Test
    void accessFilterKeepsReflectionAwayFromVendorTypesUntilAnAdapterIsRegistered() {
        ReflectionAccessFilter vendorFilter = rawClass -> rawClass == VendorTrackingToken.class
                ? FilterResult.BLOCK_ALL
                : FilterResult.INDECISIVE;
        Gson filtered = new GsonBuilder().addReflectionAccessFilter(vendorFilter).create();
        String json = """
                {"orderId": "ord-42", "token": "trk:9F2C"}""";

        JsonIOException rejected = assertThrows(JsonIOException.class,
                () -> filtered.fromJson(json, TrackedOrder.class));
        assertTrue(rejected.getMessage().startsWith(
                "ReflectionAccessFilter does not permit using reflection for class "
                        + VendorTrackingToken.class.getName()), rejected.getMessage());

        Gson adapted = filtered.newBuilder()
                .registerTypeAdapter(VendorTrackingToken.class, new VendorTrackingTokenAdapter())
                .create();
        TrackedOrder order = adapted.fromJson(json, TrackedOrder.class);

        assertEquals("ord-42", order.orderId);
        assertEquals("trk", order.token.scheme());
        assertEquals("9F2C", order.token.value());
        assertEquals(json.replace(" ", ""), adapted.toJson(order));
    }

    @Test
    void blockInaccessibleFilterAcceptsPublicMembersButRejectsPrivateFields() {
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(rawClass -> rawClass.getName().startsWith("com_google_code_gson.")
                        ? FilterResult.BLOCK_INACCESSIBLE
                        : FilterResult.INDECISIVE)
                .create();

        PublicDockSlot slot = gson.fromJson("""
                {"dock": "D4", "reservedFor": "ord-17"}""", PublicDockSlot.class);
        assertEquals("D4", slot.dock);
        assertEquals("ord-17", slot.reservedFor);

        JsonIOException rejected = assertThrows(JsonIOException.class,
                () -> gson.toJson(new PrivateDockSlot("D5")));
        assertTrue(rejected.getMessage().contains(
                "is not accessible and ReflectionAccessFilter does not permit making it accessible"),
                rejected.getMessage());
        assertTrue(rejected.getMessage().contains("dock"), rejected.getMessage());
    }

    enum Stage {
        PICKED,
        PACKED,
        SHIPPED
    }

    static class FulfillmentState {
        Queue<String> pendingOrders;
        SortedSet<String> carriers;
        EnumSet<Stage> completedStages;
        EnumMap<Stage, Integer> countsByStage;
        SortedMap<String, Integer> slaBreachesByCarrier;
        ConcurrentMap<String, Integer> retryBudget;
    }

    record WarehouseContext(String site) {
    }

    static class Shipment {
        final transient WarehouseContext context;
        String orderId;
        int parcels;

        Shipment(WarehouseContext context) {
            this.context = context;
        }

        String label() {
            return context.site() + "/" + orderId;
        }
    }

    abstract static class NotificationChannel {
        String target;
        int retries;

        abstract String address();
    }

    static class EmailChannel extends NotificationChannel {
        @Override
        String address() {
            return "mailto:" + target;
        }
    }

    static class Carrier {
        String code;
        int slaHours;
        boolean active = true;

        Carrier(String code, int slaHours) {
            this.code = code;
            this.slaHours = slaHours;
        }
    }

    /// Stands in for a type from a vendor SDK whose internals the application must not touch.
    static final class VendorTrackingToken {
        private final String scheme;
        private final String value;

        private VendorTrackingToken(String scheme, String value) {
            this.scheme = scheme;
            this.value = value;
        }

        static VendorTrackingToken parse(String text) {
            int colon = text.indexOf(':');
            return new VendorTrackingToken(text.substring(0, colon), text.substring(colon + 1));
        }

        String scheme() {
            return scheme;
        }

        String value() {
            return value;
        }

        String format() {
            return scheme + ":" + value;
        }
    }

    static final class VendorTrackingTokenAdapter extends TypeAdapter<VendorTrackingToken> {
        @Override
        public void write(JsonWriter out, VendorTrackingToken token) throws IOException {
            out.value(token.format());
        }

        @Override
        public VendorTrackingToken read(JsonReader in) throws IOException {
            return VendorTrackingToken.parse(in.nextString());
        }
    }

    static class TrackedOrder {
        String orderId;
        VendorTrackingToken token;
    }

    public static class PublicDockSlot {
        public String dock;
        public String reservedFor;

        public PublicDockSlot() {
        }
    }

    static class PrivateDockSlot {
        private final String dock;

        PrivateDockSlot(String dock) {
            this.dock = dock;
        }
    }
}
