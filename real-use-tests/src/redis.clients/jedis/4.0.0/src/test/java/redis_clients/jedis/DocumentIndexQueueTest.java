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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

/// Exercises Jedis list-backed work queues to dispatch document indexing jobs with explicit acknowledgement and retry.
public class DocumentIndexQueueTest {
    @Test
    @Timeout(60)
    public void dispatchesOldestJobsFirstAndRequeuesAnUnfinishedJob() throws Exception {
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
            try (Jedis publisher = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis worker = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String ready = "indexing:ready";
                String inFlight = "indexing:worker-a:in-flight";
                List<String> completed = new ArrayList<>();
                publisher.lpush(ready, "manual.pdf", "guide.pdf", "faq.pdf");

                // Reserving a job moves it atomically, retaining it until acknowledgement.
                String first = worker.brpoplpush(ready, inFlight, 10);
                assertThat(first).isEqualTo("manual.pdf");
                assertThat(publisher.lrange(ready, 0, -1)).containsExactly("faq.pdf", "guide.pdf");
                assertThat(publisher.lrange(inFlight, 0, -1)).containsExactly("manual.pdf");
                completed.add(first);
                assertThat(worker.lrem(inFlight, 1, first)).isEqualTo(1);
                assertThat(publisher.lrange(inFlight, 0, -1)).isEmpty();

                // A controlled processing failure leaves the reserved document available for retry.
                String unfinished = worker.brpoplpush(ready, inFlight, 10);
                assertThat(unfinished).isEqualTo("guide.pdf");
                assertThat(publisher.lrange(inFlight, 0, -1)).containsExactly("guide.pdf");
                assertThat(publisher.rpoplpush(inFlight, ready)).isEqualTo(unfinished);
                assertThat(publisher.lrange(inFlight, 0, -1)).isEmpty();
                assertThat(publisher.lrange(ready, 0, -1)).containsExactly("guide.pdf", "faq.pdf");

                // Requeued work goes behind existing work rather than starving the next document.
                for (String expected : List.of("faq.pdf", "guide.pdf")) {
                    String document = worker.brpoplpush(ready, inFlight, 10);
                    assertThat(document).isEqualTo(expected);
                    completed.add(document);
                    assertThat(worker.lrem(inFlight, 1, document)).isEqualTo(1);
                }
                assertThat(completed).containsExactly("manual.pdf", "faq.pdf", "guide.pdf");
                assertThat(publisher.llen(ready)).isZero();
                assertThat(publisher.llen(inFlight)).isZero();
                assertThat(worker.rpoplpush(ready, inFlight)).isNull();
            }
        } finally {
            server.stop();
        }
    }
}
