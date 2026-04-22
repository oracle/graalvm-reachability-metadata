/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.Uptime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UptimeDefaultImplCoverageTest {
    @Test
    void uptimeDefaultImplUsesManagementFactoryReflectively() {
        Uptime.DefaultImpl defaultImpl = new Uptime.DefaultImpl();

        assertThat(defaultImpl.getUptime()).isGreaterThanOrEqualTo(0L);
    }
}
