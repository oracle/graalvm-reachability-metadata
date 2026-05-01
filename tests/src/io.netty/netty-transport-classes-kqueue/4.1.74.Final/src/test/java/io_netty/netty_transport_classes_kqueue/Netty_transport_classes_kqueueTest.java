/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_classes_kqueue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.AcceptFilter;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueChannelConfig;
import io.netty.channel.kqueue.KQueueChannelOption;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_transport_classes_kqueueTest {
    @Test
    void kqueueAvailabilityContractIsSelfConsistent() {
        boolean available = KQueue.isAvailable();
        Throwable cause = KQueue.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(KQueue::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(KQueue.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(KQueue.isTcpFastOpenServerSideAvailable()).isFalse();

        Throwable thrown = catchThrowable(KQueue::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
        assertThat(rootCauseMessage(thrown)).isNotBlank();
    }

    @Test
    void acceptFilterExposesImmutableValueSemantics() {
        AcceptFilter httpReady = new AcceptFilter("httpready", "GET");
        AcceptFilter sameFilter = new AcceptFilter("httpready", "GET");
        AcceptFilter differentName = new AcceptFilter("dataready", "GET");
        AcceptFilter differentArgs = new AcceptFilter("httpready", "POST");

        assertThat(httpReady.filterName()).isEqualTo("httpready");
        assertThat(httpReady.filterArgs()).isEqualTo("GET");
        assertThat(httpReady).isEqualTo(httpReady);
        assertThat(httpReady).isEqualTo(sameFilter).hasSameHashCodeAs(sameFilter);
        assertThat(httpReady).isNotEqualTo(differentName);
        assertThat(httpReady).isNotEqualTo(differentArgs);
        assertThat(httpReady).isNotEqualTo("httpready, GET");
        assertThat(httpReady).hasToString("httpready, GET");
    }

    @Test
    void acceptFilterRejectsNullComponents() {
        assertThatThrownBy(() -> new AcceptFilter(null, "args"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("filterName");
        assertThatThrownBy(() -> new AcceptFilter("name", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("filterArgs");
    }

    @Test
    void kqueueChannelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                KQueueChannelOption.SO_SNDLOWAT,
                KQueueChannelOption.TCP_NOPUSH,
                KQueueChannelOption.SO_ACCEPTFILTER,
                KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void publicChannelConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(KQueueSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueDatagramChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueDomainSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueServerDomainSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueDomainDatagramChannel::new);
    }

    @Test
    void eventLoopGroupConstructorRespectsTransportAvailability() {
        assertEventLoopGroupConstructorMatchesAvailability(() -> new KQueueEventLoopGroup(1));
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (KQueue.isAvailable()) {
            Channel channel = constructor.get();
            try {
                assertThat(channel.isOpen()).isTrue();
                assertThat(channel.config()).isInstanceOf(KQueueChannelConfig.class);
                assertTransportProvidesGuessOptionRoundTrips((KQueueChannelConfig) channel.config());
            } finally {
                channel.close().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void assertTransportProvidesGuessOptionRoundTrips(KQueueChannelConfig config) {
        assertThat(config.getRcvAllocTransportProvidesGuess()).isFalse();
        assertThat(config.getOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS)).isFalse();

        assertThat(config.setOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS, true)).isTrue();
        assertThat(config.getRcvAllocTransportProvidesGuess()).isTrue();
        assertThat(config.getOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS)).isTrue();

        assertThat(config.setRcvAllocTransportProvidesGuess(false)).isSameAs(config);
        assertThat(config.getRcvAllocTransportProvidesGuess()).isFalse();
    }

    private static void assertEventLoopGroupConstructorMatchesAvailability(
            Supplier<? extends EventLoopGroup> constructor) {
        if (KQueue.isAvailable()) {
            EventLoopGroup group = constructor.get();
            try {
                assertThat(group.isShuttingDown()).isFalse();
                ((KQueueEventLoopGroup) group).setIoRatio(25);
                assertThatThrownBy(() -> ((KQueueEventLoopGroup) group).setIoRatio(0))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("ioRatio");
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
