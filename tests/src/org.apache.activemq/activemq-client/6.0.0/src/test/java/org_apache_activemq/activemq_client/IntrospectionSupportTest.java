/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.util.IntrospectionSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionSupportTest {

    @Test
    void readsBeanPropertiesAndDiagnosticFields() {
        ActiveMQPrefetchPolicy policy = new ActiveMQPrefetchPolicy();
        policy.setQueuePrefetch(37);
        Map<String, String> properties = new LinkedHashMap<>();

        assertThat(IntrospectionSupport.getProperties(policy, properties, "policy.")).isTrue();
        assertThat(properties).containsEntry("policy.queuePrefetch", "37");
        assertThat(IntrospectionSupport.findGetterMethod(ActiveMQPrefetchPolicy.class, "queuePrefetch").getName())
                .isEqualTo("getQueuePrefetch");
        assertThat(IntrospectionSupport.toString(policy, Object.class))
                .contains("queuePrefetch = 37");
    }
}
