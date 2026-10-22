/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises JNA's native pointer buffers to encode and decode IPv4 service endpoints.
 */
public class NativeIpv4EndpointTest {

    private static final int AF_INET = 2;
    private static final int ADDRESS_BYTES = 4;
    private static final int TEXT_CAPACITY = 16;

    @Test
    @Timeout(60)
    public void convertsConfiguredEndpointsToNetworkBytesAndBack() {
        String library = Platform.isWindows() ? "Ws2_32" : Platform.C_LIBRARY_NAME;
        InternetAddresses addresses = Native.load(library, InternetAddresses.class);
        Map<String, byte[]> endpoints = Map.of(
                "192.0.2.42", new byte[]{(byte) 192, 0, 2, 42},
                "198.51.100.255", new byte[]{(byte) 198, 51, 100, (byte) 255});

        // One owned allocation holds the binary address and its separate printable output buffer.
        long allocation = Native.malloc(ADDRESS_BYTES + TEXT_CAPACITY);
        assertNotEquals(0L, allocation, "Native endpoint buffer allocation must succeed");
        try {
            Pointer binary = new Pointer(allocation);
            Pointer text = binary.share(ADDRESS_BYTES);
            for (Map.Entry<String, byte[]> endpoint : endpoints.entrySet()) {
                assertEquals(1, addresses.inet_pton(AF_INET, endpoint.getKey(), binary),
                        "A configured endpoint must be accepted as a numeric IPv4 address");
                assertArrayEquals(endpoint.getValue(), binary.getByteArray(0, ADDRESS_BYTES),
                        "The native address must contain octets in network order");

                text.setMemory(0, TEXT_CAPACITY, (byte) 0);
                assertNotNull(addresses.inet_ntop(AF_INET, binary, text, TEXT_CAPACITY));
                assertEquals(endpoint.getKey(), text.getString(0, StandardCharsets.US_ASCII.name()),
                        "The displayed endpoint must match the configured numeric address");
            }

            assertEquals(0, addresses.inet_pton(AF_INET, "192.0.2.999", binary),
                    "An out-of-range octet must be rejected before an endpoint is used");
        } finally {
            Native.free(allocation);
        }
    }

    public interface InternetAddresses extends Library {
        int inet_pton(int family, String source, Pointer destination);

        Pointer inet_ntop(int family, Pointer source, Pointer destination, int capacity);
    }
}
