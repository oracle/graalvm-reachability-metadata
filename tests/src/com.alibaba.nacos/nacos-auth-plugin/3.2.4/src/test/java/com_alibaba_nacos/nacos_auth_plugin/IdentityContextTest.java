/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IdentityContextTest {
    @Test
    void retrievesIdentityParametersWithTypedDefaults() {
        IdentityContext context = new IdentityContext();
        context.setParameter("username", "reader");
        context.setParameter("attempts", 2);

        assertEquals("reader", context.getParameter("username"));
        assertEquals("reader", context.getParameter("username", "anonymous"));
        assertEquals(2, context.getParameter("attempts", 0));
        assertEquals("anonymous", context.getParameter("missing", "anonymous"));
        assertEquals(0, context.getParameter("username", 0));
        assertNull(context.getParameter("missing"));
        assertThrows(IllegalArgumentException.class, () -> context.getParameter("username", null));
    }

    @Test
    void tracksOnlyRequestDerivedIdentityNamesInAnUnmodifiableView() {
        IdentityContext context = new IdentityContext();
        context.setParameter("authenticated", true);
        context.setRequestIdentityParameter("username", "reader");
        Set<String> names = context.getRequestIdentityNames();
        context.setRequestIdentityParameter("accessToken", "request-token");
        context.setRequestIdentityParameter("username", "writer");

        assertEquals(Set.of("username", "accessToken"), names);
        assertEquals("writer", context.getParameter("username"));
        assertEquals("request-token", context.getParameter("accessToken"));
        assertEquals(true, context.getParameter("authenticated", false));
        assertThrows(UnsupportedOperationException.class, () -> names.add("injected"));
    }
}
