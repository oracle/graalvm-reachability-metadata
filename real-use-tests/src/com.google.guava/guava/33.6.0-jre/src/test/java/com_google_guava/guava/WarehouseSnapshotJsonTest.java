/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.Table;
import com.google.common.collect.TreeRangeSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.net.HostAndPort;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * JSON data binding of Guava immutable collections, multimaps, multisets, tables, ranges,
 * optionals, hash codes, and host-and-port values through Jackson's `GuavaModule`.
 *
 * A warehouse service publishes a snapshot of its stock as JSON for other services and reads
 * snapshots back from peers. The snapshot model is built from Guava value types, so it must
 * survive a JSON round trip unchanged and the wire format must stay readable to clients that
 * know nothing about Guava.
 */
public class WarehouseSnapshotJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new GuavaModule());

    @Test
    void roundTripsASnapshotBuiltFromGuavaCollections() throws Exception {
        WarehouseSnapshot snapshot = sampleSnapshot();

        String json = MAPPER.writeValueAsString(snapshot);
        WarehouseSnapshot restored = MAPPER.readValue(json, WarehouseSnapshot.class);

        assertEquals(ImmutableList.of("dock-a", "dock-b", "dock-c"), restored.docks);
        assertInstanceOf(ImmutableList.class, restored.docks);
        assertEquals(ImmutableSortedSet.of("fragile", "heavy", "perishable"), restored.handlingTags);
        assertEquals("fragile", restored.handlingTags.first());
        assertEquals(ImmutableMap.of("sku-keyboard", 40, "sku-monitor", 12), restored.stockBySku);
        assertEquals(ImmutableList.of("sku-keyboard", "sku-monitor"),
                restored.stockBySku.keySet().asList());
        assertEquals(ImmutableList.of("sku-keyboard", "sku-mouse"), restored.skusByAisle.get("A1"));
        assertEquals(ImmutableList.of("sku-monitor"), restored.skusByAisle.get("B4"));
        assertEquals(3, restored.skusByAisle.size());
        assertEquals(3, restored.pendingPicks.count("sku-keyboard"));
        assertEquals(1, restored.pendingPicks.count("sku-monitor"));
        assertEquals(0, restored.pendingPicks.count("sku-desk"));
        assertEquals(Integer.valueOf(40), restored.binLevels.get("A1", "shelf-1"));
        assertEquals(Integer.valueOf(5), restored.binLevels.get("B4", "shelf-2"));
        assertEquals(ImmutableMap.of("shelf-1", 40, "shelf-2", 7), restored.binLevels.row("A1"));
        assertEquals(Optional.of("night shift short-staffed"), restored.supervisorNote);
        assertEquals(Optional.absent(), restored.incidentId);
        assertEquals(snapshot.manifestChecksum, restored.manifestChecksum);
        assertEquals(HostAndPort.fromParts("inventory.internal", 8443), restored.replicationPeer);
        assertEquals(snapshot, restored);
    }

    @Test
    void writesGuavaTypesInAWireFormatPlainJsonClientsCanRead() throws Exception {
        JsonNode tree = MAPPER.readTree(MAPPER.writeValueAsString(sampleSnapshot()));

        assertTrue(tree.get("docks").isArray());
        assertEquals("dock-b", tree.get("docks").get(1).asText());
        assertEquals(40, tree.get("stockBySku").get("sku-keyboard").asInt());
        JsonNode aisleA1 = tree.get("skusByAisle").get("A1");
        assertTrue(aisleA1.isArray());
        assertEquals("sku-mouse", aisleA1.get(1).asText());
        assertEquals(4, tree.get("pendingPicks").size());
        assertEquals(7, tree.get("binLevels").get("A1").get("shelf-2").asInt());
        assertEquals("night shift short-staffed", tree.get("supervisorNote").asText());
        assertTrue(tree.get("incidentId").isNull());
        assertEquals(sampleSnapshot().manifestChecksum.toString(), tree.get("manifestChecksum").asText());
        assertEquals("inventory.internal:8443", tree.get("replicationPeer").asText());
    }

    @Test
    void readsSnapshotsWrittenByPeersThatDoNotUseGuava() throws Exception {
        String json = """
                {
                  "docks": ["dock-z"],
                  "handlingTags": ["heavy", "fragile", "heavy"],
                  "stockBySku": {"sku-desk": 3},
                  "skusByAisle": {"C2": ["sku-desk", "sku-lamp"]},
                  "pendingPicks": ["sku-lamp", "sku-lamp"],
                  "binLevels": {"C2": {"shelf-9": 3}},
                  "supervisorNote": null,
                  "incidentId": "INC-77",
                  "manifestChecksum": "00ff10ab",
                  "replicationPeer": "10.0.0.5:9000"
                }
                """;

        WarehouseSnapshot snapshot = MAPPER.readValue(json, WarehouseSnapshot.class);

        assertEquals(ImmutableList.of("dock-z"), snapshot.docks);
        assertEquals(ImmutableSortedSet.of("fragile", "heavy"), snapshot.handlingTags);
        assertEquals(ImmutableList.of("sku-desk", "sku-lamp"), snapshot.skusByAisle.get("C2"));
        assertEquals(2, snapshot.pendingPicks.count("sku-lamp"));
        assertEquals(Integer.valueOf(3), snapshot.binLevels.get("C2", "shelf-9"));
        assertFalse(snapshot.supervisorNote.isPresent());
        assertEquals("INC-77", snapshot.incidentId.get());
        assertEquals(HashCode.fromString("00ff10ab"), snapshot.manifestChecksum);
        assertEquals("10.0.0.5", snapshot.replicationPeer.getHost());
        assertEquals(9000, snapshot.replicationPeer.getPort());
    }

    @Test
    void roundTripsDeliveryWindowsAsRangesAndRangeSets() throws Exception {
        DeliverySchedule schedule = new DeliverySchedule();
        schedule.acceptedWeights = Range.closedOpen(1, 500);
        schedule.openHours = TreeRangeSet.create();
        schedule.openHours.add(Range.closed(8, 12));
        schedule.openHours.add(Range.closed(13, 17));
        schedule.openHours.add(Range.closed(11, 13));
        schedule.slotsByWindow = ImmutableList.of(Range.atLeast(18), Range.lessThan(6));

        String json = MAPPER.writeValueAsString(schedule);
        DeliverySchedule restored = MAPPER.readValue(json, DeliverySchedule.class);

        assertEquals(Range.closedOpen(1, 500), restored.acceptedWeights);
        assertTrue(restored.acceptedWeights.contains(499));
        assertFalse(restored.acceptedWeights.contains(500));
        assertEquals(ImmutableRangeSet.of(Range.closed(8, 17)), ImmutableRangeSet.copyOf(restored.openHours));
        assertTrue(restored.openHours.contains(12));
        assertFalse(restored.openHours.contains(18));
        assertEquals(ImmutableList.of(Range.atLeast(18), Range.lessThan(6)), restored.slotsByWindow);
        assertFalse(restored.slotsByWindow.get(0).hasUpperBound());
    }

    @Test
    void bindsTopLevelGuavaContainersThroughTypeReferences() throws Exception {
        ImmutableListMultimap<String, Integer> ordersByCustomer = ImmutableListMultimap.<String, Integer>builder()
                .putAll("alice", 101, 104)
                .put("bob", 102)
                .build();
        Table<String, String, Integer> transfers = ImmutableTable.<String, String, Integer>builder()
                .put("dock-a", "dock-b", 12)
                .put("dock-b", "dock-c", 4)
                .build();

        ImmutableListMultimap<String, Integer> restoredOrders = MAPPER.readValue(
                MAPPER.writeValueAsString(ordersByCustomer),
                new TypeReference<ImmutableListMultimap<String, Integer>>() { });
        ImmutableTable<String, String, Integer> restoredTransfers = MAPPER.readValue(
                MAPPER.writeValueAsString(transfers),
                new TypeReference<ImmutableTable<String, String, Integer>>() { });
        Multiset<String> restoredPicks = MAPPER.readValue(
                "[\"sku-a\", \"sku-b\", \"sku-a\"]",
                new TypeReference<ImmutableMultiset<String>>() { });

        assertEquals(ordersByCustomer, restoredOrders);
        assertEquals(ImmutableList.of(101, 104), restoredOrders.get("alice"));
        assertEquals(transfers, restoredTransfers);
        assertEquals(ImmutableMap.of("dock-b", 12), restoredTransfers.row("dock-a"));
        assertEquals(2, restoredPicks.count("sku-a"));
        assertEquals(ImmutableSortedSet.of("sku-a", "sku-b"), ImmutableSortedSet.copyOf(restoredPicks.elementSet()));
    }

    private static WarehouseSnapshot sampleSnapshot() {
        WarehouseSnapshot snapshot = new WarehouseSnapshot();
        snapshot.docks = ImmutableList.of("dock-a", "dock-b", "dock-c");
        snapshot.handlingTags = ImmutableSortedSet.of("perishable", "fragile", "heavy");
        snapshot.stockBySku = ImmutableMap.of("sku-keyboard", 40, "sku-monitor", 12);
        snapshot.skusByAisle = ImmutableListMultimap.<String, String>builder()
                .putAll("A1", "sku-keyboard", "sku-mouse")
                .put("B4", "sku-monitor")
                .build();
        snapshot.pendingPicks = ImmutableMultiset.<String>builder()
                .addCopies("sku-keyboard", 3)
                .add("sku-monitor")
                .build();
        snapshot.binLevels = ImmutableTable.<String, String, Integer>builder()
                .put("A1", "shelf-1", 40)
                .put("A1", "shelf-2", 7)
                .put("B4", "shelf-2", 5)
                .build();
        snapshot.supervisorNote = Optional.of("night shift short-staffed");
        snapshot.incidentId = Optional.absent();
        snapshot.manifestChecksum = Hashing.sha256().hashString("dock-a,dock-b,dock-c", StandardCharsets.UTF_8);
        snapshot.replicationPeer = HostAndPort.fromParts("inventory.internal", 8443);
        return snapshot;
    }

    /** Stock snapshot one warehouse publishes to its peers. */
    public static class WarehouseSnapshot {
        public ImmutableList<String> docks;
        public ImmutableSortedSet<String> handlingTags;
        public ImmutableMap<String, Integer> stockBySku;
        public ImmutableListMultimap<String, String> skusByAisle;
        public ImmutableMultiset<String> pendingPicks;
        public ImmutableTable<String, String, Integer> binLevels;
        public Optional<String> supervisorNote;
        public Optional<String> incidentId;
        public HashCode manifestChecksum;
        public HostAndPort replicationPeer;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WarehouseSnapshot that)) {
                return false;
            }
            return docks.equals(that.docks)
                    && handlingTags.equals(that.handlingTags)
                    && stockBySku.equals(that.stockBySku)
                    && skusByAisle.equals(that.skusByAisle)
                    && pendingPicks.equals(that.pendingPicks)
                    && binLevels.equals(that.binLevels)
                    && supervisorNote.equals(that.supervisorNote)
                    && incidentId.equals(that.incidentId)
                    && manifestChecksum.equals(that.manifestChecksum)
                    && replicationPeer.equals(that.replicationPeer);
        }

        @Override
        public int hashCode() {
            return docks.hashCode() * 31 + stockBySku.hashCode();
        }
    }

    /** Delivery windows a dock accepts, expressed as integer ranges. */
    public static class DeliverySchedule {
        public Range<Integer> acceptedWeights;
        public RangeSet<Integer> openHours;
        public ImmutableList<Range<Integer>> slotsByWindow;
    }
}
