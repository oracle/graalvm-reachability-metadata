/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.junit.jupiter.api.Test;
import org.osgi.service.blueprint.container.NoSuchComponentException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoSuchComponentExceptionTest {
    @Test
    void identifiesMissingComponentInGeneratedDiagnostic() {
        NoSuchComponentException exception = new NoSuchComponentException("inventory");

        assertEquals("inventory", exception.getComponentId());
        assertTrue(exception.getMessage().contains("inventory"));
        assertNull(exception.getCause());
    }

    @Test
    void preservesExplicitDiagnosticAndComponentId() {
        NoSuchComponentException exception = new NoSuchComponentException("Unknown inventory component", "inventory");

        assertEquals("Unknown inventory component", exception.getMessage());
        assertEquals("inventory", exception.getComponentId());
    }

    @Test
    void describesNullComponentId() {
        NoSuchComponentException exception = new NoSuchComponentException(null);

        assertNull(exception.getComponentId());
        assertTrue(exception.getMessage().contains("<null>"));
    }
}
