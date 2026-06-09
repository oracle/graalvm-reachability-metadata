/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.MethodInvocation;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.jupiter.api.Test;

public class BeanInfoTest {
    @Test
    void introspectsPublicBeanDeclaredAndInterfaceMethods() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            BeanInfo beanInfo = new BeanInfo(context, PublicBindingBean.class);

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(Exchange.BEAN_METHOD_NAME, "fromHeader");
            exchange.getIn().setHeader("subject", "Camel");

            MethodInvocation invocation = beanInfo.createInvocation(new PublicBindingBean(), exchange);

            assertThat(beanInfo.hasMethod("fromHeader")).isTrue();
            assertThat(beanInfo.hasMethod("interfaceGreeting")).isTrue();
            assertThat(invocation).isNotNull();
            assertThat(invocation.getMethod().getName()).isEqualTo("fromHeader");
            assertThat(invocation.getArguments()).containsExactly("Camel");
        } finally {
            context.stop();
        }
    }

    @Test
    void introspectsNonPublicBeanDeclaredMethods() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            BeanInfo beanInfo = new BeanInfo(context, NonPublicBindingBean.class);

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(Exchange.BEAN_METHOD_NAME, "implementationOnly");
            exchange.getIn().setBody("Camel");

            MethodInvocation invocation = beanInfo.createInvocation(new NonPublicBindingBean(), exchange);

            assertThat(beanInfo.hasMethod("implementationOnly")).isTrue();
            assertThat(beanInfo.hasMethod("contractGreeting")).isTrue();
            assertThat(invocation).isNotNull();
            assertThat(invocation.getMethod().getName()).isEqualTo("implementationOnly");
            assertThat(invocation.getArguments()).containsExactly("Camel");
        } finally {
            context.stop();
        }
    }

    @Test
    void createsInvocationForSupportedGetClassMethodName() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            PublicBindingBean bean = new PublicBindingBean();
            BeanInfo beanInfo = new BeanInfo(context, PublicBindingBean.class);
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(Exchange.BEAN_METHOD_NAME, "getClass");

            MethodInvocation invocation = beanInfo.createInvocation(bean, exchange);

            assertThat(invocation).isNotNull();
            assertThat(invocation.getMethod().getName()).isEqualTo("getClass");
            assertThat(invocation.getThis()).isSameAs(bean);
        } finally {
            context.stop();
        }
    }

    public interface PublicBindingContract {
        String interfaceGreeting(@Header("name") String name);
    }

    public static class PublicBindingBean implements PublicBindingContract {
        @Override
        public String interfaceGreeting(String name) {
            return "Hello " + name;
        }

        public String fromHeader(@Header("subject") String subject) {
            return "Header " + subject;
        }
    }

    interface NonPublicBindingContract {
        String contractGreeting(@Header("name") String name);
    }

    static class NonPublicBindingBean implements NonPublicBindingContract {
        @Override
        public String contractGreeting(String name) {
            return "Hello " + name;
        }

        public String implementationOnly(@Body String body) {
            return "Body " + body;
        }
    }
}
