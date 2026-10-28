/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.GeoUnit;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.embedded.RedisServer;

/// Exercises Jedis geospatial queries to find nearby pickup stores after a store relocates.
public class NearbyPickupStoreTest {
    @Test
    @Timeout(60)
    public void recommendsPickupStoresWithinTheCustomersSearchRadius() throws Exception {
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
            try (Jedis directory = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis storefront = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String stores = "pickup:stores:london";
                assertThat(directory.geoadd(stores, Map.of(
                        "west", new GeoCoordinate(-0.1378, 51.5074),
                        "north", new GeoCoordinate(-0.1278, 51.5374),
                        "outskirts", new GeoCoordinate(-0.1278, 51.7074))))
                        .isEqualTo(3);
                GeoRadiusParam nearestFirst = GeoRadiusParam.geoRadiusParam()
                        .withDist().withCoord().sortAscending();

                // A customer sees only stores within five kilometres, ordered by travel distance.
                List<GeoRadiusResponse> nearby = storefront.georadius(
                        stores, -0.1278, 51.5074, 5, GeoUnit.KM, nearestFirst);
                assertThat(nearby).extracting(GeoRadiusResponse::getMemberByString)
                        .containsExactly("west", "north");
                assertThat(nearby.getFirst().getDistance()).isBetween(0.6, 0.8);
                assertThat(nearby.getLast().getDistance()).isBetween(3.2, 3.5);
                assertThat(nearby.getFirst().getCoordinate().getLongitude())
                        .isCloseTo(-0.1378, within(0.00001));
                assertThat(storefront.georadius(stores, -0.1278, 51.5074, 0.1, GeoUnit.KM))
                        .isEmpty();

                // Relocation updates the existing store instead of creating a duplicate listing.
                assertThat(directory.geoadd(stores, -0.1278, 51.5124, "outskirts")).isZero();
                List<GeoRadiusResponse> updated = storefront.georadius(
                        stores, -0.1278, 51.5074, 5, GeoUnit.KM, nearestFirst);
                assertThat(updated).extracting(GeoRadiusResponse::getMemberByString)
                        .containsExactly("outskirts", "west", "north");
                assertThat(updated.getFirst().getDistance()).isBetween(0.5, 0.6);
                assertThat(updated.getFirst().getCoordinate().getLatitude())
                        .isCloseTo(51.5124, within(0.00001));

                // A compact checkout widget requests just the closest pickup option.
                List<GeoRadiusResponse> recommendation = storefront.georadius(
                        stores, -0.1278, 51.5074, 5, GeoUnit.KM,
                        GeoRadiusParam.geoRadiusParam().sortAscending().count(1));
                assertThat(recommendation).extracting(GeoRadiusResponse::getMemberByString)
                        .containsExactly("outskirts");
            }
        } finally {
            server.stop();
        }
    }
}
