/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import kotlin.UInt;
import kotlin.ranges.ClosedRange;
import kotlin.ranges.UIntRange;
import kotlin.ranges.ULongRange;
import org.junit.jupiter.api.Test;

/// Kotlin unsigned ranges (`UIntRange`, `ULongRange`) bound through their value-class
/// constructor parameters and name-mangled getters by the Kotlin module's value instantiator.
///
/// A Java IP address manager shares its pool catalog with Kotlin provisioning agents, which
/// model each IPv4 pool as a `UIntRange` of addresses, since an IPv4 address is exactly one
/// `UInt`, and the pool's lease journal as a `ULongRange` of offsets. The manager reads the
/// catalog, checks lease requests against the pools, lists the addresses of small pools,
/// publishes the catalog back to the agents with only the range bounds on the wire, pinned by
/// a mix-in, and refuses pools that no `UInt` can hold.
class KotlinUnsignedRangesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build())
            .addMixIn(UIntRange.class, RangeWireFormat.class)
            .addMixIn(ULongRange.class, RangeWireFormat.class);

    /// Keeps a range to its two constructor bounds; the getters the ranges inherit from their
    /// progressions would add `first` and `last`, which the agents do not read.
    @JsonIncludeProperties({"start", "end"})
    interface RangeWireFormat {
    }

    /// One address pool in the catalog.
    public static final class AddressPool {
        public String name;
        public UIntRange addresses;
        public ULongRange journalOffsets;
    }

    /// A Kotlin agent's request to lease one address from a named pool.
    public static final class LeaseRequest {
        public String pool;
        public UInt address;
    }

    private static final String CATALOG = """
            [
              {
                "name": "office",
                "addresses": {"start": 3232235520, "end": 3232235775},
                "journalOffsets": {"start": 0, "end": 18446744073709551615}
              },
              {
                "name": "uplink",
                "addresses": {"start": 3405803776, "end": 3405803779},
                "journalOffsets": {"start": 9223372036854775808, "end": 9223372036854775900}
              },
              {
                "name": "broadcast",
                "addresses": {"start": 4294967295, "end": 4294967295},
                "journalOffsets": {"start": 1, "end": 1}
              }
            ]
            """;

    @Test
    void checksLeaseRequestsAgainstThePools() throws Exception {
        List<AddressPool> pools = readCatalog(CATALOG);

        assertEquals(List.of(
                "office 192.168.0.0 - 192.168.0.255",
                "uplink 203.0.113.0 - 203.0.113.3",
                "broadcast 255.255.255.255 - 255.255.255.255"),
                pools.stream().map(KotlinUnsignedRangesTest::describe).toList());
        assertEquals("0..18446744073709551615", pools.get(0).journalOffsets.toString());

        List<LeaseRequest> requests = MAPPER.readValue("""
                [
                  {"pool": "office", "address": 3232235530},
                  {"pool": "office", "address": 3232235776},
                  {"pool": "uplink", "address": 3405803779},
                  {"pool": "broadcast", "address": 4294967295}
                ]
                """, new TypeReference<List<LeaseRequest>>() {
                });

        List<String> decisions = new ArrayList<>();
        for (LeaseRequest request : requests) {
            ClosedRange<UInt> pool = find(pools, request.pool).addresses;
            decisions.add(dotted(request.address) + (pool.contains(request.address) ? " granted" : " refused"));
        }
        assertEquals(List.of(
                "192.168.0.10 granted",
                "192.168.1.0 refused",
                "203.0.113.3 granted",
                "255.255.255.255 granted"), decisions);
    }

    @Test
    void listsTheAddressesOfASmallPool() throws Exception {
        AddressPool uplink = find(readCatalog(CATALOG), "uplink");

        List<String> addresses = new ArrayList<>();
        for (UInt address : uplink.addresses) {
            addresses.add(dotted(address));
        }

        assertEquals(List.of("203.0.113.0", "203.0.113.1", "203.0.113.2", "203.0.113.3"), addresses);
        assertFalse(uplink.addresses.isEmpty());
    }

    @Test
    void publishesTheCatalogBackToTheAgents() throws Exception {
        List<AddressPool> pools = readCatalog(CATALOG);

        String published = MAPPER.writeValueAsString(pools);

        JsonNode tree = MAPPER.readTree(published);
        assertEquals(2, tree.at("/0/addresses").size());
        assertEquals(2, tree.at("/0/journalOffsets").size());
        assertEquals(4294967295L, tree.at("/2/addresses/end").longValue());
        assertEquals(3405803776L, tree.at("/1/addresses/start").longValue());
        assertEquals(new BigInteger("18446744073709551615"),
                tree.at("/0/journalOffsets/end").bigIntegerValue());
        assertEquals(new BigInteger("9223372036854775808"),
                tree.at("/1/journalOffsets/start").bigIntegerValue());

        List<AddressPool> reread = readCatalog(published);
        for (int i = 0; i < pools.size(); i++) {
            assertEquals(pools.get(i).addresses, reread.get(i).addresses);
            assertEquals(pools.get(i).journalOffsets, reread.get(i).journalOffsets);
        }
    }

    @Test
    void treatsAReversedPoolAsRetired() throws Exception {
        AddressPool retired = readCatalog("""
                [{"name": "retired", "addresses": {"start": 167772170, "end": 167772160},
                  "journalOffsets": {"start": 5, "end": 4}}]
                """).get(0);

        assertTrue(retired.addresses.isEmpty());
        assertTrue(retired.journalOffsets.isEmpty());
        assertEquals(UIntRange.Companion.getEMPTY(), retired.addresses);
        assertEquals(ULongRange.Companion.getEMPTY(), retired.journalOffsets);
        assertFalse(retired.addresses.iterator().hasNext());
    }

    @Test
    void refusesPoolsOutsideTheUnsignedRange() {
        JsonMappingException tooWide = assertThrows(JsonMappingException.class, () -> readCatalog("""
                [{"name": "typo", "addresses": {"start": 0, "end": 4294967296},
                  "journalOffsets": {"start": 0, "end": 0}}]
                """));
        assertTrue(tooWide.getMessage().contains("out of range of UInt"), tooWide.getMessage());

        JsonMappingException negative = assertThrows(JsonMappingException.class, () -> readCatalog("""
                [{"name": "typo", "addresses": {"start": 0, "end": 0},
                  "journalOffsets": {"start": -1, "end": 0}}]
                """));
        assertTrue(negative.getMessage().contains("out of range of ULong"), negative.getMessage());

        MissingKotlinParameterException open = assertThrows(MissingKotlinParameterException.class,
                () -> readCatalog("""
                        [{"name": "open", "addresses": {"start": 167772160},
                          "journalOffsets": {"start": 0, "end": 0}}]
                        """));
        assertEquals("endInclusive", open.getParameter().getName());
    }

    private static List<AddressPool> readCatalog(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<AddressPool>>() {
        });
    }

    private static AddressPool find(List<AddressPool> pools, String name) {
        return pools.stream().filter(pool -> pool.name.equals(name)).findFirst().orElseThrow();
    }

    private static String describe(AddressPool pool) {
        ClosedRange<UInt> addresses = pool.addresses;
        return pool.name + " " + dotted(addresses.getStart()) + " - " + dotted(addresses.getEndInclusive());
    }

    private static String dotted(UInt address) {
        long value = Long.parseLong(address.toString());
        return (value >>> 24) + "." + ((value >>> 16) & 0xFF) + "." + ((value >>> 8) & 0xFF) + "." + (value & 0xFF);
    }
}
