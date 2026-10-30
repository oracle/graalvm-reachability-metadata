/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import org.apache.aries.unittest.mocks.MethodCall;
import org.apache.aries.unittest.mocks.Skeleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultInvocationHandlerTest {
    @Test
    void constructsFreshDefaultValuesForConcreteReturnTypes() {
        Factory factory = Skeleton.newMock(Factory.class);

        Payload first = factory.createPayload();
        Payload second = factory.createPayload();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("ready", first.status());
        assertEquals("ready", second.status());
        assertNotSame(first, second);
    }

    @Test
    void returnsNestedInterfaceMocksBackedByTheSameSkeleton() {
        Factory factory = Skeleton.newMock(Factory.class);
        Skeleton skeleton = Skeleton.getSkeleton(factory);
        skeleton.setReturnValue(new MethodCall(Child.class, "describe"), "nested result");

        Child child = factory.createChild();

        assertSame(skeleton, Skeleton.getSkeleton(child));
        assertEquals("nested result", child.describe());
        skeleton.assertCalled(new MethodCall(Factory.class, "createChild"));
        skeleton.assertCalled(new MethodCall(Child.class, "describe"));
    }

    public interface Factory {
        Payload createPayload();

        Child createChild();
    }

    public interface Child {
        String describe();
    }

    public static class Payload {
        public Payload() {
        }

        public String status() {
            return "ready";
        }
    }
}
