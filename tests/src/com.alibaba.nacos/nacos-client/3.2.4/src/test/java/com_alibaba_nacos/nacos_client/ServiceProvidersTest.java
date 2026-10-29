/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannel;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ServiceProvidersTest {
    @Test
    void discoversTheTransportProviderForChannelBuilders() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            ManagedChannel endpoint = server.newChannel();
            ManagedChannel channel = ManagedChannelBuilder.forTarget(endpoint.authority()).usePlaintext().build();
            try {
                assertEquals("ConfigQueryResponse", RequestGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS).request(GrpcUtils.convert(
                                ConfigQueryRequest.build("absent", "metadata", "tenant"))).getMetadata().getType());
            } finally {
                channel.shutdownNow();
                endpoint.shutdownNow();
                assertTrue(channel.awaitTermination(30, TimeUnit.SECONDS));
                assertTrue(endpoint.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }
}
