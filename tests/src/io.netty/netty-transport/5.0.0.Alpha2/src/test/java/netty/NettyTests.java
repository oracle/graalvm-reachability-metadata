/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
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
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.awaitility.Awaitility;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

public class NettyTests {
    @Test
    void servesHttpResponse() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Channel serverChannel = null;
        try {
            serverChannel = startServer(bossGroup, workerGroup);
            AtomicReference<Response> response = new AtomicReference<>();
            startClient(workerGroup, ((NioServerSocketChannel) serverChannel).localAddress().getPort(), response::set);
            Awaitility.await().atMost(Duration.ofSeconds(10))
                    .untilAtomic(response, CoreMatchers.equalTo(new Response(200, "HTTP/1.1", "Hello World")));
        } finally {
            if (serverChannel != null) {
                serverChannel.close();
            }
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static void startClient(EventLoopGroup group, int port, Consumer<Response> callback) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioSocketChannel.class).handler(new HttpClientInitializer(callback));
        Channel channel = bootstrap.connect("localhost", port).sync().channel();
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, "localhost");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        channel.writeAndFlush(request);
    }

    private static Channel startServer(EventLoopGroup bossGroup, EventLoopGroup workerGroup) throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new HttpServerInitializer());
        return bootstrap.bind(0).sync().channel();
    }

    private static final class HttpClientInitializer extends ChannelInitializer<SocketChannel> {
        private final Consumer<Response> callback;

        private HttpClientInitializer(Consumer<Response> callback) {
            this.callback = callback;
        }

        @Override
        protected void initChannel(SocketChannel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new HttpClientCodec());
            pipeline.addLast(new HttpObjectAggregator(1048576));
            pipeline.addLast(new HttpClientHandler(callback));
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
        protected void messageReceived(ChannelHandlerContext context, HttpObject message) {
            if (message instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) message;
                status = response.status().code();
                protocol = response.protocolVersion().toString();
            }
            if (message instanceof HttpContent) {
                HttpContent httpContent = (HttpContent) message;
                content.append(httpContent.content().toString(CharsetUtil.UTF_8));
                if (httpContent instanceof LastHttpContent) {
                    callback.accept(new Response(status, protocol, content.toString()));
                    context.close();
                }
            }
        }
    }

    private static final class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new HttpRequestDecoder());
            pipeline.addLast(new HttpObjectAggregator(1048576));
            pipeline.addLast(new HttpResponseEncoder());
            pipeline.addLast(new HttpServerHandler());
        }
    }

    private static final class HttpServerHandler extends SimpleChannelInboundHandler<Object> {
        private boolean keepAlive;

        @Override
        protected void messageReceived(ChannelHandlerContext context, Object message) {
            if (message instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) message;
                keepAlive = !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true);
                if (request.headers().contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true)) {
                    send100Continue(context);
                }
            }
            if (message instanceof LastHttpContent) {
                writeResponse(context);
                if (!keepAlive) {
                    context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

        private void writeResponse(ChannelHandlerContext context) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("Hello World", CharsetUtil.UTF_8));
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH,
                        Integer.toString(response.content().readableBytes()));
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            context.write(response);
        }

        private static void send100Continue(ChannelHandlerContext context) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
            context.write(response);
        }
    }

    private static final class Response {
        private final int status;
        private final String protocol;
        private final String content;

        private Response(int status, String protocol, String content) {
            this.status = status;
            this.protocol = protocol;
            this.content = content;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Response)) {
                return false;
            }
            Response response = (Response) object;
            return status == response.status && Objects.equals(protocol, response.protocol)
                    && Objects.equals(content, response.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, protocol, content);
        }
    }
}
