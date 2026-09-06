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
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMBeanTest {

    @Test
    void invokesAuditedMBeanOperationWithDeclaredSignature() throws Exception {
        Log4JConfigView implementation = new Log4JConfigView();
        AnnotatedMBean mBean = new AnnotatedMBean(
                implementation,
                Log4JConfigViewMBean.class,
                new ObjectName("org.apache.activemq:type=Log4JConfig"));
        String originalRootLevel = implementation.getRootLogLevel();

        try {
            Object result = mBean.invoke(
                    "setRootLogLevel",
                    new Object[]{"INFO"},
                    new String[]{String.class.getName()});

            assertThat(result).isNull();
            assertThat(implementation.getRootLogLevel()).isEqualTo("INFO");
        } finally {
            implementation.setRootLogLevel(originalRootLevel);
        }
    }
}
