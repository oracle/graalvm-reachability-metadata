/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.ZParams;
import redis.clients.jedis.exceptions.JedisAskDataException;
import redis.clients.jedis.exceptions.JedisBusyException;
import redis.clients.jedis.exceptions.JedisClusterException;
import redis.clients.jedis.exceptions.JedisMovedDataException;
import redis.clients.jedis.exceptions.JedisNoScriptException;
import redis.clients.jedis.params.geo.GeoRadiusParam;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;
import redis.clients.util.JedisClusterCRC16;
import redis.clients.util.JedisClusterHashTagUtil;
import redis.clients.util.RedisInputStream;
import redis.clients.util.RedisOutputStream;

public class JedisTest {
    @Test
    void writesRedisProtocolCommandsAndReadsAllReplyTypes() throws Exception {
        ByteArrayOutputStream commandBytes = new ByteArrayOutputStream();
        RedisOutputStream outputStream = new RedisOutputStream(commandBytes);

        Protocol.sendCommand(outputStream, Protocol.Command.SET, bytes("client:key"), bytes("value"));
        outputStream.flush();

        assertThat(asString(commandBytes.toByteArray()))
                .isEqualTo("*3\r\n$3\r\nSET\r\n$10\r\nclient:key\r\n$5\r\nvalue\r\n");
        assertThat(asString((byte[]) Protocol.read(input("+OK\r\n")))).isEqualTo("OK");
        assertThat(Protocol.read(input(":42\r\n"))).isEqualTo(42L);
        assertThat(asString((byte[]) Protocol.read(input("$5\r\nhello\r\n")))).isEqualTo("hello");
        assertThat(Protocol.read(input("$-1\r\n"))).isNull();

        Object multiBulk = Protocol.read(input("*4\r\n+PONG\r\n:7\r\n$5\r\nworld\r\n$-1\r\n"));

        assertThat(multiBulk).isInstanceOf(List.class);
        List<?> replies = (List<?>) multiBulk;
        assertThat(asString((byte[]) replies.get(0))).isEqualTo("PONG");
        assertThat(replies.get(1)).isEqualTo(7L);
        assertThat(asString((byte[]) replies.get(2))).isEqualTo("world");
        assertThat(replies.get(3)).isNull();
    }

    @Test
    void mapsRedisErrorRepliesToSpecificJedisExceptions() {
        assertThatThrownBy(() -> Protocol.read(input("-MOVED 3999 127.0.0.1:6381\r\n")))
                .isInstanceOfSatisfying(JedisMovedDataException.class, exception -> {
                    assertThat(exception.getSlot()).isEqualTo(3999);
                    assertThat(exception.getTargetNode()).isEqualTo(new HostAndPort("127.0.0.1", 6381));
                });
        assertThatThrownBy(() -> Protocol.read(input("-ASK 4000 redis.example.test:6382\r\n")))
                .isInstanceOfSatisfying(JedisAskDataException.class, exception -> {
                    assertThat(exception.getSlot()).isEqualTo(4000);
                    assertThat(exception.getTargetNode()).isEqualTo(new HostAndPort("redis.example.test", 6382));
                });
        assertThatThrownBy(() -> Protocol.read(input("-CLUSTERDOWN Hash slot not served\r\n")))
                .isInstanceOf(JedisClusterException.class);
        assertThatThrownBy(() -> Protocol.read(input("-BUSY Redis is busy running a script\r\n")))
                .isInstanceOf(JedisBusyException.class);
        assertThatThrownBy(() -> Protocol.read(input("-NOSCRIPT No matching script\r\n")))
                .isInstanceOf(JedisNoScriptException.class);

        assertThat(Protocol.readErrorLineIfPossible(input("-ERR invalid command\r\n")))
                .isEqualTo("ERR invalid command");
        assertThat(Protocol.readErrorLineIfPossible(input("+OK\r\n"))).isNull();
    }

