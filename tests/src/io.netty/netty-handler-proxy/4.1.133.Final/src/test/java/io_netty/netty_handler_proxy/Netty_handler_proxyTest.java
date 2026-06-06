/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_handler_proxy;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.Future;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Netty_handler_proxyTest {
    private static final int IO_TIMEOUT_MILLIS = 5_000;
    private static final InetSocketAddress HTTP_DESTINATION = InetSocketAddress.createUnresolved("service.example", 443);
    private static final InetSocketAddress SOCKS_DOMAIN_DESTINATION =
            InetSocketAddress.createUnresolved("destination.example", 8443);
    private static final InetSocketAddress SOCKS_IPV4_DESTINATION =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080);

    @Test
    void exposesHttpProxyConfigurationAndConnectionEvent() throws Exception {
        HttpHeaders headers = new DefaultHttpHeaders().add("X-Trace-Id", "trace-123");
        HttpProxyHandler handler = new HttpProxyHandler(unusedProxyAddress(), "user", "pass", headers, true);

        assertThat(handler.protocol()).isEqualTo("http");
        assertThat(handler.authScheme()).isEqualTo("basic");
        assertThat(handler.username()).isEqualTo("user");
        assertThat(handler.password()).isEqualTo("pass");
        assertThat(handler.connectTimeoutMillis()).isEqualTo(10_000L);
        handler.setConnectTimeoutMillis(-1L);
        assertThat(handler.connectTimeoutMillis()).isZero();

        ProxyConnectionEvent event = new ProxyConnectionEvent(
                handler.protocol(), handler.authScheme(), handler.proxyAddress(), HTTP_DESTINATION);
        assertThat(event.protocol()).isEqualTo("http");
        assertThat(event.authScheme()).isEqualTo("basic");
        assertThat((InetSocketAddress) event.proxyAddress()).isEqualTo(handler.proxyAddress());
        assertThat((InetSocketAddress) event.destinationAddress()).isEqualTo(HTTP_DESTINATION);
        assertThat(event.toString())
                .contains("http")
                .contains("basic")
                .contains("service.example")
                .contains("443");
    }

    @Test
    void performsHttpConnectWithBasicAuthenticationAndCustomHeaders() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            List<String> requestLines = readHttpHeaderBlock(input);
            assertThat(requestLines).isNotEmpty();
            assertThat(requestLines.get(0)).isEqualTo("CONNECT service.example:443 HTTP/1.1");
            assertContainsHeader(requestLines, HttpHeaderNames.HOST.toString(), "service.example");
            assertContainsHeader(requestLines, "X-Trace-Id", "trace-456");
            assertContainsHeader(requestLines, HttpHeaderNames.PROXY_AUTHORIZATION.toString(),
                    basicAuthorization("user", "pass"));
            writeAscii(output, "HTTP/1.1 200 Connection Established\r\nContent-Length: 0\r\n\r\n");
        })) {
            HttpHeaders headers = new DefaultHttpHeaders().add("X-Trace-Id", "trace-456");
            HttpProxyHandler handler = new HttpProxyHandler(proxyServer.address(), "user", "pass", headers, true);

            try (ClientConnection client = ClientConnection.connect(handler, HTTP_DESTINATION)) {
                assertThat(handler.connectFuture().isSuccess()).isTrue();
                assertThat(handler.isConnected()).isTrue();
                assertThat((InetSocketAddress) handler.destinationAddress()).isEqualTo(HTTP_DESTINATION);
                assertThat(client.recorder().event()).isNotNull();
                assertThat(client.recorder().event().protocol()).isEqualTo("http");
                assertThat(client.recorder().event().authScheme()).isEqualTo("basic");
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void reportsHttpProxyRejectionWithResponseStatus() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            List<String> requestLines = readHttpHeaderBlock(input);
            assertThat(requestLines.get(0)).isEqualTo("CONNECT service.example:443 HTTP/1.1");
            writeAscii(output, "HTTP/1.1 407 Proxy Authentication Required\r\n"
                    + "Proxy-Authenticate: Basic realm=restricted\r\n"
                    + "Content-Length: 0\r\n\r\n");
        })) {
            HttpProxyHandler handler = new HttpProxyHandler(proxyServer.address());

            try (ClientConnection client = ClientConnection.connect(handler, HTTP_DESTINATION, false)) {
                Future<Channel> connectFuture = handler.connectFuture();
                assertThat(connectFuture.isDone()).isTrue();
                assertThat(connectFuture.isSuccess()).isFalse();
                assertThat(connectFuture.cause()).isInstanceOf(ProxyConnectException.class);
                assertThat(connectFuture.cause().getMessage())
                        .contains("http")
                        .contains("none")
                        .contains("status: 407 Proxy Authentication Required");
                assertThat(client.recorder().event()).isNull();
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void performsSocks4aConnectWithUsernameForUnresolvedDomain() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x04, 0x01);
            assertThat(readUnsignedShort(input)).isEqualTo(8443);
            assertNextBytes(input, 0x00, 0x00, 0x00, 0x01);
            assertThat(readNullTerminatedAscii(input)).isEqualTo("netty-user");
            assertThat(readNullTerminatedAscii(input)).isEqualTo("destination.example");
            writeBytes(output, 0x00, 0x5a, 0x20, 0xfb, 0x7f, 0x00, 0x00, 0x01);
        })) {
            Socks4ProxyHandler handler = new Socks4ProxyHandler(proxyServer.address(), "netty-user");

            assertThat(handler.protocol()).isEqualTo("socks4");
            assertThat(handler.authScheme()).isEqualTo("username");
            assertThat(handler.username()).isEqualTo("netty-user");
            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_DOMAIN_DESTINATION)) {
                assertThat(handler.connectFuture().isSuccess()).isTrue();
                assertThat(handler.isConnected()).isTrue();
                assertThat(client.recorder().event()).isNotNull();
                assertThat(client.recorder().event().protocol()).isEqualTo("socks4");
                assertThat(client.recorder().event().authScheme()).isEqualTo("username");
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void reportsSocks4CommandFailureStatus() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x04, 0x01);
            readFully(input, 6);
            readNullTerminatedAscii(input);
            readNullTerminatedAscii(input);
            writeBytes(output, 0x00, 0x5b, 0x20, 0xfb, 0x00, 0x00, 0x00, 0x00);
        })) {
            Socks4ProxyHandler handler = new Socks4ProxyHandler(proxyServer.address());

            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_DOMAIN_DESTINATION, false)) {
                Future<Channel> connectFuture = handler.connectFuture();
                assertThat(connectFuture.isDone()).isTrue();
                assertThat(connectFuture.isSuccess()).isFalse();
                assertThat(connectFuture.cause()).isInstanceOf(ProxyConnectException.class);
                assertThat(connectFuture.cause().getMessage())
                        .contains("socks4")
                        .contains("none")
                        .contains("REJECTED_OR_FAILED");
                assertThat(client.recorder().event()).isNull();
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void performsSocks5NoAuthenticationConnectToDomain() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x05, 0x01, 0x00);
            writeBytes(output, 0x05, 0x00);

            assertNextBytes(input, 0x05, 0x01, 0x00, 0x03);
            int hostLength = readUnsignedByte(input);
            assertThat(readAscii(input, hostLength)).isEqualTo("destination.example");
            assertThat(readUnsignedShort(input)).isEqualTo(8443);
            writeBytes(output, 0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        })) {
            Socks5ProxyHandler handler = new Socks5ProxyHandler(proxyServer.address());

            assertThat(handler.protocol()).isEqualTo("socks5");
            assertThat(handler.authScheme()).isEqualTo("none");
            assertThat(handler.username()).isNull();
            assertThat(handler.password()).isNull();
            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_DOMAIN_DESTINATION)) {
                assertThat(handler.connectFuture().isSuccess()).isTrue();
                assertThat(client.recorder().event()).isNotNull();
                assertThat(client.recorder().event().protocol()).isEqualTo("socks5");
                assertThat(client.recorder().event().authScheme()).isEqualTo("none");
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void performsSocks5PasswordAuthenticationAndIpv4Connect() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x05, 0x02, 0x00, 0x02);
            writeBytes(output, 0x05, 0x02);

            assertNextBytes(input, 0x01);
            int usernameLength = readUnsignedByte(input);
            assertThat(readAscii(input, usernameLength)).isEqualTo("user");
            int passwordLength = readUnsignedByte(input);
            assertThat(readAscii(input, passwordLength)).isEqualTo("secret");
            writeBytes(output, 0x01, 0x00);

            assertNextBytes(input, 0x05, 0x01, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01);
            assertThat(readUnsignedShort(input)).isEqualTo(8080);
            writeBytes(output, 0x05, 0x00, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, 0x1f, 0x90);
        })) {
            Socks5ProxyHandler handler = new Socks5ProxyHandler(proxyServer.address(), "user", "secret");

            assertThat(handler.authScheme()).isEqualTo("password");
            assertThat(handler.username()).isEqualTo("user");
            assertThat(handler.password()).isEqualTo("secret");
            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_IPV4_DESTINATION)) {
                assertThat(handler.connectFuture().isSuccess()).isTrue();
                assertThat(client.recorder().event()).isNotNull();
                assertThat(client.recorder().event().authScheme()).isEqualTo("password");
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void reportsSocks5CommandFailureStatus() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x05, 0x01, 0x00);
            writeBytes(output, 0x05, 0x00);

            assertNextBytes(input, 0x05, 0x01, 0x00, 0x03);
            int hostLength = readUnsignedByte(input);
            assertThat(readAscii(input, hostLength)).isEqualTo("destination.example");
            assertThat(readUnsignedShort(input)).isEqualTo(8443);
            writeBytes(output, 0x05, 0x02, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        })) {
            Socks5ProxyHandler handler = new Socks5ProxyHandler(proxyServer.address());

            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_DOMAIN_DESTINATION, false)) {
                Future<Channel> connectFuture = handler.connectFuture();
                assertThat(connectFuture.isDone()).isTrue();
                assertThat(connectFuture.isSuccess()).isFalse();
                assertThat(connectFuture.cause()).isInstanceOf(ProxyConnectException.class);
                assertThat(connectFuture.cause().getMessage())
                        .contains("socks5")
                        .contains("none")
                        .contains("status: FORBIDDEN");
                assertThat(client.recorder().event()).isNull();
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void performsSocks5NoAuthenticationConnectToIpv6Address() throws Exception {
        byte[] loopbackAddress = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        InetSocketAddress ipv6Destination = new InetSocketAddress(InetAddress.getByAddress(loopbackAddress), 9443);
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x05, 0x01, 0x00);
            writeBytes(output, 0x05, 0x00);

            assertNextBytes(input, 0x05, 0x01, 0x00, 0x04);
            assertThat(readFully(input, loopbackAddress.length)).containsExactly(loopbackAddress);
            assertThat(readUnsignedShort(input)).isEqualTo(9443);
            writeBytes(output,
                    0x05, 0x00, 0x00, 0x04,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01,
                    0x24, 0xe3);
        })) {
            Socks5ProxyHandler handler = new Socks5ProxyHandler(proxyServer.address());

            try (ClientConnection client = ClientConnection.connect(handler, ipv6Destination)) {
                assertThat(handler.connectFuture().isSuccess()).isTrue();
                assertThat((InetSocketAddress) handler.destinationAddress()).isEqualTo(ipv6Destination);
                assertThat(client.recorder().event()).isNotNull();
                assertThat(client.recorder().event().protocol()).isEqualTo("socks5");
                assertThat(client.recorder().event().authScheme()).isEqualTo("none");
                assertThat((InetSocketAddress) client.recorder().event().destinationAddress()).isEqualTo(ipv6Destination);
            }
            proxyServer.awaitSuccess();
        }
    }

    @Test
    void reportsSocks5AuthenticationFailure() throws Exception {
        try (FakeProxyServer proxyServer = FakeProxyServer.start((input, output) -> {
            assertNextBytes(input, 0x05, 0x02, 0x00, 0x02);
            writeBytes(output, 0x05, 0x02);
            assertNextBytes(input, 0x01);
            int usernameLength = readUnsignedByte(input);
            assertThat(readAscii(input, usernameLength)).isEqualTo("user");
            int passwordLength = readUnsignedByte(input);
            assertThat(readAscii(input, passwordLength)).isEqualTo("wrong");
            writeBytes(output, 0x01, 0xff);
        })) {
            Socks5ProxyHandler handler = new Socks5ProxyHandler(proxyServer.address(), "user", "wrong");

            try (ClientConnection client = ClientConnection.connect(handler, SOCKS_DOMAIN_DESTINATION, false)) {
                Future<Channel> connectFuture = handler.connectFuture();
                assertThat(connectFuture.isDone()).isTrue();
                assertThat(connectFuture.isSuccess()).isFalse();
                assertThat(connectFuture.cause()).isInstanceOf(ProxyConnectException.class);
                assertThat(connectFuture.cause().getMessage())
                        .contains("socks5")
                        .contains("password")
                        .contains("authStatus: FAILURE");
                assertThat(client.recorder().event()).isNull();
            }
            proxyServer.awaitSuccess();
        }
    }

    private static InetSocketAddress unusedProxyAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 6553);
    }

    private static String basicAuthorization(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> readHttpHeaderBlock(InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while (!(line = readHttpLine(input)).isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    private static void assertContainsHeader(List<String> lines, String name, String value) {
        boolean found = lines.stream().anyMatch(line -> {
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                return false;
            }
            String actualName = line.substring(0, colonIndex);
            String actualValue = line.substring(colonIndex + 1).trim();
            return actualName.equalsIgnoreCase(name) && actualValue.equals(value);
        });
        assertThat(found).describedAs("%s: %s was present in %s", name, value, lines).isTrue();
    }

    private static String readHttpLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Unexpected end of HTTP header block");
            }
            if (previous == '\r' && value == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            line.write(value);
            previous = value;
        }
    }

    private static void assertNextBytes(InputStream input, int... expectedBytes) throws IOException {
        for (int expectedByte : expectedBytes) {
            assertThat(readUnsignedByte(input)).isEqualTo(expectedByte);
        }
    }

    private static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new IOException("Unexpected end of stream");
            }
            offset += read;
        }
        return buffer;
    }

    private static int readUnsignedByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("Unexpected end of stream");
        }
        return value;
    }

    private static int readUnsignedShort(InputStream input) throws IOException {
        return (readUnsignedByte(input) << 8) | readUnsignedByte(input);
    }

    private static String readAscii(InputStream input, int length) throws IOException {
        return new String(readFully(input, length), StandardCharsets.US_ASCII);
    }

    private static String readNullTerminatedAscii(InputStream input) throws IOException {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int next;
        while ((next = readUnsignedByte(input)) != 0) {
            value.write(next);
        }
        return value.toString(StandardCharsets.US_ASCII.name());
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static void writeBytes(OutputStream output, int... values) throws IOException {
        for (int value : values) {
            output.write(value);
        }
        output.flush();
    }

    private interface ProxyExchange {
        void handle(InputStream input, OutputStream output) throws Exception;
    }

    private static final class FakeProxyServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private FakeProxyServer(ServerSocket serverSocket, ProxyExchange exchange) {
            this.serverSocket = serverSocket;
            this.thread = new Thread(() -> run(exchange), "netty-handler-proxy-test-server");
            this.thread.setDaemon(true);
        }

        static FakeProxyServer start(ProxyExchange exchange) throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(IO_TIMEOUT_MILLIS);
            FakeProxyServer server = new FakeProxyServer(serverSocket, exchange);
            server.thread.start();
            return server;
        }

        InetSocketAddress address() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
        }

        void awaitSuccess() throws InterruptedException {
            assertThat(done.await(IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            Throwable throwable = failure.get();
            if (throwable != null) {
                throw new AssertionError("Fake proxy exchange failed", throwable);
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private void run(ProxyExchange exchange) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(IO_TIMEOUT_MILLIS);
                exchange.handle(socket.getInputStream(), socket.getOutputStream());
            } catch (SocketTimeoutException e) {
                failure.set(new AssertionError("Timed out waiting for proxy client data", e));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        }
    }

    private static final class ClientConnection implements Closeable {
        private final EventLoopGroup group;
        private final Channel channel;
        private final EventRecorder recorder;

        private ClientConnection(EventLoopGroup group, Channel channel, EventRecorder recorder) {
            this.group = group;
            this.channel = channel;
            this.recorder = recorder;
        }

        static ClientConnection connect(ProxyHandler proxyHandler, InetSocketAddress destination) throws Exception {
            return connect(proxyHandler, destination, true);
        }

        static ClientConnection connect(ProxyHandler proxyHandler, InetSocketAddress destination, boolean expectSuccess)
                throws Exception {
            EventLoopGroup group = new NioEventLoopGroup(1);
            EventRecorder recorder = new EventRecorder();
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .resolver(NoopAddressResolverGroup.INSTANCE)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(proxyHandler);
                            channel.pipeline().addLast(recorder);
                        }
                    });
            ChannelFuture tcpConnectFuture = bootstrap.connect(destination);
            assertThat(tcpConnectFuture.await(IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(tcpConnectFuture.isSuccess())
                    .describedAs("TCP connection to proxy failed: %s", tcpConnectFuture.cause())
                    .isTrue();
            Future<Channel> proxyConnectFuture = proxyHandler.connectFuture();
            assertThat(proxyConnectFuture.await(IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(proxyConnectFuture.isSuccess()).isEqualTo(expectSuccess);
            return new ClientConnection(group, tcpConnectFuture.channel(), recorder);
        }

        EventRecorder recorder() {
            return recorder;
        }

        @Override
        public void close() {
            channel.close().awaitUninterruptibly(IO_TIMEOUT_MILLIS);
            group.shutdownGracefully(0, IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).awaitUninterruptibly(IO_TIMEOUT_MILLIS);
        }
    }

    private static final class EventRecorder extends ChannelInboundHandlerAdapter {
        private ProxyConnectionEvent event;

        ProxyConnectionEvent event() {
            return event;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof ProxyConnectionEvent) {
                this.event = (ProxyConnectionEvent) event;
            }
            super.userEventTriggered(context, event);
        }
    }
}
