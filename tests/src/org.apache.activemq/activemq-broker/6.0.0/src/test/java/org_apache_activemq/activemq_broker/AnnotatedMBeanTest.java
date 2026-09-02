/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.ObjectName;

import org.apache.activemq.broker.jmx.AnnotatedMBean;
import org.junit.jupiter.api.Test;

public class AnnotatedMBeanTest {

    @Test
    void invokesAuditedOperationWithReferenceParameter() throws Exception {
        String propertyName = "org.apache.activemq.audit";
        String previousValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "entry");

        try {
            Echo implementation = new Echo();
            AnnotatedMBean mBean = new AnnotatedMBean(
                    implementation,
                    EchoMBean.class,
                    new ObjectName("org.example:type=Echo,name=audited"));

            Object result = mBean.invoke(
                    "echo", new Object[] {"message"}, new String[] {String.class.getName()});

            assertThat(result).isEqualTo("echo:message");
        } finally {
            restoreProperty(propertyName, previousValue);
        }
    }

    private static void restoreProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }

    public interface EchoMBean {
        String echo(String value);
    }

    public static final class Echo implements EchoMBean {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }
}
