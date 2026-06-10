/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_netty_jvm

import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.push
import io.ktor.server.netty.Netty
import io.ktor.server.response.UseHttp2Push
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2Frame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.CharsetUtil
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

public class NettyHttp2HandlerTest {
    @OptIn(UseHttp2Push::class)
    @Test
    fun http2PushPromiseCreatesPromisedStream() {
        val keyAlias: String = "test-key"
        val keyPassword: String = "changeit"
        val keyStore: KeyStore = generateCertificate(
            keyAlias = keyAlias,
            keyPassword = keyPassword,
            algorithm = "SHA256withRSA",
            keySizeInBits = 2048
        )
        val server = embeddedServer(
            Netty,
            configure = {
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = { keyPassword.toCharArray() },
                    privateKeyPassword = { keyPassword.toCharArray() }
                ) {
                    host = "127.0.0.1"
                    port = 0
                }
            }
        ) {
            routing {
                get("/") {
                    call.push("/pushed")
                    call.respondText("root")
                }
                get("/pushed") {
                    call.respondText("pushed")
                }
            }
        }
        val clientGroup: NioEventLoopGroup = NioEventLoopGroup(1)

        try {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().single().port }
            val result: Http2ClientResult = requestWithServerPushEnabled(clientGroup, port)

            result.failure.get()?.let { throw AssertionError("HTTP/2 client failed", it) }
            assertThat(result.rootReceived.await(10, TimeUnit.SECONDS)).isTrue()
            result.failure.get()?.let { throw AssertionError("HTTP/2 client failed", it) }
            assertThat(result.pushedReceived.await(10, TimeUnit.SECONDS)).isTrue()
            result.failure.get()?.let { throw AssertionError("HTTP/2 client failed", it) }
            assertThat(result.rootBody.toString()).isEqualTo("root")
            assertThat(result.pushedBody.toString()).isEqualTo("pushed")
        } finally {
            clientGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly()
            server.stop(gracePeriodMillis = 0, timeoutMillis = 5_000)
        }
    }

    private fun requestWithServerPushEnabled(
        clientGroup: NioEventLoopGroup,
        port: Int
    ): Http2ClientResult {
        val result: Http2ClientResult = Http2ClientResult()
        val sslContext: SslContext = SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2
                )
            )
            .build()
        val bootstrap: Bootstrap = Bootstrap()
            .group(clientGroup)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline().addLast(sslContext.newHandler(channel.alloc(), "127.0.0.1", port))
                    channel.pipeline().addLast(Http2ClientAlpnHandler(result, port))
                }
            })
        val channel = bootstrap.connect("127.0.0.1", port).syncUninterruptibly().channel()

        result.rootReceived.await(10, TimeUnit.SECONDS)
        result.pushedReceived.await(10, TimeUnit.SECONDS)
        channel.close().syncUninterruptibly()
        return result
    }
}

private class Http2ClientAlpnHandler(
    private val result: Http2ClientResult,
    private val port: Int
) : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {
    override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
        if (protocol != ApplicationProtocolNames.HTTP_2) {
            throw IllegalStateException("Expected HTTP/2 via ALPN but negotiated $protocol")
        }

        context.pipeline().addLast(
            Http2FrameCodecBuilder.forClient()
                .initialSettings(Http2Settings().pushEnabled(true))
                .build()
        )
        context.pipeline().addLast(RootRequestHandler(result, port))
        context.pipeline().addLast(Http2MultiplexHandler(PushedStreamHandler(result)))
    }
}

private class RootRequestHandler(
    private val result: Http2ClientResult,
    private val port: Int
) : SimpleChannelInboundHandler<Http2SettingsFrame>() {
    private var requestSent: Boolean = false

    override fun channelRead0(context: ChannelHandlerContext, frame: Http2SettingsFrame) {
        if (requestSent) {
            return
        }

        requestSent = true
        Http2StreamChannelBootstrap(context.channel())
            .handler(ResponseBodyHandler(result.rootBody, result.rootReceived, result))
            .open()
            .addListener { future ->
                if (!future.isSuccess) {
                    result.fail(future.cause())
                    return@addListener
                }

                val streamChannel: Http2StreamChannel = future.getNow() as Http2StreamChannel
                val headers: DefaultHttp2Headers = DefaultHttp2Headers().apply {
                    method(HttpMethod.GET.asciiName())
                    scheme(HttpScheme.HTTPS.name())
                    authority("127.0.0.1:$port")
                    path("/")
                }
                streamChannel.writeAndFlush(DefaultHttp2HeadersFrame(headers, true))
                    .addListener { writeFuture ->
                        if (!writeFuture.isSuccess) {
                            result.fail(writeFuture.cause())
                        }
                    }
            }
    }
}

private class PushedStreamHandler(
    private val result: Http2ClientResult
) : ChannelInitializer<Http2StreamChannel>() {
    override fun initChannel(channel: Http2StreamChannel) {
        channel.pipeline().addLast(ResponseBodyHandler(result.pushedBody, result.pushedReceived, result))
    }
}

private class ResponseBodyHandler(
    private val body: StringBuilder,
    private val received: CountDownLatch,
    private val result: Http2ClientResult
) : SimpleChannelInboundHandler<Http2Frame>() {
    override fun channelRead0(context: ChannelHandlerContext, frame: Http2Frame) {
        when (frame) {
            is Http2DataFrame -> {
                body.append(frame.content().toString(CharsetUtil.UTF_8))
                if (frame.isEndStream) {
                    received.countDown()
                }
            }
            is Http2HeadersFrame -> {
                if (frame.isEndStream) {
                    received.countDown()
                }
            }
        }
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        result.fail(cause)
        context.close()
    }
}

private class Http2ClientResult {
    val rootReceived: CountDownLatch = CountDownLatch(1)
    val pushedReceived: CountDownLatch = CountDownLatch(1)
    val rootBody: StringBuilder = StringBuilder()
    val pushedBody: StringBuilder = StringBuilder()
    val failure: AtomicReference<Throwable> = AtomicReference()

    fun fail(cause: Throwable?) {
        if (cause != null) {
            failure.compareAndSet(null, cause)
        }
        rootReceived.countDown()
        pushedReceived.countDown()
    }
}
