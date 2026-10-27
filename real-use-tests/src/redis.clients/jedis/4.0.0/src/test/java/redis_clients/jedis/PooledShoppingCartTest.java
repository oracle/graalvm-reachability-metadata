/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.embedded.RedisServer;

/// Exercises Jedis connection pooling to serve authenticated shopping-cart requests in a dedicated Redis database.
public class PooledShoppingCartTest {
    @Test
    @Timeout(60)
    public void sharesCartsAcrossRequestsAndReturnsTheConnectionAfterARejectedUpdate() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String password = "cart-service-secret";
        RedisServer server = RedisServer.newRedisServer()
                .bind("127.0.0.1")
                .port(port)
                .setting("requirepass " + password)
                .setting("save \"\"")
                .setting("appendonly no")
                .build();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(1);
        poolConfig.setMaxIdle(1);
        poolConfig.setMaxWait(Duration.ofSeconds(10));
        poolConfig.setTestOnBorrow(true);
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(10000)
                .socketTimeoutMillis(10000)
                .password(password)
                .database(2)
                .build();
        try {
            server.start();
            try (JedisPool pool = new JedisPool(poolConfig, new HostAndPort("127.0.0.1", port), clientConfig)) {
                CartRepository storefront = new CartRepository(pool);
                CartRepository checkout = new CartRepository(pool);
                assertThat(storefront.addItem("alice", "coffee", 2)).isEqualTo(2);
                assertThat(storefront.addItem("alice", "tea", 1)).isEqualTo(1);
                assertThat(checkout.addItem("alice", "coffee", 1)).isEqualTo(3);
                assertThat(checkout.read("alice")).isEqualTo(Map.of("coffee", "3", "tea", "1"));

                checkout.complete("alice");
                assertThatThrownBy(() -> storefront.addItem("alice", "cocoa", 1))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Cart already checked out");

                // With only one pooled connection, these requests also prove the rejected request returned it.
                assertThat(storefront.read("alice"))
                        .isEqualTo(Map.of("coffee", "3", "tea", "1", "status", "checked-out"));
                assertThat(storefront.addItem("bob", "cocoa", 4)).isEqualTo(4);
                assertThat(checkout.read("bob")).isEqualTo(Map.of("cocoa", "4"));

                // An independent client verifies the pool's authentication and database selection.
                try (Jedis audit = new Jedis("127.0.0.1", port, 10000, 10000)) {
                    audit.auth(password);
                    assertThat(audit.exists("cart:alice")).isFalse();
                    audit.select(2);
                    assertThat(audit.hgetAll("cart:alice"))
                            .isEqualTo(Map.of("coffee", "3", "tea", "1", "status", "checked-out"));
                    assertThat(audit.hgetAll("cart:bob")).isEqualTo(Map.of("cocoa", "4"));
                }
            }
        } finally {
            server.stop();
        }
    }

    private record CartRepository(JedisPool pool) {
        long addItem(String customer, String sku, int quantity) {
            try (Jedis connection = pool.getResource()) {
                String key = "cart:" + customer;
                if ("checked-out".equals(connection.hget(key, "status"))) {
                    throw new IllegalStateException("Cart already checked out");
                }
                return connection.hincrBy(key, sku, quantity);
            }
        }

        void complete(String customer) {
            try (Jedis connection = pool.getResource()) {
                connection.hset("cart:" + customer, "status", "checked-out");
            }
        }

        Map<String, String> read(String customer) {
            try (Jedis connection = pool.getResource()) {
                return connection.hgetAll("cart:" + customer);
            }
        }
    }
}
