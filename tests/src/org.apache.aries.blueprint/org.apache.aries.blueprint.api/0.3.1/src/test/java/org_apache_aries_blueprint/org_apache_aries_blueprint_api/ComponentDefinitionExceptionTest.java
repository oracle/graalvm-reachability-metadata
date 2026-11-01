/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.junit.jupiter.api.Test;
import org.osgi.service.blueprint.container.ComponentDefinitionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ComponentDefinitionExceptionTest {
    @Test
    void preservesConfigurationExplanationAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("Missing factory method");
        ComponentDefinitionException exception = new ComponentDefinitionException("Invalid inventory bean", cause);

        assertEquals("Invalid inventory bean", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void supportsMessageOnlyAndCauseOnlyDiagnostics() {
        ComponentDefinitionException messageOnly = new ComponentDefinitionException("Missing bean class");
        assertEquals("Missing bean class", messageOnly.getMessage());
        assertNull(messageOnly.getCause());

        IllegalStateException cause = new IllegalStateException("Bean initialization failed");
        ComponentDefinitionException causeOnly = new ComponentDefinitionException(cause);
        assertSame(cause, causeOnly.getCause());
        assertEquals(cause.toString(), causeOnly.getMessage());
    }

    @Test
    void supportsEmptyDiagnostic() {
        ComponentDefinitionException exception = new ComponentDefinitionException();
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }
}
