/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.CDIConstants;
import org.osgi.service.cdi.ConfigurationPolicy;
import org.osgi.service.cdi.annotations.PID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PIDInnerLiteralTest {
    @Test
    void associatesConfigurationWithItsPolicy() {
        PID required = PID.Literal.of("worker.config", ConfigurationPolicy.REQUIRED);
        PID equivalent = PID.Literal.of("worker.config", ConfigurationPolicy.REQUIRED);

        assertEquals("worker.config", required.value());
        assertEquals(ConfigurationPolicy.REQUIRED, required.policy());
        assertEquals(PID.class, required.annotationType());
        assertEquals(equivalent, required);
        assertEquals(equivalent.hashCode(), required.hashCode());
        assertNotEquals(PID.Literal.of("reader.config", ConfigurationPolicy.REQUIRED), required);
        assertNotEquals(PID.Literal.of("worker.config", ConfigurationPolicy.OPTIONAL), required);
        assertTrue(required.toString().contains("worker.config"));
    }

    @Test
    void supportsComponentNameAsAnOptionalConfigurationPid() {
        PID configuration = PID.Literal.of(CDIConstants.CDI_COMPONENT_NAME, ConfigurationPolicy.OPTIONAL);

        assertEquals(CDIConstants.CDI_COMPONENT_NAME, configuration.value());
        assertEquals(ConfigurationPolicy.OPTIONAL, configuration.policy());
    }
}
