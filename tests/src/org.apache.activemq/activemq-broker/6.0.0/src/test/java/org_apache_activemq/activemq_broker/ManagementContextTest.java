/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.apache.activemq.broker.jmx.ManagementContext;
import org.junit.jupiter.api.Test;

public class ManagementContextTest {

    @Test
    void locatesPlatformMBeanServer() {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(false);

        MBeanServer mBeanServer = context.findTigerMBeanServer();

        assertThat(mBeanServer).isSameAs(ManagementFactory.getPlatformMBeanServer());
    }
}
