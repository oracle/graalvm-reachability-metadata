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
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.exceptions.JedisAskDataException;
import redis.clients.jedis.exceptions.JedisBusyException;
import redis.clients.jedis.exceptions.JedisClusterException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisMovedDataException;
import redis.clients.jedis.exceptions.JedisNoScriptException;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.IParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;
import redis.clients.jedis.params.ZParams;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.clients.jedis.resps.Slowlog;
import redis.clients.jedis.resps.Tuple;
import redis.clients.jedis.util.JedisClusterCRC16;
import redis.clients.jedis.util.JedisClusterHashTag;
import redis.clients.jedis.util.RedisInputStream;
import redis.clients.jedis.util.RedisOutputStream;

public class JedisTest {
    @Test
    void writesRedisProtocolCommandsAndReadsAllReplyTypes() throws Exception {
        ByteArrayOutputStream commandBytes = new ByteArrayOutputStream();
        RedisOutputStream outputStream = new RedisOutputStream(commandBytes);

        Protocol.sendCommand(outputStream, new CommandArguments(Protocol.Command.SET).key(bytes("client:key")).add(bytes("value")));
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
                .containsExactly("BY", "weight_*", "LIMIT", "2", "5", "GET", "object_*", "GET", "#", "DESC", "ALPHA");

        ScanParams scanParams = new ScanParams().match("user:*").count(25);
        assertThat(commandArguments(Protocol.Command.SCAN, scanParams))
                .containsExactly("SCAN", "MATCH", "user:*", "COUNT", "25");
        assertThat(asString(ScanParams.SCAN_POINTER_START_BINARY)).isEqualTo("0");

        ZParams zParams = new ZParams().weights(1.5D, 2D, 0.25D).aggregate(ZParams.Aggregate.MAX);
        assertThat(commandArguments(Protocol.Command.ZUNIONSTORE, zParams))
                .containsExactly("ZUNIONSTORE", "WEIGHTS", "1.5", "2.0", "0.25", "AGGREGATE", "MAX");

        BitPosParams bitPosParams = new BitPosParams(3L, 14L);
        assertThat(commandArguments(Protocol.Command.BITPOS, bitPosParams)).containsExactly("BITPOS", "3", "14");
    }

    @Test
    void buildsSortedSetAndGeoParameterObjects() {
        CommandArguments zaddParams = new CommandArguments(Protocol.Command.ZADD)
                .key("leaders")
                .addParams(ZAddParams.zAddParams().nx().ch())
                .add(9.5D)
                .add("alice");
        assertThat(strings(zaddParams)).containsExactly("ZADD", "leaders", "nx", "ch", "9.5", "alice");

        CommandArguments zincrByParams = new CommandArguments(Protocol.Command.ZADD)
                .key("leaders")
                .addParams(ZIncrByParams.zIncrByParams().xx())
                .add(2.0D)
                .add("bob");
        assertThat(strings(zincrByParams)).containsExactly("ZADD", "leaders", "xx", "incr", "2.0", "bob");

        CommandArguments geoRadiusParams = new CommandArguments(Protocol.Command.GEORADIUS)
                .key("Sicily")
                .add(15)
                .add(37)
                .add(200)
                .add("km")
                .addParams(GeoRadiusParam.geoRadiusParam()
                        .withCoord()
                        .withDist()
                        .count(3)
                        .sortDescending());
        assertThat(strings(geoRadiusParams))
                .containsExactly("GEORADIUS", "Sicily", "15", "37", "200", "km", "WITHCOORD", "WITHDIST", "COUNT", "3", "DESC");
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

        Object encodedResult = BuilderFactory.ENCODED_OBJECT.build(Arrays.asList(
                bytes("root"), Arrays.asList(bytes("nested"), 5L)));
        assertThat(encodedResult).isEqualTo(Arrays.asList("root", Arrays.asList("nested", 5L)));
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
    void convertsSlowlogRepliesIntoDomainObjects() {
        List<Object> rawEntries = Arrays.<Object>asList(
                Arrays.<Object>asList(
                        7L,
                        1_460_000_000L,
                        321L,
                        Arrays.asList(bytes("SET"), bytes("slowlog:key"), bytes("value"))),
                Arrays.<Object>asList(
                        8L,
                        1_460_000_001L,
                        42L,
                        Arrays.asList(bytes("GET"), bytes("slowlog:key"))));

        List<Slowlog> entries = Slowlog.from(rawEntries);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getId()).isEqualTo(7L);
        assertThat(entries.get(0).getTimeStamp()).isEqualTo(1_460_000_000L);
        assertThat(entries.get(0).getExecutionTime()).isEqualTo(321L);
        assertThat(entries.get(0).getArgs()).containsExactly("SET", "slowlog:key", "value");
        assertThat(entries.get(0).toString()).isEqualTo("7,1460000000,321,[SET, slowlog:key, value]");
        assertThat(entries.get(1).getId()).isEqualTo(8L);
        assertThat(entries.get(1).getArgs()).containsExactly("GET", "slowlog:key");
    }

