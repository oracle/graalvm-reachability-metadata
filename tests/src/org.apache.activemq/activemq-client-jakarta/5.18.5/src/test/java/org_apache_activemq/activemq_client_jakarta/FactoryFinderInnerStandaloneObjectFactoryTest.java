/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.FactoryFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderInnerStandaloneObjectFactoryTest {
    private static final String FACTORY_PATH = "factoryfinder/";

    @Test
    void createsFactoryUsingContextClassLoaderResourceAndClass() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(FactoryFinderInnerStandaloneObjectFactoryTest.class.getClassLoader());
        try {
            Object instance = new FactoryFinder(FACTORY_PATH).newInstance("context-loader.properties");

            assertThat(instance).isInstanceOf(ActiveMQQueue.class);
        } finally {
            currentThread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void createsFactoryUsingFactoryFinderClassLoaderFallbacks() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            Object instance = new FactoryFinder(FACTORY_PATH).newInstance("factory-fallback.properties");

            assertThat(instance).isInstanceOf(ActiveMQQueue.class);
        } finally {
            currentThread.setContextClassLoader(originalLoader);
        }
    }
}
