/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.sql.DriverManager;

import org.apache.activemq.artemis.api.core.ActiveMQTimeoutException;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.junit.jupiter.api.Test;

public class ClassloadingUtilTest {
    private static final String RESOURCE_NAME = "artemis-commons-classloading.properties";
    private static final String TIMEOUT_MESSAGE = "expected timeout";

    @Test
    public void createsInstanceWithOwnerClassLoader() {
        Object instance = ClassloadingUtil.newInstanceFromClassLoader(
                ClassloadingUtilTest.class,
                ActiveMQTimeoutException.class.getName());

        assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
    }

    @Test
    public void createsInstanceWithContextClassLoaderFallback() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassloadingUtilTest.class.getClassLoader());
        try {
            Object instance = ClassloadingUtil.newInstanceFromClassLoader(
                    DriverManager.class,
                    ActiveMQTimeoutException.class.getName());

            assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    public void createsInstanceWithConstructorArguments() {
        Object instance = ClassloadingUtil.newInstanceFromClassLoader(
                ClassloadingUtilTest.class,
                ActiveMQTimeoutException.class.getName(),
                TIMEOUT_MESSAGE);

        assertThat(instance).isInstanceOfSatisfying(
                ActiveMQTimeoutException.class,
                exception -> assertThat(exception.getMessage()).isEqualTo(TIMEOUT_MESSAGE));
    }

    @Test
    public void createsVarargsInstanceWithContextClassLoaderFallback() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassloadingUtilTest.class.getClassLoader());
        try {
            Object instance = ClassloadingUtil.newInstanceFromClassLoader(
                    DriverManager.class,
                    ActiveMQTimeoutException.class.getName(),
                    TIMEOUT_MESSAGE);

            assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    public void findsResourceWithContextClassLoaderFallback() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassloadingUtilTest.class.getClassLoader());
        try {
            URL resource = ClassloadingUtil.findResource(
                    ClassLoader.getPlatformClassLoader(),
                    RESOURCE_NAME);

            assertThat(resource).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
}
