/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.InputStream;
import java.net.Inet6Address;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import org.awaitility.Awaitility;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static io.netty.channel.socket.InternetProtocolFamily.IPv4;
import static io.netty.util.NetUtil.LOCALHOST;

public class NettyTests {
    @Test
    void withSsl() throws Exception {
        test(true, false);
    }

    @Test
    public void noSsl() throws Exception {
        test(false, false);
    }

    @Test
    public void withAdaptive() throws Exception {
        test(true, true);
    }

    @Test
    void testNioSocketChannel() {
        Assumptions.assumeTrue(LOCALHOST instanceof Inet6Address);

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap().group(group)
                    .channelFactory(() -> new NioSocketChannel(SelectorProvider.provider(), IPv4))
                    .handler(new LoggingHandler());
            Assertions.assertThrows(UnsupportedAddressTypeException.class, () -> b.bind(LOCALHOST, 7777).sync().channel());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    void testNioServerSocketChannel() {
        Assumptions.assumeTrue(LOCALHOST instanceof Inet6Address);

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap().group(group)
                    .channelFactory(() -> new NioServerSocketChannel(SelectorProvider.provider(), IPv4))
                    .handler(new LoggingHandler());
            Assertions.assertThrows(UnsupportedAddressTypeException.class, () -> b.bind(LOCALHOST, 7777).sync().channel());
        } finally {
            group.shutdownGracefully();
        }
    }

    private void test(boolean ssl, boolean withAdaptive) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            int port = startServer(bossGroup, workerGroup, ssl, withAdaptive);
            AtomicReference<Response> response = new AtomicReference<>();
            startClient(workerGroup, ssl, withAdaptive, port, response::set);
            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .untilAtomic(response, CoreMatchers.equalTo(new Response(200, "HTTP/1.1", "Hello World")));
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private InputStream loadKey() {
        return Objects.requireNonNull(NettyTests.class.getResourceAsStream("/key.pem"), "/key.pem not found");
    }

    private InputStream loadCert() {
        return Objects.requireNonNull(NettyTests.class.getResourceAsStream("/cert.pem"), "/cert.pem not found");
    }

    private void startClient(EventLoopGroup group, boolean ssl, boolean withAdaptive, int port, Consumer<Response> callback) throws InterruptedException, SSLException {
        SslContext sslContext = null;
        if (ssl) {
            sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        }
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class).handler(new HttpClientInitializer(sslContext, callback));
        if (withAdaptive) {
            b.option(ChannelOption.ALLOCATOR, new AdaptiveByteBufAllocator());
        }
        Channel ch = b.connect("localhost", port).sync().channel();
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, "localhost");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ch.writeAndFlush(request);
        ch.closeFuture().sync();
    }

    private int startServer(EventLoopGroup bossGroup, EventLoopGroup workerGroup, boolean ssl, boolean withAdaptive) throws InterruptedException, SSLException {
        SslContext sslContext = null;
        if (ssl) {
            sslContext = SslContextBuilder.forServer(loadCert(), loadKey(), null).build();
        }
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new HttpServerInitializer(sslContext));
        if (withAdaptive) {
            b.option(ChannelOption.ALLOCATOR, new AdaptiveByteBufAllocator())
                    .childOption(ChannelOption.ALLOCATOR, new AdaptiveByteBufAllocator());
        }
        return ((ServerSocketChannel) b.bind(0).sync().channel()).localAddress().getPort();
    }

    private static final class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;

        private final Consumer<Response> callback;

        private HttpClientInitializer(SslContext sslContext, Consumer<Response> callback) {
            this.sslContext = sslContext;
            this.callback = callback;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc()));
            }
            p.addLast(new HttpClientCodec());
            p.addLast(new HttpContentDecompressor());
            p.addLast(new HttpObjectAggregator(1048576));
            p.addLast(new HttpClientHandler(this.callback));
        }
    }

    private static final class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final Consumer<Response> callback;

        private int status;

        private String protocol;

        private final StringBuilder content = new StringBuilder();

        private HttpClientHandler(Consumer<Response> callback) {
            this.callback = callback;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                this.status = response.status().code();
                this.protocol = response.protocolVersion().toString();
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                this.content.append(content.content().toString(CharsetUtil.UTF_8));
                if (content instanceof LastHttpContent) {
                    this.callback.accept(new Response(this.status, this.protocol, this.content.toString()));
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    private static class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslCtx;

        HttpServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslCtx != null) {
                p.addLast(sslCtx.newHandler(ch.alloc()));
            }
            p.addLast(new HttpRequestDecoder());
            p.addLast(new HttpObjectAggregator(1048576));
            p.addLast(new HttpResponseEncoder());
            p.addLast(new HttpContentCompressor());
            p.addLast(new HttpServerHandler());
        }
    }

    private static class HttpServerHandler extends SimpleChannelInboundHandler<Object> {
        private boolean keepAlive;

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                this.keepAlive = HttpUtil.isKeepAlive(request);
                if (HttpUtil.is100ContinueExpected(request)) {
                    send100Continue(ctx);
                }
            }

            if (msg instanceof LastHttpContent) {
                writeResponse(ctx);
                if (!this.keepAlive) {
                    ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }

        private void writeResponse(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("Hello World", CharsetUtil.UTF_8));
            if (this.keepAlive) {
                response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(response);
        }

        private static void send100Continue(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
            ctx.write(response);
        }
    }

    private static class Response {
        private final int status;

        private final String protocol;

        private final String content;

        Response(int status, String protocol, String content) {
            this.status = status;
            this.protocol = protocol;
            this.content = content;
        }

        @Override
        public String toString() {
            return "Response{" +
                    "status=" + status +
                    ", protocol='" + protocol + '\'' +
                    ", content='" + content + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Response response = (Response) o;
            return status == response.status && Objects.equals(protocol, response.protocol) && Objects.equals(content, response.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, protocol, content);
        }
    }
}
