/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.async.AsyncQueueFullPolicy;
import org.apache.logging.log4j.core.async.AsyncQueueFullPolicyFactory;
import org.apache.logging.log4j.core.async.EventRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class AsyncQueueFullPolicyFactoryTest {
    @Test
    void createsAndUsesTheConfiguredQueueFullPolicy() {
        AsyncQueueFullPolicy policy = TestSupport.withProperty("log4j2.AsyncQueueFullPolicy",
                ApplicationPolicy.class.getName(), AsyncQueueFullPolicyFactory::create);
        assertInstanceOf(ApplicationPolicy.class, policy);
        assertEquals(EventRoute.DISCARD, policy.getRoute(1, Level.DEBUG));
        assertEquals(EventRoute.SYNCHRONOUS, policy.getRoute(1, Level.ERROR));
    }

    public static class ApplicationPolicy implements AsyncQueueFullPolicy {
        @Override
        public EventRoute getRoute(long backgroundThreadId, Level level) {
            return level.isMoreSpecificThan(Level.WARN) ? EventRoute.SYNCHRONOUS : EventRoute.DISCARD;
        }
    }
}
