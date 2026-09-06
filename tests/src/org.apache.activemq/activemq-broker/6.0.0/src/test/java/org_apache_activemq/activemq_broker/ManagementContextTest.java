/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.ManagementContext;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagementContextTest {

    @Test
    void createsMBeanServer() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(false);

        try {
            MBeanServer server = context.getMBeanServer();

            assertThat(server).isNotNull();
        } finally {
            context.stop();
        }
    }
}
