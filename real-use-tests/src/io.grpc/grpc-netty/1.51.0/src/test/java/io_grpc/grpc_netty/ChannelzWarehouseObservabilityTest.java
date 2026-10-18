/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.channelz.v1.Channel;
import io.grpc.channelz.v1.ChannelConnectivityState;
import io.grpc.channelz.v1.ChannelzGrpc;
import io.grpc.channelz.v1.GetServerSocketsRequest;
import io.grpc.channelz.v1.GetServerSocketsResponse;
import io.grpc.channelz.v1.GetServersRequest;
import io.grpc.channelz.v1.GetSocketRequest;
import io.grpc.channelz.v1.GetSubchannelRequest;
import io.grpc.channelz.v1.GetTopChannelsRequest;
import io.grpc.channelz.v1.Socket;
import io.grpc.channelz.v1.SocketData;
import io.grpc.channelz.v1.SocketOption;
import io.grpc.channelz.v1.Subchannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ChannelzService;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Observes grpc-netty transports through Channelz, where `NettyServerBuilder` and
 * `NettyChannelBuilder` connections report their Netty socket options, addresses and HTTP/2
 * stream and message counters to a `ChannelzService` hosted on a separate admin server.
 *
 * A warehouse service runs on its own Netty server with keep-alive and no-delay child options;
 * a stock client checks inventory levels, including one unknown item, and an operations console
 * then walks the Channelz tree from the server to its socket and from the client channel
 * through its subchannel to its socket, the way a debugging dashboard would.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChannelzWarehouseObservabilityTest {

    private static final String SERVICE = "warehouse.Inventory";

    private static final MethodDescriptor<StringValue, Int32Value> STOCK_LEVEL =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "StockLevel"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    private static final Map<String, Integer> SHELVES = Map.of("pallet-jack", 4, "shrink-wrap", 120);

    private Server warehouseServer;

    private Server adminServer;

    private ManagedChannel stockChannel;

    private ManagedChannel consoleChannel;

    private ChannelzGrpc.ChannelzBlockingStub console;

    @BeforeAll
    void startServersAndChannels() throws Exception {
        warehouseServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .withChildOption(ChannelOption.SO_KEEPALIVE, true)
                .withChildOption(ChannelOption.TCP_NODELAY, true)
                .addService(inventoryService())
                .build()
                .start();
        adminServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ChannelzService.newInstance(100))
                .build()
                .start();
        stockChannel = NettyChannelBuilder.forAddress("127.0.0.1", warehouseServer.getPort())
                .usePlaintext()
                .withOption(ChannelOption.SO_KEEPALIVE, true)
                .build();
        consoleChannel = NettyChannelBuilder.forAddress("127.0.0.1", adminServer.getPort())
                .usePlaintext()
                .build();
        console = ChannelzGrpc.newBlockingStub(consoleChannel).withDeadlineAfter(30, TimeUnit.SECONDS);
    }

    @AfterAll
    void stopServersAndChannels() throws Exception {
        stockChannel.shutdown();
        consoleChannel.shutdown();
        warehouseServer.shutdown();
        adminServer.shutdown();
        assertThat(stockChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(consoleChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(warehouseServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(adminServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void reportsTheServerAndClientSocketsOfTheWarehouseConnection() {
        assertThat(stockLevel("pallet-jack")).isEqualTo(4);
        assertThat(stockLevel("shrink-wrap")).isEqualTo(120);
        assertThatThrownBy(() -> stockLevel("forklift"))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).isEqualTo("no shelf for forklift");
                });

        io.grpc.channelz.v1.Server server = await("the warehouse server reports its three calls",
                this::findWarehouseServer, found -> found.getData().getCallsStarted() == 3
                        && found.getData().getCallsSucceeded() == 2
                        && found.getData().getCallsFailed() == 1);
        assertThat(server.getListenSocketList()).hasSize(1);
        SocketData listenSocket = console.getSocket(GetSocketRequest.newBuilder()
                .setSocketId(server.getListenSocket(0).getSocketId()).build()).getSocket().getData();
        assertThat(listenSocket.getStreamsStarted()).isZero();

        GetServerSocketsResponse serverSockets = console.getServerSockets(GetServerSocketsRequest.newBuilder()
                .setServerId(server.getRef().getServerId()).build());
        assertThat(serverSockets.getSocketRefList()).hasSize(1);
        assertThat(serverSockets.getEnd()).isTrue();
        long serverSocketId = serverSockets.getSocketRef(0).getSocketId();
        Socket serverSocket = await("the server socket counts the warehouse streams",
                () -> console.getSocket(GetSocketRequest.newBuilder().setSocketId(serverSocketId).build())
                        .getSocket(),
                socket -> socket.getData().getStreamsStarted() == 3
                        && socket.getData().getStreamsSucceeded() + socket.getData().getStreamsFailed() == 3);
        assertThat(serverSocket.getLocal().getTcpipAddress().getPort()).isEqualTo(warehouseServer.getPort());
        assertThat(serverSocket.getData().getMessagesReceived()).isEqualTo(3);
        assertThat(serverSocket.getData().getMessagesSent()).isEqualTo(2);
        assertThat(serverSocket.getData().getLastMessageReceivedTimestamp().getSeconds()).isPositive();
        assertThat(serverSocket.getData().getLocalFlowControlWindow().getValue()).isPositive();
        assertThat(serverSocket.getData().getRemoteFlowControlWindow().getValue()).isPositive();
        Map<String, String> serverOptions = optionsOf(serverSocket.getData());
        assertThat(serverOptions).containsEntry("SO_KEEPALIVE", "true").containsEntry("TCP_NODELAY", "true");

        Channel channel = await("the stock channel reports its three calls", this::findStockChannel,
                found -> found.getData().getCallsStarted() == 3
                        && found.getData().getCallsSucceeded() == 2
                        && found.getData().getCallsFailed() == 1);
        assertThat(channel.getData().getTarget()).contains(String.valueOf(warehouseServer.getPort()));
        assertThat(channel.getSubchannelRefList()).hasSize(1);
        Subchannel subchannel = console.getSubchannel(GetSubchannelRequest.newBuilder()
                .setSubchannelId(channel.getSubchannelRef(0).getSubchannelId()).build()).getSubchannel();
        assertThat(subchannel.getData().getState().getState())
                .isEqualTo(ChannelConnectivityState.State.READY);
        assertThat(subchannel.getSocketRefList()).hasSize(1);
        Socket clientSocket = console.getSocket(GetSocketRequest.newBuilder()
                .setSocketId(subchannel.getSocketRef(0).getSocketId()).build()).getSocket();
        assertThat(clientSocket.getRemote().getTcpipAddress().getPort()).isEqualTo(warehouseServer.getPort());
        assertThat(clientSocket.getLocal().getTcpipAddress().getPort())
                .isEqualTo(serverSocket.getRemote().getTcpipAddress().getPort());
        assertThat(clientSocket.getData().getStreamsStarted()).isEqualTo(3);
        assertThat(clientSocket.getData().getMessagesSent()).isEqualTo(3);
        assertThat(clientSocket.getData().getMessagesReceived()).isEqualTo(2);
        assertThat(optionsOf(clientSocket.getData())).containsEntry("SO_KEEPALIVE", "true");
    }

    private int stockLevel(String item) {
        return ClientCalls.blockingUnaryCall(stockChannel, STOCK_LEVEL,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS),
                StringValue.of(item)).getValue();
    }

    private io.grpc.channelz.v1.Server findWarehouseServer() {
        return console.getServers(GetServersRequest.newBuilder().setMaxResults(100).build())
                .getServerList().stream()
                .filter(candidate -> candidate.getListenSocketList().stream().anyMatch(ref -> console
                        .getSocket(GetSocketRequest.newBuilder().setSocketId(ref.getSocketId()).build())
                        .getSocket().getLocal().getTcpipAddress().getPort() == warehouseServer.getPort()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("warehouse server is not registered in Channelz"));
    }

    private Channel findStockChannel() {
        String port = ":" + warehouseServer.getPort();
        return console.getTopChannels(GetTopChannelsRequest.newBuilder().setMaxResults(100).build())
                .getChannelList().stream()
                .filter(candidate -> candidate.getData().getTarget().endsWith(port))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stock channel is not registered in Channelz"));
    }

    private static Map<String, String> optionsOf(SocketData data) {
        return data.getOptionList().stream()
                .filter(option -> !option.getValue().isEmpty())
                .collect(Collectors.toMap(SocketOption::getName, SocketOption::getValue, (a, b) -> a));
    }

    private static ServerServiceDefinition inventoryService() {
        return ServerServiceDefinition.builder(SERVICE)
                .addMethod(STOCK_LEVEL, ServerCalls.asyncUnaryCall((request, responses) -> {
                    Integer level = SHELVES.get(request.getValue());
                    if (level == null) {
                        responses.onError(Status.NOT_FOUND
                                .withDescription("no shelf for " + request.getValue())
                                .asRuntimeException());
                        return;
                    }
                    responses.onNext(Int32Value.of(level));
                    responses.onCompleted();
                }))
                .build();
    }

    /// Polls Channelz until the snapshot satisfies the condition; counters settle on the event loop.
    private static <T> T await(String description, Supplier<T> snapshot, Predicate<T> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        T latest = snapshot.get();
        while (!condition.test(latest)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting until " + description + ": " + latest);
            }
            Thread.onSpinWait();
            latest = snapshot.get();
        }
        return latest;
    }
}
