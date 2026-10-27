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
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XClaimParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamPendingEntry;
import redis.embedded.RedisServer;

/// Exercises Jedis Streams consumer groups to recover an unacknowledged fulfillment order.
public class OrderStreamRecoveryTest {
    @Test
    @Timeout(60)
    public void replacementWorkerClaimsAndCompletesAnInterruptedOrder() throws Exception {
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
            try (Jedis producer = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String stream = "fulfillment:orders";
                String group = "shipping";
                String completed = "fulfillment:completed";
                Map<String, String> firstOrder = Map.of("orderId", "order-201", "sku", "coffee");
                Map<String, String> secondOrder = Map.of("orderId", "order-202", "sku", "tea");
                producer.xgroupCreate(stream, group, new StreamEntryID("0-0"), true);
                StreamEntryID firstId = producer.xadd(stream, new StreamEntryID("1-0"), firstOrder);
                StreamEntryID secondId = producer.xadd(stream, new StreamEntryID("2-0"), secondOrder);
                Map<String, StreamEntryID> unread = Map.of(stream, StreamEntryID.UNRECEIVED_ENTRY);
                XReadGroupParams oneOrder = XReadGroupParams.xReadGroupParams().count(1);

                // The original worker receives an order, then shuts down before acknowledging it.
                try (Jedis originalWorker = new Jedis("127.0.0.1", port, 10000, 10000)) {
                    List<Map.Entry<String, List<StreamEntry>>> delivery = originalWorker.xreadGroup(
                            group, "worker-a", oneOrder, unread);
                    assertThat(delivery).hasSize(1);
                    assertThat(delivery.getFirst().getKey()).isEqualTo(stream);
                    assertThat(delivery.getFirst().getValue()).hasSize(1);
                    StreamEntry order = delivery.getFirst().getValue().getFirst();
                    assertThat(order.getID()).isEqualTo(firstId);
                    assertThat(order.getFields()).isEqualTo(firstOrder);
                }
                assertThat(producer.xpending(stream, group).getTotal()).isEqualTo(1);
                assertThat(producer.hgetAll(completed)).isEmpty();

                try (Jedis replacement = new Jedis("127.0.0.1", port, 10000, 10000)) {
                    // Shutdown is known, so claim immediately instead of relying on elapsed time.
                    List<StreamEntry> recovered = replacement.xclaim(stream, group, "worker-b", 0,
                            XClaimParams.xClaimParams(), firstId);
                    assertThat(recovered).hasSize(1);
                    StreamEntry retriedOrder = recovered.getFirst();
                    assertThat(retriedOrder.getID()).isEqualTo(firstId);
                    assertThat(retriedOrder.getFields()).isEqualTo(firstOrder);
                    List<StreamPendingEntry> pending = replacement.xpending(
                            stream, group, firstId, firstId, 10, "worker-b");
                    assertThat(pending).hasSize(1);
                    assertThat(pending.getFirst().getConsumerName()).isEqualTo("worker-b");
                    assertThat(pending.getFirst().getDeliveredTimes()).isEqualTo(2);

                    replacement.hset(completed, retriedOrder.getFields().get("orderId"), "shipped");
                    assertThat(replacement.xack(stream, group, retriedOrder.getID())).isEqualTo(1);

                    // New-message reads continue from the group cursor, not the recovered entry.
                    List<Map.Entry<String, List<StreamEntry>>> next = replacement.xreadGroup(
                            group, "worker-b", oneOrder, unread);
                    assertThat(next).hasSize(1);
                    assertThat(next.getFirst().getValue()).hasSize(1);
                    StreamEntry nextOrder = next.getFirst().getValue().getFirst();
                    assertThat(nextOrder.getID()).isEqualTo(secondId);
                    assertThat(nextOrder.getFields()).isEqualTo(secondOrder);
                    replacement.hset(completed, nextOrder.getFields().get("orderId"), "shipped");
                    assertThat(replacement.xack(stream, group, nextOrder.getID())).isEqualTo(1);
                    assertThat(replacement.xreadGroup(group, "worker-b", oneOrder, unread)).isNullOrEmpty();
                }

                assertThat(producer.xpending(stream, group, firstId, secondId, 10, "worker-b")).isEmpty();
                assertThat(producer.hgetAll(completed))
                        .isEqualTo(Map.of("order-201", "shipped", "order-202", "shipped"));
                assertThat(producer.xlen(stream)).isEqualTo(2);
            }
        } finally {
            server.stop();
        }
    }
}
