/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.AnnotatedMBean;
import org.apache.activemq.broker.jmx.Log4JConfigView;
import org.apache.activemq.broker.jmx.Log4JConfigViewMBean;
import org.apache.activemq.broker.util.AuditLog;
import org.apache.activemq.broker.util.AuditLogService;
import org.apache.activemq.broker.util.DefaultAuditLogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import javax.management.ObjectName;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class AnnotatedMBeanTest {

    @Test
    void auditsManagementOperationsWithStringParameters() throws Exception {
        List<String> operations = new ArrayList<>();
        AuditLog log = entry -> operations.add(entry.getOperation());
        AuditLogService audit = AuditLogService.getAuditLog();
        audit.setFactory(() -> List.of(log));
        Log4JConfigView view = new Log4JConfigView();
        String logger = "reachability.broker.management";
        String originalLevel = view.getLogLevel(logger);
        try {
            AnnotatedMBean mbean = new AnnotatedMBean(view, Log4JConfigViewMBean.class,
                    new ObjectName("org.apache.activemq:type=Logging,brokerName=audit-test"));
            mbean.invoke("setLogLevel", new Object[]{logger, "WARN"},
                    new String[]{String.class.getName(), String.class.getName()});
            assertThat(view.getLogLevel(logger)).isEqualTo("WARN");
            Object level = mbean.invoke("getLogLevel", new Object[]{logger},
                    new String[]{String.class.getName()});
            assertThat(level).isEqualTo("WARN");
            assertThat(operations).contains(
                    Log4JConfigView.class.getName() + ".setLogLevel",
                    Log4JConfigView.class.getName() + ".getLogLevel");
        } finally {
            audit.setFactory(new DefaultAuditLogFactory());
            view.setLogLevel(logger, originalLevel);
        }
    }
}