    @Test
    void buildsCommandParameterObjectsInRedisArgumentOrder() {
        SortingParams sortingParams = new SortingParams()
                .by("weight_*")
                .limit(2, 5)
                .get("object_*", "#")
                .desc()
                .alpha();
        assertThat(strings(sortingParams.getParams()))
                .containsExactly("by", "weight_*", "limit", "2", "5", "get", "object_*", "get", "#", "desc", "alpha");

        ScanParams scanParams = new ScanParams().match("user:*").count(25);
        assertThat(strings(scanParams.getParams())).containsExactly("match", "user:*", "count", "25");
        assertThat(asString(ScanParams.SCAN_POINTER_START_BINARY)).isEqualTo("0");

        ZParams zParams = new ZParams().weightsByDouble(1.5D, 2D, 0.25D).aggregate(ZParams.Aggregate.MAX);
        assertThat(strings(zParams.getParams())).containsExactly("weights", "1.5", "2.0", "0.25", "aggregate", "MAX");

        BitPosParams bitPosParams = new BitPosParams(3L, 14L);
        assertThat(strings(bitPosParams.getParams())).containsExactly("3", "14");
    }

    @Test
    void buildsSortedSetAndGeoParameterObjects() {
        byte[][] zaddParams = ZAddParams.zAddParams().nx().ch()
                .getByteParams(bytes("leaders"), bytes("9.5"), bytes("alice"));
        assertThat(strings(zaddParams)).containsExactly("leaders", "nx", "ch", "9.5", "alice");

        byte[][] zincrByParams = ZIncrByParams.zIncrByParams().xx()
                .getByteParams(bytes("leaders"), bytes("2.0"), bytes("bob"));
        assertThat(strings(zincrByParams)).containsExactly("leaders", "xx", "incr", "2.0", "bob");

        byte[][] geoRadiusParams = GeoRadiusParam.geoRadiusParam()
                .withCoord()
                .withDist()
                .count(3)
                .sortDescending()
                .getByteParams(bytes("Sicily"), bytes("15"), bytes("37"), bytes("200"), bytes("km"));
        assertThat(strings(geoRadiusParams))
                .containsExactly("Sicily", "15", "37", "200", "km", "withcoord", "withdist", "count", "3", "desc");
    }

    @Test
    void convertsRawRedisResponsesWithBuilderFactory() {
        assertThat(BuilderFactory.STRING.build(bytes("plain"))).isEqualTo("plain");
        assertThat(BuilderFactory.BOOLEAN.build(1L)).isTrue();
        assertThat(BuilderFactory.DOUBLE.build(bytes("12.75"))).isEqualTo(12.75D);
        assertThat(BuilderFactory.STRING_LIST.build(Arrays.asList(bytes("first"), null, bytes("second"))))
                .containsExactly("first", null, "second");

        Map<String, String> stringMap = BuilderFactory.STRING_MAP.build(Arrays.asList(
                bytes("field-one"), bytes("value-one"), bytes("field-two"), bytes("value-two")));
        assertThat(stringMap).containsEntry("field-one", "value-one").containsEntry("field-two", "value-two");

        Set<Tuple> scoredMembers = BuilderFactory.TUPLE_ZSET.build(Arrays.asList(
                bytes("alice"), bytes("8.5"), bytes("bob"), bytes("9.25")));
        assertThat(scoredMembers)
                .extracting(Tuple::getElement)
                .containsExactly("alice", "bob");
        assertThat(scoredMembers)
                .extracting(Tuple::getScore)
                .containsExactly(8.5D, 9.25D);

        Object evalResult = BuilderFactory.EVAL_RESULT.build(Arrays.asList(
                bytes("root"), Arrays.asList(bytes("nested"), 5L)));
        assertThat(evalResult).isEqualTo(Arrays.asList("root", Arrays.asList("nested", 5L)));
    }

