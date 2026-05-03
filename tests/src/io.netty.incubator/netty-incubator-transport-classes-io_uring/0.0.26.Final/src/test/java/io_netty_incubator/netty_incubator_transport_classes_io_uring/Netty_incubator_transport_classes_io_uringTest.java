/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty_incubator.netty_incubator_transport_classes_io_uring;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringDatagramChannelConfig;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.incubator.channel.uring.IOUringServerSocketChannelConfig;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import io.netty.incubator.channel.uring.IOUringSocketChannelConfig;
import io.netty.incubator.channel.uring.IOUringTcpInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_incubator_transport_classes_io_uringTest {
    @Test
    void ioUringAvailabilityContractIsSelfConsistent() {
        boolean available = IOUring.isAvailable();
        Throwable cause = IOUring.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(IOUring::ensureAvailability).doesNotThrowAnyException();
            assertThatCode(IOUring::isTcpFastOpenClientSideAvailable).doesNotThrowAnyException();
            assertThatCode(IOUring::isTcpFastOpenServerSideAvailable).doesNotThrowAnyException();
            assertThat(IOUringDatagramChannel.isSegmentedDatagramPacketSupported()).isTrue();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(IOUring.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(IOUring.isTcpFastOpenServerSideAvailable()).isFalse();
        assertThat(IOUringDatagramChannel.isSegmentedDatagramPacketSupported()).isFalse();

        Throwable thrown = catchThrowable(IOUring::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
        assertThat(rootCauseMessage(thrown)).isNotBlank();
    }

    @Test
    void publicChannelConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(IOUringSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IOUringServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IOUringDatagramChannel::new);
    }

    @Test
    void datagramProtocolFamilyConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(() -> new IOUringDatagramChannel(InternetProtocolFamily.IPv4));
    }

    @Test
    void eventLoopGroupConstructorRespectsTransportAvailability() {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "iouring-test-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        assertEventLoopGroupConstructorMatchesAvailability(() -> new IOUringEventLoopGroup(1, factory, 8, 2));
    }

    @Test
    void ioUringChannelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                IOUringChannelOption.TCP_CORK,
                IOUringChannelOption.TCP_NOTSENT_LOWAT,
                IOUringChannelOption.TCP_KEEPIDLE,
                IOUringChannelOption.TCP_KEEPINTVL,
                IOUringChannelOption.TCP_KEEPCNT,
                IOUringChannelOption.TCP_USER_TIMEOUT,
                IOUringChannelOption.IP_FREEBIND,
                IOUringChannelOption.IP_TRANSPARENT,
                IOUringChannelOption.IP_RECVORIGDSTADDR,
                IOUringChannelOption.TCP_DEFER_ACCEPT,
                IOUringChannelOption.TCP_QUICKACK,
                IOUringChannelOption.TCP_MD5SIG,
                IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void ioUringFastOpenOptionsReuseTheSharedChannelOptionInstances() {
        assertThat(IOUringChannelOption.TCP_FASTOPEN).isSameAs(ChannelOption.TCP_FASTOPEN);
        assertThat(IOUringChannelOption.TCP_FASTOPEN.name())
                .isEqualTo(ChannelOption.TCP_FASTOPEN.name())
                .contains("TCP_FASTOPEN");

        assertThat(IOUringChannelOption.TCP_FASTOPEN_CONNECT).isSameAs(ChannelOption.TCP_FASTOPEN_CONNECT);
        assertThat(IOUringChannelOption.TCP_FASTOPEN_CONNECT.name())
                .isEqualTo(ChannelOption.TCP_FASTOPEN_CONNECT.name())
                .contains("TCP_FASTOPEN_CONNECT");

        assertThat(ChannelOption.valueOf(IOUringChannelOption.TCP_FASTOPEN.name()))
                .isSameAs(IOUringChannelOption.TCP_FASTOPEN);
        assertThat(ChannelOption.valueOf(IOUringChannelOption.TCP_FASTOPEN_CONNECT.name()))
                .isSameAs(IOUringChannelOption.TCP_FASTOPEN_CONNECT);
    }

    @Test
    void tcpInfoStartsWithZeroedKernelCounters() {
        IOUringTcpInfo tcpInfo = new IOUringTcpInfo();

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
    }

    @Test
    void socketChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringSocketChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringSocketChannel channel = new IOUringSocketChannel();
        try {
            IOUringSocketChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.TCP_CORK,
                    IOUringChannelOption.TCP_NOTSENT_LOWAT,
                    IOUringChannelOption.TCP_KEEPIDLE,
                    IOUringChannelOption.TCP_KEEPINTVL,
                    IOUringChannelOption.TCP_KEEPCNT,
                    IOUringChannelOption.TCP_USER_TIMEOUT,
                    IOUringChannelOption.TCP_QUICKACK,
                    IOUringChannelOption.TCP_FASTOPEN_CONNECT
            );

            assertThat(config.setAllowHalfClosure(true)).isSameAs(config);
            assertThat(config.isAllowHalfClosure()).isTrue();
            assertThat(config.setOption(IOUringChannelOption.TCP_CORK, true)).isTrue();
            assertThat(config.getOption(IOUringChannelOption.TCP_CORK)).isTrue();
            assertThat(config.setOption(IOUringChannelOption.TCP_CORK, false)).isTrue();
            assertThat(config.isTcpCork()).isFalse();
            assertThat(config.setOption(IOUringChannelOption.TCP_FASTOPEN_CONNECT, false)).isTrue();
            assertThat(config.isTcpFastOpenConnect()).isFalse();
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPIDLE, 30)).isTrue();
            assertThat(config.getTcpKeepIdle()).isEqualTo(30);
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPINTVL, 10)).isTrue();
            assertThat(config.getTcpKeepIntvl()).isEqualTo(10);
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPCNT, 5)).isTrue();
            assertThat(config.getTcpKeepCnt()).isEqualTo(5);
            assertThat(config.setOption(IOUringChannelOption.TCP_USER_TIMEOUT, 1_000)).isTrue();
            assertThat(config.getTcpUserTimeout()).isEqualTo(1_000);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void serverSocketChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringServerSocketChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringServerSocketChannel channel = new IOUringServerSocketChannel();
        try {
            IOUringServerSocketChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.TCP_DEFER_ACCEPT,
                    IOUringChannelOption.TCP_FASTOPEN,
                    IOUringChannelOption.IP_FREEBIND,
                    IOUringChannelOption.IP_TRANSPARENT
            );

            assertThat(config.setBacklog(16)).isSameAs(config);
            assertThat(config.getBacklog()).isEqualTo(16);
            assertThat(config.setOption(IOUringChannelOption.TCP_DEFER_ACCEPT, 2)).isTrue();
            assertThat(config.getTcpDeferAccept()).isEqualTo(2);
            assertThat(config.setOption(IOUringChannelOption.TCP_FASTOPEN, 4)).isTrue();
            assertThat(config.getTcpFastopen()).isEqualTo(4);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void datagramChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringDatagramChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringDatagramChannel channel = new IOUringDatagramChannel();
        try {
            IOUringDatagramChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE,
                    IOUringChannelOption.IP_FREEBIND,
                    IOUringChannelOption.IP_TRANSPARENT
            );

            assertThat(config.setOption(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1_200)).isTrue();
            assertThat(config.getMaxDatagramPayloadSize()).isEqualTo(1_200);
            assertThat(config.setMaxDatagramPayloadSize(512)).isSameAs(config);
            assertThat(config.getOption(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE)).isEqualTo(512);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (IOUring.isAvailable()) {
            Channel channel = constructor.get();
            try {
                assertThat(channel.isOpen()).isTrue();
                assertThat(channel.isActive()).isFalse();
                assertThat(channel.config()).isNotNull();
                assertThat(channel.metadata()).isNotNull();
            } finally {
                channel.close().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void assertEventLoopGroupConstructorMatchesAvailability(
            Supplier<? extends EventLoopGroup> constructor) {
        if (IOUring.isAvailable()) {
            EventLoopGroup group = constructor.get();
            try {
                assertThat(group.isShuttingDown()).isFalse();
                assertThat(group.next()).isNotNull();
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
