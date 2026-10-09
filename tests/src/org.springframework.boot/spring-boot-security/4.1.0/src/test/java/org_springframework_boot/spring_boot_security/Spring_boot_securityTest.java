/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_securityTest {

    private final ApplicationContextRunner securityContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    private final WebApplicationContextRunner userDetailsContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class));

    @Test
    void securityPropertiesExposeDefaultsAndRetainConfiguredUserValues() {
        SecurityProperties properties = new SecurityProperties();
        SecurityProperties.User user = properties.getUser();
        String generatedPassword = user.getPassword();

        assertThat(user.getName()).isEqualTo("user");
        assertThat(generatedPassword).isNotBlank();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.isPasswordGenerated()).isTrue();

        user.setPassword("");
        assertThat(user.getPassword()).isEqualTo(generatedPassword);
        assertThat(user.isPasswordGenerated()).isTrue();

        user.setName("alice");
        user.setPassword("{noop}secret");
        user.setRoles(List.of("USER", "ADMIN"));

        assertThat(user.getName()).isEqualTo("alice");
        assertThat(user.getPassword()).isEqualTo("{noop}secret");
        assertThat(user.getRoles()).containsExactly("USER", "ADMIN");
        assertThat(user.isPasswordGenerated()).isFalse();
    }

    @Test
    void securityAutoConfigurationBindsPropertiesAndCreatesAuthenticationEventPublisher() {
        this.securityContextRunner
                .withPropertyValues("spring.security.user.name=alice", "spring.security.user.roles=ADMIN,REPORTER")
                .run((context) -> {
                    assertThat(context).hasSingleBean(SecurityProperties.class);
                    assertThat(context).hasSingleBean(DefaultAuthenticationEventPublisher.class);
                    assertThat(context).hasSingleBean(AuthenticationEventPublisher.class);

                    SecurityProperties.User user = context.getBean(SecurityProperties.class).getUser();
                    assertThat(user.getName()).isEqualTo("alice");
                    assertThat(user.getRoles()).containsExactly("ADMIN", "REPORTER");
                });
    }

    @Test
    void securityAutoConfigurationBacksOffWhenAuthenticationEventPublisherExists() {
        this.securityContextRunner.withUserConfiguration(ExistingAuthenticationEventPublisherConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(AuthenticationEventPublisher.class);
                    assertThat(context).hasBean("existingAuthenticationEventPublisher");
                    assertThat(context).doesNotHaveBean("authenticationEventPublisher");
                });
    }

    @Test
    void userDetailsServiceAutoConfigurationCreatesConfiguredInMemoryUser() {
        this.userDetailsContextRunner
                .withPropertyValues("spring.security.user.name=alice", "spring.security.user.password={noop}secret",
                        "spring.security.user.roles=USER,ADMIN")
                .run((context) -> {
                    assertThat(context).hasSingleBean(UserDetailsService.class);
                    assertThat(context).hasSingleBean(InMemoryUserDetailsManager.class);

                    UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("alice");
                    assertThat(user.getUsername()).isEqualTo("alice");
                    assertThat(user.getPassword()).isEqualTo("{noop}secret");
                    assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
                });
    }

    @Test
    void userDetailsServiceAutoConfigurationGeneratesCredentialsByDefault() {
        this.userDetailsContextRunner.run((context) -> {
            assertThat(context).hasSingleBean(UserDetailsService.class);

            UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("user");
            assertThat(user.getUsername()).isEqualTo("user");
            assertThat(user.getPassword()).startsWith("{noop}").hasSizeGreaterThan("{noop}".length());
            assertThat(user.getAuthorities()).isEmpty();
        });
    }

    @Test
    void userDetailsServiceAutoConfigurationBacksOffForExistingUserDetailsService() {
        this.userDetailsContextRunner.withUserConfiguration(ExistingUserDetailsServiceConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(UserDetailsService.class);
                    assertThat(context).hasBean("existingUserDetailsService");

                    UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("provided");
                    assertThat(user.getUsername()).isEqualTo("provided");
                    assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                            .containsExactly("ROLE_OPERATOR");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ExistingAuthenticationEventPublisherConfiguration {

        @Bean
        DefaultAuthenticationEventPublisher existingAuthenticationEventPublisher(
                ApplicationEventPublisher applicationEventPublisher) {
            return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class ExistingUserDetailsServiceConfiguration {

        @Bean
        UserDetailsService existingUserDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("provided").password("{noop}provided-secret").roles("OPERATOR").build());
        }

    }

}
