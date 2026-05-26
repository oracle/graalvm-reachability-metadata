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

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleNamingContextBuilderTest {
    @Test
    void configuresMockEnvironmentPropertiesAndProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "metadata-test")
                .withProperty("feature.enabled", "true");
        environment.setActiveProfiles("native", "test");

        assertThat(environment.getRequiredProperty("spring.application.name")).isEqualTo("metadata-test");
        assertThat(environment.getProperty("feature.enabled", Boolean.class)).isTrue();
        assertThat(environment.acceptsProfiles(Profiles.of("native & test"))).isTrue();
    }

    public static class TestInitialContextFactory implements InitialContextFactory {
        static int createdInstances;

        public TestInitialContextFactory() {
            createdInstances++;
        }

        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            return null;
        }
    }
}
