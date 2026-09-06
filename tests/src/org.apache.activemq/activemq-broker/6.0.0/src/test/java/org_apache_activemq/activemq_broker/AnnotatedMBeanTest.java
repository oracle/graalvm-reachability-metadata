/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.AnnotatedMBean;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMBeanTest {

    @Test
    void invokesMBeanOperationWithDeclaredSignature() throws Exception {
        AnnotatedMBean mBean = new AnnotatedMBean(
                new GreetingView(),
                GreetingViewMBean.class,
                new ObjectName("org.apache.activemq:type=Greeting"));

        Object result = mBean.invoke(
                "greet",
                new Object[]{"ActiveMQ"},
                new String[]{String.class.getName()});

        assertThat(result).isEqualTo("Hello, ActiveMQ");
    }

    public interface GreetingViewMBean {

        String greet(String name);
    }

    public static class GreetingView implements GreetingViewMBean {

        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