    @Test
    void convertsGeoResponsesAndValueObjects() {
        GeoCoordinate coordinate = new GeoCoordinate(13.361389D, 38.115556D);
        GeoCoordinate sameCoordinate = new GeoCoordinate(13.361389D, 38.115556D);
        assertThat(coordinate).isEqualTo(sameCoordinate);
        assertThat(coordinate.toString()).isEqualTo("(13.361389,38.115556)");

        List<GeoCoordinate> coordinates = BuilderFactory.GEO_COORDINATE_LIST.build(Arrays.asList(
                Arrays.asList(bytes("13.361389"), bytes("38.115556")),
                null,
                Arrays.asList(bytes("15.087269"), bytes("37.502669"))));
        assertThat(coordinates).containsExactly(
                new GeoCoordinate(13.361389D, 38.115556D),
                null,
                new GeoCoordinate(15.087269D, 37.502669D));

        List<GeoRadiusResponse> responses = BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT.build(Arrays.asList(
                Arrays.asList(
                        bytes("Palermo"),
                        bytes("190.4424"),
                        Arrays.asList(bytes("13.361389"), bytes("38.115556"))),
                Arrays.asList(
                        bytes("Catania"),
                        bytes("56.4413"),
                        Arrays.asList(bytes("15.087269"), bytes("37.502669")))));
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getMemberByString()).isEqualTo("Palermo");
        assertThat(responses.get(0).getDistance()).isEqualTo(190.4424D);
        assertThat(responses.get(0).getCoordinate()).isEqualTo(new GeoCoordinate(13.361389D, 38.115556D));
        assertThat(responses.get(1).getMemberByString()).isEqualTo("Catania");
    }

    @Test
    void parsesShardInfoUriAndCreatesUnconnectedJedisResource() {
        JedisShardInfo redisUri = new JedisShardInfo(URI.create("redis://:secret@cache.example.test:6380/3"));
        assertThat(redisUri.getHost()).isEqualTo("cache.example.test");
        assertThat(redisUri.getPort()).isEqualTo(6380);
        assertThat(redisUri.getPassword()).isEqualTo("secret");
        assertThat(redisUri.getDb()).isEqualTo(3);
        assertThat(redisUri.getSsl()).isFalse();

        JedisShardInfo redissUri = new JedisShardInfo("rediss://:tls-secret@secure.example.test:6381/4");
        assertThat(redissUri.getHost()).isEqualTo("secure.example.test");
        assertThat(redissUri.getPort()).isEqualTo(6381);
        assertThat(redissUri.getPassword()).isEqualTo("tls-secret");
        assertThat(redissUri.getDb()).isEqualTo(4);
        assertThat(redissUri.getSsl()).isTrue();

        JedisShardInfo shardInfo = new JedisShardInfo("localhost", 6382, 500, "primary", true);
        shardInfo.setConnectionTimeout(700);
        shardInfo.setSoTimeout(900);
        shardInfo.setPassword("changed");
        assertThat(shardInfo.getName()).isEqualTo("primary");
        assertThat(shardInfo.getConnectionTimeout()).isEqualTo(700);
        assertThat(shardInfo.getSoTimeout()).isEqualTo(900);
        assertThat(shardInfo.getPassword()).isEqualTo("changed");
        assertThat(shardInfo.toString()).contains("localhost").contains("6382");

        Jedis jedis = shardInfo.createResource();
        assertThat(jedis).isNotNull();
        assertThat(jedis.isConnected()).isFalse();
        jedis.close();
    }

    @Test
    void handlesHostAndPortParsingAndClusterHashSlots() {
        HostAndPort parsed = HostAndPort.parseString("redis.example.test:7001");
        assertThat(parsed.getHost()).isEqualTo("redis.example.test");
        assertThat(parsed.getPort()).isEqualTo(7001);
        assertThat(parsed.toString()).isEqualTo("redis.example.test:7001");
        assertThat(HostAndPort.extractParts("2001:db8::10:7002"))
                .containsExactly("2001:db8::10", "7002");
        assertThat(new HostAndPort("127.0.0.1", 6379)).isEqualTo(new HostAndPort("localhost", 6379));

        String taggedKey = "cart:{user-42}:items";
        assertThat(JedisClusterHashTagUtil.getHashTag(taggedKey)).isEqualTo("user-42");
        assertThat(JedisClusterCRC16.getSlot(taggedKey)).isEqualTo(JedisClusterCRC16.getSlot("profile:{user-42}"));
        assertThat(JedisClusterHashTagUtil.isClusterCompliantMatchPattern("cart:{user-42}:*")).isTrue();
        assertThat(JedisClusterHashTagUtil.isClusterCompliantMatchPattern("cart:user-*"))
                .isFalse();
        assertThat(JedisClusterCRC16.getCRC16("123456789")).isEqualTo(12739);
    }

    private static RedisInputStream input(String data) {
        return new RedisInputStream(new ByteArrayInputStream(bytes(data)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String asString(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static List<String> strings(Collection<byte[]> values) {
        return values.stream().map(JedisTest::asString).collect(Collectors.toList());
    }

    private static List<String> strings(byte[][] values) {
        return Arrays.stream(values).map(JedisTest::asString).collect(Collectors.toList());
    }
}
