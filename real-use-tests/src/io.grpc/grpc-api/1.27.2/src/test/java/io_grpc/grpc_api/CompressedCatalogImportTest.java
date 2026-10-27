/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's message compression subsystem for a bulk catalog import and its per-product receipt.
public class CompressedCatalogImportTest {
    private static final String SERVICE_NAME = "catalog.ImportService";
    private static final MethodDescriptor<StringValue, StringValue> IMPORT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Import"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void importsACompressedCatalogAndReadsTheCompressedReceipt() throws Exception {
        List<String> catalogRows = new ArrayList<>();
        List<String> expectedReceipt = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            String sku = "STATIONERY-" + index;
            String description = "Refillable notebook with recycled paper and a durable blue cover " + index;
            catalogRows.add(sku + "," + description);
            expectedReceipt.add(sku + ",IMPORTED," + description);
        }
        CatalogImportService catalog = new CatalogImportService();
        Server server = ServerBuilder.forPort(0).addService(catalog.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            // Both the uploaded catalog and the detailed receipt use the built-in gzip codec.
            StringValue receipt = ClientCalls.blockingUnaryCall(channel, IMPORT,
                    CallOptions.DEFAULT.withCompression("gzip").withDeadlineAfter(10, TimeUnit.SECONDS),
                    StringValue.of(String.join("\n", catalogRows)));

            assertEquals(String.join("\n", expectedReceipt), receipt.getValue());
            assertEquals(200, catalog.products.size());
            for (String row : catalogRows) {
                String[] fields = row.split(",", 2);
                assertEquals(fields[1], catalog.products.get(fields[0]));
            }
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Catalog server did not terminate");
        }
    }

    private static final class CatalogImportService {
        private final Map<String, String> products = new ConcurrentHashMap<>();

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(IMPORT, ServerCalls.asyncUnaryCall(this::importCatalog))
                    .build();
        }

        private void importCatalog(StringValue request, StreamObserver<StringValue> response) {
            ServerCallStreamObserver<StringValue> receipt = (ServerCallStreamObserver<StringValue>) response;
            receipt.setCompression("gzip");
            List<String> results = new ArrayList<>();
            for (String row : request.getValue().lines().toList()) {
                String[] fields = row.split(",", 2);
                products.put(fields[0], fields[1]);
                results.add(fields[0] + ",IMPORTED," + fields[1]);
            }
            receipt.onNext(StringValue.of(String.join("\n", results)));
            receipt.onCompleted();
        }
    }
}
