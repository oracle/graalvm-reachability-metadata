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
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's RPC cancellation lifecycle subsystem to release an abandoned report job.
public class ReportCancellationTest {
    private static final String SERVICE_NAME = "reporting.ReportService";
    private static final MethodDescriptor<StringValue, StringValue> GENERATE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Generate"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void cancelsAnAbandonedReportAndReleasesItsJobSlot() throws Exception {
        ReportService reports = new ReportService();
        Server server = ServerBuilder.forPort(0).addService(reports.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            ClientCall<StringValue, StringValue> call = channel.newCall(GENERATE,
                    CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS));
            BlockingQueue<String> progress = new LinkedBlockingQueue<>();
            CompletableFuture<Status> completion = new CompletableFuture<>();
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onMessage(StringValue update) {
                    progress.add(update.getValue());
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    completion.complete(status);
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage(StringValue.of("monthly-sales"));
            call.halfClose();

            // Wait for the server to allocate the report job, not for an arbitrary delay.
            assertEquals("monthly-sales:started", progress.poll(10, TimeUnit.SECONDS));
            assertEquals(Set.of("monthly-sales"), reports.activeJobs);

            // Closing the report screen abandons the RPC instead of leaving background work running.
            call.cancel("Report screen closed", null);
            assertEquals(Status.Code.CANCELLED, completion.get(10, TimeUnit.SECONDS).getCode());
            assertEquals("monthly-sales", reports.abandonedJobs.poll(10, TimeUnit.SECONDS));
            assertTrue(reports.activeJobs.isEmpty(), "Cancelled report still occupies a job slot");
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Report server did not terminate");
        }
    }

    private static final class ReportService {
        private final Set<String> activeJobs = ConcurrentHashMap.newKeySet();
        private final BlockingQueue<String> abandonedJobs = new LinkedBlockingQueue<>();

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(GENERATE, ServerCalls.asyncServerStreamingCall(this::generate))
                    .build();
        }

        private void generate(StringValue request, StreamObserver<StringValue> response) {
            String reportId = request.getValue();
            ServerCallStreamObserver<StringValue> updates = (ServerCallStreamObserver<StringValue>) response;
            activeJobs.add(reportId);
            updates.setOnCancelHandler(() -> {
                activeJobs.remove(reportId);
                abandonedJobs.add(reportId);
            });
            // The job remains open for later progress updates until the consumer cancels it.
            updates.onNext(StringValue.of(reportId + ":started"));
        }
    }
}
