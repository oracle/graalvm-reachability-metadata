/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultC3P0ConfigFinderTest {
    @Test
    void loadsNamedConfigurationFromAnExplicitClasspathResource() throws Exception {
        try (AutoCloseable configuration = C3p0Support.configuration(
                        "com.mchange.v2.c3p0.cfg.xml", "classloader:/pool-settings.xml")) {
            WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource("analytics");
            assertEquals(1, source.getMinPoolSize());
            assertEquals(5, source.getMaxPoolSize());
            assertEquals(30000, source.getCheckoutTimeout());
        }
    }
}
