/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbit;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.MultiRabbitListenerAnnotationBeanPostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.RabbitListenerConfigUtils;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiRabbitListenerAnnotationBeanPostProcessorInnerRabbitListenerAdminReplacementInvocationHandlerTest {

    @Test
    void delegatesNonAdminAnnotationMethodsToOriginalRabbitListenerAnnotation() {
        CapturingMultiRabbitListenerAnnotationBeanPostProcessor processor =
                new CapturingMultiRabbitListenerAnnotationBeanPostProcessor();
        processor.setBeanClassLoader(getClass().getClassLoader());

        ListenerBean listenerBean = new ListenerBean();
        Object processed = processor.postProcessAfterInitialization(listenerBean, "listenerBean");

        assertThat(processed).isSameAs(listenerBean);
        assertThat(processor.listenerId).isEqualTo("replacementInvocationHandlerListener");
        assertThat(processor.queueNames).containsExactly("replacement.invocation.handler.queue");
        assertThat(processor.adminSeenByProcessListener).isEqualTo(RabbitListenerConfigUtils.RABBIT_ADMIN_BEAN_NAME);
        assertThat(processor.declarable.getDeclaringAdmins()).hasSize(1);
        assertThat(processor.declarable.getDeclaringAdmins().iterator().next())
                .isEqualTo(RabbitListenerConfigUtils.RABBIT_ADMIN_BEAN_NAME);
    }

    public static final class ListenerBean {

        @RabbitListener(id = "replacementInvocationHandlerListener", queues = "replacement.invocation.handler.queue")
        public void handle(String payload) {
            assertThat(payload).isNotNull();
        }

    }

    private static final class CapturingMultiRabbitListenerAnnotationBeanPostProcessor
            extends MultiRabbitListenerAnnotationBeanPostProcessor {

        private final Queue declarable = new Queue("replacement.invocation.handler.declarable");

        private String listenerId;

        private String[] queueNames = new String[0];

        private String adminSeenByProcessListener;

        @Override
        protected Collection<Declarable> processListener(MethodRabbitListenerEndpoint endpoint,
                RabbitListener rabbitListener, Object bean, Object target, String beanName) {

            this.listenerId = rabbitListener.id();
            this.queueNames = rabbitListener.queues();
            this.adminSeenByProcessListener = rabbitListener.admin();
            return List.of(this.declarable);
        }

    }

}
