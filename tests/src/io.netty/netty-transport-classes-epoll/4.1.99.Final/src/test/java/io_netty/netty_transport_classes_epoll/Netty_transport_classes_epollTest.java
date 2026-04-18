/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_classes_epoll;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.epoll.EpollTcpInfo;
import io.netty.channel.epoll.VSockAddress;
import io.netty.channel.socket.InternetProtocolFamily;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class Netty_transport_classes_epollTest {
    @Test
    void epollAvailabilityContractIsSelfConsistent() {
        boolean available = Epoll.isAvailable();
        Throwable cause = Epoll.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(Epoll::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(Epoll.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(Epoll.isTcpFastOpenServerSideAvailable()).isFalse();
        assertThat(EpollDatagramChannel.isSegmentedDatagramPacketSupported()).isFalse();

        Throwable thrown = catchThrowable(Epoll::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
        assertThat(rootCauseMessage(thrown)).contains("netty_transport_native_epoll");
    }

    @Test
    void epollPublicTypesRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(EpollSocketChannel::new);
        assertChannelConstructorMatchesAvailability(EpollServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(EpollDatagramChannel::new);
        assertEventLoopGroupConstructorMatchesAvailability(EpollEventLoopGroup::new);
    }

    @Test
    void epollChannelOptionsAreRegisteredInTheConstantPool() {
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
                EpollChannelOption.UDP_GRO
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void epollProtocolFamilyConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(() -> new EpollSocketChannel(InternetProtocolFamily.IPv4));
        assertChannelConstructorMatchesAvailability(() -> new EpollServerSocketChannel(InternetProtocolFamily.IPv4));
        assertChannelConstructorMatchesAvailability(() -> new EpollDatagramChannel(InternetProtocolFamily.IPv4));
    }

    @Test
    void epollValueObjectsExposeStableStateWithoutNativeTransport() {
        assertThat(EpollMode.values()).containsExactly(EpollMode.EDGE_TRIGGERED, EpollMode.LEVEL_TRIGGERED);
        assertThat(EpollMode.valueOf("EDGE_TRIGGERED")).isSameAs(EpollMode.EDGE_TRIGGERED);
        assertThat(EpollMode.valueOf("LEVEL_TRIGGERED")).isSameAs(EpollMode.LEVEL_TRIGGERED);

        EpollTcpInfo tcpInfo = new EpollTcpInfo();
        assertThat(new long[] {
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
        }).containsOnly(0L);

        VSockAddress address = new VSockAddress(3, 7);
        VSockAddress sameAddress = new VSockAddress(3, 7);
        VSockAddress differentAddress = new VSockAddress(3, 8);

        assertThat(address.getCid()).isEqualTo(3);
        assertThat(address.getPort()).isEqualTo(7);
        assertThat(address).isEqualTo(sameAddress).hasSameHashCodeAs(sameAddress);
        assertThat(address).isNotEqualTo(differentAddress);
        assertThat(address).hasToString("VSockAddress{cid=3, port=7}");
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (Epoll.isAvailable()) {
            Channel channel = constructor.get();
            try {
                assertThat(channel.isOpen()).isTrue();
            } finally {
                channel.close().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void assertEventLoopGroupConstructorMatchesAvailability(Supplier<? extends EventLoopGroup> constructor) {
        if (Epoll.isAvailable()) {
            EventLoopGroup group = constructor.get();
            try {
                assertThat(group.isShuttingDown()).isFalse();
            } finally {
                group.shutdownGracefully().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
