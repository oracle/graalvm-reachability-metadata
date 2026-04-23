/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultC3P0ConfigFinderTest {
    @Test
    void loadsXmlConfigFromClassLoaderResourceOverride() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("com.mchange.v2.c3p0.cfg.xml", "classloader:/c3p0-test-config.xml");
        MultiPropertiesConfig overrideConfig =
            MultiPropertiesConfig.fromProperties("DefaultC3P0ConfigFinderTest", overrides);

        try {
            C3P0Config.refreshMainConfig(
                new MultiPropertiesConfig[] {overrideConfig},
                "DefaultC3P0ConfigFinderTest classloader xml override"
            );

            assertThat(C3P0Config.getUnspecifiedUserProperty("maxPoolSize", null)).isEqualTo("9");
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }
}
