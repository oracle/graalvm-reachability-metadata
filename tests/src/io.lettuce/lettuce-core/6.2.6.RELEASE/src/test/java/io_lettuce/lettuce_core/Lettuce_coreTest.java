/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.GeoArgs;
import io.lettuce.core.GeoCoordinates;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class Lettuce_coreTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void redisUriAndClientOptionsCanBeConfiguredWithoutConnecting() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6380)
                .withDatabase(2)
                .withSsl(false)
                .withTimeout(COMMAND_TIMEOUT)
                .build();

        RedisClient client = RedisClient.create(uri);
        try {
            SocketOptions socketOptions = SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(500))
                    .keepAlive(true)
                    .build();
            ClientOptions clientOptions = ClientOptions.builder()
                    .autoReconnect(false)
                    .pingBeforeActivateConnection(true)
                    .socketOptions(socketOptions)
                    .build();

            client.setOptions(clientOptions);
            client.setDefaultTimeout(Duration.ofSeconds(3));

            assertThat(uri.getHost()).isEqualTo("localhost");
            assertThat(uri.getPort()).isEqualTo(6380);
            assertThat(uri.getDatabase()).isEqualTo(2);
            assertThat(uri.getTimeout()).isEqualTo(COMMAND_TIMEOUT);
            assertThat(client.getOptions()).isSameAs(clientOptions);
        } finally {
            shutdown(client);
        }
    }

    @Test
    void synchronousCommandsRoundTripAgainstRedisProtocolServer() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisCommands<String, String> commands = connection.sync();

                assertThat(commands.ping()).isEqualTo("PONG");
                assertThat(commands.set("message", "hello")).isEqualTo("OK");
                assertThat(commands.get("message")).isEqualTo("hello");
                assertThat(commands.incr("counter")).isEqualTo(1L);
                assertThat(commands.incr("counter")).isEqualTo(2L);
                assertThat(commands.hset("hash", "field", "value")).isTrue();
                assertThat(commands.hget("hash", "field")).isEqualTo("value");
                assertThat(commands.lpush("list", "one", "two")).isEqualTo(2L);
                assertThat(commands.lrange("list", 0, -1)).containsExactly("two", "one");
                assertThat(commands.sadd("set", "a", "b", "a")).isEqualTo(2L);
                assertThat(commands.smembers("set")).containsExactlyInAnyOrder("a", "b");

                assertThat(server.commands()).extracting(command -> command.get(0))
                        .contains("PING", "SET", "GET", "INCR", "HSET", "HGET", "LPUSH", "LRANGE", "SADD", "SMEMBERS");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    @Test
    void commandArgumentBuildersEncodeOptionsAndKeyValueResults() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisCommands<String, String> commands = connection.sync();

                SetArgs setArgs = SetArgs.Builder.nx().ex(10);
                assertThat(commands.set("guarded", "first", setArgs)).isEqualTo("OK");
                List<KeyValue<String, String>> values = commands.mget("guarded", "missing");
                assertThat(values).extracting(KeyValue::getKey, KeyValue::hasValue)
                        .containsExactly(
                                tuple("guarded", true),
                                tuple("missing", false));
                assertThat(values.get(0).getValue()).isEqualTo("first");
                assertThat(values.get(1).getValueOrElse("fallback")).isEqualTo("fallback");

                List<String> setCommand = server.commands().stream()
                        .filter(command -> command.size() > 1)
                        .filter(command -> command.get(0).equals("SET"))
                        .filter(command -> command.get(1).equals("guarded"))
                        .findFirst()
                        .orElseThrow(AssertionError::new);
                assertThat(setCommand).containsExactly("SET", "guarded", "first", "EX", "10", "NX");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    @Test
    void asynchronousAndReactiveCommandsUseTheSameConnectionInfrastructure() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisAsyncCommands<String, String> async = connection.async();

                RedisFuture<String> set = async.set("async-key", "async-value");
                RedisFuture<String> get = async.get("async-key");
                assertThat(set.get(5, TimeUnit.SECONDS)).isEqualTo("OK");
                assertThat(get.get(5, TimeUnit.SECONDS)).isEqualTo("async-value");

                RedisReactiveCommands<String, String> reactive = connection.reactive();
                assertThat(reactive.set("reactive-key", "reactive-value").block(COMMAND_TIMEOUT)).isEqualTo("OK");
                assertThat(reactive.get("reactive-key").block(COMMAND_TIMEOUT)).isEqualTo("reactive-value");
                assertThat(Flux.from(reactive.mget("async-key", "reactive-key"))
                                .map(KeyValue::getValue)
                                .collectList()
                                .block(COMMAND_TIMEOUT))
                        .containsExactly("async-value", "reactive-value");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    @Test
    void geospatialCommandsDecodeCoordinatesAndDistances() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisCommands<String, String> commands = connection.sync();

                assertThat(commands.geoadd("places", 13.361389, 38.115556, "Palermo")).isEqualTo(1L);
                assertThat(commands.geoadd("places", 15.087269, 37.502669, "Catania")).isEqualTo(1L);

                List<GeoCoordinates> positions = commands.geopos("places", "Palermo", "Catania");
                Double distance = commands.geodist("places", "Palermo", "Catania", GeoArgs.Unit.km);

                assertThat(positions).hasSize(2);
                assertThat(positions.get(0).getX().doubleValue()).isCloseTo(13.361389, within(0.000001));
                assertThat(positions.get(0).getY().doubleValue()).isCloseTo(38.115556, within(0.000001));
                assertThat(positions.get(1).getX().doubleValue()).isCloseTo(15.087269, within(0.000001));
                assertThat(positions.get(1).getY().doubleValue()).isCloseTo(37.502669, within(0.000001));
                assertThat(distance).isCloseTo(166.0, within(1.0));
                assertThat(server.commands()).extracting(command -> command.get(0))
                        .contains("GEOADD", "GEOPOS", "GEODIST");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    @Test
    void sortedSetCommandsMapScoredValues() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisCommands<String, String> commands = connection.sync();

                assertThat(commands.zadd("leaders", 10.5, "alice")).isEqualTo(1L);
                assertThat(commands.zadd("leaders", 7.0, "bob")).isEqualTo(1L);
                List<ScoredValue<String>> leaders = commands.zrangeWithScores("leaders", 0, -1);

                assertThat(leaders).extracting(ScoredValue::getValue).containsExactly("bob", "alice");
                assertThat(leaders).extracting(ScoredValue::getScore).containsExactly(7.0, 10.5);
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    @Test
    void scriptingCommandsDecodeRequestedOutputTypes() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisCommands<String, String> commands = connection.sync();

                String selectedKey = commands.eval("return KEYS[1]", ScriptOutputType.VALUE,
                        new String[] {"script-key"});
                Long totalInputs = commands.eval("return #KEYS + #ARGV", ScriptOutputType.INTEGER,
                        new String[] {"first-key", "second-key"}, "argument");
                List<String> echoedInputs = commands.eval("return {KEYS[1], ARGV[1]}", ScriptOutputType.MULTI,
                        new String[] {"script-key"}, "script-value");

                assertThat(selectedKey).isEqualTo("script-key");
                assertThat(totalInputs).isEqualTo(3L);
                assertThat(echoedInputs).containsExactly("script-key", "script-value");
                assertThat(server.commands().stream()
                                .filter(command -> command.get(0).equals("EVAL"))
                                .toList())
                        .extracting(command -> command.get(1))
                        .containsExactly("return KEYS[1]", "return #KEYS + #ARGV", "return {KEYS[1], ARGV[1]}");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    private static void shutdown(RedisClient client) {
        client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
    }

    private static void closeConnection(StatefulRedisConnection<String, String> connection) {
        if (connection != null) {
            connection.close();
        }
    }

    private static final class FakeRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final Map<String, String> strings = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<String, Map<String, String>> hashes = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<String, List<String>> lists = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<String, Set<String>> sets = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<String, Map<String, Double>> sortedSets = Collections.synchronizedMap(new LinkedHashMap<>());
        private final Map<String, Map<String, GeoCoordinates>> geoIndexes = Collections.synchronizedMap(new LinkedHashMap<>());
        private volatile boolean closed;

        FakeRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            thread = new Thread(this::acceptConnections, "fake-redis-server");
            thread.setDaemon(true);
            thread.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(COMMAND_TIMEOUT)
                    .build();
        }

        List<List<String>> commands() {
            return commands;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(5_000);
                    handle(socket);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while (!closed && !socket.isClosed()) {
                List<String> command;
                try {
                    command = readCommand(input);
                } catch (EOFException e) {
                    return;
                } catch (SocketTimeoutException e) {
                    return;
                }
                commands.add(command);
                writeResponse(output, command);
                output.flush();
            }
        }

        private List<String> readCommand(BufferedInputStream input) throws IOException {
            String firstLine = readLine(input);
            if (!firstLine.startsWith("*")) {
                throw new IOException("Expected RESP array but got: " + firstLine);
            }
            int count = Integer.parseInt(firstLine.substring(1));
            List<String> command = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String bulkHeader = readLine(input);
                if (!bulkHeader.startsWith("$")) {
                    throw new IOException("Expected RESP bulk string but got: " + bulkHeader);
                }
                int length = Integer.parseInt(bulkHeader.substring(1));
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                    throw new EOFException();
                }
                String value = new String(bytes, StandardCharsets.UTF_8);
                command.add(i == 0 ? value.toUpperCase() : value);
            }
            return command;
        }

        private String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException();
                }
                if (next == '\r') {
                    int lineFeed = input.read();
                    if (lineFeed != '\n') {
                        throw new IOException("Expected LF after CR");
                    }
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
            }
        }

        private void writeResponse(OutputStream output, List<String> command) throws IOException {
            String name = command.get(0);
            switch (name) {
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "AUTH":
                case "CLIENT":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                case "SET":
                    strings.put(command.get(1), command.get(2));
                    writeSimple(output, "OK");
                    break;
                case "GET":
                    writeBulk(output, strings.get(command.get(1)));
                    break;
                case "MGET":
                    writeArray(output, command.subList(1, command.size()).stream().map(strings::get).toList());
                    break;
                case "INCR":
                    writeInteger(output, increment(command.get(1)));
                    break;
                case "HSET":
                    writeInteger(output, hset(command.get(1), command.get(2), command.get(3)) ? 1 : 0);
                    break;
                case "HGET":
                    writeBulk(output, hashes.getOrDefault(command.get(1), Collections.emptyMap()).get(command.get(2)));
                    break;
                case "LPUSH":
                    writeInteger(output, lpush(command));
                    break;
                case "LRANGE":
                    writeArray(output, lrange(command));
                    break;
                case "SADD":
                    writeInteger(output, sadd(command));
                    break;
                case "SMEMBERS":
                    writeArray(output, new ArrayList<>(sets.getOrDefault(command.get(1), Collections.emptySet())));
                    break;
                case "ZADD":
                    writeInteger(output, zadd(command));
                    break;
                case "ZRANGE":
                    writeArray(output, zrange(command));
                    break;
                case "GEOADD":
                    writeInteger(output, geoadd(command));
                    break;
                case "GEOPOS":
                    writeGeoPositions(output, command);
                    break;
                case "GEODIST":
                    writeBulk(output, geodist(command));
                    break;
                case "EVAL":
                    writeEvalResponse(output, command);
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private long increment(String key) {
            long value = Long.parseLong(strings.getOrDefault(key, "0")) + 1;
            strings.put(key, Long.toString(value));
            return value;
        }

        private boolean hset(String key, String field, String value) {
            Map<String, String> hash = hashes.computeIfAbsent(key, unused -> new LinkedHashMap<>());
            boolean absent = !hash.containsKey(field);
            hash.put(field, value);
            return absent;
        }

        private long lpush(List<String> command) {
            List<String> list = lists.computeIfAbsent(command.get(1), unused -> new ArrayList<>());
            for (int i = 2; i < command.size(); i++) {
                list.add(0, command.get(i));
            }
            return list.size();
        }

        private List<String> lrange(List<String> command) {
            List<String> list = lists.getOrDefault(command.get(1), Collections.emptyList());
            int start = Integer.parseInt(command.get(2));
            int stop = Integer.parseInt(command.get(3));
            int normalizedStop = stop < 0 ? list.size() + stop : stop;
            if (list.isEmpty() || start >= list.size() || normalizedStop < start) {
                return Collections.emptyList();
            }
            return new ArrayList<>(list.subList(start, Math.min(normalizedStop + 1, list.size())));
        }

        private long sadd(List<String> command) {
            Set<String> set = sets.computeIfAbsent(command.get(1), unused -> new LinkedHashSet<>());
            long added = 0;
            for (int i = 2; i < command.size(); i++) {
                if (set.add(command.get(i))) {
                    added++;
                }
            }
            return added;
        }

        private long zadd(List<String> command) {
            Map<String, Double> sortedSet = sortedSets.computeIfAbsent(command.get(1), unused -> new LinkedHashMap<>());
            String member = command.get(3);
            boolean absent = !sortedSet.containsKey(member);
            sortedSet.put(member, Double.parseDouble(command.get(2)));
            return absent ? 1 : 0;
        }

        private List<String> zrange(List<String> command) {
            boolean withScores = command.stream().anyMatch("WITHSCORES"::equalsIgnoreCase);
            Map<String, Double> sortedSet = sortedSets.getOrDefault(command.get(1), Collections.emptyMap());
            Queue<Map.Entry<String, Double>> entries = new ArrayDeque<>(sortedSet.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .toList());
            List<String> response = new ArrayList<>();
            while (!entries.isEmpty()) {
                Map.Entry<String, Double> entry = entries.remove();
                response.add(entry.getKey());
                if (withScores) {
                    response.add(Double.toString(entry.getValue()));
                }
            }
            return response;
        }

        private long geoadd(List<String> command) {
            Map<String, GeoCoordinates> index = geoIndexes.computeIfAbsent(command.get(1), unused -> new LinkedHashMap<>());
            GeoCoordinates coordinates = GeoCoordinates.create(Double.parseDouble(command.get(2)),
                    Double.parseDouble(command.get(3)));
            boolean absent = !index.containsKey(command.get(4));
            index.put(command.get(4), coordinates);
            return absent ? 1 : 0;
        }

        private String geodist(List<String> command) {
            Map<String, GeoCoordinates> index = geoIndexes.getOrDefault(command.get(1), Collections.emptyMap());
            GeoCoordinates from = index.get(command.get(2));
            GeoCoordinates to = index.get(command.get(3));
            if (from == null || to == null) {
                return null;
            }
            double distanceMeters = haversineMeters(from, to);
            String unit = command.size() > 4 ? command.get(4) : "m";
            double convertedDistance = switch (unit.toLowerCase()) {
                case "km" -> distanceMeters / 1_000.0;
                case "mi" -> distanceMeters / 1_609.344;
                case "ft" -> distanceMeters * 3.28084;
                default -> distanceMeters;
            };
            return Double.toString(convertedDistance);
        }

        private double haversineMeters(GeoCoordinates from, GeoCoordinates to) {
            double earthRadiusMeters = 6_371_000.0;
            double fromLatitude = Math.toRadians(from.getY().doubleValue());
            double toLatitude = Math.toRadians(to.getY().doubleValue());
            double latitudeDelta = Math.toRadians(to.getY().doubleValue() - from.getY().doubleValue());
            double longitudeDelta = Math.toRadians(to.getX().doubleValue() - from.getX().doubleValue());
            double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                    + Math.cos(fromLatitude) * Math.cos(toLatitude)
                    * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
            return 2 * earthRadiusMeters * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        }

        private void writeGeoPositions(OutputStream output, List<String> command) throws IOException {
            Map<String, GeoCoordinates> index = geoIndexes.getOrDefault(command.get(1), Collections.emptyMap());
            output.write(("*" + (command.size() - 2) + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (int i = 2; i < command.size(); i++) {
                GeoCoordinates coordinates = index.get(command.get(i));
                if (coordinates == null) {
                    output.write("*-1\r\n".getBytes(StandardCharsets.UTF_8));
                    continue;
                }
                output.write("*2\r\n".getBytes(StandardCharsets.UTF_8));
                writeBulk(output, coordinates.getX().toString());
                writeBulk(output, coordinates.getY().toString());
            }
        }

        private void writeEvalResponse(OutputStream output, List<String> command) throws IOException {
            String script = command.get(1);
            int keyCount = Integer.parseInt(command.get(2));
            List<String> keys = command.subList(3, 3 + keyCount);
            List<String> arguments = command.subList(3 + keyCount, command.size());
            if (script.contains("#KEYS")) {
                writeInteger(output, keys.size() + arguments.size());
            } else if (script.contains("{")) {
                writeArray(output, List.of(keys.get(0), arguments.get(0)));
            } else {
                writeBulk(output, keys.get(0));
            }
        }

        private void writeSimple(OutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeInteger(OutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(OutputStream output, String value) throws IOException {
            if (value == null) {
                output.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void writeArray(OutputStream output, List<String> values) throws IOException {
            output.write(("*" + values.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                writeBulk(output, value);
            }
        }
    }
}
