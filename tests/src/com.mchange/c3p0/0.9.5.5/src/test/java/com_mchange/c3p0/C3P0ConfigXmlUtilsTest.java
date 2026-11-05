/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigXmlUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class C3P0ConfigXmlUtilsTest {
    @Test
    void optionalDefaultResourceFallsBackAndExplicitXmlBindsNamedSettings() throws Exception {
        assertNull(C3P0ConfigXmlUtils.extractXmlConfigFromDefaultResource(false));
        String xml = """
                        <c3p0-config>
                          <default-config><property name="minPoolSize">1</property></default-config>
                          <named-config name="reports"><property name="maxPoolSize">7</property></named-config>
                        </c3p0-config>
                        """;
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            C3P0Config.setMainConfig(C3P0ConfigXmlUtils.extractXmlConfigFromInputStream(input, false));
            WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource("reports");
            assertEquals(1, source.getMinPoolSize());
            assertEquals(7, source.getMaxPoolSize());
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }
}
