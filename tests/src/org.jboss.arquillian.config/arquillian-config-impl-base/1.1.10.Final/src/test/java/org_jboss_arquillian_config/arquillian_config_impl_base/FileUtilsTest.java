/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_config.arquillian_config_impl_base;

import org.jboss.arquillian.config.impl.extension.ConfigurationRegistrar;
import org.jboss.arquillian.core.api.event.ManagerStarted;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileUtilsTest {
    private static final String PROPERTIES_RESOURCE = "file-utils-test-arquillian.properties";

    @Test
    void loadConfigurationReadsConfiguredPropertiesFromClassPathResource() {
        String previousXmlProperty = System.getProperty(ConfigurationRegistrar.ARQUILLIAN_XML_PROPERTY);
        String previousPropertiesProperty = System.getProperty(ConfigurationRegistrar.ARQUILLIAN_PROP_PROPERTY);
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(FileUtilsTest.class.getClassLoader());
        System.clearProperty(ConfigurationRegistrar.ARQUILLIAN_XML_PROPERTY);
        System.setProperty(ConfigurationRegistrar.ARQUILLIAN_PROP_PROPERTY, PROPERTIES_RESOURCE);
        try {
            assertThatThrownBy(() -> new ConfigurationRegistrar().loadConfiguration(new ManagerStarted()))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            restoreProperty(ConfigurationRegistrar.ARQUILLIAN_XML_PROPERTY, previousXmlProperty);
            restoreProperty(ConfigurationRegistrar.ARQUILLIAN_PROP_PROPERTY, previousPropertiesProperty);
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static void restoreProperty(String propertyName, String previousProperty) {
        if (previousProperty == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousProperty);
        }
    }
}
