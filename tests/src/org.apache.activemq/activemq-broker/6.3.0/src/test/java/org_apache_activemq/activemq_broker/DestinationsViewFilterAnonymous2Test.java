/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class DestinationsViewFilterAnonymous2Test {

    @Test
    void sortsBeforePagingDestinationViews() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName("destination-query-broker");
        broker.setPersistent(false);
        broker.setUseJmx(true);
        broker.setUseShutdownHook(false);
        broker.getManagementContext().setCreateConnector(false);
        broker.setDestinations(new ActiveMQDestination[]{
                new ActiveMQQueue("alpha.orders"), new ActiveMQQueue("zeta.orders")
        });
        try {
            broker.start();
            String ascending = broker.getAdminView().queryQueues(
                    """
                    {"filter":"","sortColumn":"name","sortOrder":"asc"}
                    """, 1, 1);
            assertThat(ascending).contains("alpha.orders", "\"count\":2").doesNotContain("zeta.orders");
            String descending = broker.getAdminView().queryQueues(
                    """
                    {"filter":"","sortColumn":"name","sortOrder":"desc"}
                    """, 1, 1);
            assertThat(descending).contains("zeta.orders", "\"count\":2").doesNotContain("alpha.orders");
        } finally {
            broker.stop();
        }
    }
}
