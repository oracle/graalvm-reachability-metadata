/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_amqp;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.client.AmqpClient;
import org.springframework.amqp.client.AmqpConnectionFactory;
import org.springframework.amqp.client.SingleAmqpConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.AmqpAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.AmqpClientCustomizer;
import org.springframework.boot.amqp.autoconfigure.AmqpConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.AmqpProperties;
import org.springframework.boot.amqp.autoconfigure.ConnectionOptionsCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_amqpTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AmqpAutoConfiguration.class));

    @Test
    void amqpPropertiesExposeConnectionAndClientSettings() {
        AmqpProperties properties = new AmqpProperties();

        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(5672);
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getClient().getDefaultToAddress()).isNull();
        assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofSeconds(60));

        properties.setHost("broker.example.test");
        properties.setPort(5678);
        properties.setUsername("alice");
        properties.setPassword("secret");
        properties.getClient().setDefaultToAddress("orders.created");
        properties.getClient().setCompletionTimeout(Duration.ofMillis(750));

        assertThat(properties.getHost()).isEqualTo("broker.example.test");
        assertThat(properties.getPort()).isEqualTo(5678);
        assertThat(properties.getUsername()).isEqualTo("alice");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(properties.getClient().getDefaultToAddress()).isEqualTo("orders.created");
        assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void amqpConnectionDetailsAddressRecordAndDefaultsAreUsable() {
        AmqpConnectionDetails.Address address = new AmqpConnectionDetails.Address("broker.example.test", 5679);
        AmqpConnectionDetails details = () -> address;

        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.getAddress().host()).isEqualTo("broker.example.test");
        assertThat(details.getAddress().port()).isEqualTo(5679);
        assertThat(details.getUsername()).isNull();
        assertThat(details.getPassword()).isNull();

        AmqpConnectionDetails authenticatedDetails = new AmqpConnectionDetails() {

            @Override
            public AmqpConnectionDetails.Address getAddress() {
                return address;
            }

            @Override
            public String getUsername() {
                return "alice";
            }

            @Override
            public String getPassword() {
                return "secret";
            }

        };

        assertThat(authenticatedDetails.getAddress()).isSameAs(address);
        assertThat(authenticatedDetails.getUsername()).isEqualTo("alice");
        assertThat(authenticatedDetails.getPassword()).isEqualTo("secret");
    }

    @Test
    void autoConfigurationCreatesConnectionFactoryClientAndAppliesCustomizers() {
        AtomicBoolean connectionOptionsCustomizerInvoked = new AtomicBoolean();
        AtomicBoolean clientCustomizerInvoked = new AtomicBoolean();

        this.contextRunner
                .withPropertyValues("spring.amqp.host=broker.example.test", "spring.amqp.port=5679",
                        "spring.amqp.username=alice", "spring.amqp.password=secret",
                        "spring.amqp.client.default-to-address=orders.created",
                        "spring.amqp.client.completion-timeout=750ms")
                .withBean(ConnectionOptionsCustomizer.class,
                        () -> (connectionOptions) -> connectionOptionsCustomizerInvoked.set(true))
                .withBean(AmqpClientCustomizer.class, () -> (builder) -> clientCustomizerInvoked.set(true))
                .run((context) -> {
                    assertThat(context).hasSingleBean(AmqpProperties.class);
                    assertThat(context).hasSingleBean(AmqpConnectionDetails.class);
                    assertThat(context).hasSingleBean(AmqpConnectionFactory.class);
                    assertThat(context).hasSingleBean(AmqpClient.class);

                    AmqpProperties properties = context.getBean(AmqpProperties.class);
                    assertThat(properties.getHost()).isEqualTo("broker.example.test");
                    assertThat(properties.getPort()).isEqualTo(5679);
                    assertThat(properties.getUsername()).isEqualTo("alice");
                    assertThat(properties.getPassword()).isEqualTo("secret");
                    assertThat(properties.getClient().getDefaultToAddress()).isEqualTo("orders.created");
                    assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofMillis(750));

                    AmqpConnectionDetails connectionDetails = context.getBean(AmqpConnectionDetails.class);
                    assertThat(connectionDetails.getAddress().host()).isEqualTo("broker.example.test");
                    assertThat(connectionDetails.getAddress().port()).isEqualTo(5679);
                    assertThat(connectionDetails.getUsername()).isEqualTo("alice");
                    assertThat(connectionDetails.getPassword()).isEqualTo("secret");

                    assertThat(context.getBean(AmqpConnectionFactory.class))
                            .isInstanceOf(SingleAmqpConnectionFactory.class);
                    assertThat(context.getBean(AmqpClient.class)).isNotNull();
                    assertThat(connectionOptionsCustomizerInvoked).isTrue();
                    assertThat(clientCustomizerInvoked).isTrue();
                });
    }

}
