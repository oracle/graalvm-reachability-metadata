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

/// Exercises Jedis set algebra to select a deduplicated campaign audience while respecting marketing consent.
public class CampaignAudienceSetTest {
    @Test
    @Timeout(60)
    public void rebuildsCampaignRecipientsAfterConsentAndSuppressionChanges() throws Exception {
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
            try (Jedis preferences = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis campaign = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String coffeeFans = "interests:coffee";
                String teaFans = "interests:tea";
                String consent = "marketing:consent";
                String suppressed = "marketing:suppressed";
                String interested = "campaign:spring:interested";
                String eligible = "campaign:spring:eligible";
                String recipients = "campaign:spring:recipients";
                preferences.sadd(coffeeFans, "alice", "bob", "cara");
                preferences.sadd(teaFans, "bob", "dan", "erin");
                preferences.sadd(consent, "alice", "bob", "dan", "erin", "frank");
                preferences.sadd(suppressed, "erin");

                // Replayed preference events and overlapping interests must not duplicate mailings.
                assertThat(preferences.sadd(coffeeFans, "bob")).isZero();
                assertThat(campaign.sunionstore(interested, coffeeFans, teaFans)).isEqualTo(5);
                assertThat(campaign.sinterstore(eligible, interested, consent)).isEqualTo(4);
                assertThat(campaign.sdiffstore(recipients, eligible, suppressed)).isEqualTo(3);
                assertThat(campaign.smembers(recipients)).containsExactlyInAnyOrder("alice", "bob", "dan");
                assertThat(campaign.sismember(recipients, "cara")).isFalse();
                assertThat(campaign.sismember(recipients, "erin")).isFalse();
                assertThat(campaign.sismember(recipients, "frank")).isFalse();

                // Before dispatch, rebuild from current consent and suppression records.
                preferences.srem(consent, "bob");
                preferences.sadd(consent, "cara");
                preferences.srem(suppressed, "erin");
                campaign.sinterstore(eligible, interested, consent);
                assertThat(campaign.sdiffstore(recipients, eligible, suppressed)).isEqualTo(4);
                assertThat(preferences.smembers(recipients))
                        .containsExactlyInAnyOrder("alice", "cara", "dan", "erin");
                assertThat(preferences.sismember(recipients, "bob")).isFalse();
                assertThat(preferences.smembers(coffeeFans)).containsExactlyInAnyOrder("alice", "bob", "cara");

                // Suspending the campaign's entire audience must clear the previous recipient snapshot.
                preferences.sadd(suppressed, "alice", "cara", "dan", "erin");
                assertThat(campaign.sdiffstore(recipients, eligible, suppressed)).isZero();
                assertThat(preferences.smembers(recipients)).isEmpty();
            }
        } finally {
            server.stop();
        }
    }
}
