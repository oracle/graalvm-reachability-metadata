/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client_basic;

import com.alibaba.nacos.client.auth.ram.identify.Credentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class Nacos_client_basicTest {
    @Test
    void credentialsRequireBothAccessAndSecretKeys() {
        Credentials credentials = new Credentials();
        assertFalse(credentials.valid());

        credentials.setAccessKey("client-access-key");
        assertFalse(credentials.valid());

        credentials.setSecretKey("client-secret-key");
        credentials.setTenantId("client-tenant");
        assertTrue(credentials.valid());
        assertEquals("client-access-key", credentials.getAccessKey());
        assertEquals("client-secret-key", credentials.getSecretKey());
        assertEquals("client-tenant", credentials.getTenantId());
    }
}
