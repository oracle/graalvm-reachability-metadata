/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbit;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.RabbitListenerAnnotationBeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitListenerAnnotationBeanPostProcessorTest {

    @Test
    void processesRabbitListenerMethodOnJdkProxy() {
        RabbitListenerAnnotationBeanPostProcessor processor = new RabbitListenerAnnotationBeanPostProcessor();
        processor.setBeanClassLoader(getClass().getClassLoader());
        processor.setBeanFactory(new DefaultListableBeanFactory());

        ListenerService listener = createJdkProxy(new AnnotatedListenerService());
        assertThat(AopUtils.isJdkDynamicProxy(listener)).isTrue();
        assertThat(AopUtils.getTargetClass(listener)).isEqualTo(AnnotatedListenerService.class);

        Object processed = processor.postProcessAfterInitialization(listener, "listenerService");

        assertThat(processed).isSameAs(listener);
    }

    private static ListenerService createJdkProxy(ListenerService target) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(ListenerService.class);
        proxyFactory.setProxyTargetClass(false);
        return (ListenerService) proxyFactory.getProxy();
    }

    public interface ListenerService {

        void handle(String payload);

    }

    public static final class AnnotatedListenerService implements ListenerService {

        @Override
        @RabbitListener(id = "coverageListener", queues = "coverage.queue")
        public void handle(String payload) {
            assertThat(payload).isNotNull();
        }

    }

}
