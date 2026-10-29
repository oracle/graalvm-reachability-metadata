/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Spring_security_coreTest {
    @Test
    void erasesCredentialsWithoutLosingAuthenticatedIdentity() {
        User principal = new User("alex", "secret", AuthorityUtils.createAuthorityList("ROLE_READER"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, "session-credential", principal.getAuthorities());

        authentication.eraseCredentials();

        assertNull(authentication.getCredentials());
        assertNull(principal.getPassword());
        assertSame(principal, authentication.getPrincipal());
        assertEquals("alex", authentication.getName());
        assertIterableEquals(principal.getAuthorities(), authentication.getAuthorities());
        assertTrue(authentication.isAuthenticated());
    }
}
