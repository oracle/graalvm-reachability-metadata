/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises seeded one-shot XXHash64 rendezvous hashing for customer-to-shard routing.
/// Removing a shard moves only its customers, and restoring it recovers their original placement.
public class CustomerShardRoutingTest {
    @Test
    @Timeout(60)
    public void preservesCustomerPlacementAcrossShardMaintenanceAndRouterRestart() {
        List<String> allShards = List.of("orders-east", "orders-central", "orders-west");
        CustomerRouter original = new CustomerRouter(allShards);
        Map<String, String> assignments = new HashMap<>();
        for (int customer = 0; customer < 256; customer++) {
            String customerId = "customer-" + customer;
            assignments.put(customerId, original.shardFor(customerId));
        }
        assertEquals(new HashSet<>(allShards), new HashSet<>(assignments.values()),
                "Customer traffic must reach every configured shard");

        // Service discovery may return the same nodes in a different order after a restart.
        CustomerRouter restarted = new CustomerRouter(
                List.of("orders-west", "orders-east", "orders-central"));
        CustomerRouter maintenance = new CustomerRouter(List.of("orders-east", "orders-west"));
        CustomerRouter restored = new CustomerRouter(allShards);
        Set<String> reassignedCustomers = new HashSet<>();
        Set<String> replacementShards = new HashSet<>();
        for (Map.Entry<String, String> assignment : assignments.entrySet()) {
            String customerId = assignment.getKey();
            String previousShard = assignment.getValue();
            assertEquals(previousShard, restarted.shardFor(customerId),
                    "A router restart must not move a customer's order data");
            String duringMaintenance = maintenance.shardFor(customerId);
            assertNotEquals("orders-central", duringMaintenance,
                    "No request may be routed to the shard under maintenance");
            if (previousShard.equals("orders-central")) {
                reassignedCustomers.add(customerId);
                replacementShards.add(duringMaintenance);
            } else {
                assertEquals(previousShard, duringMaintenance,
                        "Customers on healthy shards must not move during maintenance");
            }
            assertEquals(previousShard, restored.shardFor(customerId),
                    "Restoring the shard must restore the original customer placement");
        }
        assertTrue(!reassignedCustomers.isEmpty(), "Maintenance must exercise customer failover");
        assertTrue(reassignedCustomers.size() < assignments.size(),
                "Maintenance must not move every customer's data");
        assertEquals(Set.of("orders-east", "orders-west"), replacementShards,
                "Both surviving shards must accept customers from the removed shard");
    }

    private static final class CustomerRouter {
        private static final long ROUTING_SEED = 0x43A19B27L;
        private final XXHash64 hash = XXHashFactory.fastestInstance().hash64();
        private final List<String> shards;

        private CustomerRouter(List<String> shards) {
            if (shards.isEmpty()) {
                throw new IllegalArgumentException("At least one shard is required");
            }
            this.shards = List.copyOf(shards);
        }

        private String shardFor(String customerId) {
            String selected = null;
            long highestScore = 0;
            for (String shard : shards) {
                // A delimiter separates customer and shard names in the routing namespace.
                byte[] key = (customerId + '\0' + shard).getBytes(UTF_8);
                long score = hash.hash(key, 0, key.length, ROUTING_SEED);
                if (selected == null || Long.compareUnsigned(score, highestScore) > 0
                        || (score == highestScore && shard.compareTo(selected) < 0)) {
                    selected = shard;
                    highestScore = score;
                }
            }
            return selected;
        }
    }
}
