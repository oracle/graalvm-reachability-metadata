/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_securityTest {

    private final ApplicationContextRunner securityContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    private final WebApplicationContextRunner userDetailsContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class));

    private final ReactiveWebApplicationContextRunner reactiveUserDetailsContextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ReactiveUserDetailsServiceAutoConfiguration.class));

    private final WebApplicationContextRunner servletSecurityContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServletWebSecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class));

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
    void servletWebSecurityAutoConfigurationCreatesAuthenticatedFilterChainAndRegistersIt() {
        this.servletSecurityContextRunner
                .withUserConfiguration(ExistingUserDetailsServiceConfiguration.class)
                .withPropertyValues("spring.security.filter.order=123",
                        "spring.security.filter.dispatcher-types=REQUEST,ERROR")
                .run((context) -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(DelegatingFilterProxyRegistrationBean.class);

                    SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setRequestURI("/secured");
                    assertThat(filterChain.matches(request)).isTrue();
                    assertThat(filterChain.getFilters()).extracting(Object::getClass)
                            .contains(UsernamePasswordAuthenticationFilter.class, BasicAuthenticationFilter.class);

                    DelegatingFilterProxyRegistrationBean registration =
                            context.getBean(DelegatingFilterProxyRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(123);
                    assertThat(registration.determineDispatcherTypes())
                            .containsExactlyInAnyOrder(DispatcherType.REQUEST, DispatcherType.ERROR);
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
    void reactiveUserDetailsServiceAutoConfigurationCreatesConfiguredInMemoryUser() {
        this.reactiveUserDetailsContextRunner
                .withPropertyValues("spring.security.user.name=alice", "spring.security.user.password={noop}secret",
                        "spring.security.user.roles=USER,ADMIN")
                .run((context) -> {
                    assertThat(context).hasSingleBean(ReactiveUserDetailsService.class);
                    assertThat(context).hasSingleBean(MapReactiveUserDetailsService.class);

                    UserDetails user = context.getBean(ReactiveUserDetailsService.class)
                            .findByUsername("alice").block(Duration.ofSeconds(10));
                    assertThat(user).isNotNull();
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
    void userDetailsServiceAutoConfigurationAddsNoopPrefixToPlaintextPassword() {
        this.userDetailsContextRunner.withPropertyValues("spring.security.user.password=secret").run((context) -> {
            UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("user");

            assertThat(user.getPassword()).isEqualTo("{noop}secret");
        });
    }

    @Test
    void userDetailsServiceAutoConfigurationPreservesPasswordWhenPasswordEncoderIsAvailable() {
        this.userDetailsContextRunner.withUserConfiguration(PasswordEncoderConfiguration.class)
                .withPropertyValues("spring.security.user.password=encoded-secret")
                .run((context) -> {
                    UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("user");

                    assertThat(user.getPassword()).isEqualTo("encoded-secret");
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

    @Configuration(proxyBeanMethods = false)
    static class PasswordEncoderConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new TestPasswordEncoder();
        }

    }

    static final class TestPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded-" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).contentEquals(encodedPassword);
        }

    }

}
