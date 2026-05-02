/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty_incubator.netty_incubator_codec_classes_quic;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.incubator.codec.quic.DefaultQuicStreamFrame;
import io.netty.incubator.codec.quic.FlushStrategy;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QLogConfiguration;
import io.netty.incubator.codec.quic.Quic;
import io.netty.incubator.codec.quic.QuicChannelOption;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.incubator.codec.quic.QuicError;
import io.netty.incubator.codec.quic.QuicHeaderParser;
import io.netty.incubator.codec.quic.QuicPacketType;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamAddress;
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.incubator.codec.quic.SegmentedDatagramPacketAllocator;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_incubator_codec_classes_quicTest {
    @Test
    void quicAvailabilityContractIsSelfConsistent() {
        boolean available = Quic.isAvailable();
        Throwable cause = Quic.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(Quic::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(Quic.isVersionSupported(0)).isFalse();

        Throwable thrown = catchThrowable(Quic::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
    }

    @Test
    void streamFrameBufferOperationsPreserveFinAndContentSemantics() {
        QuicStreamFrame frame = new DefaultQuicStreamFrame(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}), true);
        QuicStreamFrame copy = frame.copy();
        QuicStreamFrame duplicate = frame.duplicate();
        QuicStreamFrame retainedDuplicate = frame.retainedDuplicate();
        QuicStreamFrame replacement = frame.replace(Unpooled.wrappedBuffer(new byte[] {9, 8, 7}));

        try {
            assertThat(frame.hasFin()).isTrue();
            assertThat(copy.hasFin()).isTrue();
            assertThat(duplicate.hasFin()).isTrue();
            assertThat(retainedDuplicate.hasFin()).isTrue();
            assertThat(replacement.hasFin()).isTrue();

            assertThat(frame.content().readableBytes()).isEqualTo(3);
            assertThat(copy.content()).isNotSameAs(frame.content());
            assertThat(duplicate.content()).isNotSameAs(frame.content());
            assertThat(retainedDuplicate.content()).isNotSameAs(frame.content());

            frame.content().setByte(0, 5);
            assertThat(copy.content().getByte(0)).isEqualTo((byte) 1);
            assertThat(duplicate.content().getByte(0)).isEqualTo((byte) 5);
            assertThat(retainedDuplicate.content().getByte(0)).isEqualTo((byte) 5);
            assertThat(replacement.content().getByte(0)).isEqualTo((byte) 9);

            assertThat(frame.refCnt()).isEqualTo(2);
            assertThat(frame.retain()).isSameAs(frame);
            assertThat(frame.refCnt()).isEqualTo(3);
            assertThat(frame.release()).isFalse();
            assertThat(frame.refCnt()).isEqualTo(2);
        } finally {
            copy.release();
            retainedDuplicate.release();
            replacement.release();
            frame.release();
        }
    }

    @Test
    void emptyFinFrameIsImmutableFinMarker() {
        QuicStreamFrame emptyFin = QuicStreamFrame.EMPTY_FIN;

        assertThat(emptyFin.hasFin()).isTrue();
        assertThat(emptyFin.content().readableBytes()).isZero();
        assertThat(emptyFin.copy().hasFin()).isTrue();
        assertThat(emptyFin.duplicate().hasFin()).isTrue();
        assertThat(emptyFin.retainedDuplicate().hasFin()).isTrue();
        assertThatThrownBy(() -> emptyFin.content().writeByte(1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void flushStrategiesApplyStrictPacketAndByteThresholds() {
        FlushStrategy bytes = FlushStrategy.afterNumBytes(32);
        FlushStrategy packets = FlushStrategy.afterNumPackets(3);

        assertThat(bytes.shouldFlushNow(100, 31)).isFalse();
        assertThat(bytes.shouldFlushNow(1, 32)).isFalse();
        assertThat(bytes.shouldFlushNow(1, 33)).isTrue();

        assertThat(packets.shouldFlushNow(3, 10_000)).isFalse();
        assertThat(packets.shouldFlushNow(4, 0)).isTrue();

        assertThat(FlushStrategy.DEFAULT.shouldFlushNow(1, 27_000)).isFalse();
        assertThat(FlushStrategy.DEFAULT.shouldFlushNow(1, 27_001)).isTrue();

        assertThatThrownBy(() -> FlushStrategy.afterNumBytes(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlushStrategy.afterNumPackets(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quicChannelOptionsAreRegisteredInNettyConstantPool() {
        List<ChannelOption<?>> options = List.of(
                QuicChannelOption.READ_FRAMES,
                QuicChannelOption.QLOG,
                QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());
        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void qlogConfigurationAndStreamPriorityExposeStableValueSemantics() {
        QLogConfiguration qlog = new QLogConfiguration("/tmp/qlog", "integration", "quic test run");
        assertThat(qlog.path()).isEqualTo("/tmp/qlog");
        assertThat(qlog.logTitle()).isEqualTo("integration");
        assertThat(qlog.logDescription()).isEqualTo("quic test run");

        QuicStreamPriority priority = new QuicStreamPriority(7, true);
        QuicStreamPriority samePriority = new QuicStreamPriority(7, true);
        QuicStreamPriority differentPriority = new QuicStreamPriority(8, true);

        assertThat(priority.urgency()).isEqualTo(7);
        assertThat(priority.isIncremental()).isTrue();
        assertThat(priority).isEqualTo(samePriority).hasSameHashCodeAs(samePriority);
        assertThat(priority).isNotEqualTo(differentPriority);
        assertThat(priority).hasToString("QuicStreamPriority{urgency=7, incremental=true}");
        assertThatThrownBy(() -> new QuicStreamPriority(-1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuicStreamPriority(128, false)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new QLogConfiguration(null, "title", "description"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QLogConfiguration("path", null, "description"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QLogConfiguration("path", "title", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void segmentedDatagramAllocatorNoneRejectsPacketAllocation() {
        ByteBuf content = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4});
        try {
            assertThat(SegmentedDatagramPacketAllocator.NONE.maxNumSegments()).isZero();
            assertThatThrownBy(() -> SegmentedDatagramPacketAllocator.NONE.newPacket(
                    content,
                    2,
                    new InetSocketAddress("127.0.0.1", 9999)
            )).isInstanceOf(UnsupportedOperationException.class);
        } finally {
            content.release();
        }
    }

    @Test
    void simpleEnumsExposeExpectedProtocolConstants() {
        assertThat(QuicStreamType.values()).containsExactly(
                QuicStreamType.UNIDIRECTIONAL,
                QuicStreamType.BIDIRECTIONAL
        );
        assertThat(QuicStreamType.valueOf("UNIDIRECTIONAL")).isSameAs(QuicStreamType.UNIDIRECTIONAL);
        assertThat(QuicStreamType.valueOf("BIDIRECTIONAL")).isSameAs(QuicStreamType.BIDIRECTIONAL);

        assertThat(QuicPacketType.values()).containsExactly(
                QuicPacketType.INITIAL,
                QuicPacketType.RETRY,
                QuicPacketType.HANDSHAKE,
                QuicPacketType.ZERO_RTT,
                QuicPacketType.SHORT,
                QuicPacketType.VERSION_NEGOTIATION
        );
        assertThat(QuicPacketType.valueOf("SHORT")).isSameAs(QuicPacketType.SHORT);
    }

    @Test
    void nativeBackedEnumsExposeMappingsWhenNativeLibraryIsAvailable() {
        if (!Quic.isAvailable()) {
            assertMayRequireNativeLibrary(() -> QuicError.values());
            assertMayRequireNativeLibrary(() -> QuicCongestionControlAlgorithm.values());
            return;
        }

        assertThat(QuicError.values()).containsExactly(
                QuicError.BUFFER_TOO_SHORT,
                QuicError.UNKNOWN_VERSION,
                QuicError.INVALID_FRAME,
                QuicError.INVALID_PACKET,
                QuicError.INVALID_STATE,
                QuicError.INVALID_STREAM_STATE,
                QuicError.INVALID_TRANSPORT_PARAM,
                QuicError.CRYPTO_FAIL,
                QuicError.TLS_FAIL,
                QuicError.FLOW_CONTROL,
                QuicError.STREAM_LIMIT,
                QuicError.FINAL_SIZE,
                QuicError.CONGESTION_CONTROL,
                QuicError.STREAM_RESET,
                QuicError.STREAM_STOPPED
        );
        assertThat(QuicError.INVALID_PACKET.toString()).contains("INVALID_PACKET");
        assertThat(QuicCongestionControlAlgorithm.values()).containsExactly(
                QuicCongestionControlAlgorithm.RENO,
                QuicCongestionControlAlgorithm.CUBIC
        );
    }

    @Test
    void quicHeaderParserExtractsVersionNegotiationHeader() throws Exception {
        if (!Quic.isAvailable()) {
            assertNativeBackedTypeUnavailable(() -> new QuicHeaderParser(0, 4));
            return;
        }

        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 4433);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 9443);
        byte[] destinationConnectionId = new byte[] {1, 2, 3, 4};
        byte[] sourceConnectionId = new byte[] {5, 6, 7};
        boolean[] processed = {false};
        ByteBuf packet = Unpooled.directBuffer();
        packet.writeByte(0x80);
        packet.writeInt(0);
        packet.writeByte(destinationConnectionId.length);
        packet.writeBytes(destinationConnectionId);
        packet.writeByte(sourceConnectionId.length);
        packet.writeBytes(sourceConnectionId);
        packet.writeInt(1);

        try (QuicHeaderParser parser = new QuicHeaderParser(0, destinationConnectionId.length)) {
            parser.parse(sender, recipient, packet, (local, remote, payload, type, version, scid, dcid, token) -> {
                assertThat(local).isSameAs(sender);
                assertThat(remote).isSameAs(recipient);
                assertThat(payload).isSameAs(packet);
                assertThat(type).isSameAs(QuicPacketType.VERSION_NEGOTIATION);
                assertThat(version).isZero();
                assertThat(readBytes(dcid)).containsExactly(destinationConnectionId);
                assertThat(readBytes(scid)).containsExactly(sourceConnectionId);
                assertThat(token.readableBytes()).isZero();
                processed[0] = true;
            });
        } finally {
            packet.release();
        }

        assertThat(processed[0]).isTrue();
    }

    @Test
    void streamAddressIdentifiesStreamsByTheirNumericStreamId() {
        long streamId = 1_234;
        QuicStreamAddress address = new QuicStreamAddress(streamId);
        QuicStreamAddress sameAddress = new QuicStreamAddress(streamId);
        QuicStreamAddress differentAddress = new QuicStreamAddress(streamId + 1);
        SocketAddress socketAddress = address;

        assertThat(socketAddress).isSameAs(address);
        assertThat(address.streamId()).isEqualTo(streamId);
        assertThat(address).isEqualTo(sameAddress).hasSameHashCodeAs(sameAddress);
        assertThat(address).isNotEqualTo(differentAddress);
        assertThat(address).isNotEqualTo(new InetSocketAddress("127.0.0.1", 4433));
        assertThat(address).hasToString("QuicStreamAddress{streamId=1234}");
        assertThat(new QuicStreamAddress(Long.MAX_VALUE).streamId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void nativeBackedAddressAndTokenTypesRespectTransportAvailability() {
        if (!Quic.isAvailable()) {
            assertNativeBackedTypeUnavailable(() -> QuicConnectionAddress.EPHEMERAL.toString());
            assertNativeBackedTypeUnavailable(() -> InsecureQuicTokenHandler.INSTANCE.maxTokenLength());
            return;
        }

        byte[] id = new byte[] {1, 2, 3, 4};
        QuicConnectionAddress address = new QuicConnectionAddress(id);
        QuicConnectionAddress sameAddress = new QuicConnectionAddress(new byte[] {1, 2, 3, 4});
        QuicConnectionAddress differentAddress = new QuicConnectionAddress(new byte[] {1, 2, 3, 5});
        id[0] = 9;

        assertThat(QuicConnectionAddress.EPHEMERAL).isSameAs(QuicConnectionAddress.EPHEMERAL);
        assertThat(QuicConnectionAddress.EPHEMERAL).isNotEqualTo(address);
        assertThat(QuicConnectionAddress.EPHEMERAL).hasToString("QuicConnectionAddress{EPHEMERAL}");
        assertThat(address).isEqualTo(sameAddress).hasSameHashCodeAs(sameAddress);
        assertThat(address).isNotEqualTo(differentAddress);
        assertThat(address).hasToString("QuicConnectionAddress{connId=01020304}");

        assertInsecureTokenHandlerRoundTrip();
    }

    @Test
    void nativeBackedConnectionIdGeneratorsRespectTransportAvailability() {
        QuicConnectionIdGenerator random = QuicConnectionIdGenerator.randomGenerator();
        QuicConnectionIdGenerator signer = QuicConnectionIdGenerator.signGenerator();

        assertThat(random.isIdempotent()).isFalse();
        assertThat(signer.isIdempotent()).isTrue();

        if (!Quic.isAvailable()) {
            assertMayRequireNativeLibrary(random::maxConnectionIdLength);
            assertMayRequireNativeLibrary(signer::maxConnectionIdLength);
            return;
        }

        assertConnectionIdGeneratorsExposeNativeBackedState(random, signer);
    }

    @Test
    void nativeBackedCodecBuildersRespectTransportAvailability() {
        if (!Quic.isAvailable()) {
            assertNativeBackedTypeUnavailable(QuicClientCodecBuilder::new);
            assertNativeBackedTypeUnavailable(QuicServerCodecBuilder::new);
            return;
        }

        QuicClientCodecBuilder clientBuilder = new QuicClientCodecBuilder()
                .flushStrategy(FlushStrategy.afterNumPackets(2))
                .grease(true)
                .maxSendUdpPayloadSize(1_200)
                .maxRecvUdpPayloadSize(1_200)
                .initialMaxData(8_192)
                .initialMaxStreamsBidirectional(4)
                .initialMaxStreamsUnidirectional(2);
        QuicServerCodecBuilder serverBuilder = new QuicServerCodecBuilder()
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .connectionIdAddressGenerator(QuicConnectionIdGenerator.signGenerator())
                .option(QuicChannelOption.READ_FRAMES, true)
                .streamOption(QuicChannelOption.READ_FRAMES, true);

        assertThat(clientBuilder.clone()).isNotSameAs(clientBuilder).isInstanceOf(QuicClientCodecBuilder.class);
        assertThat(serverBuilder.clone()).isNotSameAs(serverBuilder).isInstanceOf(QuicServerCodecBuilder.class);
        assertThatThrownBy(() -> clientBuilder.localConnectionIdLength(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serverBuilder.statelessResetToken(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] readBytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    private static void assertConnectionIdGeneratorsExposeNativeBackedState(
            QuicConnectionIdGenerator random,
            QuicConnectionIdGenerator signer
    ) {
        int idLength = Math.min(8, random.maxConnectionIdLength());
        ByteBuffer input = ByteBuffer.wrap(new byte[] {9, 8, 7, 6, 5, 4});

        ByteBuffer randomId = random.newId(idLength);
        ByteBuffer signedId = signer.newId(input.duplicate(), idLength);
        ByteBuffer signedIdAgain = signer.newId(input.duplicate(), idLength);

        assertThat(randomId.remaining()).isEqualTo(idLength);
        assertThat(signedId.remaining()).isEqualTo(idLength);
        assertThat(signedIdAgain).isEqualTo(signedId.duplicate());
        assertThatThrownBy(() -> random.newId(random.maxConnectionIdLength() + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.newId(idLength)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> signer.newId(ByteBuffer.allocate(0), idLength))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInsecureTokenHandlerRoundTrip() {
        ByteBuf token = Unpooled.buffer(InsecureQuicTokenHandler.INSTANCE.maxTokenLength());
        ByteBuf destinationConnectionId = Unpooled.wrappedBuffer(new byte[] {10, 11, 12, 13});
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 4433);
        InetSocketAddress differentAddress = new InetSocketAddress("127.0.0.2", 4433);

        try {
            assertThat(InsecureQuicTokenHandler.INSTANCE.writeToken(token, destinationConnectionId, address)).isTrue();
            assertThat(token.readableBytes()).isEqualTo("netty".length() + 4 + 4);
            assertThat(destinationConnectionId.readerIndex()).isZero();
            assertThat(InsecureQuicTokenHandler.INSTANCE.validateToken(token, address))
                    .isEqualTo("netty".length() + 4);
            assertThat(InsecureQuicTokenHandler.INSTANCE.validateToken(token, differentAddress)).isEqualTo(-1);
        } finally {
            destinationConnectionId.release();
            token.release();
        }
    }

    private static void assertMayRequireNativeLibrary(ThrowableAssert.ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);

        if (thrown != null) {
            assertThat(thrown).isInstanceOf(LinkageError.class);
        }
    }

    private static void assertNativeBackedTypeUnavailable(ThrowableAssert.ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);

        assertThat(thrown).isInstanceOf(LinkageError.class);
    }
}
