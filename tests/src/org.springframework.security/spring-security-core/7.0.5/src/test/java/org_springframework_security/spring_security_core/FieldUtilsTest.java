/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.util.FieldUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FieldUtilsTest {
    @Test
    void readsNestedSecurityContextFields() throws IllegalAccessException {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated("reader", "secret");
        SecurityContextImpl context = new SecurityContextImpl(token);

        assertSame(token, FieldUtils.getProtectedFieldValue("authentication", context));
        assertEquals("reader", FieldUtils.getFieldValue(context, "authentication.principal"));
        assertEquals("authentication", FieldUtils.getField(SecurityContextImpl.class, "authentication").getName());
    }

    @Test
    void updatesAnInheritedAuthenticationField() {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated("reader", "secret");
        token.setDetails("old-session");

        assertEquals("old-session", FieldUtils.getProtectedFieldValue("details", token));
        FieldUtils.setProtectedFieldValue("details", token, "new-session");
        assertEquals("new-session", token.getDetails());
    }
}
