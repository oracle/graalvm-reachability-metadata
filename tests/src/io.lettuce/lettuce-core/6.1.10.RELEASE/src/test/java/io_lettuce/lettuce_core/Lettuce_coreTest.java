/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.BitFieldArgs;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.CompositeArgument;
import io.lettuce.core.GeoArgs;
import io.lettuce.core.GeoCoordinates;
import io.lettuce.core.GeoValue;
import io.lettuce.core.GeoWithin;
import io.lettuce.core.KeyValue;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SortArgs;
import io.lettuce.core.SslOptions;
import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.Value;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.cluster.models.slots.ClusterSlotRange;
import io.lettuce.core.cluster.models.slots.ClusterSlotsParser;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.models.role.RedisInstance;
import io.lettuce.core.models.stream.PendingMessage;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.output.ValueOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.SslProvider;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Lettuce_coreTest {
    @Test
    void parsesBuildsAndCopiesRedisUris() {
        RedisURI standaloneUri = RedisURI.builder()
                .withHost("cache.example.test")
                .withPort(6380)
                .withDatabase(5)
                .withClientName("lettuce-native")
                .withAuthentication("default", "s3cr3t")
                .withTimeout(Duration.ofMillis(250))
                .build();

        assertThat(standaloneUri.getHost()).isEqualTo("cache.example.test");
        assertThat(standaloneUri.getPort()).isEqualTo(6380);
        assertThat(standaloneUri.getDatabase()).isEqualTo(5);
        assertThat(standaloneUri.getClientName()).isEqualTo("lettuce-native");
        assertThat(standaloneUri.getUsername()).isEqualTo("default");
        assertThat(standaloneUri.getPassword()).containsExactly('s', '3', 'c', 'r', '3', 't');
        assertThat(standaloneUri.getTimeout()).isEqualTo(Duration.ofMillis(250));

        RedisURI tlsUri = RedisURI.builder(standaloneUri)
                .withHost("secure.example.test")
                .withPort(6381)
                .withSsl(true)
                .withStartTls(true)
                .withVerifyPeer(SslVerifyMode.CA)
                .build();

        assertThat(tlsUri.isSsl()).isTrue();
        assertThat(tlsUri.isStartTls()).isTrue();
        assertThat(tlsUri.getVerifyMode()).isEqualTo(SslVerifyMode.CA);
        assertThat(tlsUri.toURI().getScheme()).isEqualTo("redis+tls");

        RedisURI parsedTlsUri = RedisURI.create("rediss://:tls-secret@parsed.example.test:6382/4?verifyPeer=NONE");
        assertThat(parsedTlsUri.isSsl()).isTrue();
        assertThat(parsedTlsUri.getHost()).isEqualTo("parsed.example.test");
        assertThat(parsedTlsUri.getDatabase()).isEqualTo(4);
        assertThat(parsedTlsUri.getVerifyMode()).isEqualTo(SslVerifyMode.NONE);

        RedisURI sentinelUri = RedisURI.Builder
                .sentinel("sentinel-one.example.test", 26379, "primary")
                .withSentinel("sentinel-two.example.test", 26380)
                .withAuthentication("default", "data-pw")
                .withDatabase(2)
                .build();

        assertThat(sentinelUri.getSentinelMasterId()).isEqualTo("primary");
        assertThat(sentinelUri.getSentinels())
                .extracting(RedisURI::getHost)
                .containsExactly("sentinel-one.example.test", "sentinel-two.example.test");
        assertThat(sentinelUri.getSentinels())
                .extracting(RedisURI::getPort)
                .containsExactly(26379, 26380);
        assertThat(sentinelUri.getDatabase()).isEqualTo(2);
    }

    @Test
    void configuresClientSocketSslTimeoutAndClusterOptionsWithoutConnecting() {
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .timeoutCommands(true)
                .fixedTimeout(Duration.ofSeconds(3))
                .build();
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .keepAlive(true)
                .tcpNoDelay(false)
                .build();
        SslOptions sslOptions = SslOptions.builder()
                .jdkSslProvider()
                .protocols("TLSv1.3", "TLSv1.2")
                .cipherSuites("TLS_AES_128_GCM_SHA256")
                .handshakeTimeout(Duration.ofSeconds(4))
                .build();
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(false)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .pingBeforeActivateConnection(false)
                .protocolVersion(ProtocolVersion.RESP3)
                .publishOnScheduler(true)
                .requestQueueSize(128)
                .socketOptions(socketOptions)
                .sslOptions(sslOptions)
                .timeoutOptions(timeoutOptions)
                .build();

        assertThat(clientOptions.isAutoReconnect()).isFalse();
        assertThat(clientOptions.getDisconnectedBehavior())
                .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        assertThat(clientOptions.getConfiguredProtocolVersion()).isEqualTo(ProtocolVersion.RESP3);
        assertThat(clientOptions.getRequestQueueSize()).isEqualTo(128);
        assertThat(clientOptions.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(1500));
        assertThat(clientOptions.getSocketOptions().isKeepAlive()).isTrue();
        assertThat(clientOptions.getSocketOptions().isTcpNoDelay()).isFalse();
        assertThat(clientOptions.getSslOptions().getSslProvider()).isEqualTo(SslProvider.JDK);
        assertThat(clientOptions.getSslOptions().getProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(clientOptions.getSslOptions().getCipherSuites()).containsExactly("TLS_AES_128_GCM_SHA256");
        assertThat(clientOptions.getTimeoutOptions().isTimeoutCommands()).isTrue();

        ClusterTopologyRefreshOptions refreshOptions = ClusterTopologyRefreshOptions.builder()
                .enableAdaptiveRefreshTrigger(
                        ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                        ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS)
                .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(7))
                .dynamicRefreshSources(false)
                .enablePeriodicRefresh(Duration.ofMinutes(2))
                .refreshTriggersReconnectAttempts(3)
                .build();
        ClusterClientOptions clusterOptions = ClusterClientOptions.builder(clientOptions)
                .maxRedirects(5)
                .topologyRefreshOptions(refreshOptions)
                .validateClusterNodeMembership(false)
                .nodeFilter(redisClusterNode -> !redisClusterNode.is(RedisClusterNode.NodeFlag.FAIL))
                .build();

        assertThat(clusterOptions.getMaxRedirects()).isEqualTo(5);
        assertThat(clusterOptions.isValidateClusterNodeMembership()).isFalse();
        assertThat(clusterOptions.getTopologyRefreshOptions().isPeriodicRefreshEnabled()).isTrue();
        assertThat(clusterOptions.getTopologyRefreshOptions().getRefreshPeriod()).isEqualTo(Duration.ofMinutes(2));
        assertThat(clusterOptions.getTopologyRefreshOptions().getAdaptiveRefreshTriggers())
                .containsExactlyInAnyOrder(
                        ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                        ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS);
        RedisClusterNode healthyNode = RedisClusterNode.of(
                "127.0.0.1:7000@17000 myself,master - 0 0 1 connected 0");
        assertThat(clusterOptions.getNodeFilter().test(healthyNode)).isTrue();

        RedisClient client = RedisClient.create(RedisURI.create("redis://localhost:6379/0"));
        try {
            client.setDefaultTimeout(Duration.ofSeconds(2));
            client.setOptions(clientOptions);

            assertThat(client.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(client.getOptions().getConfiguredProtocolVersion()).isEqualTo(ProtocolVersion.RESP3);
            assertThat(client.getOptions().getRequestQueueSize()).isEqualTo(128);
            assertThat(client.getResources()).isNotNull();
        } finally {
            client.shutdown(Duration.ZERO, Duration.ZERO);
        }
    }

    @Test
    void encodesCommandsAndBuildsRedisArgumentObjects() {
        CommandArgs<String, String> setArgs = new CommandArgs<>(StringCodec.UTF8)
                .addKey("cache:key")
                .addValue("payload");
        new SetArgs().ex(Duration.ofSeconds(60)).nx().build(setArgs);
        Command<String, String, String> setCommand = new Command<>(
                CommandType.SET, new StatusOutput<>(StringCodec.UTF8), setArgs);

        ByteBuf commandBuffer = Unpooled.buffer();
        try {
            setCommand.encode(commandBuffer);

            assertThat(readBuffer(commandBuffer)).isEqualTo(
                    "*6\r\n"
                            + "$3\r\nSET\r\n"
                            + "$9\r\ncache:key\r\n"
                            + "$7\r\npayload\r\n"
                            + "$2\r\nEX\r\n"
                            + "$2\r\n60\r\n"
                            + "$2\r\nNX\r\n");
        } finally {
            commandBuffer.release();
        }

        String scanArgs = commandString(new ScanArgs().match("user:*").limit(25));
        assertThat(scanArgs).isEqualTo("MATCH dXNlcjoq COUNT 25");

        String geoArgs = commandString(new GeoArgs()
                .withDistance()
                .withCoordinates()
                .withHash()
                .withCount(5, true)
                .desc());
        assertThat(geoArgs).contains("WITHDIST", "WITHCOORD", "WITHHASH", "COUNT 5", "ANY", "desc");

        String sortArgs = commandString(new SortArgs()
                .by("weight_*")
                .limit(Limit.create(2, 4))
                .get("object_*")
                .get("#")
                .desc()
                .alpha());
        assertThat(sortArgs).isEqualTo("BY weight_* GET object_* GET # LIMIT 2 4 DESC ALPHA");

        String bitFieldArgs = commandString(new BitFieldArgs()
                .overflow(BitFieldArgs.OverflowType.SAT)
                .get(BitFieldArgs.signed(8), BitFieldArgs.offset(0))
                .set(BitFieldArgs.unsigned(4), BitFieldArgs.typeWidthBasedOffset(2), 7)
                .incrBy(BitFieldArgs.signed(16), BitFieldArgs.offset(4), -2));
        assertThat(bitFieldArgs).isEqualTo("OVERFLOW U0FU GET i8 0 SET u4 #2 7 INCRBY i16 4 -2");

        CommandArgs<String, String> streamArgs = new CommandArgs<>(StringCodec.UTF8);
        new XReadArgs().block(Duration.ofMillis(500)).count(10).build(streamArgs);
        assertThat(streamArgs.toCommandString()).isEqualTo("BLOCK 500 COUNT 10");
    }

    @Test
    void roundTripsCodecsAndCommandOutputs() {
        String unicodeValue = "caf\u00e9";
        ByteBuffer encodedString = StringCodec.UTF8.encodeValue(unicodeValue);
        assertThat(StringCodec.UTF8.decodeValue(encodedString)).isEqualTo(unicodeValue);

        byte[] bytes = new byte[] { 1, 2, 3, 4 };
        ByteBuffer encodedBytes = ByteArrayCodec.INSTANCE.encodeValue(bytes);
        assertThat(ByteArrayCodec.INSTANCE.decodeValue(encodedBytes)).containsExactly(bytes);

        RedisCodec<String, byte[]> mixedCodec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        assertThat(mixedCodec.decodeKey(utf8Buffer("key-one"))).isEqualTo("key-one");
        assertThat(mixedCodec.decodeValue(ByteBuffer.wrap(new byte[] { 9, 8, 7 }))).containsExactly(9, 8, 7);

        StatusOutput<String, String> statusOutput = new StatusOutput<>(StringCodec.UTF8);
        statusOutput.set(utf8Buffer("QUEUED"));
        assertThat(statusOutput.get()).isEqualTo("QUEUED");

        IntegerOutput<String, String> integerOutput = new IntegerOutput<>(StringCodec.UTF8);
        integerOutput.set(42L);
        assertThat(integerOutput.get()).isEqualTo(42L);

        ValueOutput<String, String> valueOutput = new ValueOutput<>(StringCodec.UTF8);
        valueOutput.set(utf8Buffer("stored-value"));
        assertThat(valueOutput.get()).isEqualTo("stored-value");

        Command<String, String, String> getCommand = new Command<>(CommandType.GET, valueOutput);
        getCommand.complete();
        assertThat(getCommand.isDone()).isTrue();
        assertThat(getCommand.get()).isEqualTo("stored-value");
    }

    @Test
    void mapsOptionalValueGeoScoreStreamAndRangeDomainObjects() {
        Value<String> value = Value.just("redis");
        assertThat(value.hasValue()).isTrue();
        assertThat(value.map(String::toUpperCase).getValue()).isEqualTo("REDIS");
        assertThat(Value.<String>empty().getValueOrElse("fallback")).isEqualTo("fallback");
        assertThatThrownBy(() -> Value.<String>empty().getValueOrElseThrow(() -> new IllegalStateException("missing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing");

        KeyValue<String, Integer> keyValue = KeyValue.just("counter", 41);
        assertThat(keyValue.getKey()).isEqualTo("counter");
        assertThat(keyValue.map(count -> count + 1).getValue()).isEqualTo(42);
        assertThat(KeyValue.empty("absent").isEmpty()).isTrue();

        ScoredValue<String> scoredValue = ScoredValue.just(12.5D, "alice");
        assertThat(scoredValue.getScore()).isEqualTo(12.5D);
        assertThat(scoredValue.map(String::toUpperCase).getValue()).isEqualTo("ALICE");
        assertThat(scoredValue.mapScore(score -> score.doubleValue() + 0.5D).getScore()).isEqualTo(13D);

        GeoCoordinates coordinates = GeoCoordinates.create(13.361389D, 38.115556D);
        GeoValue<String> geoValue = GeoValue.just(coordinates, "Palermo");
        GeoWithin<String> geoWithin = new GeoWithin<>("Palermo", 190.4424D, 3479099956230698L, coordinates);
        assertThat(geoValue.getLongitude()).isEqualTo(13.361389D);
        assertThat(geoValue.getLatitude()).isEqualTo(38.115556D);
        assertThat(geoWithin.getMember()).isEqualTo("Palermo");
        assertThat(geoWithin.toValue()).isEqualTo(geoValue);

        Map<String, String> streamBody = new LinkedHashMap<>();
        streamBody.put("sensor-id", "1234");
        streamBody.put("temperature", "21.5");
        StreamMessage<String, String> streamMessage = new StreamMessage<>("readings", "1650000000000-0", streamBody);
        assertThat(streamMessage.getStream()).isEqualTo("readings");
        assertThat(streamMessage.getBody()).containsEntry("temperature", "21.5");

        PendingMessage pendingMessage = new PendingMessage("1650000000000-0", "consumer-a", 1200L, 3L);
        assertThat(pendingMessage.getConsumer()).isEqualTo("consumer-a");
        assertThat(pendingMessage.getSinceLastDelivery()).isEqualTo(Duration.ofMillis(1200));
        assertThat(pendingMessage.getRedeliveryCount()).isEqualTo(3L);

        Range<Long> inclusiveRange = Range.create(10L, 20L);
        Range<Long> exclusiveLowerRange = Range.from(Range.Boundary.excluding(10L), Range.Boundary.including(20L));
        assertThat(inclusiveRange.getLower().isIncluding()).isTrue();
        assertThat(inclusiveRange.getUpper().getValue()).isEqualTo(20L);
        assertThat(exclusiveLowerRange.getLower().isIncluding()).isFalse();
        assertThat(Limit.unlimited().isLimited()).isFalse();
        assertThat(Limit.from(3).getOffset()).isZero();
        assertThat(Limit.from(3).getCount()).isEqualTo(3L);
    }

    @Test
    void calculatesClusterSlotsAndBuildsTopologyModels() {
        int userSlot = SlotHash.getSlot("{user1000}.following");
        assertThat(SlotHash.getSlot("{user1000}.followers")).isEqualTo(userSlot);
        assertThat(userSlot).isBetween(0, SlotHash.SLOT_COUNT - 1);

        RedisClusterNode upstream = new RedisClusterNode(
                RedisURI.create("redis://127.0.0.1:7000"),
                "node-a",
                true,
                null,
                1L,
                2L,
                3L,
                Arrays.asList(0, 1, 2),
                EnumSet.of(RedisClusterNode.NodeFlag.UPSTREAM));
        upstream.addAlias(RedisURI.create("redis://localhost:7000"));

        assertThat(upstream.isConnected()).isTrue();
        assertThat(upstream.hasSlot(1)).isTrue();
        assertThat(upstream.getRole()).isEqualTo(RedisInstance.Role.UPSTREAM);
        assertThat(upstream.getAliases()).extracting(RedisURI::getHost).containsExactly("localhost");

        RedisClusterNode replica = new RedisClusterNode(
                RedisURI.create("redis://127.0.0.2:7001"),
                "node-b",
                true,
                "node-a",
                0L,
                0L,
                3L,
                Collections.emptyList(),
                EnumSet.of(RedisClusterNode.NodeFlag.REPLICA));
        ClusterSlotRange slotRange = new ClusterSlotRange(0, 2, upstream, Collections.singletonList(replica));
        assertThat(slotRange.getFrom()).isZero();
        assertThat(slotRange.getTo()).isEqualTo(2);
        assertThat(slotRange.getUpstream()).isEqualTo(upstream);
        assertThat(slotRange.getReplicaNodes()).containsExactly(replica);

        Partitions partitions = new Partitions();
        partitions.addPartition(upstream);
        partitions.addPartition(replica);
        partitions.updateCache();
        assertThat(partitions.getPartitionBySlot(2)).isEqualTo(upstream);
        assertThat(partitions.getPartitionByNodeId("node-b")).isEqualTo(replica);
        assertThat(partitions.clone()).hasSize(2);

        List<Object> rawClusterSlots = Arrays.asList(
                Arrays.asList(
                        0L,
                        2L,
                        Arrays.asList("127.0.0.1", 7000L, "node-a"),
                        Arrays.asList("127.0.0.2", 7001L, "node-b")),
                Arrays.asList(
                        3L,
                        5L,
                        Arrays.asList("127.0.0.3", 7002L, "node-c")));
        List<ClusterSlotRange> parsedRanges = ClusterSlotsParser.parse(rawClusterSlots);

        assertThat(parsedRanges).hasSize(2);
        assertThat(parsedRanges.get(0).getFrom()).isEqualTo(0);
        assertThat(parsedRanges.get(0).getTo()).isEqualTo(2);
        assertThat(parsedRanges.get(0).getUpstream().getNodeId()).isEqualTo("node-a");
        assertThat(parsedRanges.get(0).getReplicaNodes())
                .extracting(RedisClusterNode::getNodeId)
                .containsExactly("node-b");
        assertThat(parsedRanges.get(1).getUpstream().getUri().getPort()).isEqualTo(7002);
    }

    private static String commandString(CompositeArgument argument) {
        CommandArgs<String, String> commandArgs = new CommandArgs<>(StringCodec.UTF8);
        argument.build(commandArgs);
        return commandArgs.toCommandString();
    }

    private static ByteBuffer utf8Buffer(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readBuffer(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
