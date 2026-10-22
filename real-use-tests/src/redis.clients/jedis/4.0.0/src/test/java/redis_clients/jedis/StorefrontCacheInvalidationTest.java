/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.embedded.RedisServer;

/// Exercises Jedis Pub/Sub to invalidate storefront cache entries when product prices change.
public class StorefrontCacheInvalidationTest {
    @Test
    @Timeout(60)
    public void evictsChangedProductsAndReloadsTheirCurrentPrices() throws Exception {
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
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            server.start();
            try (Jedis subscriber = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis catalog = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String prices = "catalog:prices";
                String channel = "catalog:price-changes";
                catalog.hset(prices, Map.of("coffee", "1250", "tea", "650", "cocoa", "850"));
                Map<String, String> localCache = new ConcurrentHashMap<>(catalog.hgetAll(prices));
                CountDownLatch subscribed = new CountDownLatch(1);
                CountDownLatch invalidated = new CountDownLatch(2);
                JedisPubSub invalidator = new JedisPubSub() {
                    @Override
                    public void onSubscribe(String subscribedChannel, int subscribedChannels) {
                        subscribed.countDown();
                    }

                    @Override
                    public void onMessage(String messageChannel, String product) {
                        localCache.remove(product);
                        invalidated.countDown();
                    }
                };
                Future<?> subscription = worker.submit(() -> subscriber.subscribe(invalidator, channel));

                // The acknowledgement ensures neither price-change event is published too early.
                assertThat(subscribed.await(10, TimeUnit.SECONDS)).isTrue();
                catalog.hset(prices, "coffee", "1400");
                assertThat(catalog.publish(channel, "coffee")).isEqualTo(1);
                catalog.hset(prices, "tea", "700");
                assertThat(catalog.publish(channel, "tea")).isEqualTo(1);
                assertThat(invalidated.await(10, TimeUnit.SECONDS)).isTrue();

                invalidator.unsubscribe();
                subscription.get(10, TimeUnit.SECONDS);
                assertThat(localCache).containsExactlyInAnyOrderEntriesOf(Map.of("cocoa", "850"));

                // A subsequent storefront request reloads only the invalidated products.
                String coffeePrice = localCache.computeIfAbsent("coffee", product -> catalog.hget(prices, product));
                String teaPrice = localCache.computeIfAbsent("tea", product -> catalog.hget(prices, product));
                assertThat(coffeePrice).isEqualTo("1400");
                assertThat(teaPrice).isEqualTo("700");
                assertThat(localCache).containsExactlyInAnyOrderEntriesOf(
                        Map.of("coffee", "1400", "tea", "700", "cocoa", "850"));
                assertThat(catalog.publish(channel, "coffee")).isZero();
            }
        } finally {
            try {
                server.stop();
            } finally {
                worker.shutdownNow();
                assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }
}
