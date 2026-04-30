/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
import org.junit.jupiter.api.Test;

public class ClassLoaderSearchUtilTest {
    private static final String DEFAULT_EXTENSION_LOADER = "org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader";

    @Test
    public void searchesConfiguredClassLoadersForDefaultExtensionLoader() {
        assertEquals(DEFAULT_EXTENSION_LOADER, ServiceExtensionLoader.class.getName());
        ClassLoader testClassLoader = ClassLoaderSearchUtilTest.class.getClassLoader();

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> new ConfigurationBuilder()
                .classLoaders(Collections.singletonList(testClassLoader))
                .build());

        assertInstanceOf(NoSuchMethodException.class, exception.getCause());
    }
}
