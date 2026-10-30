/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.remote.client.RpcConstants;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RpcConstantsTest {
    @Test
    void enumeratesOnlyLabeledClientTlsProperties() {
        Set<String> parameters = RpcConstants.getRpcParams();
        assertEquals(Set.of(
                RpcConstants.RPC_CLIENT_TLS_ENABLE,
                RpcConstants.RPC_CLIENT_TLS_PROVIDER,
                RpcConstants.RPC_CLIENT_MUTUAL_AUTH,
                RpcConstants.RPC_CLIENT_TLS_PROTOCOLS,
                RpcConstants.RPC_CLIENT_TLS_CIPHERS,
                RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH,
                RpcConstants.RPC_CLIENT_TLS_CERT_KEY,
                RpcConstants.RPC_CLIENT_TLS_TRUST_PWD,
                RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH,
                RpcConstants.RPC_CLIENT_TLS_TRUST_ALL), parameters);
        assertFalse(parameters.contains(RpcConstants.NACOS_CLIENT_RPC));
        assertThrows(UnsupportedOperationException.class, () -> parameters.add("custom"));
    }
}
