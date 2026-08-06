/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ChannelLogger;
import io.grpc.ClientStreamTracer;
import io.grpc.ConnectivityState;
import io.grpc.InternalChannelz;
import io.grpc.InternalInstrumented;
import io.grpc.InternalLogId;
import io.grpc.InternalWithLogId;
import io.grpc.Metadata;
import io.grpc.NameResolver;
import io.grpc.ProxyDetector;
import io.grpc.SynchronizationContext;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

/** Exercises Channelz's public diagnostics model with a realistic transport lifecycle. */
public class GrpcApiChannelzLifecycleTest {
    @Test
    void channelzTracksAndPaginatesRegisteredTransportResources() {
        InternalChannelz channelz = new InternalChannelz();
        Instrumented<InternalChannelz.ChannelStats> root = new Instrumented<>("root");
        Instrumented<InternalChannelz.ChannelStats> subchannel = new Instrumented<>("subchannel");
        Instrumented<InternalChannelz.ServerStats> server = new Instrumented<>("server");
        Instrumented<InternalChannelz.SocketStats> clientSocket = new Instrumented<>("client-socket");
        Instrumented<InternalChannelz.SocketStats> listenSocket = new Instrumented<>("listen-socket");

        channelz.addRootChannel(root);
        channelz.addSubchannel(subchannel);
        channelz.addServer(server);
        channelz.addClientSocket(clientSocket);
        channelz.addListenSocket(listenSocket);
        channelz.addServerSocket(server, listenSocket);

        assertThat(channelz.getRootChannel(InternalChannelz.id(root))).isSameAs(root);
        assertThat(channelz.getChannel(InternalChannelz.id(root))).isSameAs(root);
        assertThat(channelz.getSubchannel(InternalChannelz.id(subchannel))).isSameAs(subchannel);
        assertThat(channelz.getSocket(InternalChannelz.id(clientSocket))).isSameAs(clientSocket);
        assertThat(channelz.containsServer(server.getLogId())).isTrue();
        assertThat(channelz.containsSubchannel(subchannel.getLogId())).isTrue();
        assertThat(channelz.containsClientSocket(clientSocket.getLogId())).isTrue();
        assertThat(channelz.getRootChannels(0, 10).channels).containsExactly(root);
        assertThat(channelz.getServers(0, 10).servers).containsExactly(server);
        assertThat(channelz.getServerSockets(InternalChannelz.id(server), 0, 10).sockets)
                .containsExactly(listenSocket);

        channelz.removeServerSocket(server, listenSocket);
        channelz.removeListenSocket(listenSocket);
        channelz.removeClientSocket(clientSocket);
        channelz.removeServer(server);
        channelz.removeSubchannel(subchannel);
        channelz.removeRootChannel(root);
        assertThat(channelz.getRootChannels(0, 10).channels).isEmpty();
        assertThat(channelz.getServers(0, 10).servers).isEmpty();
        assertThat(channelz.getSocket(InternalChannelz.id(clientSocket))).isNull();
        assertThat(channelz.getSubchannel(InternalChannelz.id(subchannel))).isNull();
        assertThat(InternalChannelz.instance()).isNotNull();
    }

