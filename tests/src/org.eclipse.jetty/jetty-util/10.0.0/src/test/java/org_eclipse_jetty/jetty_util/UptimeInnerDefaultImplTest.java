/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.Uptime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UptimeInnerDefaultImplTest {
    @Test
    void reportsNonnegativeMonotonicRuntimeUptime() {
        Uptime.DefaultImpl implementation = new Uptime.DefaultImpl();
        long first = implementation.getUptime();
        long second = implementation.getUptime();
        assertTrue(first >= 0);
        assertTrue(second >= first);
        assertTrue(Uptime.getUptime() >= 0);
    }
}
