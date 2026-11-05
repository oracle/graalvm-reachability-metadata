/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.ServiceScope;
import org.osgi.service.cdi.annotations.ServiceInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceInstanceInnerLiteralTest {
    @Test
    void distinguishesServiceScopes() {
        for (ServiceScope scope : ServiceScope.values()) {
            ServiceInstance instance = ServiceInstance.Literal.of(scope);
            ServiceInstance equivalent = ServiceInstance.Literal.of(scope);

            assertEquals(scope, instance.value());
            assertEquals(ServiceInstance.class, instance.annotationType());
            assertEquals(equivalent, instance);
            assertEquals(equivalent.hashCode(), instance.hashCode());
            assertTrue(instance.toString().contains(scope.name()));
        }
        assertNotEquals(ServiceInstance.Literal.of(ServiceScope.BUNDLE),
                ServiceInstance.Literal.of(ServiceScope.PROTOTYPE));
        assertNotEquals(ServiceInstance.Literal.of(ServiceScope.SINGLETON),
                ServiceInstance.Literal.of(ServiceScope.BUNDLE));
    }
}
