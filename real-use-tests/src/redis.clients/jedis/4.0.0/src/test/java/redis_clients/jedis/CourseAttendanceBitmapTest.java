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
import redis.clients.jedis.args.BitOP;
import redis.embedded.RedisServer;

/// Exercises Jedis bitmaps to track course attendance and identify students eligible for completion certificates.
public class CourseAttendanceBitmapTest {
    @Test
    @Timeout(60)
    public void countsDistinctAttendeesAndRecomputesCompletionAfterAnAttendanceCorrection() throws Exception {
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
            try (Jedis attendance = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis reporting = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String firstModule = "course:redis:module-1:attendance";
                String secondModule = "course:redis:module-2:attendance";
                String completed = "course:redis:completed";
                String reached = "course:redis:reached";
                for (long student : new long[] {7, 42, 1001}) {
                    attendance.setbit(firstModule, student, true);
                }
                for (long student : new long[] {42, 1001, 2048}) {
                    attendance.setbit(secondModule, student, true);
                }

                // Redelivery of an attendance event does not count the same student twice.
                assertThat(attendance.setbit(secondModule, 42, true)).isTrue();
                assertThat(reporting.bitcount(firstModule)).isEqualTo(3);
                assertThat(reporting.bitcount(secondModule)).isEqualTo(3);

                // Both modules are required for a certificate; either module counts toward reach.
                reporting.bitop(BitOP.AND, completed, firstModule, secondModule);
                reporting.bitop(BitOP.OR, reached, firstModule, secondModule);
                assertThat(reporting.bitcount(completed)).isEqualTo(2);
                assertThat(reporting.bitcount(reached)).isEqualTo(4);
                assertThat(reporting.getbit(completed, 42)).isTrue();
                assertThat(reporting.getbit(completed, 1001)).isTrue();
                assertThat(reporting.getbit(completed, 7)).isFalse();
                assertThat(reporting.getbit(completed, 2048)).isFalse();
                assertThat(reporting.getbit(reached, 9999)).isFalse();

                // Correct an erroneous check-in and overwrite the materialized eligibility bitmap.
                assertThat(attendance.setbit(secondModule, 1001, false)).isTrue();
                reporting.bitop(BitOP.AND, completed, firstModule, secondModule);
                reporting.bitop(BitOP.OR, reached, firstModule, secondModule);
                assertThat(reporting.bitcount(secondModule)).isEqualTo(2);
                assertThat(reporting.bitcount(completed)).isEqualTo(1);
                assertThat(reporting.getbit(completed, 42)).isTrue();
                assertThat(reporting.getbit(completed, 1001)).isFalse();
                assertThat(reporting.getbit(firstModule, 1001)).isTrue();
                assertThat(reporting.getbit(reached, 1001)).isTrue();
                assertThat(reporting.bitcount(reached)).isEqualTo(4);
            }
        } finally {
            server.stop();
        }
    }
}