    @Test
    void channelzValueBuildersDescribeSocketAndServerStatistics() {
        InternalChannelz.TcpInfo tcp = new InternalChannelz.TcpInfo.Builder()
                .setState(1).setCaState(2).setRetransmits(3).setProbes(4).setBackoff(5).setOptions(6)
                .setSndWscale(7).setRcvWscale(8).setRto(9).setAto(10).setSndMss(11).setRcvMss(12)
                .setUnacked(13).setSacked(14).setLost(15).setRetrans(16).setFackets(17)
                .setLastDataSent(18).setLastAckSent(19).setLastDataRecv(20).setLastAckRecv(21)
                .setPmtu(22).setRcvSsthresh(23).setRtt(24).setRttvar(25).setSndSsthresh(26)
                .setSndCwnd(27).setAdvmss(28).setReordering(29).build();
        InternalChannelz.TransportStats transport = new InternalChannelz.TransportStats(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        InternalChannelz.Security security = new InternalChannelz.Security(
                new InternalChannelz.OtherSecurity("in-process", null));
        InternalChannelz.SocketOptions options = new InternalChannelz.SocketOptions.Builder().setTcpInfo(tcp).build();
        InternalChannelz.SocketStats socketStats = new InternalChannelz.SocketStats(transport,
                new InetSocketAddress("localhost", 1000), new InetSocketAddress("localhost", 2000), options, security);
        Instrumented<InternalChannelz.SocketStats> socket = new Instrumented<>("socket");
        InternalChannelz.ServerStats stats = new InternalChannelz.ServerStats.Builder().setCallsStarted(9)
                .setCallsSucceeded(7).setCallsFailed(2).setLastCallStartedNanos(42)
                .addListenSockets(Collections.singletonList(socket)).build();
        InternalChannelz.ChannelStats channel = new InternalChannelz.ChannelStats.Builder().setTarget("dns:///orders")
                .setState(ConnectivityState.READY).setSockets(Collections.singletonList(socket)).build();

        assertThat(tcp.reordering).isEqualTo(29);
        assertThat(transport.messagesReceived).isEqualTo(7);
        assertThat(socketStats.security.other.name).isEqualTo("in-process");
        assertThat(stats.callsStarted).isEqualTo(9);
        assertThat(stats.listenSockets).containsExactly(socket);
        assertThat(channel.sockets).containsExactly(socket);
    }

    @Test
    void resolverArgumentsAndTracerFactoryExposeConfiguredRuntimeServices() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            SynchronizationContext context = new SynchronizationContext((thread, failure) -> {
                throw new AssertionError(failure);
            });
            ChannelLogger logger = new ChannelLogger() {
                @Override public void log(ChannelLogLevel level, String message) {}
                @Override public void log(ChannelLogLevel level, String message, Object... args) {}
            };
            Executor executor = Runnable::run;
            ProxyDetector detector = address -> null;
            NameResolver.Args args = NameResolver.Args.newBuilder().setDefaultPort(8443)
                    .setProxyDetector(detector).setSynchronizationContext(context).setScheduledExecutorService(scheduler)
                    .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                        @Override
                        public NameResolver.ConfigOrError parseServiceConfig(java.util.Map<String, ?> rawConfig) {
                            return NameResolver.ConfigOrError.fromConfig(rawConfig);
                        }
                    }).setChannelLogger(logger).setOffloadExecutor(executor).build();
            ClientStreamTracer tracer = new ClientStreamTracer() {};
            ClientStreamTracer.Factory factory = new ClientStreamTracer.Factory() {
                @Override public ClientStreamTracer newClientStreamTracer(
                        CallOptions options, Metadata headers) { return tracer; }
                @Override public ClientStreamTracer newClientStreamTracer(
                        ClientStreamTracer.StreamInfo info, Metadata headers) { return tracer; }
            };

            assertThat(args.getDefaultPort()).isEqualTo(8443);
            assertThat(args.toBuilder().build().getChannelLogger()).isSameAs(logger);
            assertThat(args.getProxyDetector()).isSameAs(detector);
            assertThat(args.getSynchronizationContext()).isSameAs(context);
            assertThat(args.getScheduledExecutorService()).isSameAs(scheduler);
            assertThat(args.getOffloadExecutor()).isSameAs(executor);
            assertThat(args.getServiceConfigParser().parseServiceConfig(Collections.singletonMap("policy", "pick_first"))
                    .getConfig()).isEqualTo(Collections.singletonMap("policy", "pick_first"));
            assertThat(factory.newClientStreamTracer(CallOptions.DEFAULT, new Metadata())).isSameAs(tracer);
            assertThat(factory.newClientStreamTracer(ClientStreamTracer.StreamInfo.newBuilder().build(), new Metadata()))
                    .isSameAs(tracer);
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static final class Instrumented<T> implements InternalInstrumented<T> {
        private final InternalLogId id;

        private Instrumented(String name) {
            id = InternalLogId.allocate(name, "coverage");
        }

        @Override
        public InternalLogId getLogId() {
            return id;
        }

        @Override
        public com.google.common.util.concurrent.ListenableFuture<T> getStats() {
            return null;
        }
    }
}
