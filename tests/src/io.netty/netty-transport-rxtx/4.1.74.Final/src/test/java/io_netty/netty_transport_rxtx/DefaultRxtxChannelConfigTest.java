/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_rxtx;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelOption;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.rxtx.RxtxChannel;
import io.netty.channel.rxtx.RxtxChannelConfig;
import io.netty.channel.rxtx.RxtxChannelConfig.Databits;
import io.netty.channel.rxtx.RxtxChannelConfig.Paritybit;
import io.netty.channel.rxtx.RxtxChannelConfig.Stopbits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.netty.channel.rxtx.RxtxChannelOption.BAUD_RATE;
import static io.netty.channel.rxtx.RxtxChannelOption.DATA_BITS;
import static io.netty.channel.rxtx.RxtxChannelOption.DTR;
import static io.netty.channel.rxtx.RxtxChannelOption.PARITY_BIT;
import static io.netty.channel.rxtx.RxtxChannelOption.READ_TIMEOUT;
import static io.netty.channel.rxtx.RxtxChannelOption.RTS;
import static io.netty.channel.rxtx.RxtxChannelOption.STOP_BITS;
import static io.netty.channel.rxtx.RxtxChannelOption.WAIT_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class DefaultRxtxChannelConfigTest {
    private OioEventLoopGroup group;
    private RxtxChannel channel;
    private RxtxChannelConfig config;

    @BeforeEach
    void createChannel() throws Exception {
        group = new OioEventLoopGroup(1);
        channel = new RxtxChannel();
        group.register(channel).get(10, TimeUnit.SECONDS);
        config = channel.config();
    }

    @AfterEach
    void closeChannel() throws Exception {
        try {
            if (channel.isRegistered()) {
                channel.close().get(10, TimeUnit.SECONDS);
            }
        } finally {
            group.shutdownGracefully(0, 10, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void providesDefaultSerialSettings() {
        assertEquals(115200, config.getBaudrate());
        assertSame(Databits.DATABITS_8, config.getDatabits());
        assertSame(Stopbits.STOPBITS_1, config.getStopbits());
        assertSame(Paritybit.NONE, config.getParitybit());
        assertFalse(config.isDtr());
        assertFalse(config.isRts());
        assertEquals(0, config.getWaitTimeMillis());
    }

    @Test
    void exposesFluentSerialSettingsAsChannelOptions() {
        assertSame(config, config.setBaudrate(57600)
                .setDatabits(Databits.DATABITS_7)
                .setStopbits(Stopbits.STOPBITS_2)
                .setParitybit(Paritybit.EVEN)
                .setDtr(true)
                .setRts(true)
                .setWaitTimeMillis(10000)
                .setReadTimeout(10000));

        assertEquals(57600, config.getOption(BAUD_RATE).intValue());
        assertSame(Databits.DATABITS_7, config.getOption(DATA_BITS));
        assertSame(Stopbits.STOPBITS_2, config.getOption(STOP_BITS));
        assertSame(Paritybit.EVEN, config.getOption(PARITY_BIT));
        assertTrue(config.getOption(DTR));
        assertTrue(config.getOption(RTS));
        assertEquals(10000, config.getOption(WAIT_TIME).intValue());
        assertEquals(10000, config.getOption(READ_TIMEOUT).intValue());

        Map<ChannelOption<?>, Object> options = config.getOptions();
        assertEquals(57600, options.get(BAUD_RATE));
        assertEquals(Databits.DATABITS_7, options.get(DATA_BITS));
        assertEquals(Stopbits.STOPBITS_2, options.get(STOP_BITS));
        assertEquals(Paritybit.EVEN, options.get(PARITY_BIT));
        assertEquals(true, options.get(DTR));
        assertEquals(true, options.get(RTS));
        assertEquals(10000, options.get(WAIT_TIME));
    }

    @Test
    void appliesSerialSettingsThroughTypedOptions() {
        assertTrue(config.setOption(BAUD_RATE, 19200));
        assertTrue(config.setOption(DATA_BITS, Databits.DATABITS_6));
        assertTrue(config.setOption(STOP_BITS, Stopbits.STOPBITS_1_5));
        assertTrue(config.setOption(PARITY_BIT, Paritybit.ODD));
        assertTrue(config.setOption(DTR, true));
        assertTrue(config.setOption(RTS, true));
        assertTrue(config.setOption(WAIT_TIME, 10000));
        assertTrue(config.setOption(READ_TIMEOUT, 20000));

        assertEquals(19200, config.getBaudrate());
        assertSame(Databits.DATABITS_6, config.getDatabits());
        assertSame(Stopbits.STOPBITS_1_5, config.getStopbits());
        assertSame(Paritybit.ODD, config.getParitybit());
        assertTrue(config.isDtr());
        assertTrue(config.isRts());
        assertEquals(10000, config.getWaitTimeMillis());
        assertEquals(20000, config.getReadTimeout());

        assertTrue(config.setOption(DTR, false));
        assertTrue(config.setOption(RTS, false));
        assertFalse(config.isDtr());
        assertFalse(config.isRts());
    }

    @Test
    void supportsInheritedChannelOptions() {
        assertSame(config, config.setConnectTimeoutMillis(10000)
                .setWriteSpinCount(8)
                .setAutoClose(false));
        assertTrue(config.setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000));
        assertTrue(config.setOption(ChannelOption.WRITE_SPIN_COUNT, 16));
        assertEquals(20000, config.getConnectTimeoutMillis());
        assertEquals(16, config.getWriteSpinCount());
        assertFalse(config.isAutoClose());
        assertEquals(20000, config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS).intValue());
    }

    @Test
    void rejectsNegativeDelaysAndTimeoutsWithoutChangingSettings() {
        config.setWaitTimeMillis(10000).setReadTimeout(10000);
        assertThrows(IllegalArgumentException.class, () -> config.setWaitTimeMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setReadTimeout(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setOption(WAIT_TIME, -1));
        assertThrows(IllegalArgumentException.class, () -> config.setOption(READ_TIMEOUT, -1));
        assertEquals(10000, config.getWaitTimeMillis());
        assertEquals(10000, config.getReadTimeout());
    }

    @Test
    void convertsSerialConstantsToConfigurationEnums() {
        for (Databits bits : Databits.values()) {
            assertSame(bits, Databits.valueOf(bits.value()));
            config.setDatabits(bits);
            assertSame(bits, config.getOption(DATA_BITS));
        }
        for (Stopbits bits : Stopbits.values()) {
            assertSame(bits, Stopbits.valueOf(bits.value()));
            config.setStopbits(bits);
            assertSame(bits, config.getOption(STOP_BITS));
        }
        for (Paritybit parity : Paritybit.values()) {
            assertSame(parity, Paritybit.valueOf(parity.value()));
            config.setParitybit(parity);
            assertSame(parity, config.getOption(PARITY_BIT));
        }
        assertThrows(IllegalArgumentException.class, () -> Databits.valueOf(-1));
        assertThrows(IllegalArgumentException.class, () -> Stopbits.valueOf(-1));
        assertThrows(IllegalArgumentException.class, () -> Paritybit.valueOf(-1));
    }
}
