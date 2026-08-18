/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                ActiveMQTimeoutException.class.getName(),
                ActiveMQTimeoutException.class);

        assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
    }

    @Test
    public void createsInstanceWithContextClassLoaderFallback() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassloadingUtilTest.class.getClassLoader());
        try {
            Object instance = ClassloadingUtil.newInstanceFromClassLoader(
                    DriverManager.class,
                    ActiveMQTimeoutException.class.getName(),
                    ActiveMQTimeoutException.class);

            assertThat(instance).isInstanceOf(ActiveMQTimeoutException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    public void createsInstanceWithConstructorArguments() throws Exception {
        Object instance = ClassloadingUtil.getInstanceForParamsWithTypeCheck(
                ActiveMQTimeoutException.class.getName(),
                ActiveMQTimeoutException.class,
                ClassloadingUtilTest.class.getClassLoader(),
                new Class<?>[] {String.class},
                TIMEOUT_MESSAGE);

        assertThat(instance).isInstanceOfSatisfying(
                ActiveMQTimeoutException.class,
                exception -> assertThat(exception.getMessage()).isEqualTo(TIMEOUT_MESSAGE));
    }

    @Test
    public void rejectsInstanceThatDoesNotMatchExpectedType() {
        assertThatThrownBy(() -> ClassloadingUtil.newInstanceFromClassLoader(
                ClassloadingUtilTest.class,
                ActiveMQTimeoutException.class.getName(),
                DriverManager.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ActiveMQTimeoutException.class.getName())
                .hasMessageContaining(DriverManager.class.getName());
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
