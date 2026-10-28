/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

/// Exercises Jedis HyperLogLog analytics to deduplicate daily visitors and rebuild a weekly reach summary.
public class UniqueVisitorAnalyticsTest {
    @Test
    @Timeout(60)
    public void mergesDailyReachWithoutDoubleCountingRepeatVisitsAndIncludesLateEvents() throws Exception {
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
            try (Jedis collector = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis dashboard = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String monday = "analytics:shop:visitors:monday";
                String tuesday = "analytics:shop:visitors:tuesday";
                String weekly = "analytics:shop:visitors:weekly";
                collector.pfadd(monday, "visitor-alice", "visitor-bob", "visitor-cara");
                collector.pfadd(tuesday, "visitor-bob", "visitor-cara", "visitor-dan");

                // HyperLogLog estimates are exact for this small, fixed visitor fixture.
                assertThat(dashboard.pfcount(monday)).isEqualTo(3);
                assertThat(dashboard.pfcount(tuesday)).isEqualTo(3);
                assertThat(dashboard.pfcount(monday, tuesday)).isEqualTo(4);

                // Replayed page views do not increase reach, even across collection batches.
                collector.pfadd(monday, "visitor-alice", "visitor-alice", "visitor-bob");
                collector.pfadd(tuesday, "visitor-bob", "visitor-dan");
                assertThat(dashboard.pfcount(monday, tuesday)).isEqualTo(4);
                assertThat(dashboard.pfmerge(weekly, monday, tuesday)).isEqualTo("OK");
                assertThat(dashboard.pfcount(weekly)).isEqualTo(4);

                // A late event updates its daily sketch, not the previously materialized report.
                collector.pfadd(tuesday, "visitor-erin", "visitor-erin");
                assertThat(dashboard.pfcount(tuesday)).isEqualTo(4);
                assertThat(dashboard.pfcount(weekly)).isEqualTo(4);
                assertThat(dashboard.pfcount(monday, tuesday)).isEqualTo(5);

                // Refreshing the weekly report is idempotent and leaves daily inputs intact.
                dashboard.pfmerge(weekly, monday, tuesday);
                assertThat(collector.pfcount(weekly)).isEqualTo(5);
                dashboard.pfmerge(weekly, monday, tuesday);
                assertThat(collector.pfcount(weekly)).isEqualTo(5);
                assertThat(collector.pfcount(monday)).isEqualTo(3);
                assertThat(collector.pfcount(tuesday)).isEqualTo(4);
            }
        } finally {
            server.stop();
        }
    }
}
