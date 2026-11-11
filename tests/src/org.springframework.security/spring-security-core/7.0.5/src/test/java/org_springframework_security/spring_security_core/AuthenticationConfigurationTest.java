/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthenticationConfigurationTest {
    @Test
    void constructsAndWiresProvidersAndTheirContractsForAuthenticationAndAuthorization() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(User.withUsername("reader")
                .password(encoder.encode("secret")).roles("READER").build());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("users", users);
            context.getBeanFactory().registerSingleton("encoder", encoder);
            context.getBeanFactory().registerSingleton("authorization", AuthorityAuthorizationManager.hasRole("READER"));
            context.registerBeanDefinition("anonymous", BeanDefinitionBuilder
                    .genericBeanDefinition("org.springframework.security.authentication.AnonymousAuthenticationProvider")
                    .addConstructorArgValue("site-key").getBeanDefinition());
            context.registerBeanDefinition("dao", BeanDefinitionBuilder
                    .genericBeanDefinition("org.springframework.security.authentication.dao.DaoAuthenticationProvider")
                    .addConstructorArgReference("users")
                    .addPropertyReference("passwordEncoder", "encoder")
                    .addPropertyValue("forcePrincipalAsString", true).getBeanDefinition());
            ManagedList<RuntimeBeanReference> providers = new ManagedList<>();
            providers.add(new RuntimeBeanReference("anonymous"));
            providers.add(new RuntimeBeanReference("dao"));
            context.registerBeanDefinition("authentication", BeanDefinitionBuilder
                    .genericBeanDefinition("org.springframework.security.authentication.ProviderManager")
                    .addConstructorArgValue(providers).getBeanDefinition());
            context.registerBeanDefinition("security", BeanDefinitionBuilder.genericBeanDefinition(SecurityService.class)
                    .addConstructorArgReference("anonymous")
                    .addConstructorArgReference("dao")
                    .addConstructorArgReference("dao")
                    .addConstructorArgReference("authentication")
                    .addConstructorArgReference("authorization").getBeanDefinition());
            context.refresh();
            SecurityService service = context.getBean(SecurityService.class);

            Authentication authenticated = service.login("secret");
            assertTrue(authenticated.isAuthenticated());
            assertEquals("reader", authenticated.getPrincipal());
            assertNull(authenticated.getCredentials());
            assertTrue(service.mayRead(authenticated));
            assertTrue(service.daoSettings.isForcePrincipalAsString());
            assertTrue(service.dao.supports(UsernamePasswordAuthenticationToken.class));
            assertThrows(BadCredentialsException.class, () -> service.login("incorrect"));

            AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken("site-key", "guest",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            assertSame(anonymous, service.anonymous.authenticate(anonymous));
            assertSame(anonymous, service.authentication.authenticate(anonymous));
            assertFalse(service.mayRead(anonymous));
            AnonymousAuthenticationToken foreign = new AnonymousAuthenticationToken("another-site", "guest", anonymous.getAuthorities());
            assertThrows(BadCredentialsException.class, () -> service.authentication.authenticate(foreign));
        }
    }

    public static final class SecurityService {
        private final AnonymousAuthenticationProvider anonymous;
        private final AuthenticationProvider dao;
        private final AbstractUserDetailsAuthenticationProvider daoSettings;
        private final ProviderManager authentication;
        private final AuthorizationManager<Object> authorization;

        public SecurityService(AnonymousAuthenticationProvider anonymous, AuthenticationProvider dao,
                               AbstractUserDetailsAuthenticationProvider daoSettings, ProviderManager authentication,
                               AuthorizationManager<Object> authorization) {
            this.anonymous = anonymous;
            this.dao = dao;
            this.daoSettings = daoSettings;
            this.authentication = authentication;
            this.authorization = authorization;
        }

        public Authentication login(String password) {
            return authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("reader", password));
        }

        public boolean mayRead(Authentication principal) {
            return authorization.authorize(() -> principal, "document").isGranted();
        }
    }
}
