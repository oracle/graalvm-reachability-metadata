/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.ComponentScoped;
import org.osgi.service.cdi.annotations.SingleComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComponentScopedInnerLiteralTest {
    @Test
    void representsComponentScopeIndependentlyOfTheComponentStereotype() {
        ComponentScoped scope = ComponentScoped.Literal.INSTANCE;
        ComponentScoped equivalent = new ComponentScoped.Literal();

        assertEquals(ComponentScoped.class, scope.annotationType());
        assertEquals(equivalent, scope);
        assertEquals(scope, equivalent);
        assertEquals(0, scope.hashCode());
        assertNotEquals(SingleComponent.Literal.INSTANCE, scope);
        assertTrue(scope.toString().contains(ComponentScoped.class.getName()));
    }
}
