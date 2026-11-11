/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityJacksonModulesTest {
    @Test
    void discoversModulesAndRoundTripsAnAuthenticatedSecurityContext() {
        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader()))
                .build();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("reader", "secret",
                List.of(new SimpleGrantedAuthority("ROLE_READER")));
        SecurityContext original = new SecurityContextImpl(authentication);

        String json = mapper.writeValueAsString(original);
        SecurityContext restored = mapper.readValue(json, SecurityContext.class);

        assertEquals(original, restored);
        assertEquals("reader", restored.getAuthentication().getName());
        assertTrue(restored.getAuthentication().isAuthenticated());
        assertEquals(List.copyOf(authentication.getAuthorities()), List.copyOf(restored.getAuthentication().getAuthorities()));
    }
}
