/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.spring.ServiceBean;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.StaticApplicationContext;

public class ServiceBeanTest {
    @BeforeAll
    static void installStaticallyRepresentableAdaptiveExtensions() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
    }

    @Test
    void registersAsAnApplicationListener() {
        StaticApplicationContext context = new StaticApplicationContext();
        RecordingServiceBean service = new RecordingServiceBean();
        RecordingEvent event = new RecordingEvent(this);

        try {
            service.setApplicationContext(context);
            context.refresh();
            context.publishEvent(event);

            assertThat(ServiceBean.getSpringContext()).isSameAs(context);
            assertThat(service.getLastEvent()).isSameAs(event);
        } finally {
            SpringExtensionFactory.removeApplicationContext(context);
            context.close();
        }
    }

    @Test
    void registersAsAnApplicationListenerForNonPublicContext() {
        NonPublicListenerApplicationContext context =
                new NonPublicListenerApplicationContext();
        context.refresh();
        RecordingServiceBean service = new RecordingServiceBean();
        RecordingEvent event = new RecordingEvent(this);

        try {
            service.setApplicationContext(context);
            context.publishEvent(event);

            assertThat(service.getLastEvent()).isSameAs(event);
        } finally {
            SpringExtensionFactory.removeApplicationContext(context);
            context.close();
        }
    }

    public static class RecordingServiceBean extends ServiceBean<Object> {
        private ApplicationEvent lastEvent;

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            lastEvent = event;
        }

        public ApplicationEvent getLastEvent() {
            return lastEvent;
        }
    }

    private static class NonPublicListenerApplicationContext
            extends StaticApplicationContext {
        @Override
        public void addApplicationListener(ApplicationListener listener) {
            super.addApplicationListener(listener);
        }
    }

    public static class RecordingEvent extends ApplicationEvent {
        public RecordingEvent(Object source) {
            super(source);
        }
    }
}
