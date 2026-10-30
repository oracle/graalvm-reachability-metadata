/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_portunif;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.portunif.PUContext;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.PUProtocol;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class PUFilterTest {
    @Test
    void registersAndDeregistersProtocolChains() {
        PUFilter filter = new PUFilter();
        ProtocolFinder finder = new PrefixFinder('A');
        FilterChain chain = filter.getPUFilterChainBuilder().add(new ReplyFilter("alpha")).build();

        PUProtocol protocol = filter.register(finder, chain);
        assertSame(finder, protocol.getProtocolFinder());
        assertSame(chain, protocol.getFilterChain());
        assertSame(filter.getBackChannelFilter(), chain.get(0));
        assertEquals(1, filter.getProtocols().size());
        assertTrue(filter.getProtocols().contains(protocol));

        PUContext context = new PUContext(filter);
        assertTrue(context.isSticky());
        assertFalse(context.noProtocolsFound());
        context.setSticky(false);
        assertFalse(context.isSticky());

        filter.deregister(protocol);
        assertTrue(filter.getProtocols().isEmpty());
        assertTrue(new PUContext(filter).noProtocolsFound());
        filter.register(protocol);
        assertTrue(filter.getProtocols().contains(protocol));
    }

    @Test
    void rejectsProtocolChainsWithoutThisFiltersBackChannel() {
        PUFilter filter = new PUFilter();
        FilterChain chain = FilterChainBuilder.stateless().add(new ReplyFilter("reply")).build();
        ProtocolFinder finder = new PrefixFinder('A');

        assertThrows(IllegalStateException.class, () -> filter.register(finder, chain));
        assertTrue(filter.getProtocols().isEmpty());
    }

    @Test
    void routesTwoProtocolsOnOnePortAndKeepsConnectionsSticky() throws Exception {
        PUFilter filter = new PUFilter();
        PrefixFinder alphaFinder = new PrefixFinder('A');
        PrefixFinder betaFinder = new PrefixFinder('B');
        filter.register(alphaFinder, filter.getPUFilterChainBuilder().add(new ReplyFilter("alpha")).build());
        filter.register(betaFinder, filter.getPUFilterChainBuilder().add(new ReplyFilter("beta")).build());

        try (TestServer server = new TestServer(filter); Socket alpha = new Socket(); Socket beta = new Socket()) {
            connect(alpha, server.address);
            connect(beta, server.address);
            assertReply(alpha, "A:first\n", "alpha:A:first\n");
            assertReply(alpha, "B:still-alpha\n", "alpha:B:still-alpha\n");
            assertReply(beta, "B:second\n", "beta:B:second\n");
            assertEquals(2, alphaFinder.calls.get());
            assertEquals(1, betaFinder.calls.get());
        }
    }

    @Test
    void forwardsUnrecognizedTrafficToTheParentChain() throws Exception {
        PUFilter filter = new PUFilter();
        PrefixFinder finder = new PrefixFinder('A');
        filter.register(finder, filter.getPUFilterChainBuilder().add(new ReplyFilter("alpha")).build());

        try (TestServer server = new TestServer(filter); Socket socket = new Socket()) {
            connect(socket, server.address);
            assertReply(socket, "X:unknown\n", "fallback:X:unknown\n");
            assertReply(socket, "A:still-fallback\n", "fallback:A:still-fallback\n");
            assertEquals(1, finder.calls.get());
        }
    }

    @Test
    void waitsForMoreDataBeforeSelectingAProtocol() throws Exception {
        PUFilter filter = new PUFilter();
        CountDownLatch waitingForData = new CountDownLatch(1);
        PrefixFinder finder = new PrefixFinder('A');
        filter.register((context, chainContext) -> {
            ProtocolFinder.Result result = finder.find(context, chainContext);
            if (result == ProtocolFinder.Result.NEED_MORE_DATA) {
                waitingForData.countDown();
            }
            return result;
        }, filter.getPUFilterChainBuilder().add(new ReplyFilter("alpha")).build());

        try (TestServer server = new TestServer(filter); Socket socket = new Socket()) {
            connect(socket, server.address);
            socket.getOutputStream().write('A');
            socket.getOutputStream().flush();
            assertTrue(waitingForData.await(10, TimeUnit.SECONDS));
            assertReply(socket, ":fragmented\n", "alpha:A:fragmented\n");
            assertEquals(2, finder.calls.get());
        }
    }

    private static void connect(Socket socket, SocketAddress address) throws IOException {
        socket.setSoTimeout(10000);
        socket.connect(address, 10000);
    }

    private static void assertReply(Socket socket, String request, String expected) throws IOException {
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        byte[] response = socket.getInputStream().readNBytes(expected.length());
        assertEquals(expected, new String(response, StandardCharsets.US_ASCII));
    }

    private static final class PrefixFinder implements ProtocolFinder {
        private final char prefix;
        private final AtomicInteger calls = new AtomicInteger();

        private PrefixFinder(char prefix) {
            this.prefix = prefix;
        }

        @Override
        public Result find(PUContext context, FilterChainContext chainContext) {
            calls.incrementAndGet();
            Buffer message = chainContext.getMessage();
            if (message.remaining() < 2) {
                return Result.NEED_MORE_DATA;
            }
            return message.get(message.position()) == prefix ? Result.FOUND : Result.NOT_FOUND;
        }
    }

    private static final class ReplyFilter extends BaseFilter {
        private final String protocol;

        private ReplyFilter(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public NextAction handleRead(FilterChainContext context) throws IOException {
            Buffer message = context.getMessage();
            String request = message.toStringContent(StandardCharsets.US_ASCII);
            if (!request.endsWith("\n")) {
                return context.getStopAction(message);
            }
            context.write(Buffers.wrap(context.getMemoryManager(), protocol + ":" + request, StandardCharsets.US_ASCII));
            message.tryDispose();
            return context.getStopAction();
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final TCPNIOTransport transport;
        private final SocketAddress address;

        private TestServer(PUFilter filter) throws IOException {
            transport = TCPNIOTransportBuilder.newInstance().build();
            transport.setSelectorRunnersCount(1);
            transport.setKernelThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(2).setMaxPoolSize(2));
            transport.setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(2).setMaxPoolSize(2));
            transport.setProcessor(FilterChainBuilder.stateless()
                    .add(new TransportFilter()).add(filter).add(new ReplyFilter("fallback")).build());
            try {
                TCPNIOServerConnection listener = transport.bind("127.0.0.1", 0);
                address = listener.getLocalAddress();
                transport.start();
            } catch (IOException | RuntimeException | Error failure) {
                transport.stop();
                throw failure;
            }
        }

        @Override
        public void close() throws IOException {
            transport.stop();
        }
    }
}
