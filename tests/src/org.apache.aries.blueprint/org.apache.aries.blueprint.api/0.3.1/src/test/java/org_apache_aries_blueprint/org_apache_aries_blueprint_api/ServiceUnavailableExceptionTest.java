/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.junit.jupiter.api.Test;
import org.osgi.framework.ServiceException;
import org.osgi.service.blueprint.container.ServiceUnavailableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ServiceUnavailableExceptionTest {
    @Test
    void identifiesUnsatisfiedServiceFilterAndFailureType() {
        String filter = "(objectClass=example.Inventory)";
        ServiceUnavailableException exception = new ServiceUnavailableException("Inventory unavailable", filter);

        assertEquals("Inventory unavailable", exception.getMessage());
        assertEquals(filter, exception.getFilter());
        assertEquals(ServiceException.UNREGISTERED, exception.getType());
        assertNull(exception.getCause());
    }

    @Test
    void preservesUnderlyingServiceFailure() {
        String filter = "(&(objectClass=example.Inventory)(region=west))";
        IllegalStateException cause = new IllegalStateException("Service was unregistered");
        ServiceUnavailableException exception = new ServiceUnavailableException("Lookup failed", filter, cause);

        assertEquals("Lookup failed", exception.getMessage());
        assertEquals(filter, exception.getFilter());
        assertEquals(ServiceException.UNREGISTERED, exception.getType());
        assertSame(cause, exception.getCause());
    }
}
