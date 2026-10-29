/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.util.FieldUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldUtilsTest {
    @Test
    void readsNestedPrincipalFields() throws IllegalAccessException {
        User user = new User("alex", "secret", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, "secret");

        assertEquals(user.getUsername(), FieldUtils.getFieldValue(authentication, "principal.username"));
        assertEquals(user.getPassword(), FieldUtils.getFieldValue(authentication, "principal.password"));
    }

    @Test
    void readsAndUpdatesAnInheritedAuthenticationField() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("alex", "secret");
        authentication.setDetails("web-client");

        assertEquals("web-client", FieldUtils.getProtectedFieldValue("details", authentication));
        FieldUtils.setProtectedFieldValue("details", authentication, "console-client");

        assertEquals("console-client", authentication.getDetails());
        assertEquals("console-client", FieldUtils.getProtectedFieldValue("details", authentication));
    }
}
