/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.BrokerView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DestinationsViewFilterAnonymous2Test {

    @Test
    @Timeout(30)
    void sortsAndPagesDestinationViewsByGetterValue() throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName("destination-view-filter-broker");
        brokerService.setPersistent(false);
        brokerService.setUseJmx(true);
        brokerService.setUseShutdownHook(false);
        brokerService.getManagementContext().setCreateConnector(false);

        try {
            brokerService.start();
            BrokerView adminView = brokerService.getAdminView();
            adminView.addQueue("alpha.orders");
            adminView.addQueue("zulu.orders");

            String result = adminView.queryQueues(
                    """
                    {"filter":"","sortColumn":"name","sortOrder":"desc"}
                    """,
                    1,
                    1);

            assertThat(result).contains("\"count\":2", "zulu.orders");
            assertThat(result).doesNotContain("alpha.orders");
        } finally {
            brokerService.stop();
        }
    }
}
