/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.api.core.ActiveMQTimeoutException;
import org.apache.activemq.artemis.utils.FactoryFinder;
import org.junit.jupiter.api.Test;

public class FactoryFinderInnerStandaloneObjectFactoryTest {
    private static final String FACTORY_PATH = "factoryfinder/";

    @Test
    public void createsFactoryUsingContextClassLoaderResourceAndClass() throws Exception {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(
                FactoryFinderInnerStandaloneObjectFactoryTest.class.getClassLoader());
        try {
            Object instance = new FactoryFinder(FACTORY_PATH).newInstance("context-loader.properties");

            assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    public void createsFactoryUsingFactoryFinderClassLoaderFallbacks() throws Exception {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            Object instance = new FactoryFinder(FACTORY_PATH).newInstance("factory-fallback.properties");

            assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
}
