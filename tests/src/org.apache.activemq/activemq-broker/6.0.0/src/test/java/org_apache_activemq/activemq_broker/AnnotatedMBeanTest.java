/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;

import org.apache.activemq.broker.jmx.AnnotatedMBean;
import org.apache.activemq.broker.jmx.MBeanInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

public class AnnotatedMBeanTest {

    @Test
    @ResourceLock("activemq-audit-configuration")
    void describesAndInvokesOperationWithReferenceParameter() throws Exception {
        String previousAuditSetting = System.setProperty("org.apache.activemq.audit", "entry");
        try {
            Echo implementation = new Echo();
            AnnotatedMBean mBean = new AnnotatedMBean(
                    implementation,
                    EchoMBean.class,
                    new ObjectName("org.example:type=Echo,name=standard"));

            MBeanOperationInfo operation = mBean.getMBeanInfo().getOperations()[0];
            Object result = mBean.invoke(
                    "echo", new Object[] {"message"}, new String[] {String.class.getName()});

            assertThat(operation.getDescription()).isEqualTo("Echoes a value");
            assertThat(operation.getSignature()[0].getName()).isEqualTo("value");
            assertThat(result).isEqualTo("echo:message");
        } finally {
            restoreProperty("org.apache.activemq.audit", previousAuditSetting);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public interface EchoMBean {
        @MBeanInfo("Echoes a value")
        String echo(@MBeanInfo("value") String value);
    }

    public static final class Echo implements EchoMBean {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }
}
