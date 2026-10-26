/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.Int32Value;
import io.grpc.CallOptions;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's bidirectional streaming subsystem for an interactive stock-reservation session.
public class BidirectionalStockReservationTest {
    private static final String SERVICE_NAME = "warehouse.ReservationService";
    private static final MethodDescriptor<Int32Value, Int32Value> RESERVE =
            MethodDescriptor.<Int32Value, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Reserve"))
                    .setRequestMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void receivesReservationAcknowledgmentsBeforeClosingTheRequestStream() throws Exception {
        ReservationService reservations = new ReservationService(20);
        Server server = ServerBuilder.forPort(0).addService(reservations.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            ReservationReceipts receipts = new ReservationReceipts();
            StreamObserver<Int32Value> requests = ClientCalls.asyncBidiStreamingCall(
                    channel.newCall(RESERVE, CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS)),
                    receipts);

            // A picker waits for confirmation before reserving the next group of items.
            requests.onNext(Int32Value.of(3));
            assertEquals(Integer.valueOf(17), receipts.remainingStock.poll(10, TimeUnit.SECONDS));
            requests.onNext(Int32Value.of(5));
            assertEquals(Integer.valueOf(12), receipts.remainingStock.poll(10, TimeUnit.SECONDS));
            requests.onNext(Int32Value.of(2));
            assertEquals(Integer.valueOf(10), receipts.remainingStock.poll(10, TimeUnit.SECONDS));

            // The server acknowledged all picks while the upload side was still open.
            requests.onCompleted();
            receipts.completed.get(10, TimeUnit.SECONDS);
            assertEquals(List.of(), List.copyOf(receipts.remainingStock));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Reservation server did not terminate");
        }
    }

    private static final class ReservationReceipts implements StreamObserver<Int32Value> {
        private final BlockingQueue<Integer> remainingStock = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> completed = new CompletableFuture<>();

        @Override
        public void onNext(Int32Value receipt) {
            remainingStock.add(receipt.getValue());
        }

        @Override
        public void onError(Throwable failure) {
            completed.completeExceptionally(failure);
        }

        @Override
        public void onCompleted() {
            completed.complete(null);
        }
    }

    private static final class ReservationService {
        private final int initialStock;

        private ReservationService(int initialStock) {
            this.initialStock = initialStock;
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(RESERVE, ServerCalls.asyncBidiStreamingCall(this::reserve))
                    .build();
        }

        private StreamObserver<Int32Value> reserve(StreamObserver<Int32Value> receipts) {
            return new StreamObserver<>() {
                private int available = initialStock;

                @Override
                public void onNext(Int32Value quantity) {
                    available -= quantity.getValue();
                    receipts.onNext(Int32Value.of(available));
                }

                @Override
                public void onError(Throwable failure) {
                    // An interrupted picking session sends no further acknowledgments.
                }

                @Override
                public void onCompleted() {
                    receipts.onCompleted();
                }
            };
        }
    }
}
