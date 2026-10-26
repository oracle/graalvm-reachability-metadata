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
import redis.embedded.RedisServer;

/// Exercises Jedis sorted sets to maintain a tournament leaderboard with live scores and disqualifications.
public class TournamentLeaderboardTest {
    @Test
    @Timeout(60)
    public void publishesRankedStandingsAfterScoreUpdatesAndDisqualification() throws Exception {
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
            try (Jedis scoring = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis scoreboard = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String tournament = "tournament:summer:standings";
                scoring.zadd(tournament, Map.of("alice", 100.0, "bob", 80.0, "cara", 60.0, "dan", 40.0));
                assertThat(standings(scoreboard, tournament, 0, 2)).containsExactly(
                        new Standing("alice", 100), new Standing("bob", 80), new Standing("cara", 60));

                // Match results accumulate rather than replacing a player's earlier points.
                assertThat(scoring.zincrby(tournament, 60, "cara")).isEqualTo(120);
                assertThat(scoring.zincrby(tournament, 10, "bob")).isEqualTo(90);
                assertThat(standings(scoreboard, tournament, 0, 2)).containsExactly(
                        new Standing("cara", 120), new Standing("alice", 100), new Standing("bob", 90));
                assertThat(scoreboard.zrevrank(tournament, "cara") + 1).isEqualTo(1);
                assertThat(scoreboard.zrevrank(tournament, "dan") + 1).isEqualTo(4);

                // Removing a disqualified player immediately promotes the remaining contestants.
                assertThat(scoring.zrem(tournament, "alice")).isEqualTo(1);
                assertThat(standings(scoreboard, tournament, 0, 0)).containsExactly(new Standing("cara", 120));
                assertThat(standings(scoreboard, tournament, 1, 2)).containsExactly(
                        new Standing("bob", 90), new Standing("dan", 40));
                assertThat(scoreboard.zrevrank(tournament, "dan") + 1).isEqualTo(3);
                assertThat(scoreboard.zrevrank(tournament, "alice")).isNull();
                assertThat(scoreboard.zscore(tournament, "alice")).isNull();
                assertThat(scoreboard.zcard(tournament)).isEqualTo(3);
            }
        } finally {
            server.stop();
        }
    }

    private static List<Standing> standings(Jedis client, String key, long first, long last) {
        return client.zrevrangeWithScores(key, first, last).stream()
                .map(entry -> new Standing(entry.getElement(), entry.getScore()))
                .toList();
    }

    private record Standing(String player, double points) {
    }
}
