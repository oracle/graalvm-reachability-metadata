/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class GrpcUtilAnonymous4Test {
    @Test
    void schedulesRpcDeadlinesAndCompletesFutureCalls() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            ManagedChannel channel = server.newChannel();
            try {
                ConfigQueryRequest request = ConfigQueryRequest.build("absent", "metadata", "tenant");
                Payload reply = RequestGrpc.newFutureStub(channel).withDeadlineAfter(30, TimeUnit.SECONDS)
                        .request(GrpcUtils.convert(request)).get(30, TimeUnit.SECONDS);
                ConfigQueryResponse response = (ConfigQueryResponse) GrpcUtils.parse(reply);
                assertEquals(300, response.getErrorCode());
            } finally {
                channel.shutdownNow();
                assertTrue(channel.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }
}
