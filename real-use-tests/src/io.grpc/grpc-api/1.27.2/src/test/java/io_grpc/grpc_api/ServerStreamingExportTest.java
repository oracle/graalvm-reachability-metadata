/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's server-streaming and client inbound flow-control subsystem for an order export.
public class ServerStreamingExportTest {
    private static final String SERVICE_NAME = "reporting.OrderExportService";
    private static final MethodDescriptor<StringValue, StringValue> EXPORT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Export"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void consumesAnOrderedExportOneRecordAtATime() throws Exception {
        List<String> expectedRows = List.of("order-101,notebook,2", "order-102,pencil,12", "order-103,eraser,1");
        OrderExportService exports = new OrderExportService(Map.of(
                "account-42", expectedRows,
                "account-99", List.of("order-999,folder,8")));
        Server server = ServerBuilder.forPort(0).addService(exports.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            ClientCall<StringValue, StringValue> call = channel.newCall(EXPORT,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS));
            List<String> exportedRows = new ArrayList<>();
            AtomicReference<Status> completion = new AtomicReference<>();
            CountDownLatch finished = new CountDownLatch(1);

            call.start(new ClientCall.Listener<>() {
                @Override
                public void onMessage(StringValue row) {
                    // Persist each row before granting demand for the next record.
                    exportedRows.add(row.getValue());
                    call.request(1);
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    completion.set(status);
                    finished.countDown();
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage(StringValue.of("account-42"));
            call.halfClose();

            assertTrue(finished.await(20, TimeUnit.SECONDS), "Order export did not finish");
            assertNotNull(completion.get());
            assertEquals(Status.Code.OK, completion.get().getCode());
            assertEquals(expectedRows, exportedRows);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Export server did not terminate");
        }
    }

    private static final class OrderExportService {
        private final Map<String, List<String>> rowsByAccount;

        private OrderExportService(Map<String, List<String>> rowsByAccount) {
            this.rowsByAccount = Map.copyOf(rowsByAccount);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(EXPORT, ServerCalls.asyncServerStreamingCall(this::export))
                    .build();
        }

        private void export(StringValue account, StreamObserver<StringValue> response) {
            for (String row : rowsByAccount.getOrDefault(account.getValue(), List.of())) {
                response.onNext(StringValue.of(row));
            }
            response.onCompleted();
        }
    }
}
