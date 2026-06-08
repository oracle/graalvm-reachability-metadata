/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.junit.jupiter.api.Test;

import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleNamingContextBuilderTest {
    @Test
    void resolvesClassValuedPropertiesAndProfilesFromMockPropertySources() {
        MockPropertySource propertySource = new MockPropertySource("testProperties")
                .withProperty(Context.INITIAL_CONTEXT_FACTORY, TestInitialContextFactory.class)
                .withProperty("application.name", "reachability-metadata")
                .withProperty("application.retries", "3");

        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(propertySource);
        environment.setActiveProfiles("native", "test");

        assertThat(environment.getProperty(Context.INITIAL_CONTEXT_FACTORY, Object.class))
                .isSameAs(TestInitialContextFactory.class);
        assertThat(environment.getRequiredProperty("application.name")).isEqualTo("reachability-metadata");
        assertThat(environment.getProperty("application.retries", Integer.class)).isEqualTo(3);
        assertThat(environment.acceptsProfiles(Profiles.of("native & test"))).isTrue();
    }

    public static class TestInitialContextFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            return null;
        }
    }
}
