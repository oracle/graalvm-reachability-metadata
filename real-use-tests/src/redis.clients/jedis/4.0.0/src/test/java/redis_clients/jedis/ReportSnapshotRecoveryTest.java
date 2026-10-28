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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.RestoreParams;
import redis.embedded.RedisServer;

/// Exercises Jedis DUMP/RESTORE snapshots to recover a binary report and its metadata after an interrupted replacement.
public class ReportSnapshotRecoveryTest {
    @Test
    @Timeout(60)
    public void restoresTheSavedReportWithoutRollingBackOtherReports() throws Exception {
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
            byte[] bodyKey = "reports:quarterly:body".getBytes(StandardCharsets.UTF_8);
            String metadataKey = "reports:quarterly:metadata";
            String otherReport = "reports:weekly:body";
            byte[] originalBody = new byte[] {37, 80, 68, 70, 0, 10, -1, -128, 13, 0, 42};
            Map<String, String> originalMetadata = Map.of(
                    "filename", "quarterly.pdf", "revision", "7", "owner", "finance");
            byte[] bodySnapshot;
            byte[] metadataSnapshot;
            try (Jedis publisher = new Jedis("127.0.0.1", port, 10000, 10000)) {
                publisher.set(bodyKey, originalBody);
                publisher.hset(metadataKey, originalMetadata);
                publisher.set(otherReport, "weekly-revision-1");

                // The replacement job saves opaque Redis snapshots before changing either key.
                bodySnapshot = publisher.dump(bodyKey);
                metadataSnapshot = publisher.dump(metadataKey);
                assertThat(bodySnapshot).isNotEmpty();
                assertThat(metadataSnapshot).isNotEmpty();

                publisher.del(bodyKey);
                publisher.del(metadataKey);
                publisher.set(metadataKey, "replacement-in-progress");
                publisher.set(otherReport, "weekly-revision-2");
            }

            // A recovery worker reconnects after the interrupted job has closed its connection.
            try (Jedis recovery = new Jedis("127.0.0.1", port, 10000, 10000)) {
                assertThat(recovery.get(bodyKey)).isNull();
                assertThat(recovery.restore(bodyKey, 0, bodySnapshot)).isEqualTo("OK");

                // Restoration is non-destructive unless the operator explicitly requests replacement.
                assertThatThrownBy(() -> recovery.restore(metadataKey, 0, metadataSnapshot))
                        .isInstanceOf(JedisDataException.class)
                        .hasMessageContaining("BUSYKEY");
                assertThat(recovery.get(metadataKey)).isEqualTo("replacement-in-progress");
                assertThat(recovery.restore(metadataKey, 0, metadataSnapshot, new RestoreParams().replace()))
                        .isEqualTo("OK");
            }

            try (Jedis reader = new Jedis("127.0.0.1", port, 10000, 10000)) {
                assertThat(reader.get(bodyKey)).containsExactly(originalBody);
                assertThat(reader.hgetAll(metadataKey)).isEqualTo(originalMetadata);
                assertThat(reader.get(otherReport)).isEqualTo("weekly-revision-2");
                assertThat(reader.pttl(bodyKey)).isEqualTo(-1);
                assertThat(reader.pttl(metadataKey)).isEqualTo(-1);
            }
        } finally {
            server.stop();
        }
    }
}
