/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_jmx;

import javax.management.MBeanServerFactory;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationMBeanExporterTest {

    @Test
    void skipsAnonymousHandlerWrapperAroundMessageHandler() {
        MessageHandler delegate = message -> { };
        AbstractMessageHandler anonymousWrapper = new AbstractMessageHandler() {

            @Override
            protected void handleMessageInternal(Message<?> message) {
                delegate.handleMessage(message);
            }

        };

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            context.getBeanFactory().registerSingleton("anonymousWrapper", anonymousWrapper);

            IntegrationMBeanExporter exporter = new IntegrationMBeanExporter();
            exporter.setServer(MBeanServerFactory.newMBeanServer());
            exporter.setBeanFactory(context.getBeanFactory());
            exporter.setApplicationContext(context);
            exporter.afterSingletonsInstantiated();

            assertThat(anonymousWrapper.getClass().isAnonymousClass()).isTrue();
            assertThat(exporter.getHandlerCount()).isZero();
        }
    }

}
