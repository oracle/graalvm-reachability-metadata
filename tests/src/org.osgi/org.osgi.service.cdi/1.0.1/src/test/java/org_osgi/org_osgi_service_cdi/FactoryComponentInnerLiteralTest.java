/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.CDIConstants;
import org.osgi.service.cdi.annotations.FactoryComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FactoryComponentInnerLiteralTest {
    @Test
    void describesFactoryConfigurationPid() {
        FactoryComponent factory = FactoryComponent.Literal.of("worker.factory");
        FactoryComponent equivalent = FactoryComponent.Literal.of("worker.factory");

        assertEquals("worker.factory", factory.value());
        assertEquals(FactoryComponent.class, factory.annotationType());
        assertEquals(equivalent, factory);
        assertEquals(equivalent.hashCode(), factory.hashCode());
        assertNotEquals(FactoryComponent.Literal.of("reader.factory"), factory);
        assertTrue(factory.toString().contains("worker.factory"));
    }

    @Test
    void supportsUsingComponentNameAsFactoryPid() {
        FactoryComponent factory = FactoryComponent.Literal.of(CDIConstants.CDI_COMPONENT_NAME);

        assertEquals(CDIConstants.CDI_COMPONENT_NAME, factory.value());
    }
}
