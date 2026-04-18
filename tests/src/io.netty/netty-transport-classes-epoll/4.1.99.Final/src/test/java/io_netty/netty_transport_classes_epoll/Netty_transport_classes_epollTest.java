/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_classes_epoll;

import java.net.InetSocketAddress;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.epoll.EpollTcpInfo;
import io.netty.channel.epoll.SegmentedDatagramPacket;
import io.netty.channel.epoll.VSockAddress;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class Netty_transport_classes_epollTest {

    @Test
    void epollAvailabilityStateIsSelfConsistent() {
        if (Epoll.isAvailable()) {
            assertThat(Epoll.unavailabilityCause()).isNull();
            assertThatCode(Epoll::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(Epoll.unavailabilityCause())
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("netty_transport_native_epoll");
        assertThatThrownBy(Epoll::ensureAvailability)
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("failed to load the required native library");
        assertThat(Epoll.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(Epoll.isTcpFastOpenServerSideAvailable()).isFalse();
        assertThat(EpollDatagramChannel.isSegmentedDatagramPacketSupported()).isFalse();
    }

    @Test
    void epollConstantsAndValueObjectsExposeStablePublicState() {
        assertThat(EpollMode.values()).containsExactly(EpollMode.EDGE_TRIGGERED, EpollMode.LEVEL_TRIGGERED);
        assertThat(EpollMode.valueOf("EDGE_TRIGGERED")).isSameAs(EpollMode.EDGE_TRIGGERED);
        assertThat(EpollMode.valueOf("LEVEL_TRIGGERED")).isSameAs(EpollMode.LEVEL_TRIGGERED);

        List<ChannelOption<?>> options = List.of(
                EpollChannelOption.TCP_CORK,
                EpollChannelOption.TCP_NOTSENT_LOWAT,
                EpollChannelOption.TCP_KEEPIDLE,
                EpollChannelOption.TCP_KEEPINTVL,
                EpollChannelOption.TCP_KEEPCNT,
                EpollChannelOption.TCP_USER_TIMEOUT,
                EpollChannelOption.IP_FREEBIND,
                EpollChannelOption.IP_TRANSPARENT,
                EpollChannelOption.IP_RECVORIGDSTADDR,
                EpollChannelOption.TCP_DEFER_ACCEPT,
                EpollChannelOption.TCP_QUICKACK,
                EpollChannelOption.SO_BUSY_POLL,
                EpollChannelOption.EPOLL_MODE,
                EpollChannelOption.TCP_MD5SIG,
                EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE,
                EpollChannelOption.UDP_GRO);
        assertThat(options).doesNotContainNull();
        assertThat(options).extracting(ChannelOption::name).doesNotHaveDuplicates();
        assertThat(options).extracting(ChannelOption::name).anySatisfy(name -> assertThat(name).endsWith("TCP_CORK"));
        assertThat(options).extracting(ChannelOption::name).anySatisfy(name -> assertThat(name).endsWith("EPOLL_MODE"));
        assertThat(options).extracting(ChannelOption::name).contains("TCP_MD5SIG", "UDP_GRO");

        VSockAddress address = new VSockAddress(VSockAddress.VMADDR_CID_HOST, 8080);
        VSockAddress sameAddress = new VSockAddress(VSockAddress.VMADDR_CID_HOST, 8080);
        VSockAddress differentAddress = new VSockAddress(VSockAddress.VMADDR_CID_LOCAL, 8080);
        assertThat(address).isEqualTo(sameAddress).hasSameHashCodeAs(sameAddress);
        assertThat(address).isNotEqualTo(differentAddress);
        assertThat(address.getCid()).isEqualTo(VSockAddress.VMADDR_CID_HOST);
        assertThat(address.getPort()).isEqualTo(8080);
        assertThat(address.toString()).contains("cid=" + VSockAddress.VMADDR_CID_HOST, "port=8080");

        EpollTcpInfo tcpInfo = new EpollTcpInfo();
        assertThat(readTcpInfo(tcpInfo)).containsOnly(0L);
    }

    @Test
    void vSockWildcardAddressesExposeSpecialEndpointConstants() {
        VSockAddress wildcardAddress = new VSockAddress(
                VSockAddress.VMADDR_CID_ANY,
                VSockAddress.VMADDR_PORT_ANY);
        VSockAddress sameWildcardAddress = new VSockAddress(
                VSockAddress.VMADDR_CID_ANY,
                VSockAddress.VMADDR_PORT_ANY);
        VSockAddress hostWildcardPortAddress = new VSockAddress(
                VSockAddress.VMADDR_CID_HOST,
                VSockAddress.VMADDR_PORT_ANY);
        VSockAddress wildcardCidConcretePortAddress = new VSockAddress(VSockAddress.VMADDR_CID_ANY, 9000);

        assertThat(wildcardAddress).isEqualTo(sameWildcardAddress).hasSameHashCodeAs(sameWildcardAddress);
        assertThat(wildcardAddress)
                .isNotEqualTo(hostWildcardPortAddress)
                .isNotEqualTo(wildcardCidConcretePortAddress)
                .isNotEqualTo("not-an-address");
        assertThat(wildcardAddress.getCid()).isEqualTo(VSockAddress.VMADDR_CID_ANY);
        assertThat(wildcardAddress.getPort()).isEqualTo(VSockAddress.VMADDR_PORT_ANY);
        assertThat(wildcardAddress.toString())
                .contains("cid=" + VSockAddress.VMADDR_CID_ANY, "port=" + VSockAddress.VMADDR_PORT_ANY);
        assertThat(List.of(
                VSockAddress.VMADDR_CID_ANY,
                VSockAddress.VMADDR_CID_HYPERVISOR,
                VSockAddress.VMADDR_CID_LOCAL,
                VSockAddress.VMADDR_CID_HOST))
                .doesNotHaveDuplicates();
    }

    @Test
    void segmentedDatagramPacketSupportAndCopiesBehaveConsistently() {
        assertThat(SegmentedDatagramPacket.isSupported())
                .isEqualTo(EpollDatagramChannel.isSegmentedDatagramPacketSupported());

        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 8081);
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 8082);

        if (!SegmentedDatagramPacket.isSupported()) {
            assertThatThrownBy(() -> new SegmentedDatagramPacket(Unpooled.EMPTY_BUFFER, 1, recipient, sender))
                    .isInstanceOf(IllegalStateException.class);
            return;
        }

        SegmentedDatagramPacket packet = null;
        SegmentedDatagramPacket copiedPacket = null;
        SegmentedDatagramPacket replacedPacket = null;
        try {
            packet = new SegmentedDatagramPacket(
                    Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 }),
                    2,
                    recipient,
                    sender);
            assertThat(packet.segmentSize()).isEqualTo(2);
            assertThat(packet.recipient()).isEqualTo(recipient);
            assertThat(packet.sender()).isEqualTo(sender);
            assertThat(packet.content().readableBytes()).isEqualTo(4);

            copiedPacket = packet.copy();
            assertThat(copiedPacket).isNotSameAs(packet);
            assertThat(copiedPacket.segmentSize()).isEqualTo(packet.segmentSize());
            assertThat(copiedPacket.recipient()).isEqualTo(recipient);
            assertThat(copiedPacket.sender()).isEqualTo(sender);
            assertThat(copiedPacket.content()).isNotSameAs(packet.content());
            assertThat(copiedPacket.content().getByte(0)).isEqualTo((byte) 1);

            packet.content().setByte(0, 9);
            assertThat(packet.content().getByte(0)).isEqualTo((byte) 9);
            assertThat(copiedPacket.content().getByte(0)).isEqualTo((byte) 1);

            replacedPacket = packet.replace(Unpooled.wrappedBuffer(new byte[] { 7, 8, 9 }));
            assertThat(replacedPacket).isNotSameAs(packet);
            assertThat(replacedPacket.segmentSize()).isEqualTo(packet.segmentSize());
            assertThat(replacedPacket.recipient()).isEqualTo(recipient);
            assertThat(replacedPacket.sender()).isEqualTo(sender);
            assertThat(replacedPacket.content().readableBytes()).isEqualTo(3);
            assertThat(replacedPacket.content().getByte(0)).isEqualTo((byte) 7);
            assertThat(replacedPacket.content().getByte(2)).isEqualTo((byte) 9);
        } finally {
            ReferenceCountUtil.safeRelease(replacedPacket);
            ReferenceCountUtil.safeRelease(copiedPacket);
            ReferenceCountUtil.safeRelease(packet);
        }
    }

    @Test
    void nativeChannelConstructorsFailCleanlyWithoutTheNativeEpollArtifact() {
        assumeFalse(Epoll.isAvailable(), "This test only applies when the native epoll library is absent");

        assertNativeLibraryFailure(() -> new EpollEventLoopGroup(), "failed to load the required native library");
        assertNativeLibraryFailure(() -> new EpollSocketChannel(), "newSocketStreamFd");
        assertNativeLibraryFailure(() -> new EpollServerSocketChannel(), "newSocketStreamFd");
        assertNativeLibraryFailure(() -> new EpollDatagramChannel(), "newSocketDgramFd");
        assertNativeLibraryFailure(() -> new EpollDomainSocketChannel(), "newSocketDomainFd");
        assertNativeLibraryFailure(() -> new EpollServerDomainSocketChannel(), "newSocketDomainFd");
        assertNativeLibraryFailure(() -> new EpollDomainDatagramChannel(), "newSocketDomainDgramFd");
    }

    private static void assertNativeLibraryFailure(ThrowingConstructor constructor, String messageFragment) {
        assertThatThrownBy(constructor::construct)
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining(messageFragment);
    }

    private static long[] readTcpInfo(EpollTcpInfo tcpInfo) {
        return new long[] {
                tcpInfo.state(),
                tcpInfo.caState(),
                tcpInfo.retransmits(),
                tcpInfo.probes(),
                tcpInfo.backoff(),
                tcpInfo.options(),
                tcpInfo.sndWscale(),
                tcpInfo.rcvWscale(),
                tcpInfo.rto(),
                tcpInfo.ato(),
                tcpInfo.sndMss(),
                tcpInfo.rcvMss(),
                tcpInfo.unacked(),
                tcpInfo.sacked(),
                tcpInfo.lost(),
                tcpInfo.retrans(),
                tcpInfo.fackets(),
                tcpInfo.lastDataSent(),
                tcpInfo.lastAckSent(),
                tcpInfo.lastDataRecv(),
                tcpInfo.lastAckRecv(),
                tcpInfo.pmtu(),
                tcpInfo.rcvSsthresh(),
                tcpInfo.rtt(),
                tcpInfo.rttvar(),
                tcpInfo.sndSsthresh(),
                tcpInfo.sndCwnd(),
                tcpInfo.advmss(),
                tcpInfo.reordering(),
                tcpInfo.rcvRtt(),
                tcpInfo.rcvSpace(),
                tcpInfo.totalRetrans()
        };
    }

    @FunctionalInterface
    private interface ThrowingConstructor {
        Object construct();
    }
}
