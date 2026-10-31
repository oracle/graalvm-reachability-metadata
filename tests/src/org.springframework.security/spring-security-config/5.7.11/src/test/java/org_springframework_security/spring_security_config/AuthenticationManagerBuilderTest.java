/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthenticationManagerBuilderTest {
    @Test
    void inMemoryUsersAuthenticateWithConfiguredRolesAndErasedCredentials() throws Exception {
        AuthenticationManagerBuilder builder = new AuthenticationManagerBuilder(SecurityTestSupport.objectPostProcessor());
        builder.inMemoryAuthentication()
                .withUser("reader").password("{noop}read-secret").roles("READER", "AUDITOR")
                .and().withUser("operator").password("{noop}operate-secret").authorities("orders:write");
        AuthenticationManager manager = builder.build();

        Authentication reader = manager.authenticate(new UsernamePasswordAuthenticationToken("reader", "read-secret"));
        assertThat(reader.isAuthenticated()).isTrue();
        assertThat(reader.getName()).isEqualTo("reader");
        assertThat(reader.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_READER", "ROLE_AUDITOR");
        assertThat(reader.getCredentials()).isNull();
        assertThat(((UserDetails) reader.getPrincipal()).getPassword()).isNull();

        Authentication operator = manager.authenticate(new UsernamePasswordAuthenticationToken("operator", "operate-secret"));
        assertThat(operator.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("orders:write");
        assertThat(builder.getDefaultUserDetailsService().loadUserByUsername("reader").isEnabled()).isTrue();
    }

    @Test
    void rejectsIncorrectCredentialsAndDisabledAccounts() throws Exception {
        AuthenticationManagerBuilder builder = new AuthenticationManagerBuilder(SecurityTestSupport.objectPostProcessor());
        builder.inMemoryAuthentication()
                .withUser("reader").password("{noop}read-secret").roles("READER")
                .and().withUser("inactive").password("{noop}inactive-secret").roles("READER").disabled(true);
        AuthenticationManager manager = builder.build();

        assertThrows(BadCredentialsException.class,
                () -> manager.authenticate(new UsernamePasswordAuthenticationToken("reader", "incorrect")));
        assertThrows(BadCredentialsException.class,
                () -> manager.authenticate(new UsernamePasswordAuthenticationToken("unknown", "read-secret")));
        assertThrows(DisabledException.class,
                () -> manager.authenticate(new UsernamePasswordAuthenticationToken("inactive", "inactive-secret")));
    }

    @Test
    void userDetailsServiceAuthenticatesEncodedPasswords() throws Exception {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        UserDetails user = User.withUsername("auditor").password(encoder.encode("audit-secret")).roles("AUDITOR").build();
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(user);
        AuthenticationManagerBuilder builder = new AuthenticationManagerBuilder(SecurityTestSupport.objectPostProcessor());
        builder.userDetailsService(users).passwordEncoder(encoder);
        AuthenticationManager manager = builder.build();

        Authentication result = manager.authenticate(new UsernamePasswordAuthenticationToken("auditor", "audit-secret"));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("auditor");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_AUDITOR");
        assertThat(builder.getDefaultUserDetailsService()).isSameAs(users);
        assertThrows(BadCredentialsException.class,
                () -> manager.authenticate(new UsernamePasswordAuthenticationToken("auditor", "incorrect")));
    }

    @Test
    void parentManagerSuppliesAuthenticationAndCredentialRetentionPolicy() throws Exception {
        AuthenticationManagerBuilder parent = new AuthenticationManagerBuilder(SecurityTestSupport.objectPostProcessor());
        parent.eraseCredentials(false).inMemoryAuthentication()
                .withUser("service").password("{noop}service-secret").roles("SERVICE");
        AuthenticationManagerBuilder child = new AuthenticationManagerBuilder(SecurityTestSupport.objectPostProcessor());
        child.parentAuthenticationManager(parent.build());
        AuthenticationManager manager = child.build();

        Authentication result = manager.authenticate(new UsernamePasswordAuthenticationToken("service", "service-secret"));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("service");
        assertThat(result.getCredentials()).isEqualTo("service-secret");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_SERVICE");
    }
}
