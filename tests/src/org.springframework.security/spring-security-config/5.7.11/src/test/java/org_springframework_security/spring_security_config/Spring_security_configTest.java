/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.authentication.AuthenticationManagerFactoryBean;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.core.userdetails.UserDetailsMapFactoryBean;
import org.springframework.security.config.provisioning.UserDetailsManagerResourceFactoryBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Spring_security_configTest {

    @Test
    void inMemoryAuthenticationBuilderCreatesAuthenticatingProviderManager() throws Exception {
        List<Object> postProcessedObjects = new ArrayList<>();
        AuthenticationManagerBuilder builder = new AuthenticationManagerBuilder(
                new RecordingObjectPostProcessor(postProcessedObjects));

        builder.eraseCredentials(false)
                .inMemoryAuthentication()
                .withUser("alice")
                .password("{noop}secret")
                .roles("ADMIN")
                .and()
                .withUser(User.withUsername("bob")
                        .password("{noop}password")
                        .authorities(new SimpleGrantedAuthority("SCOPE_messages")));

        assertThat(builder.isConfigured()).isFalse();

        AuthenticationManager authenticationManager = builder.build();
        assertThat(builder.getDefaultUserDetailsService().loadUserByUsername("alice").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "secret"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(((ProviderManager) authenticationManager).isEraseCredentialsAfterAuthentication()).isFalse();
        assertThat(postProcessedObjects.stream().anyMatch(ProviderManager.class::isInstance)).isTrue();
    }

    @Test
    void userDetailsManagerResourceFactoryParsesInlineUsers() throws Exception {
        UserDetailsManagerResourceFactoryBean factory = UserDetailsManagerResourceFactoryBean.fromString("""
                admin={noop}admin-password,ROLE_USER,ROLE_ADMIN
                disabled={noop}disabled-password,disabled,ROLE_USER
                user={noop}user-password,enabled,ROLE_USER
                """);

        assertThat(factory.getObjectType()).isEqualTo(InMemoryUserDetailsManager.class);

        InMemoryUserDetailsManager userDetailsManager = factory.getObject();
        UserDetails admin = userDetailsManager.loadUserByUsername("admin");
        UserDetails disabled = userDetailsManager.loadUserByUsername("disabled");

        assertThat(admin.getPassword()).isEqualTo("{noop}admin-password");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(userDetailsManager.userExists("user")).isTrue();
    }

    @Test
    void userDetailsMapFactoryCreatesEnabledAndDisabledUsers() {
        Map<String, String> userProperties = new HashMap<>();
        userProperties.put("reader", "{noop}reader-password,ROLE_READER");
        userProperties.put("writer", "{noop}writer-password,enabled,ROLE_READER,ROLE_WRITER");
        userProperties.put("locked", "{noop}locked-password,disabled,ROLE_READER");

        UserDetailsMapFactoryBean factory = new UserDetailsMapFactoryBean(userProperties);
        Collection<UserDetails> users = factory.getObject();

        assertThat(factory.getObjectType()).isEqualTo(Collection.class);
        assertThat(users)
                .extracting(UserDetails::getUsername)
                .containsExactlyInAnyOrder("reader", "writer", "locked");
        assertThat(userNamed(users, "writer").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_READER", "ROLE_WRITER");
        assertThat(userNamed(users, "locked").isEnabled()).isFalse();
    }

    @Test
    void authenticationManagerFactoryBeanFallsBackToUserDetailsServiceAndPasswordEncoder() throws Exception {
        PasswordEncoder passwordEncoder = new PrefixPasswordEncoder();
        UserDetailsService userDetailsService = new InMemoryUserDetailsManager(User.withUsername("carol")
                .password(passwordEncoder.encode("s3cr3t"))
                .roles("AUDITOR")
                .build());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("userDetailsService", userDetailsService);
        beanFactory.addBean("passwordEncoder", passwordEncoder);
        AuthenticationManagerFactoryBean factory = new AuthenticationManagerFactoryBean();
        factory.setBeanFactory(beanFactory);

        AuthenticationManager authenticationManager = factory.getObject();
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("carol", "s3cr3t"));

        assertThat(factory.getObjectType()).isEqualTo(ProviderManager.class);
        assertThat(factory.isSingleton()).isTrue();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_AUDITOR");
    }

    @Test
    void authenticationManagerFactoryBeanReportsMissingGlobalAuthenticationManager() {
        AuthenticationManagerFactoryBean factory = new AuthenticationManagerFactoryBean();
        factory.setBeanFactory(new StaticListableBeanFactory());

        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(factory::getObject)
                .withMessageContaining("Did you forget to add a global <authentication-manager> element");
    }

    @Test
    void configuredSecurityBuilderInvokesConfigurerLifecycleAndObjectPostProcessors() throws Exception {
        TestSecurityBuilder builder = new TestSecurityBuilder(new ObjectPostProcessor<Object>() {
            @Override
            public <O> O postProcess(O object) {
                if (object instanceof StringBuilder) {
                    ((StringBuilder) object).append("-post-processed");
                }
                return object;
            }
        });
        TestSecurityConfigurer configurer = builder.apply(new TestSecurityConfigurer("configured"));

        assertThat(configurer.and()).isSameAs(builder);
        assertThat(builder.getConfigurers(TestSecurityConfigurer.class)).containsExactly(configurer);
        assertThat(builder.getSharedObjects()).isEmpty();

        String built = builder.build();

        assertThat(built).isEqualTo("configured-init-configure-post-processed");
        assertThat(builder.getSharedObject(String.class)).isEqualTo("configured-init");
        assertThat(builder.getObject()).isEqualTo(built);
    }

    @Test
    void customizerAndGrantedAuthorityDefaultsExposeSimplePublicContracts() {
        List<String> values = new ArrayList<>(List.of("original"));
        Customizer<List<String>> addCustomizedValue = list -> list.add("customized");
        Customizer<List<String>> defaults = Customizer.withDefaults();

        addCustomizedValue.customize(values);
        defaults.customize(values);
        GrantedAuthorityDefaults authorityDefaults = new GrantedAuthorityDefaults("APP_");

        assertThat(values).containsExactly("original", "customized");
        assertThat(authorityDefaults.getRolePrefix()).isEqualTo("APP_");
    }

    private static UserDetails userNamed(Collection<UserDetails> users, String username) {
        return users.stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
    }

    private static final class RecordingObjectPostProcessor implements ObjectPostProcessor<Object> {

        private final List<Object> postProcessedObjects;

        private RecordingObjectPostProcessor(List<Object> postProcessedObjects) {
            this.postProcessedObjects = postProcessedObjects;
        }

        @Override
        public <O> O postProcess(O object) {
            this.postProcessedObjects.add(object);
            return object;
        }
    }

    private static final class PrefixPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "{prefix}" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }

    private static final class TestSecurityBuilder
            extends AbstractConfiguredSecurityBuilder<String, TestSecurityBuilder> {

        private String value = "unset";

        private TestSecurityBuilder(ObjectPostProcessor<Object> objectPostProcessor) {
            super(objectPostProcessor);
        }

        private void setValue(String value) {
            this.value = value;
        }

        @Override
        protected String performBuild() {
            StringBuilder result = postProcess(new StringBuilder(this.value));
            return result.toString();
        }
    }

    private static final class TestSecurityConfigurer
            extends SecurityConfigurerAdapter<String, TestSecurityBuilder> {

        private final String value;

        private TestSecurityConfigurer(String value) {
            this.value = value;
        }

        @Override
        public void init(TestSecurityBuilder builder) {
            builder.setSharedObject(String.class, this.value + "-init");
        }

        @Override
        public void configure(TestSecurityBuilder builder) {
            builder.setValue(builder.getSharedObject(String.class) + "-configure");
        }
    }
}
