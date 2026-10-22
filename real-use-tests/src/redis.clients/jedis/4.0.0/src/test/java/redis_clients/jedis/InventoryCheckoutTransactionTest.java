/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.embedded.RedisServer;

/// Exercises Jedis optimistic transactions to reserve inventory and publish a checkout atomically.
public class InventoryCheckoutTransactionTest {
    @Test
    @Timeout(60)
    public void retriesCheckoutAfterAConcurrentInventoryUpdate() throws Exception {
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
            try (Jedis checkout = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis warehouse = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String stockKey = "inventory:coffee:available";
                String orderKey = "orders:order-104";
                String pendingOrders = "orders:pending";
                Map<String, String> order = Map.of("sku", "coffee", "quantity", "3", "status", "reserved");
                checkout.set(stockKey, "10");

                // A warehouse correction arrives after checkout reads the available stock.
                checkout.watch(stockKey);
                assertThat(Integer.parseInt(checkout.get(stockKey))).isGreaterThanOrEqualTo(3);
                assertThat(warehouse.decrBy(stockKey, 2)).isEqualTo(8);
                try (Transaction reservation = checkout.multi()) {
                    reservation.decrBy(stockKey, 3);
                    reservation.hset(orderKey, order);
                    reservation.lpush(pendingOrders, "order-104");
                    assertThat(reservation.exec()).isNull();
                }

                // An aborted checkout must neither consume stock nor publish a partial order.
                assertThat(warehouse.get(stockKey)).isEqualTo("8");
                assertThat(warehouse.exists(orderKey)).isFalse();
                assertThat(warehouse.lrange(pendingOrders, 0, -1)).isEmpty();

                // Retry from a fresh stock read, then commit all checkout effects together.
                checkout.watch(stockKey);
                assertThat(Integer.parseInt(checkout.get(stockKey))).isGreaterThanOrEqualTo(3);
                try (Transaction reservation = checkout.multi()) {
                    Response<Long> remaining = reservation.decrBy(stockKey, 3);
                    reservation.hset(orderKey, order);
                    Response<Long> queued = reservation.lpush(pendingOrders, "order-104");
                    List<Object> committed = reservation.exec();
                    assertThat(committed).containsExactly(5L, 3L, 1L);
                    assertThat(remaining.get()).isEqualTo(5);
                    assertThat(queued.get()).isEqualTo(1);
                }

                // A different connection observes the complete reservation.
                assertThat(warehouse.get(stockKey)).isEqualTo("5");
                assertThat(warehouse.hgetAll(orderKey)).isEqualTo(order);
                assertThat(warehouse.lrange(pendingOrders, 0, -1)).containsExactly("order-104");
            }
        } finally {
            server.stop();
        }
    }
}
