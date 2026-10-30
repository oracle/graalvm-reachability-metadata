/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import java.util.Dictionary;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.aries.mocks.BundleContextMock;
import org.apache.aries.unittest.mocks.Skeleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BundleContextMockTest {
    @AfterEach
    void clearRegistry() {
        BundleContextMock.clear();
    }

    @Test
    void registersLooksUpAndUnregistersAServiceByItsInterfaceName() throws Exception {
        Bundle bundle = Skeleton.newMock(Bundle.class);
        BundleContextMock context = new BundleContextMock(bundle);
        AtomicInteger invocations = new AtomicInteger();
        Runnable service = invocations::incrementAndGet;
        Dictionary<String, Object> properties = new MapDictionary<>();
        properties.put("role", "worker");

        ServiceRegistration<?> registration = context.registerService(Runnable.class.getName(), service, properties);
        try {
            ServiceReference<?> reference = context.getServiceReference(Runnable.class.getName());
            assertNotNull(reference);
            assertEquals("worker", reference.getProperty("role"));
            assertArrayEquals(new String[]{Runnable.class.getName()},
                    (String[]) reference.getProperty(Constants.OBJECTCLASS));
            assertSame(bundle, reference.getBundle());
            assertEquals(1, context.getServiceReferences(Runnable.class.getName(), "(role=worker)").length);
            assertSame(service, context.getService(reference));
            ((Runnable) context.getService(reference)).run();
            assertEquals(1, invocations.get());
        } finally {
            registration.unregister();
        }
        assertNull(context.getServiceReference(Runnable.class.getName()));
    }
}
