/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.access.method.MapBasedMethodSecurityMetadataSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.util.MethodInvocationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapBasedMethodSecurityMetadataSourceTest {
    @Test
    void registersWildcardRulesAndKeepsMoreSpecificMethodRule() {
        MapBasedMethodSecurityMetadataSource source = new MapBasedMethodSecurityMetadataSource();
        List<ConfigAttribute> reader = SecurityConfig.createList("ROLE_READER");
        List<ConfigAttribute> editor = SecurityConfig.createList("ROLE_EDITOR");
        List<ConfigAttribute> administrator = SecurityConfig.createList("ROLE_ADMIN");
        source.addSecureMethod(InMemoryUserDetailsManager.class, "load*", reader);
        source.addSecureMethod(InMemoryUserDetailsManager.class, "updateUser", administrator);
        source.addSecureMethod(InMemoryUserDetailsManager.class, "*User", editor);
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        User user = new User("alex", "secret", List.of());
        users.createUser(user);
        MethodInvocation load = MethodInvocationUtils.create(users, "loadUserByUsername", "alex");
        MethodInvocation create = MethodInvocationUtils.createFromClass(InMemoryUserDetailsManager.class, "createUser");
        MethodInvocation update = MethodInvocationUtils.createFromClass(InMemoryUserDetailsManager.class, "updateUser");

        assertEquals(reader, source.getAttributes(load));
        assertEquals(editor, source.getAttributes(create.getMethod(), InMemoryUserDetailsManager.class));
        assertEquals(administrator, source.getAttributes(update.getMethod(), InMemoryUserDetailsManager.class));
        assertTrue(source.getAllConfigAttributes().containsAll(reader));
        assertTrue(source.getAllConfigAttributes().containsAll(editor));
        assertTrue(source.getAllConfigAttributes().containsAll(administrator));
        assertEquals("alex", users.loadUserByUsername("alex").getUsername());
    }
}
