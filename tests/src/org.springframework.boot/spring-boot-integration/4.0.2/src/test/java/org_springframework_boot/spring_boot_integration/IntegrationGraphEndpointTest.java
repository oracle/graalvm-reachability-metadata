/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_integration;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.integration.actuate.endpoint.IntegrationGraphEndpoint;
import org.springframework.boot.integration.actuate.endpoint.IntegrationGraphEndpoint.GraphDescriptor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.graph.IntegrationGraphServer;
import org.springframework.integration.graph.IntegrationNode;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class IntegrationGraphEndpointTest {

    @Test
    void describesApplicationChannels() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("application", Map.of("spring.application.name", "order-routing")));
            context.registerBean("incomingOrders", DirectChannel.class, () -> new DirectChannel());
            context.registerBean("pendingOrders", QueueChannel.class, () -> new QueueChannel());
            context.registerBean(IntegrationGraphServer.class, IntegrationGraphServer::new);
            context.refresh();

            IntegrationGraphEndpoint endpoint = new IntegrationGraphEndpoint(context.getBean(IntegrationGraphServer.class));
            GraphDescriptor graph = endpoint.graph();

            assertThat(graph.getContentDescriptor()).containsEntry("name", "order-routing")
                    .containsEntry("provider", "spring-integration");
            assertThat(graph.getNodes()).extracting(IntegrationNode::getName)
                    .containsExactlyInAnyOrder("incomingOrders", "pendingOrders");
            assertThat(graph.getNodes()).extracting(IntegrationNode::getComponentType)
                    .containsExactly("channel", "channel");
            assertThat(graph.getLinks()).isEmpty();
        }
    }

    @Test
    void rebuildsTheCachedGraphWhenApplicationChannelsChange() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("orders", QueueChannel.class, () -> new QueueChannel());
            context.registerBean(IntegrationGraphServer.class, IntegrationGraphServer::new);
            context.refresh();

            IntegrationGraphEndpoint endpoint = new IntegrationGraphEndpoint(context.getBean(IntegrationGraphServer.class));
            GraphDescriptor original = endpoint.graph();
            assertThat(original.getNodes()).extracting(IntegrationNode::getName).containsExactly("orders");

            context.getBeanFactory().registerSingleton("completedOrders", new QueueChannel());
            assertThat(endpoint.graph().getNodes()).extracting(IntegrationNode::getName).containsExactly("orders");

            endpoint.rebuild();
            assertThat(endpoint.graph().getNodes()).extracting(IntegrationNode::getName)
                    .containsExactlyInAnyOrder("orders", "completedOrders");
            assertThat(original.getNodes()).extracting(IntegrationNode::getName).containsExactly("orders");

            context.removeBeanDefinition("orders");
            endpoint.rebuild();
            assertThat(endpoint.graph().getNodes()).extracting(IntegrationNode::getName).containsExactly("completedOrders");
        }
    }
}
