/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.awaitility.Awaitility;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NettyTests {
    private int port;

    @Test
    void withSsl() throws Exception {
        test(true);
    }

    @Test
    public void noSsl() throws Exception {
        test(false);
    }

    private void test(boolean ssl) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            startServer(bossGroup, workerGroup, ssl);
            AtomicReference<Response> response = new AtomicReference<>();
            startClient(workerGroup, ssl, response::set);
            Awaitility.await().atMost(Duration.ofSeconds(10))
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

    private SSLContext createClientSslContext() throws Exception {
        TrustManager[] trustManagers = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);
        return sslContext;
    }

    private SSLContext createServerSslContext() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        String pem;
        try (InputStream certStream = loadCert(); InputStream keyStream = loadKey()) {
            certificate = certificateFactory.generateCertificate(certStream);
            pem = new String(keyStream.readAllBytes(), CharsetUtil.UTF_8);
        }
        String key = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("test", privateKey, new char[0], new Certificate[] { certificate });

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

    private void startClient(EventLoopGroup group, boolean ssl, Consumer<Response> callback) throws Exception {
        SSLContext sslContext = null;
        if (ssl) {
            sslContext = createClientSslContext();
        }
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class).handler(new HttpClientInitializer(sslContext, callback));
        ChannelFuture connectFuture = b.connect("localhost", port);
        Assertions.assertTrue(connectFuture.await(10, TimeUnit.SECONDS), "Timed out connecting to Netty server");
        Assertions.assertTrue(connectFuture.isSuccess(),
                () -> "Failed connecting to Netty server: " + connectFuture.cause());
        Channel ch = connectFuture.channel();
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaders.Names.HOST, "localhost");
        request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        ch.writeAndFlush(request);
        Assertions.assertTrue(ch.closeFuture().await(10, TimeUnit.SECONDS),
                "Timed out waiting for Netty client channel to close");
    }

    private void startServer(EventLoopGroup bossGroup, EventLoopGroup workerGroup, boolean ssl) throws Exception {
        SSLContext sslContext = null;
        if (ssl) {
            sslContext = createServerSslContext();
        }
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new HttpServerInitializer(sslContext));
        ChannelFuture bindFuture = b.bind(0);
        Assertions.assertTrue(bindFuture.await(10, TimeUnit.SECONDS), "Timed out binding Netty server");
        Assertions.assertTrue(bindFuture.isSuccess(), () -> "Failed binding Netty server: " + bindFuture.cause());
        Channel channel = bindFuture.channel();
        this.port = ((NioServerSocketChannel) channel).localAddress().getPort();
    }

    private static final class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SSLContext sslContext;

        private final Consumer<Response> callback;

        private HttpClientInitializer(SSLContext sslContext, Consumer<Response> callback) {
            this.sslContext = sslContext;
            this.callback = callback;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslContext != null) {
                SSLEngine engine = sslContext.createSSLEngine("localhost", 0);
                engine.setUseClientMode(true);
                p.addLast(new SslHandler(engine));
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
        protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                this.status = response.getStatus().code();
                this.protocol = response.getProtocolVersion().toString();
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

        private final SSLContext sslCtx;

        HttpServerInitializer(SSLContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslCtx != null) {
                SSLEngine engine = sslCtx.createSSLEngine();
                engine.setUseClientMode(false);
                p.addLast(new SslHandler(engine));
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
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                this.keepAlive = HttpHeaders.isKeepAlive(request);
                if (HttpHeaders.is100ContinueExpected(request)) {
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
                response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
                response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
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
