/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.Int32Value;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's client-streaming RPC subsystem to total a warehouse stock-intake batch.
public class ClientStreamingStockIntakeTest {
    private static final String SERVICE_NAME = "warehouse.StockIntakeService";
    private static final MethodDescriptor<Int32Value, Int32Value> RECEIVE_BATCH =
            MethodDescriptor.<Int32Value, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "ReceiveBatch"))
                    .setRequestMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void totalsUploadedQuantitiesWhenEachBatchIsClosed() throws Exception {
        StockIntakeService intake = new StockIntakeService();
        Server server = ServerBuilder.forPort(0).addService(intake.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            assertEquals(25, uploadBatch(channel, List.of(12, 8, 5)));
            // A subsequent batch has its own accumulator, even on the same connection.
            assertEquals(7, uploadBatch(channel, List.of(3, 4)));
            assertEquals(0, uploadBatch(channel, List.of()));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Stock intake server did not terminate");
        }
    }

    private static int uploadBatch(Channel channel, List<Integer> quantities) throws Exception {
        CompletableFuture<Integer> receipt = new CompletableFuture<>();
        StreamObserver<Int32Value> upload = ClientCalls.asyncClientStreamingCall(
                channel.newCall(RECEIVE_BATCH, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)),
                new StreamObserver<>() {
                    private int total;

                    @Override
                    public void onNext(Int32Value response) {
                        total = response.getValue();
                    }

                    @Override
                    public void onError(Throwable failure) {
                        receipt.completeExceptionally(failure);
                    }

                    @Override
                    public void onCompleted() {
                        receipt.complete(total);
                    }
                });
        for (int quantity : quantities) {
            upload.onNext(Int32Value.of(quantity));
        }
        // Half-close the upload so the server can commit the batch and return its receipt.
        upload.onCompleted();
        return receipt.get(20, TimeUnit.SECONDS);
    }

    private static final class StockIntakeService {
        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(RECEIVE_BATCH, ServerCalls.asyncClientStreamingCall(this::receiveBatch))
                    .build();
        }

        private StreamObserver<Int32Value> receiveBatch(StreamObserver<Int32Value> receipt) {
            return new StreamObserver<>() {
                private int total;

                @Override
                public void onNext(Int32Value quantity) {
                    total += quantity.getValue();
                }

                @Override
                public void onError(Throwable failure) {
                    // An interrupted upload is discarded without issuing a receipt.
                }

                @Override
                public void onCompleted() {
                    receipt.onNext(Int32Value.of(total));
                    receipt.onCompleted();
                }
            };
        }
    }
}
