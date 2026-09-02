/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_amqp;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_amqpTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class));

    @Test
    void rabbitPropertiesExposeConnectionAndTemplateSettings() {
        RabbitProperties properties = new RabbitProperties();

        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isNull();
        assertThat(properties.determinePort()).isEqualTo(5672);
        assertThat(properties.getUsername()).isEqualTo("guest");
        assertThat(properties.getPassword()).isEqualTo("guest");
        assertThat(properties.getTemplate().getRoutingKey()).isEmpty();
        assertThat(properties.getTemplate().getReplyTimeout()).isNull();

        properties.setHost("broker.example.test");
        properties.setPort(5678);
        properties.setUsername("alice");
        properties.setPassword("secret");
        properties.getTemplate().setRoutingKey("orders.created");
        properties.getTemplate().setReplyTimeout(Duration.ofMillis(750));

        assertThat(properties.getHost()).isEqualTo("broker.example.test");
        assertThat(properties.getPort()).isEqualTo(5678);
        assertThat(properties.determinePort()).isEqualTo(5678);
        assertThat(properties.getUsername()).isEqualTo("alice");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(properties.getTemplate().getRoutingKey()).isEqualTo("orders.created");
        assertThat(properties.getTemplate().getReplyTimeout()).isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void rabbitConnectionDetailsAddressRecordAndDefaultsAreUsable() {
        RabbitConnectionDetails.Address address = new RabbitConnectionDetails.Address("broker.example.test", 5679);
        RabbitConnectionDetails details = () -> List.of(address);

        assertThat(details.getAddresses()).containsExactly(address);
        assertThat(details.getFirstAddress()).isEqualTo(address);
        assertThat(details.getFirstAddress().host()).isEqualTo("broker.example.test");
        assertThat(details.getFirstAddress().port()).isEqualTo(5679);
        assertThat(details.getUsername()).isNull();
        assertThat(details.getPassword()).isNull();
        assertThat(details.getVirtualHost()).isNull();

        RabbitConnectionDetails authenticatedDetails = new RabbitConnectionDetails() {

            @Override
            public List<RabbitConnectionDetails.Address> getAddresses() {
                return List.of(address);
            }

            @Override
            public String getUsername() {
                return "alice";
            }

            @Override
            public String getPassword() {
                return "secret";
            }

            @Override
            public String getVirtualHost() {
                return "orders";
            }

        };

        assertThat(authenticatedDetails.getFirstAddress()).isSameAs(address);
        assertThat(authenticatedDetails.getUsername()).isEqualTo("alice");
        assertThat(authenticatedDetails.getPassword()).isEqualTo("secret");
        assertThat(authenticatedDetails.getVirtualHost()).isEqualTo("orders");
    }

    @Test
    void autoConfigurationCreatesConnectionFactoryTemplateAndAppliesCustomizers() {
        AtomicBoolean connectionFactoryCustomizerInvoked = new AtomicBoolean();
        AtomicBoolean templateCustomizerInvoked = new AtomicBoolean();

        this.contextRunner
                .withPropertyValues("spring.rabbitmq.host=broker.example.test", "spring.rabbitmq.port=5679",
                        "spring.rabbitmq.username=alice", "spring.rabbitmq.password=secret",
                        "spring.rabbitmq.template.routing-key=orders.created",
                        "spring.rabbitmq.template.reply-timeout=750ms")
                .withBean(ConnectionFactoryCustomizer.class,
                        () -> (connectionFactory) -> connectionFactoryCustomizerInvoked.set(true))
                .withBean(RabbitTemplateCustomizer.class, () -> (template) -> templateCustomizerInvoked.set(true))
                .run((context) -> {
                    assertThat(context).hasSingleBean(RabbitProperties.class);
                    assertThat(context).hasSingleBean(RabbitConnectionDetails.class);
                    assertThat(context).hasSingleBean(ConnectionFactory.class);
                    assertThat(context).hasSingleBean(RabbitTemplate.class);

                    RabbitProperties properties = context.getBean(RabbitProperties.class);
                    assertThat(properties.getHost()).isEqualTo("broker.example.test");
                    assertThat(properties.getPort()).isEqualTo(5679);
                    assertThat(properties.getUsername()).isEqualTo("alice");
                    assertThat(properties.getPassword()).isEqualTo("secret");
                    assertThat(properties.getTemplate().getRoutingKey()).isEqualTo("orders.created");
                    assertThat(properties.getTemplate().getReplyTimeout()).isEqualTo(Duration.ofMillis(750));

                    RabbitConnectionDetails connectionDetails = context.getBean(RabbitConnectionDetails.class);
                    assertThat(connectionDetails.getFirstAddress().host()).isEqualTo("broker.example.test");
                    assertThat(connectionDetails.getFirstAddress().port()).isEqualTo(5679);
                    assertThat(connectionDetails.getUsername()).isEqualTo("alice");
                    assertThat(connectionDetails.getPassword()).isEqualTo("secret");

                    assertThat(context.getBean(ConnectionFactory.class)).isInstanceOf(CachingConnectionFactory.class);
                    assertThat(context.getBean(RabbitTemplate.class)).isNotNull();
                    assertThat(connectionFactoryCustomizerInvoked).isTrue();
                    assertThat(templateCustomizerInvoked).isTrue();
                });
    }

}
