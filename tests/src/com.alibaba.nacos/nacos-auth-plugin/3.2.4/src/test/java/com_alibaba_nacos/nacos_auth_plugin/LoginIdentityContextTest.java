/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LoginIdentityContextTest {
    @Test
    void mergesLoginCredentialsAndReplacesExistingValues() {
        LoginIdentityContext context = new LoginIdentityContext();
        context.setParameter("username", "reader");
        context.setParameter("accessToken", "old-token");
        context.setParameters(Map.of("accessToken", "renewed-token", "tenant", "tenant-a"));

        assertEquals("reader", context.getParameter("username"));
        assertEquals("renewed-token", context.getParameter("accessToken", "no-token"));
        assertEquals("tenant-a", context.getParameter("tenant"));
        assertEquals(Set.of("username", "accessToken", "tenant"), context.getAllKey());
    }

    @Test
    void usesDefaultsForAbsentOrNullCredentialsWithoutReplacingEmptyValues() {
        LoginIdentityContext context = new LoginIdentityContext();
        context.setParameter("accessToken", null);
        context.setParameter("username", "");

        assertNull(context.getParameter("missing"));
        assertEquals("anonymous", context.getParameter("missing", "anonymous"));
        assertEquals("no-token", context.getParameter("accessToken", "no-token"));
        assertEquals("", context.getParameter("username", "anonymous"));
    }
}
