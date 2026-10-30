/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_portunif;

import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.portunif.PUContext;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.portunif.finders.SSLProtocolFinder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(60)
public class SSLProtocolFinderTest {
    @Test
    void requestsMoreDataForAnIncompleteTlsHeader() throws Exception {
        assertDetection(new byte[]{22, 3, 3}, ProtocolFinder.Result.NEED_MORE_DATA);
    }

    @Test
    void requestsMoreDataForAnIncompleteTlsRecord() throws Exception {
        assertDetection(new byte[]{22, 3, 3, 0, 16, 1, 0}, ProtocolFinder.Result.NEED_MORE_DATA);
    }

    @Test
    void rejectsPlaintextWithoutConsumingIt() throws Exception {
        assertDetection("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII), ProtocolFinder.Result.NOT_FOUND);
    }

    private static void assertDetection(byte[] input, ProtocolFinder.Result expected) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SSLProtocolFinder finder = new SSLProtocolFinder(new SSLEngineConfigurator(sslContext, false, false, false));
        PUContext protocolContext = new PUContext(new PUFilter());
        FilterChainContext context = new FilterChainContext();
        Buffer buffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, input);
        context.setMessage(buffer);
        try {
            assertEquals(expected, finder.find(protocolContext, context));
            assertEquals(0, buffer.position());
            assertEquals(input.length, buffer.remaining());
        } finally {
            buffer.tryDispose();
            context.completeAndRecycle();
        }
    }
}
