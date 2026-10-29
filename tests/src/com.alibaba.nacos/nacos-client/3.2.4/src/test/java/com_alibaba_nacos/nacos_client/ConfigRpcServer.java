/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannel;
import com.alibaba.nacos.shaded.io.grpc.Server;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import com.alibaba.nacos.shaded.io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/// A loopback implementation of the config wire protocol, without an external registry process.
final class ConfigRpcServer implements AutoCloseable {
    private final Server server;
    private final Map<String, String> configurations = new ConcurrentHashMap<>();
    private final Queue<Request> streamRequests = new ConcurrentLinkedQueue<>();
    private final Queue<Throwable> streamErrors = new ConcurrentLinkedQueue<>();

    ConfigRpcServer() throws Exception {
        server = NettyServerBuilder.forPort(0).addService(new Requests()).addService(new Streams()).build().start();
    }

    Properties properties() {
        Properties properties = TestSupport.clientProperties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + (server.getPort() - 1000));
        return properties;
    }

    ManagedChannel newChannel() {
        return NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
    }

    Payload query(ManagedChannel channel, String dataId) {
        ConfigQueryRequest request = ConfigQueryRequest.build(dataId, "metadata", "tenant");
        return RequestGrpc.newBlockingStub(channel).withDeadlineAfter(30, TimeUnit.SECONDS).request(GrpcUtils.convert(request));
    }

    String content(String dataId) {
        return configurations.get(dataId);
    }

    private Response respond(Request request) {
        if (request instanceof ServerCheckRequest) {
            return new ServerCheckResponse("metadata-test-connection", false);
        }
        if (request instanceof HealthCheckRequest) {
            return new HealthCheckResponse();
        }
        if (request instanceof ConfigPublishRequest publish) {
            configurations.put(publish.getDataId(), publish.getContent());
            return ConfigPublishResponse.buildSuccessResponse();
        }
        if (request instanceof ConfigRemoveRequest remove) {
            configurations.remove(remove.getDataId());
            return ConfigRemoveResponse.buildSuccessResponse();
        }
        if (request instanceof ConfigBatchListenRequest) {
            return new ConfigChangeBatchListenResponse();
        }
        if (request instanceof ConfigQueryRequest query) {
            String content = configurations.get(query.getDataId());
            return content == null ? ConfigQueryResponse.buildFailResponse(300, "config not found")
                    : ConfigQueryResponse.buildSuccessResponse(content);
        }
        throw new IllegalArgumentException("Unexpected request: " + request.getClass().getName());
    }

    private final class Requests extends RequestGrpc.RequestImplBase {
        @Override
        public void request(Payload payload, StreamObserver<Payload> observer) {
            Request request = (Request) GrpcUtils.parse(payload);
            Response response = respond(request);
            response.setRequestId(request.getRequestId());
            observer.onNext(GrpcUtils.convert(response));
            observer.onCompleted();
        }
    }

    private final class Streams extends BiRequestStreamGrpc.BiRequestStreamImplBase {
        @Override
        public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> observer) {
            return new StreamObserver<Payload>() {
                @Override
                public void onNext(Payload payload) {
                    streamRequests.add((Request) GrpcUtils.parse(payload));
                }

                @Override
                public void onError(Throwable error) {
                    streamErrors.add(error);
                }

                @Override
                public void onCompleted() {
                    observer.onCompleted();
                }
            };
        }
    }

    @Override
    public void close() throws Exception {
        server.shutdownNow();
        if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Config RPC server did not terminate");
        }
    }
}
