/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_rxtx;

import io.netty.channel.rxtx.RxtxDeviceAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RxtxDeviceAddressTest {
    @Test
    void preservesPlatformSpecificSerialDeviceNames() {
        RxtxDeviceAddress unixAddress = new RxtxDeviceAddress("/dev/ttyUSB0");
        RxtxDeviceAddress windowsAddress = new RxtxDeviceAddress("COM3");

        assertEquals("/dev/ttyUSB0", unixAddress.value());
        assertEquals("COM3", windowsAddress.value());
    }
}
