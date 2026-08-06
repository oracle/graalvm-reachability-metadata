/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ClientStreamTracer;
import io.grpc.Codec;
import io.grpc.ConnectivityState;
import io.grpc.DecompressorRegistry;
import io.grpc.EquivalentAddressGroup;
import io.grpc.HttpConnectProxiedSocketAddress;
import io.grpc.InternalChannelz;
import io.grpc.InternalLogId;
import io.grpc.InternalWithLogId;
import io.grpc.LoadBalancer;
import io.grpc.ManagedChannelProvider;
import io.grpc.Metadata;
import io.grpc.SynchronizationContext;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class GrpcApiInfrastructureTest {
    @Test
    void streamAndSubchannelArgumentsPreserveTransportConfiguration() throws Exception {
        Attributes transport = Attributes.newBuilder().set(Attributes.Key.create("transport"), "local").build();
        CallOptions options = CallOptions.DEFAULT.withOption(CallOptions.Key.createWithDefault("tenant", "public"), "private");
        ClientStreamTracer.StreamInfo streamInfo = ClientStreamTracer.StreamInfo.newBuilder()
                .setTransportAttrs(transport).setCallOptions(options).build();
        assertThat(streamInfo.getTransportAttrs()).isEqualTo(transport);
        assertThat(streamInfo.toBuilder().build().getCallOptions()).isEqualTo(options);
        assertThat(streamInfo.toString()).contains("transportAttrs");

        EquivalentAddressGroup endpoint = new EquivalentAddressGroup(new InetSocketAddress("localhost", 8443));
        LoadBalancer.CreateSubchannelArgs.Key<String> zone =
                LoadBalancer.CreateSubchannelArgs.Key.createWithDefault("zone", "default");
        LoadBalancer.CreateSubchannelArgs args = LoadBalancer.CreateSubchannelArgs.newBuilder()
                .setAddresses(Collections.singletonList(endpoint)).setAttributes(transport)
                .addOption(zone, "blue").build();
        assertThat(args.getAddresses()).containsExactly(endpoint);
        assertThat(args.getAttributes()).isEqualTo(transport);
        assertThat(args.getOption(zone)).isEqualTo("blue");
        assertThat(args.toBuilder().build().toString()).contains("blue");
        assertThat(zone.getDefault()).isEqualTo("default");
        assertThat(zone.toString()).isEqualTo("zone");

        assertThat(Codec.Identity.NONE.decompress(new ByteArrayInputStream(new byte[] {3})).read())
                .isEqualTo(3);
        assertThat(DecompressorRegistry.getDefaultInstance().getKnownMessageEncodings())
                .contains("identity", "gzip");
    }

    @Test
    void publicSchedulingMetadataAndProviderFlowsExerciseDeferredOperations() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        SynchronizationContext context = new SynchronizationContext((thread, failure) -> {
            throw new AssertionError(failure);
        });
        AtomicReference<String> scheduledDescription = new AtomicReference<>();
        ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1) {
            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                scheduledDescription.set(String.valueOf(command));
                return super.schedule(command, delay, unit);
            }
        };
        try {
            context.schedule(() -> ran.set(true), 0, TimeUnit.MILLISECONDS, scheduler);
            scheduler.shutdown();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(ran).isTrue();
            assertThat(scheduledDescription.get()).contains("scheduled in SynchronizationContext");
        } finally {
            scheduler.shutdownNow();
        }

        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("attempt", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "value");
        Iterator<String> values = metadata.getAll(key).iterator();
        assertThat(values.next()).isEqualTo("value");
        assertThatThrownBy(values::remove)
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(ManagedChannelProvider.provider()).isInstanceOf(ManagedChannelProvider.class);
    }

    @Test
    void proxyAddressBuilderExposesConfiguredRouteAndIdentity() {
        InetSocketAddress proxy = new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 8080);
        InetSocketAddress target = new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 443);
        HttpConnectProxiedSocketAddress route = HttpConnectProxiedSocketAddress.newBuilder()
                .setProxyAddress(proxy).setTargetAddress(target).setUsername("alice").setPassword("secret").build();
        HttpConnectProxiedSocketAddress equivalent = HttpConnectProxiedSocketAddress.newBuilder()
                .setProxyAddress(proxy).setTargetAddress(target).setUsername("alice").setPassword("secret").build();
        assertThat(route.getProxyAddress()).isEqualTo(proxy);
        assertThat(route.getTargetAddress()).isEqualTo(target);
        assertThat(route.getUsername()).isEqualTo("alice");
        assertThat(route.getPassword()).isEqualTo("secret");
        assertThat(route).isEqualTo(equivalent);
        assertThat(route.hashCode()).isEqualTo(equivalent.hashCode());
        assertThat(route.toString()).contains("8080", "443");
    }

    @Test
    void channelzBuildersRetainOperationalStatistics() {
        InternalWithLogId channel = () -> InternalLogId.allocate("channel", "one");
        InternalChannelz.ChannelTrace.Event event = new InternalChannelz.ChannelTrace.Event.Builder()
                .setDescription("connected").setTimestampNanos(12)
                .setSeverity(InternalChannelz.ChannelTrace.Event.Severity.CT_INFO)
                .setChannelRef(channel).build();
        InternalChannelz.ChannelTrace trace = new InternalChannelz.ChannelTrace.Builder()
                .setCreationTimeNanos(10).setNumEventsLogged(1).setEvents(Collections.singletonList(event)).build();
        InternalChannelz.ChannelStats stats = new InternalChannelz.ChannelStats.Builder()
                .setTarget("dns:///service").setState(ConnectivityState.READY).setChannelTrace(trace)
                .setCallsStarted(5).setCallsSucceeded(4).setCallsFailed(1).setLastCallStartedNanos(11)
                .setSubchannels(Collections.singletonList(channel)).build();
        assertThat(event).isEqualTo(new InternalChannelz.ChannelTrace.Event.Builder()
                .setDescription("connected").setTimestampNanos(12)
                .setSeverity(InternalChannelz.ChannelTrace.Event.Severity.CT_INFO).setChannelRef(channel).build());
        assertThat(event.toString()).contains("connected");
        assertThat(trace.events).containsExactly(event);
        assertThat(stats.target).isEqualTo("dns:///service");
        assertThat(stats.callsStarted).isEqualTo(5);
        assertThat(stats.callsSucceeded).isEqualTo(4);
        assertThat(stats.callsFailed).isEqualTo(1);
    }

    @Test
    void socketOptionBuildersProduceInspectableNetworkConfiguration() {
        InternalChannelz.TcpInfo tcpInfo = new InternalChannelz.TcpInfo.Builder()
                .setState(1).setCaState(2).setRtt(3).setRttvar(4).setSndCwnd(5).setUnacked(6).build();
        InternalChannelz.SocketOptions options = new InternalChannelz.SocketOptions.Builder()
                .setSocketOptionTimeoutMillis(500).setSocketOptionLingerSeconds(3).setTcpInfo(tcpInfo)
                .addOption("keepAlive", true).addOption("retries", 2).addOption("mode", "active").build();
        assertThat(options.soTimeoutMillis).isEqualTo(500);
        assertThat(options.lingerSeconds).isEqualTo(3);
        assertThat(options.tcpInfo).isSameAs(tcpInfo);
        assertThat(options.others).containsEntry("keepAlive", "true").containsEntry("retries", "2")
                .containsEntry("mode", "active");
    }
}
