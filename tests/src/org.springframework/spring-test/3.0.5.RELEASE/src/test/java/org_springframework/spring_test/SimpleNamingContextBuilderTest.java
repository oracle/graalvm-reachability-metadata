/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.junit.jupiter.api.Test;

import org.springframework.mock.jndi.SimpleNamingContextBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleNamingContextBuilderTest {
    @Test
    void createsInitialContextFactoryFromEnvironmentClass() {
        SimpleNamingContextBuilder builder = new SimpleNamingContextBuilder();
        builder.deactivate();
        TestInitialContextFactory.createdInstances = 0;

        Properties environment = new Properties();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, TestInitialContextFactory.class);

        InitialContextFactory factory = builder.createInitialContextFactory(environment);

        assertThat(factory).isInstanceOf(TestInitialContextFactory.class);
        assertThat(TestInitialContextFactory.createdInstances).isEqualTo(1);
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
