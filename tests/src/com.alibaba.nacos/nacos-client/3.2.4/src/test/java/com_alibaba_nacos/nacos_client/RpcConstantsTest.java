/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.common.remote.client.RpcConstants;
import org.junit.jupiter.api.Test;

public class RpcConstantsTest {
    @Test
    void exposesTlsParametersForClientConfiguration() {
        assertTrue(RpcConstants.getRpcParams().contains(RpcConstants.RPC_CLIENT_TLS_ENABLE));
        assertTrue(RpcConstants.getRpcParams().contains(RpcConstants.RPC_CLIENT_TLS_PROTOCOLS));
        assertTrue(RpcConstants.getRpcParams().contains(RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH));
    }
}
