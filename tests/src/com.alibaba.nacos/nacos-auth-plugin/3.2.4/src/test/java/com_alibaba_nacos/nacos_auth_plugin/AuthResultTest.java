/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthResultTest {
    @Test
    void successfulAuthenticationCanCarryIdentityData() {
        Map<String, String> identity = Map.of("username", "reader", "tenant", "tenant-a");
        AuthResult<?> result = AuthResult.successResult(identity);

        assertTrue(result.isSuccess());
        assertSame(identity, result.getData());
        assertEquals(0, result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    void successfulAuthorizationDoesNotRequireResponseData() {
        AuthResult<?> result = AuthResult.successResult();

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        assertEquals(0, result.getErrorCode());
    }

    @Test
    void rejectedAuthorizationPreservesItsCodeAndFormatsTheDiagnostic() {
        AuthResult<?> result = AuthResult.failureResult(403, "Write permission required");

        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
        assertEquals("Write permission required", result.getErrorMessage());
        assertEquals("Code: 403, Message: Write permission required.", result.format());
        assertNull(result.getData());
    }
}
