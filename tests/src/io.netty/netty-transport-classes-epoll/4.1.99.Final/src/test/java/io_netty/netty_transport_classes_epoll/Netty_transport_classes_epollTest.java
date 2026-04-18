/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_classes_epoll;

import java.util.List;

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
import io.netty.channel.epoll.VSockAddress;
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
