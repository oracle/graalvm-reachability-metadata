/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.texen.util.PropertiesUtil;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class PropertiesUtilTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Properties properties = new PropertiesUtil().load("fixtures/settings.properties");
        assertEquals("hello", properties.getProperty("greeting"));
        assertEquals("strict", properties.getProperty("mode"));
    }
}
