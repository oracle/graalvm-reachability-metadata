/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ServiceInnerLiteralTest {
    @Test
    void describesInterfacesUnderWhichAServiceIsPublished() {
        Service service = Service.Literal.of(new Class<?>[]{Runnable.class, AutoCloseable.class});
        Service equivalent = Service.Literal.of(new Class<?>[]{Runnable.class, AutoCloseable.class});

        assertArrayEquals(new Class<?>[]{Runnable.class, AutoCloseable.class}, service.value());
        assertEquals(Service.class, service.annotationType());
        assertEquals(equivalent, service);
        assertEquals(equivalent.hashCode(), service.hashCode());
        assertNotEquals(Service.Literal.of(new Class<?>[]{Runnable.class}), service);
        assertNotEquals(Service.Literal.of(new Class<?>[]{AutoCloseable.class, Runnable.class}), service);
    }

    @Test
    void supportsInferredServiceInterfaces() {
        Service service = Service.Literal.of(new Class<?>[0]);

        assertEquals(0, service.value().length);
        assertEquals(Service.Literal.of(new Class<?>[0]), service);
    }
}
