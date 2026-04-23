/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultC3P0ConfigFinderTest {
    private static final String XML_CONFIG_PROPERTY = "com.mchange.v2.c3p0.cfg.xml";

    @Test
    void loadsXmlConfigurationFromClassLoaderResource() throws Exception {
        C3p0TestSupport.withRefreshedProperty(XML_CONFIG_PROPERTY, "classloader:c3p0-test-config.xml", () -> {
            assertThat(C3P0Config.getUnspecifiedUserProperty("maxPoolSize", null)).isEqualTo("9");
        });
    }
}
