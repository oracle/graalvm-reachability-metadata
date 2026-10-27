/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.embedded.RedisServer;

/// Exercises Jedis pipelining to warm a catalog cache and assemble a batch of product cards.
public class CatalogCachePipelineTest {
    @Test
    @Timeout(60)
    public void batchesCatalogWritesAndReadsWhileIdentifyingCacheMisses() throws Exception {
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
            List<ProductCard> catalog = List.of(
                    new ProductCard("coffee", "Roasted coffee", 1250, 8),
                    new ProductCard("tea", "Green tea", 650, 0));
            try (Jedis warmer = new Jedis("127.0.0.1", port, 10000, 10000);
                    Pipeline writes = warmer.pipelined()) {
                for (ProductCard product : catalog) {
                    writes.hset("catalog:product:" + product.sku(), Map.of(
                            "name", product.name(), "priceCents", Integer.toString(product.priceCents())));
                    writes.set("catalog:stock:" + product.sku(), Integer.toString(product.available()));
                }
                writes.sync();
            }

            // A storefront fetches cards in request order with one pipeline flush.
            List<String> requested = List.of("tea", "cocoa", "coffee");
            List<PendingCard> pending = new ArrayList<>();
            List<ProductCard> cards = new ArrayList<>();
            List<String> cacheMisses = new ArrayList<>();
            try (Jedis storefront = new Jedis("127.0.0.1", port, 10000, 10000);
                    Pipeline reads = storefront.pipelined()) {
                for (String sku : requested) {
                    pending.add(new PendingCard(sku,
                            reads.hgetAll("catalog:product:" + sku),
                            reads.get("catalog:stock:" + sku)));
                }
                reads.sync();
                for (PendingCard request : pending) {
                    Map<String, String> details = request.details().get();
                    String available = request.stock().get();
                    if (details.isEmpty()) {
                        cacheMisses.add(request.sku());
                        assertThat(available).isNull();
                    } else {
                        cards.add(new ProductCard(request.sku(), details.get("name"),
                                Integer.parseInt(details.get("priceCents")), Integer.parseInt(available)));
                    }
                }

                assertThat(cards).containsExactly(catalog.get(1), catalog.get(0));
                assertThat(cacheMisses).containsExactly("cocoa");
                assertThat(cards.getFirst().available()).isZero();
                assertThat(cards.getLast().available()).isEqualTo(8);

                // The connection remains usable after consuming the complete batch, including misses.
                assertThat(storefront.hgetAll("catalog:product:coffee"))
                        .isEqualTo(Map.of("name", "Roasted coffee", "priceCents", "1250"));
            }
        } finally {
            server.stop();
        }
    }

    private record ProductCard(String sku, String name, int priceCents, int available) {
    }

    private record PendingCard(String sku, Response<Map<String, String>> details, Response<String> stock) {
    }
}
