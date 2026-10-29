/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ReflectionLongAdderCounterTest {
    @Test
    void recordsCompletedTransportCalls() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            ManagedChannel channel = server.newChannel();
            try {
                ConfigQueryResponse response = (ConfigQueryResponse) GrpcUtils.parse(server.query(channel, "absent"));
                assertFalse(response.isSuccess());
                assertTrue(response.getMessage().contains("not found"));
            } finally {
                channel.shutdownNow();
                assertTrue(channel.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }
}
