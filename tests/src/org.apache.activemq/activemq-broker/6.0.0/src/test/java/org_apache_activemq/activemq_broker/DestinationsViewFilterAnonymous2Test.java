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
    void sortsDestinationViewsThroughBrokerQueryApi() throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName("destination-query-broker");
        brokerService.setPersistent(false);
        brokerService.setUseJmx(true);
        brokerService.getManagementContext().setCreateConnector(false);
        brokerService.setUseShutdownHook(false);

        try {
            brokerService.start();
            BrokerView brokerView = brokerService.getAdminView();
            brokerView.addQueue("alpha");
            brokerView.addQueue("zeta");

            String result = brokerView.queryQueues(
                    "{\"filter\":\"\",\"sortColumn\":\"name\",\"sortOrder\":\"desc\"}", 1, 1);

            assertThat(result).contains("destinationName=zeta").doesNotContain("destinationName=alpha");
        } finally {
            brokerService.stop();
        }
    }
}
