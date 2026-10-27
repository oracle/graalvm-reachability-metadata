/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.embedded.RedisServer;

/// Exercises Jedis cursor-based keyspace scanning to retire one tenant's obsolete catalog cache generation.
public class RetiredCatalogCacheScanTest {
    @Test
    @Timeout(60)
    public void removesRetiredProductsWithoutEvictingCurrentCatalogsOrSessions() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        RedisServer server = RedisServer.newRedisServer()
                .bind("127.0.0.1")
                .port(port)
                .setting("save \"\"")
                .setting("appendonly no")
                .build();
        try {
            server.start();
            try (Jedis storefront = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis maintenance = new Jedis("127.0.0.1", port, 10000, 10000)) {
                Set<String> obsolete = new HashSet<>();
                for (int product = 0; product < 40; product++) {
                    String key = "cache:tenant-a:catalog:v1:product-" + product;
                    storefront.set(key, "retired-card-" + product);
                    obsolete.add(key);
                }
                Map<String, String> retained = Map.of(
                        "cache:tenant-a:catalog:v2:product-1", "current-card",
                        "cache:tenant-a:catalog:v10:product-1", "preview-card",
                        "cache:tenant-b:catalog:v1:product-1", "other-tenant-card",
                        "session:tenant-a:customer-42", "signed-in");
                retained.forEach((key, value) -> storefront.set(key, value));

                // A full cursor iteration is required even when a MATCH-filtered page is empty.
                Set<String> discovered = findGeneration(maintenance, "tenant-a", "v1");
                assertThat(discovered).containsExactlyInAnyOrderElementsOf(obsolete);
                assertThat(maintenance.unlink(discovered.toArray(String[]::new))).isEqualTo(obsolete.size());

                // Observe invalidation through another connection, not allocator or reclamation timing.
                for (String key : obsolete) {
                    assertThat(storefront.get(key)).isNull();
                }
                for (Map.Entry<String, String> entry : retained.entrySet()) {
                    assertThat(storefront.get(entry.getKey())).isEqualTo(entry.getValue());
                }
                assertThat(findGeneration(maintenance, "tenant-a", "v1")).isEmpty();
                assertThat(storefront.dbSize()).isEqualTo(retained.size());

                // A later catalog warm-up remains available after the retirement job completes.
                storefront.set("cache:tenant-a:catalog:v2:product-2", "new-card");
                assertThat(maintenance.get("cache:tenant-a:catalog:v2:product-2")).isEqualTo("new-card");
            }
        } finally {
            server.stop();
        }
    }

    private static Set<String> findGeneration(Jedis cache, String tenant, String generation) {
        ScanParams parameters = new ScanParams()
                .match("cache:" + tenant + ":catalog:" + generation + ":*")
                .count(3);
        Set<String> keys = new HashSet<>();
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = cache.scan(cursor, parameters);
            // SCAN may repeat keys; collect before deletion to keep the scanned keyspace stable.
            keys.addAll(page.getResult());
            cursor = page.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        return keys;
    }
}
