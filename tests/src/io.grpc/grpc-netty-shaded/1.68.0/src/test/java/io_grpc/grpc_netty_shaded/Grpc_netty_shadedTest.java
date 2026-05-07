/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

public class Grpc_netty_shadedTest {
    private static final String SERVICE_NAME = "nativeimage.Echo";
    private static final MethodDescriptor<String, String> UNARY_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "UnaryEcho"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();
    private static final MethodDescriptor<String, String> BIDI_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "BidirectionalEcho"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();
    private static final String CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDJTCCAg2gAwIBAgIUArwIcVEoOwEJewbp6lAmm8+RTjcwDQYJKoZIhvcNAQEL
            BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUwNzIwMjExN1oXDTM2MDUw
            NDIwMjExN1owFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
            AAOCAQ8AMIIBCgKCAQEAs1kiOcjYT6DwFREt0+g/caZKSg3Z04sw9IU6GA1O9wW6
            zjhof8KwfZtfjduEh5U8VrXGZbOo3KnSCkc9aVCuY5X01dJ7wd5lbDUDxkG/dUqa
            So78jkrgfB+8tNv0qp29xeEJqpl0i9Ur+BcxfJIsxyNh4HO11JWgezJNJhK/3Z0J
            1RBhJiyb1VSpsntWOOMeiP2w7Rtyec9dbnsd8sJ9X4QoHDgWZPuLMDiiN8g+VSOw
            ntIpmvsPj6Sb5dohi971Zi5zbpIln65QQuXg0Sz8FsvG/LUtxipbR7Qs/fIU8W9c
            iZVhE6GH7iAnM0FcXrDyNFGEiGpqtfHyNwCzW9VV0wIDAQABo28wbTAdBgNVHQ4E
            FgQUqWq7we9gGLTrIEEy7b1kH+xVTCgwHwYDVR0jBBgwFoAUqWq7we9gGLTrIEEy
            7b1kH+xVTCgwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH
            BH8AAAEwDQYJKoZIhvcNAQELBQADggEBAHuHFpgCLoWVewE4pIkYuNiKzR168MgP
            FOvLikKTaXvCtxF78rqiIHyeKuBEc0Loi0nO1mkfCkEmBi+ZWpgwo4uKNChKPcTm
            ObFuGG3QIxfvtcmOXOmRrXxaHnjbDfHTMUnWqvWCSc4paaAyRxQpObMXQ65EHpiI
            2VHOKzf/P4AOMeN7hIX8PGkzrWKaAtI7TX/G4rU2q3dhmCy2IhXmKQHwRYU+FpuA
            A3007No3C/86xmlWYzT3b9zEoZq6iY8ghMTBG4D4nNXM1AkVRIPURskp+H5XV6P0
            GkrUF7J0WmfD+elZro00/BqWwGbamTrXwk8emOhC0KFoR2sfqgIxA/Q=
            -----END CERTIFICATE-----
            """;
    private static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCzWSI5yNhPoPAV
            ES3T6D9xpkpKDdnTizD0hToYDU73BbrOOGh/wrB9m1+N24SHlTxWtcZls6jcqdIK
            Rz1pUK5jlfTV0nvB3mVsNQPGQb91SppKjvyOSuB8H7y02/Sqnb3F4QmqmXSL1Sv4
            FzF8kizHI2Hgc7XUlaB7Mk0mEr/dnQnVEGEmLJvVVKmye1Y44x6I/bDtG3J5z11u
            ex3ywn1fhCgcOBZk+4swOKI3yD5VI7Ce0ima+w+PpJvl2iGL3vVmLnNukiWfrlBC
            5eDRLPwWy8b8tS3GKltHtCz98hTxb1yJlWEToYfuICczQVxesPI0UYSIamq18fI3
            ALNb1VXTAgMBAAECggEAGIxySVuK9DdQtWmBDtji2kELOSC1OKX8QPP9dC5aKSjB
            HZoN/7Lb6o16YlXySYatWCcQbXpOeknKZLrElqZDAIGVnjDt3Kb+1fVZu2jjdoAM
            J3lz61wnZwYHE/BpiHMH905qvs27bKp0lsRB802k0Gsw6gKcmGkUqthDRBtb5M7v
            ymkNvcDPJyEqNjI53Wp1dMqhgWhZkZohW+Y8wYvdluZpQgyj7mrPujOCTG6M/vXP
            zvTNAAby7DPVfSZLSOWEzzudBOoNlRkEvEpaKyJer8ytg/u4BHcY4carGbrVQx42
            8Qw1ydAETb+U0YlPw/ZTuX5Le9OnfmSXWLav4ep7/QKBgQDc5jJCtS0TXxF+yBed
            i1FRyJKO/QyvArlxMJl57NwjtOpqG1UuWhKyf2Uxv5arE4i8rAjlAw+MFk0szAih
            OMqVXEmV24p61qZquk5+ikV7WOEsJrGv/PLYq9idg9XaOqQ87bz8pGyW92s//5s4
            7zauHjUUZmRZ/BZO8bXuZhUVFwKBgQDP2LZcdb+RXhiM+d+IHdfOoMRyr1LI1Omw
            p0/vgx764ll4W43vY7Z8rxQQSK6lHuiY3JvsHcdiyy5gayS/miFpP83xPHjgni2d
            RD2IgQVUiBymxkhTMBQn7tg9Qw/49sMS9X43iyMEHpAv5h8wHN8rw0sVgc64lciU
            8z6RD4nypQKBgQC58VORb2yYB8h0Tf4C8YjsLMehcUTB9KsgqmYmicjsjZdc5dEY
            CV3/vtjxvXIYY4MQPkfmbmMh6ovgD4ecHm/4tgyDBqBUsma3JEh6n+3I3JH+Vjvw
            Bh5tYIogXR8gaYhieURB7i4yDebLol+I12PRwT+xAleqn1Yv8arRGEDa1QKBgQCH
            SlXxu0d19Rzf7uocrOhDfIxC5nJpfYWb0lyK1/u7bMi2OkoaT/qCEGhr7ROZMZRP
            pBHuULfvS7glVLi36zjiTIDeDPHVq8CfRMMU7n6stmiH+jsrwvjrwWGKBvQHp3/1
            AE0nFG83iDlspEsaw0BVOSrPlg4cQosswWSxgb7WbQKBgQC8u51j1PMX8/Fy5C5h
            SseFMVLfeplU1nRRzyaeMlsq7C3m/Ake7AMXh8qWzxHl+3VH/2huj24SldVH6hMT
            CEIAGL3dW1NUiV6OykWo9HQvJpMQ/31bqbBZ1hFR5pOLoIWMFf4evMlOVroWS6qx
            4XI0vkZZ+pWv876ANMzCwMswmw==
            -----END PRIVATE KEY-----
            """;

