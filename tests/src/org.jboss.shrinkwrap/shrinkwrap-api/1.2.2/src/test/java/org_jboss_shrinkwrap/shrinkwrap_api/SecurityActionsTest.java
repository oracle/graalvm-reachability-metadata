/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    @Test
    void createsInstanceFromClassNameWithNoArgumentConstructor() throws Exception {
        Method newInstance = Class.forName("org.jboss.shrinkwrap.api.SecurityActions").getDeclaredMethod(
            "newInstance",
            String.class,
            Class[].class,
            Object[].class,
            Class.class,
            ClassLoader.class);
        newInstance.setAccessible(true);

        Object created = newInstance.invoke(
            null,
            ConfigurationBuilder.class.getName(),
            new Class<?>[] {},
            new Object[] {},
            ConfigurationBuilder.class,
            ConfigurationBuilder.class.getClassLoader());

        assertThat(created).isInstanceOf(ConfigurationBuilder.class);
        ConfigurationBuilder builder = (ConfigurationBuilder) created;
        assertThat(builder.getClassLoaders()).isNull();
        assertThat(builder.getExecutorService()).isNull();
        assertThat(builder.getExtensionLoader()).isNull();
    }
}
