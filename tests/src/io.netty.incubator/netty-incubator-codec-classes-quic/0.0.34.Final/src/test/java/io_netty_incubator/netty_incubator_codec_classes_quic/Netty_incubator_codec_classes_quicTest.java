/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty_incubator.netty_incubator_codec_classes_quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramPacket;
import io.netty.incubator.codec.quic.BoringSSLAsyncPrivateKeyMethod;
import io.netty.incubator.codec.quic.DefaultQuicStreamFrame;
import io.netty.incubator.codec.quic.FlushStrategy;
import io.netty.incubator.codec.quic.QLogConfiguration;
import io.netty.incubator.codec.quic.Quic;
import io.netty.incubator.codec.quic.QuicChannelOption;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.incubator.codec.quic.QuicPacketType;
import io.netty.incubator.codec.quic.QuicStreamAddress;
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.incubator.codec.quic.SegmentedDatagramPacketAllocator;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_incubator_codec_classes_quicTest {
    @Test
    void quicAvailabilityReportsNativeStateConsistently() {
        boolean available = Quic.isAvailable();
        Throwable cause = Quic.unavailabilityCause();

        if (available) {
            assertThat(cause).isNull();
            assertThatCode(Quic::ensureAvailability).doesNotThrowAnyException();
        } else {
            assertThat(cause).isNotNull();
            assertThatThrownBy(Quic::ensureAvailability)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasCause(cause);
        }
        assertThat(Quic.isVersionSupported(0)).isFalse();
    }

    @Test
    void streamFramePreservesFinFlagAndByteBufHolderSemantics() {
        ByteBuf data = Unpooled.buffer(3).writeBytes(new byte[] {1, 2, 3});
        DefaultQuicStreamFrame frame = new DefaultQuicStreamFrame(data, true);
        QuicStreamFrame copy = null;
        QuicStreamFrame retainedDuplicate = null;
        QuicStreamFrame replacement = null;
        QuicStreamFrame emptyReplacement = null;
        try {
            assertThat(frame.hasFin()).isTrue();
            assertThat(frame.content().readableBytes()).isEqualTo(3);
            assertThat(frame.content().getUnsignedByte(0)).isEqualTo((short) 1);

            copy = frame.copy();
            QuicStreamFrame duplicate = frame.duplicate();
            retainedDuplicate = frame.retainedDuplicate();
            replacement = frame.replace(Unpooled.wrappedBuffer(new byte[] {8, 9}));

            frame.content().setByte(0, 7);
            assertThat(copy.hasFin()).isTrue();
            assertThat(copy.content().getUnsignedByte(0)).isEqualTo((short) 1);
            assertThat(duplicate.hasFin()).isTrue();
            assertThat(duplicate.content().getUnsignedByte(0)).isEqualTo((short) 7);
            assertThat(retainedDuplicate.content().getUnsignedByte(0)).isEqualTo((short) 7);
            assertThat(replacement.hasFin()).isTrue();
            assertThat(replacement.content().readableBytes()).isEqualTo(2);
            assertThat(replacement.content().getUnsignedByte(0)).isEqualTo((short) 8);

            assertThat(frame.retain()).isSameAs(frame);
            assertThat(frame.touch("quic-frame")).isSameAs(frame);
            assertThat(frame.release()).isFalse();

            assertThat(QuicStreamFrame.EMPTY_FIN.hasFin()).isTrue();
            assertThat(QuicStreamFrame.EMPTY_FIN.content().readableBytes()).isZero();
            assertThat(QuicStreamFrame.EMPTY_FIN.copy()).isSameAs(QuicStreamFrame.EMPTY_FIN);
            assertThat(QuicStreamFrame.EMPTY_FIN.release()).isFalse();
            emptyReplacement = QuicStreamFrame.EMPTY_FIN.replace(Unpooled.wrappedBuffer(new byte[] {42}));
            assertThat(emptyReplacement.hasFin()).isTrue();
            assertThat(emptyReplacement.content().getUnsignedByte(0)).isEqualTo((short) 42);
        } finally {
            if (retainedDuplicate != null) {
                retainedDuplicate.release();
            }
            if (copy != null) {
                copy.release();
            }
            if (replacement != null) {
                replacement.release();
            }
            if (emptyReplacement != null) {
                emptyReplacement.release();
            }
            frame.release();
        }
    }

    @Test
    void flushStrategiesTriggerOnlyAfterConfiguredThresholds() {
        FlushStrategy bytes = FlushStrategy.afterNumBytes(8);
        assertThat(bytes.shouldFlushNow(100, 8)).isFalse();
        assertThat(bytes.shouldFlushNow(0, 9)).isTrue();

        FlushStrategy packets = FlushStrategy.afterNumPackets(2);
        assertThat(packets.shouldFlushNow(2, 0)).isFalse();
        assertThat(packets.shouldFlushNow(3, 0)).isTrue();
        assertThat(FlushStrategy.DEFAULT.shouldFlushNow(0, 0)).isFalse();

        assertThatIllegalArgumentException().isThrownBy(() -> FlushStrategy.afterNumBytes(0));
        assertThatIllegalArgumentException().isThrownBy(() -> FlushStrategy.afterNumPackets(-1));
    }

    @Test
    void valueObjectsExposeStableStateEqualityAndValidation() {
        QLogConfiguration qlog = new QLogConfiguration("/tmp/session.qlog", "title", "description");
        assertThat(qlog.path()).isEqualTo("/tmp/session.qlog");
        assertThat(qlog.logTitle()).isEqualTo("title");
        assertThat(qlog.logDescription()).isEqualTo("description");
        assertThatNullPointerException().isThrownBy(() -> new QLogConfiguration(null, "title", "description"));
        assertThatNullPointerException().isThrownBy(() -> new QLogConfiguration("/tmp/session.qlog", null,
                "description"));

        QuicStreamAddress address = new QuicStreamAddress(1234);
        assertThat(address.streamId()).isEqualTo(1234);
        assertThat(address).isEqualTo(new QuicStreamAddress(1234));
        assertThat(address).hasSameHashCodeAs(new QuicStreamAddress(1234));
        assertThat(address).isNotEqualTo(new QuicStreamAddress(5678));
        assertThat(address.toString()).contains("streamId=1234");

        QuicStreamPriority priority = new QuicStreamPriority(10, true);
        assertThat(priority.urgency()).isEqualTo(10);
        assertThat(priority.isIncremental()).isTrue();
        assertThat(priority).isEqualTo(new QuicStreamPriority(10, true));
        assertThat(priority).isNotEqualTo(new QuicStreamPriority(10, false));
        assertThat(priority.toString()).contains("urgency=10", "incremental=true");
        assertThatIllegalArgumentException().isThrownBy(() -> new QuicStreamPriority(-1, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new QuicStreamPriority(128, false));
    }

    @Test
    void publicEnumsAndChannelOptionsExposeExpectedConstants() {
        assertThat(QuicPacketType.values()).containsExactly(
                QuicPacketType.INITIAL,
                QuicPacketType.RETRY,
                QuicPacketType.HANDSHAKE,
                QuicPacketType.ZERO_RTT,
                QuicPacketType.SHORT,
                QuicPacketType.VERSION_NEGOTIATION);
        assertThat(QuicPacketType.valueOf("ZERO_RTT")).isSameAs(QuicPacketType.ZERO_RTT);
        assertThat(QuicStreamType.values()).containsExactly(
                QuicStreamType.UNIDIRECTIONAL,
                QuicStreamType.BIDIRECTIONAL);
        assertThat(QuicCongestionControlAlgorithm.values()).containsExactly(
                QuicCongestionControlAlgorithm.RENO,
                QuicCongestionControlAlgorithm.CUBIC);

        ChannelOption<Boolean> readFrames = QuicChannelOption.READ_FRAMES;
        ChannelOption<QLogConfiguration> qlog = QuicChannelOption.QLOG;
        ChannelOption<SegmentedDatagramPacketAllocator> segmentedAllocator =
                QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR;
        assertThat(readFrames.name()).endsWith("#READ_FRAMES");
        assertThat(qlog.name()).endsWith("#QLOG");
        assertThat(segmentedAllocator.name()).endsWith("#SEGMENTED_DATAGRAM_PACKET_ALLOCATOR");
    }

    @Test
    void connectionIdGeneratorsExposeRandomAndSignedStrategies() {
        QuicConnectionIdGenerator random = QuicConnectionIdGenerator.randomGenerator();
        assertThat(random.isIdempotent()).isFalse();

        QuicConnectionIdGenerator signed = QuicConnectionIdGenerator.signGenerator();
        assertThat(signed.isIdempotent()).isTrue();
        assertThatThrownBy(() -> signed.newId(8))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("input to sign with");
    }

    @Test
    void asyncPrivateKeyMethodSupportsSigningAndDecryptingWithNettyFutures() throws Exception {
        BoringSSLAsyncPrivateKeyMethod privateKeyMethod = new BoringSSLAsyncPrivateKeyMethod() {
            @Override
            public Future<byte[]> sign(SSLEngine engine, int signatureAlgorithm, byte[] input) {
                byte[] signature = Arrays.copyOf(input, input.length + 1);
                signature[input.length] = (byte) signatureAlgorithm;
                return ImmediateEventExecutor.INSTANCE.newSucceededFuture(signature);
            }

            @Override
            public Future<byte[]> decrypt(SSLEngine engine, byte[] input) {
                byte[] decrypted = input.clone();
                for (int i = 0; i < decrypted.length / 2; i++) {
                    byte tmp = decrypted[i];
                    decrypted[i] = decrypted[decrypted.length - 1 - i];
                    decrypted[decrypted.length - 1 - i] = tmp;
                }
                return ImmediateEventExecutor.INSTANCE.newSucceededFuture(decrypted);
            }
        };

        int signatureAlgorithm = 1234;
        Future<byte[]> signatureFuture = privateKeyMethod.sign(null, signatureAlgorithm, new byte[] {1, 2, 3});
        Future<byte[]> decryptedFuture = privateKeyMethod.decrypt(null, new byte[] {4, 5, 6});

        assertThat(signatureFuture.isSuccess()).isTrue();
        assertThat(signatureFuture.get()).containsExactly(1, 2, 3, (byte) signatureAlgorithm);
        assertThat(decryptedFuture.isSuccess()).isTrue();
        assertThat(decryptedFuture.get()).containsExactly(6, 5, 4);
    }

    @Test
    void segmentedDatagramAllocatorContractsAreUsableWithNettyPackets() {
        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 9999);
        SegmentedDatagramPacketAllocator allocator = (buffer, segmentSize, recipient) ->
                new DatagramPacket(buffer, recipient);
        ByteBuf content = Unpooled.buffer(4).writeInt(0x10203040);
        DatagramPacket packet = allocator.newPacket(content, 2, remoteAddress);
        try {
            assertThat(allocator.maxNumSegments()).isEqualTo(10);
            assertThat(packet.recipient()).isEqualTo(remoteAddress);
            assertThat(packet.content().getInt(0)).isEqualTo(0x10203040);
        } finally {
            packet.release();
        }

        assertThat(SegmentedDatagramPacketAllocator.NONE.maxNumSegments()).isZero();
        assertThatThrownBy(() -> SegmentedDatagramPacketAllocator.NONE.newPacket(
                Unpooled.EMPTY_BUFFER, 1, remoteAddress)).isInstanceOf(UnsupportedOperationException.class);
    }
}
