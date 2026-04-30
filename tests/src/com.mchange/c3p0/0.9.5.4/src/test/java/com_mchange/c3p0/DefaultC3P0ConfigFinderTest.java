/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class DefaultC3P0ConfigFinderTest {
    private static final String XML_CONFIG_PROPERTY = "com.mchange.v2.c3p0.cfg.xml";
    private static final String XML_CONFIG_RESOURCE = "classloader:/com_mchange/c3p0/default-c3p0-config-finder.xml";

    @Test
    void loadsXmlConfigurationFromConfiguredClassLoaderResource() {
        Properties properties = new Properties();
        properties.setProperty(XML_CONFIG_PROPERTY, XML_CONFIG_RESOURCE);
        MultiPropertiesConfig overrides = MultiPropertiesConfig.fromProperties(properties);

        try {
            C3P0Config.refreshMainConfig(new MultiPropertiesConfig[] {overrides}, "classloader XML config resource");

            assertThat(C3P0Config.getUnspecifiedUserProperty("checkoutTimeout", null)).isEqualTo("4321");
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }
}