    @Test
    void createsJedisFromUriAndClientConfiguration() throws Exception {
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(700)
                .socketTimeoutMillis(900)
                .password("changed")
                .database(3)
                .clientName("primary")
                .ssl(true)
                .build();
        assertThat(config.getConnectionTimeoutMillis()).isEqualTo(700);
        assertThat(config.getSocketTimeoutMillis()).isEqualTo(900);
        assertThat(config.getPassword()).isEqualTo("changed");
        assertThat(config.getDatabase()).isEqualTo(3);
        assertThat(config.getClientName()).isEqualTo("primary");
        assertThat(config.isSsl()).isTrue();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<List<List<String>>> acceptedConnection = acceptSingleConnection(serverSocket);
            URI redisUri = URI.create("redis://localhost:" + serverSocket.getLocalPort() + "/0");
            DefaultJedisClientConfig uriConfig = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(500)
                    .socketTimeoutMillis(500)
                    .clientName("uri-client")
                    .build();

            try (Jedis jedis = new Jedis(redisUri, uriConfig)) {
                assertThat(jedis.isConnected()).isTrue();
                assertThat(jedis.toString()).contains("localhost").contains(String.valueOf(serverSocket.getLocalPort()));
            }
            List<List<String>> initializationCommands = acceptedConnection.get(5, TimeUnit.SECONDS);
            assertThat(initializationCommands)
                    .anySatisfy(command -> assertThat(command)
                            .containsExactly("CLIENT", "SETNAME", "uri-client"));

            List<List<String>> clientInfoCommands = initializationCommands.stream()
                    .filter(command -> command.size() > 1 && "SETINFO".equals(command.get(1)))
                    .collect(Collectors.toList());
            if (!clientInfoCommands.isEmpty()) {
                assertThat(clientInfoCommands)
                        .anySatisfy(command -> assertThat(command)
                                .containsExactly("CLIENT", "SETINFO", "LIB-NAME", "jedis"))
                        .anySatisfy(command -> assertThat(command)
                                .startsWith("CLIENT", "SETINFO", "LIB-VER"));
            }
        }
    }

    @Test
    void handlesHostAndPortParsingAndClusterHashSlots() {
        HostAndPort parsed = HostAndPort.from("redis.example.test:7001");
        assertThat(parsed.getHost()).isEqualTo("redis.example.test");
        assertThat(parsed.getPort()).isEqualTo(7001);
        assertThat(parsed.toString()).isEqualTo("redis.example.test:7001");
        HostAndPort ipv6 = HostAndPort.from("2001:db8::10:7002");
        assertThat(ipv6.getHost()).isEqualTo("2001:db8::10");
        assertThat(ipv6.getPort()).isEqualTo(7002);
        assertThat(new HostAndPort("127.0.0.1", 6379)).isEqualTo(new HostAndPort("127.0.0.1", 6379));

        String taggedKey = "cart:{user-42}:items";
        assertThat(JedisClusterHashTag.getHashTag(taggedKey)).isEqualTo("user-42");
        assertThat(JedisClusterCRC16.getSlot(taggedKey)).isEqualTo(JedisClusterCRC16.getSlot("profile:{user-42}"));
        assertThat(JedisClusterHashTag.isClusterCompliantMatchPattern("cart:{user-42}:*")).isTrue();
        assertThat(JedisClusterHashTag.isClusterCompliantMatchPattern("cart:user-*"))
                .isFalse();
        assertThat(JedisClusterCRC16.getCRC16("123456789")).isEqualTo(12739);
    }

    private static CompletableFuture<List<List<String>>> acceptSingleConnection(ServerSocket serverSocket) {
        CompletableFuture<List<List<String>>> acceptedConnection = new CompletableFuture<>();
        Thread serverThread = new Thread(() -> {
            List<List<String>> initializationCommands = new ArrayList<>();
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(2_000);
                RedisInputStream inputStream = new RedisInputStream(socket.getInputStream());
                RedisOutputStream outputStream = new RedisOutputStream(socket.getOutputStream());
                while (true) {
                    initializationCommands.add(redisCommand(inputStream));
                    outputStream.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (SocketTimeoutException exception) {
                if (!acceptedConnection.isDone()) {
                    acceptedConnection.complete(initializationCommands);
                }
            } catch (JedisConnectionException exception) {
                if (!acceptedConnection.isDone()) {
                    acceptedConnection.complete(initializationCommands);
                }
            } catch (IOException | RuntimeException | AssertionError exception) {
                if (!acceptedConnection.isDone() && !serverSocket.isClosed()) {
                    acceptedConnection.completeExceptionally(exception);
                }
            }
        }, "jedis-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        return acceptedConnection;
    }

    private static List<String> redisCommand(RedisInputStream inputStream) {
        Object command = Protocol.read(inputStream);
        assertThat(command).isInstanceOf(List.class);
        return ((List<?>) command).stream()
                .map(part -> asString((byte[]) part))
                .collect(Collectors.toList());
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

    private static List<String> strings(CommandArguments arguments) {
        List<String> values = new ArrayList<>();
        for (Rawable argument : arguments) {
            values.add(asString(argument.getRaw()));
        }
        return values;
    }

    private static List<String> commandArguments(Protocol.Command command, IParams params) {
        CommandArguments arguments = new CommandArguments(command);
        params.addParams(arguments);
        return strings(arguments);
    }
}