    @Test
    void plaintextNettyTransportHandlesUnaryCall() throws Exception {
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .directExecutor()
                    .maxInboundMessageSize(1024 * 1024)
                    .build();

            assertEquals("echo:plain", invokeUnary(channel, "plain"));
        } finally {
            shutdown(channel, server);
        }
    }

    @Test
    void customNioEventLoopsSupportBidirectionalStreaming() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        EventLoopGroup clientGroup = new NioEventLoopGroup(1);
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0)
                    .bossEventLoopGroup(bossGroup)
                    .workerEventLoopGroup(workerGroup)
                    .channelType(NioServerSocketChannel.class)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .eventLoopGroup(clientGroup)
                    .channelType(NioSocketChannel.class)
                    .usePlaintext()
                    .directExecutor()
                    .build();

            assertEquals(List.of("stream:one", "stream:two"), invokeBidirectionalStreaming(channel, "one", "two"));
        } finally {
            shutdown(channel, server);
            shutdownGracefully(clientGroup);
            shutdownGracefully(workerGroup);
            shutdownGracefully(bossGroup);
        }
    }

    @Test
    void tlsNettyTransportHandlesTrustedServerCertificate(@TempDir Path tempDir) throws Exception {
        Path certificateFile = Files.writeString(tempDir.resolve("localhost-cert.pem"), CERTIFICATE_PEM);
        Path privateKeyFile = Files.writeString(tempDir.resolve("localhost-key.pem"), PRIVATE_KEY_PEM);
        SslContext serverSslContext = GrpcSslContexts
                .forServer(certificateFile.toFile(), privateKeyFile.toFile())
                .build();
        SslContext clientSslContext = GrpcSslContexts.forClient()
                .trustManager(certificateFile.toFile())
                .build();
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0)
                    .sslContext(serverSslContext)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                    .sslContext(clientSslContext)
                    .overrideAuthority("localhost")
                    .directExecutor()
                    .build();

            assertEquals("echo:secure", invokeUnary(channel, "secure"));
        } finally {
            shutdown(channel, server);
        }
    }

    private static ServerServiceDefinition echoService() {
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(UNARY_METHOD, new UnaryEchoHandler())
                .addMethod(BIDI_METHOD, new BidirectionalEchoHandler())
                .build();
    }

    private static String invokeUnary(ManagedChannel channel, String request) throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<Status> status = new AtomicReference<>();
        ClientCall<String, String> call = channel.newCall(
                UNARY_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                response.set(message);
            }

            @Override
            public void onClose(Status closedStatus, Metadata trailers) {
                status.set(closedStatus);
                closed.countDown();
            }
        }, new Metadata());
        call.request(1);
        call.sendMessage(request);
        call.halfClose();

        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(Status.Code.OK, status.get().getCode());
        assertNotNull(response.get());
        return response.get();
    }

    private static List<String> invokeBidirectionalStreaming(ManagedChannel channel, String... requests)
            throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        List<String> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Status> status = new AtomicReference<>();
        ClientCall<String, String> call = channel.newCall(
                BIDI_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                responses.add(message);
            }

            @Override
            public void onClose(Status closedStatus, Metadata trailers) {
                status.set(closedStatus);
                closed.countDown();
            }
        }, new Metadata());
        call.request(requests.length);
        for (String request : requests) {
            call.sendMessage(request);
        }
        call.halfClose();

        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(Status.Code.OK, status.get().getCode());
        return List.copyOf(responses);
    }

    private static void shutdown(ManagedChannel channel, Server server) throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            assertTrue(channel.awaitTermination(5, TimeUnit.SECONDS));
        }
        if (server != null) {
            server.shutdownNow();
            assertTrue(server.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void shutdownGracefully(EventLoopGroup group) throws InterruptedException {
        assertTrue(group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS));
    }

    private static final class UnaryEchoHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                private String request;

                @Override
                public void onMessage(String message) {
                    request = message;
                }

                @Override
                public void onHalfClose() {
                    call.sendMessage("echo:" + request);
                    call.close(Status.OK, new Metadata());
                }
            };
        }
    }

    private static final class BidirectionalEchoHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("stream:" + message);
                    call.request(1);
                }

                @Override
                public void onHalfClose() {
                    call.close(Status.OK, new Metadata());
                }
            };
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), UTF_8);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read gRPC message", exception);
            }
        }
    }
}
