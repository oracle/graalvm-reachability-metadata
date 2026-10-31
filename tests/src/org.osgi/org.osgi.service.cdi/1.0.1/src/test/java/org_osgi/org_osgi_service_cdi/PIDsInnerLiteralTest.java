/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.ConfigurationPolicy;
import org.osgi.service.cdi.annotations.PID;
import org.osgi.service.cdi.annotations.PIDs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class PIDsInnerLiteralTest {
    @Test
    void preservesOrderedConfigurationsAndTheirPolicies() {
        PID required = PID.Literal.of("worker.config", ConfigurationPolicy.REQUIRED);
        PID optional = PID.Literal.of("defaults.config", ConfigurationPolicy.OPTIONAL);
        PIDs configurations = PIDs.Literal.of(new PID[]{required, optional});
        PIDs equivalent = PIDs.Literal.of(new PID[]{
                PID.Literal.of("worker.config", ConfigurationPolicy.REQUIRED),
                PID.Literal.of("defaults.config", ConfigurationPolicy.OPTIONAL)});

        assertArrayEquals(new PID[]{required, optional}, configurations.value());
        assertEquals(PIDs.class, configurations.annotationType());
        assertEquals(equivalent, configurations);
        assertEquals(equivalent.hashCode(), configurations.hashCode());
        assertNotEquals(PIDs.Literal.of(new PID[]{optional, required}), configurations);
        assertNotEquals(PIDs.Literal.of(new PID[]{required}), configurations);
    }

    @Test
    void supportsAnEmptyConfigurationList() {
        PIDs configurations = PIDs.Literal.of(new PID[0]);

        assertEquals(0, configurations.value().length);
        assertEquals(PIDs.Literal.of(new PID[0]), configurations);
    }
}
