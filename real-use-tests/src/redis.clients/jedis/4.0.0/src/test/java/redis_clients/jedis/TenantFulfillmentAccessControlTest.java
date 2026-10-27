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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.embedded.RedisServer;

/// Exercises Jedis access control to isolate a tenant's fulfillment worker and revoke its write permission live.
public class TenantFulfillmentAccessControlTest {
    @Test
    @Timeout(60)
    public void restrictsShipmentUpdatesToTheTenantAndHonorsAnOperatorWriteFreeze() throws Exception {
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
            try (Jedis operator = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis worker = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String user = "acme-fulfillment";
                String password = "shipment-worker-secret";
                String shipment = "shipments:acme:order-301";
                String otherTenant = "shipments:other:order-302";
                Map<String, String> packed = Map.of("status", "packed", "tracking", "parcel-301");
                Map<String, String> otherShipment = Map.of("status", "queued", "tracking", "parcel-302");
                operator.hset(otherTenant, otherShipment);
                assertThat(operator.aclSetUser(user, "reset", "on", ">" + password,
                        "~shipments:acme:*", "+hset", "+hgetall")).isEqualTo("OK");

                // The worker uses its own credentials, never the operator's unrestricted connection.
                assertThat(worker.auth(user, password)).isEqualTo("OK");
                assertThat(worker.hset(shipment, packed)).isEqualTo(2);
                assertThat(worker.hgetAll(shipment)).isEqualTo(packed);
                assertThatThrownBy(() -> worker.hgetAll(otherTenant))
                        .isInstanceOf(JedisDataException.class)
                        .hasMessageContaining("NOPERM");
                assertThatThrownBy(() -> worker.hset(otherTenant, "status", "shipped"))
                        .isInstanceOf(JedisDataException.class)
                        .hasMessageContaining("NOPERM");
                assertThatThrownBy(() -> worker.flushDB())
                        .isInstanceOf(JedisDataException.class)
                        .hasMessageContaining("NOPERM");
                assertThat(operator.hgetAll(otherTenant)).isEqualTo(otherShipment);
                assertThat(operator.hgetAll(shipment)).isEqualTo(packed);

                // A write freeze affects the already authenticated connection without a reconnect.
                assertThat(operator.aclSetUser(user, "-hset")).isEqualTo("OK");
                assertThatThrownBy(() -> worker.hset(shipment, "status", "shipped"))
                        .isInstanceOf(JedisDataException.class)
                        .hasMessageContaining("NOPERM");
                assertThat(worker.hgetAll(shipment)).isEqualTo(packed);
                assertThat(operator.hgetAll(shipment)).isEqualTo(packed);

                // Once operations resume, the same worker can publish the shipment's final status.
                assertThat(operator.aclSetUser(user, "+hset")).isEqualTo("OK");
                assertThat(worker.hset(shipment, "status", "shipped")).isZero();
                Map<String, String> shipped = Map.of("status", "shipped", "tracking", "parcel-301");
                assertThat(worker.hgetAll(shipment)).isEqualTo(shipped);
                assertThat(operator.hgetAll(shipment)).isEqualTo(shipped);
                assertThat(operator.hgetAll(otherTenant)).isEqualTo(otherShipment);
            }
        } finally {
            server.stop();
        }
    }
}
