/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GrpcConstantsTest {
    @Test
    void enumeratesOnlyLabeledGrpcClientProperties() {
        Set<String> parameters = GrpcConstants.getRpcParams();
        assertEquals(Set.of(
                GrpcConstants.GRPC_NAME,
                GrpcConstants.GRPC_THREADPOOL_KEEPALIVETIME,
                GrpcConstants.GRPC_THREADPOOL_CORE_SIZE,
                GrpcConstants.GRPC_RETRY_TIMES,
                GrpcConstants.GRPC_TIMEOUT_MILLS,
                GrpcConstants.GRPC_CONNECT_KEEP_ALIVE_TIME,
                GrpcConstants.GRPC_THREADPOOL_MAX_SIZE,
                GrpcConstants.GRPC_SERVER_CHECK_TIMEOUT,
                GrpcConstants.GRPC_QUEUESIZE,
                GrpcConstants.GRPC_HEALTHCHECK_RETRY_TIMES,
                GrpcConstants.GRPC_HEALTHCHECK_TIMEOUT,
                GrpcConstants.GRPC_MAX_INBOUND_MESSAGE_SIZE,
                GrpcConstants.GRPC_CHANNEL_KEEP_ALIVE_TIME,
                GrpcConstants.GRPC_CHANNEL_KEEP_ALIVE_TIMEOUT,
                GrpcConstants.GRPC_CHANNEL_CAPABILITY_NEGOTIATION_TIMEOUT,
                GrpcConstants.GRPC_THREADPOOL_ALLOW_CORE_THREAD_TIMEOUT), parameters);
        assertFalse(parameters.contains(GrpcConstants.NACOS_SERVER_GRPC_PORT_OFFSET_KEY));
        assertThrows(UnsupportedOperationException.class, () -> parameters.clear());
    }
}
