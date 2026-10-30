/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.List;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.jackson2.CoreJackson2Module;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityJackson2ModulesTest {
    @Test
    void discoversModulesAndRoundTripsAuthenticatedSecurityContext() throws Exception {
        List<Module> modules = SecurityJackson2Modules.getModules(getClass().getClassLoader());
        assertTrue(modules.stream().anyMatch(module -> module instanceof CoreJackson2Module));
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(modules);
        User principal = new User("alex", "secret", AuthorityUtils.createAuthorityList("ROLE_READER"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, "session-credential", principal.getAuthorities());
        authentication.setDetails("console-client");
        SecurityContextImpl context = new SecurityContextImpl(authentication);

        String json = mapper.writeValueAsString(context);
        SecurityContextImpl restored = mapper.readValue(json, SecurityContextImpl.class);

        Authentication restoredAuthentication = restored.getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, restoredAuthentication);
        assertTrue(restoredAuthentication.isAuthenticated());
        assertEquals("session-credential", restoredAuthentication.getCredentials());
        assertEquals("console-client", restoredAuthentication.getDetails());
        User restoredPrincipal = assertInstanceOf(User.class, restoredAuthentication.getPrincipal());
        assertEquals(principal.getUsername(), restoredPrincipal.getUsername());
        assertEquals(principal.getPassword(), restoredPrincipal.getPassword());
        assertEquals(principal.getAuthorities(), restoredPrincipal.getAuthorities());
        assertEquals(principal.isEnabled(), restoredPrincipal.isEnabled());
        assertEquals(principal.isAccountNonLocked(), restoredPrincipal.isAccountNonLocked());
    }
}
