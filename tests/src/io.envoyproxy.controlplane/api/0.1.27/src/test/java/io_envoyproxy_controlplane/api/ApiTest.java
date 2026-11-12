/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.Any;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc.AggregatedDiscoveryServiceImplBase;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

public class ApiTest {
    @Test
    void streamsRequestedRoutesThroughAggregatedDiscoveryService() throws Exception {
        AggregatedDiscoveryServiceImplBase service = new AggregatedDiscoveryServiceImplBase() {
            @Override
            public StreamObserver<DiscoveryRequest> streamAggregatedResources(
                    StreamObserver<DiscoveryResponse> responses) {
                return new StreamObserver<>() {
                    @Override
                    public void onNext(DiscoveryRequest request) {
                        RouteConfiguration routes = RouteConfiguration.newBuilder()
                                .setName(request.getResourceNames(0)).build();
                        responses.onNext(DiscoveryResponse.newBuilder()
                                .setVersionInfo("revision-stream")
                                .setTypeUrl(request.getTypeUrl())
                                .setNonce(request.getResourceNames(0))
                                .addResources(Any.pack(routes))
                                .build());
                    }

                    @Override
                    public void onError(Throwable error) {
                        responses.onError(error);
                    }

                    @Override
                    public void onCompleted() {
                        responses.onCompleted();
                    }
                };
            }
        };
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start();
        try {
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            try {
                List<DiscoveryResponse> received = new ArrayList<>();
                CompletableFuture<List<DiscoveryResponse>> completed = new CompletableFuture<>();
                StreamObserver<DiscoveryRequest> requests = AggregatedDiscoveryServiceGrpc.newStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS)
                        .streamAggregatedResources(new StreamObserver<>() {
                            @Override
                            public void onNext(DiscoveryResponse response) {
                                received.add(response);
                            }

                            @Override
                            public void onError(Throwable error) {
                                completed.completeExceptionally(error);
                            }

                            @Override
                            public void onCompleted() {
                                completed.complete(List.copyOf(received));
                            }
                        });
                String typeUrl = "type.googleapis.com/envoy.config.route.v3.RouteConfiguration";
                requests.onNext(DiscoveryRequest.newBuilder().setTypeUrl(typeUrl).addResourceNames("routes-west").build());
                requests.onNext(DiscoveryRequest.newBuilder().setTypeUrl(typeUrl).addResourceNames("routes-east").build());
                requests.onCompleted();
                List<DiscoveryResponse> responses = completed.get(30, TimeUnit.SECONDS);
                assertEquals(2, responses.size());
                assertEquals("routes-west", responses.get(0).getResources(0).unpack(RouteConfiguration.class).getName());
                assertEquals("routes-east", responses.get(1).getResources(0).unpack(RouteConfiguration.class).getName());
                assertEquals("routes-east", responses.get(1).getNonce());
                assertEquals(typeUrl, responses.get(1).getTypeUrl());
                assertEquals("revision-stream", responses.get(1).getVersionInfo());
            } finally {
                channel.shutdownNow();
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS));
            }
        } finally {
            server.shutdownNow();
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
