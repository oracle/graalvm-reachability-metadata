/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_amqp;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.protonj2.client.ConnectionOptions;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.client.AmqpClient;
import org.springframework.amqp.client.AmqpConnectionFactory;
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
    void amqpPropertiesExposeBrokerAndClientSettings() {
        AmqpProperties properties = new AmqpProperties();

        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(5672);
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofSeconds(60));

        properties.setHost("broker.example.test");
        properties.setPort(5678);
        properties.setUsername("orders-user");
        properties.setPassword("orders-password");
        properties.getClient().setDefaultToAddress("orders.created");
        properties.getClient().setCompletionTimeout(Duration.ofMillis(250));

        assertThat(properties.getHost()).isEqualTo("broker.example.test");
        assertThat(properties.getPort()).isEqualTo(5678);
        assertThat(properties.getUsername()).isEqualTo("orders-user");
        assertThat(properties.getPassword()).isEqualTo("orders-password");
        assertThat(properties.getClient().getDefaultToAddress()).isEqualTo("orders.created");
        assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void amqpConnectionDetailsAddressRecordAndDefaultsAreUsable() {
        AmqpConnectionDetails.Address address = new AmqpConnectionDetails.Address("details.example.test", 6001);
        AmqpConnectionDetails details = () -> address;

        assertThat(details.getAddress()).isSameAs(address);
        assertThat(details.getUsername()).isNull();
        assertThat(details.getPassword()).isNull();
        assertThat(address.host()).isEqualTo("details.example.test");
        assertThat(address.port()).isEqualTo(6001);
    }

    @Test
    void autoConfigurationCreatesConnectionFactoryAndClientFromProperties() {
        AtomicBoolean connectionOptionsCustomizerInvoked = new AtomicBoolean();
        AtomicBoolean clientCustomizerInvoked = new AtomicBoolean();

        this.contextRunner
                .withPropertyValues("spring.amqp.host=broker.example.test", "spring.amqp.port=5678",
                        "spring.amqp.username=orders-user", "spring.amqp.password=orders-password",
                        "spring.amqp.client.default-to-address=orders.created",
                        "spring.amqp.client.completion-timeout=250ms")
                .withBean(ConnectionOptionsCustomizer.class,
                        () -> (connectionOptions) -> customizeConnectionOptions(connectionOptions,
                                connectionOptionsCustomizerInvoked))
                .withBean(AmqpClientCustomizer.class, () -> (builder) -> {
                    clientCustomizerInvoked.set(true);
                    builder.completionTimeout(Duration.ofMillis(125));
                }).run((context) -> {
                    assertThat(context).hasSingleBean(AmqpProperties.class);
                    assertThat(context).hasSingleBean(AmqpConnectionDetails.class);
                    assertThat(context).hasSingleBean(AmqpConnectionFactory.class);
                    assertThat(context).hasSingleBean(AmqpClient.class);

                    AmqpProperties properties = context.getBean(AmqpProperties.class);
                    assertThat(properties.getHost()).isEqualTo("broker.example.test");
                    assertThat(properties.getPort()).isEqualTo(5678);
                    assertThat(properties.getUsername()).isEqualTo("orders-user");
                    assertThat(properties.getPassword()).isEqualTo("orders-password");
                    assertThat(properties.getClient().getDefaultToAddress()).isEqualTo("orders.created");
                    assertThat(properties.getClient().getCompletionTimeout()).isEqualTo(Duration.ofMillis(250));

                    AmqpConnectionDetails details = context.getBean(AmqpConnectionDetails.class);
                    assertThat(details.getAddress())
                            .isEqualTo(new AmqpConnectionDetails.Address("broker.example.test", 5678));
                    assertThat(details.getUsername()).isEqualTo("orders-user");
                    assertThat(details.getPassword()).isEqualTo("orders-password");
                    assertThat(connectionOptionsCustomizerInvoked).isTrue();
                    assertThat(clientCustomizerInvoked).isTrue();
                });
    }

    @Test
    void autoConfigurationUsesUserProvidedConnectionDetails() {
        AmqpConnectionDetails customDetails = new AmqpConnectionDetails() {

            @Override
            public AmqpConnectionDetails.Address getAddress() {
                return new AmqpConnectionDetails.Address("custom.example.test", 6002);
            }

            @Override
            public String getUsername() {
                return "custom-user";
            }

            @Override
            public String getPassword() {
                return "custom-password";
            }

        };
        AtomicBoolean connectionOptionsCustomizerInvoked = new AtomicBoolean();

        this.contextRunner.withPropertyValues("spring.amqp.host=ignored.example.test", "spring.amqp.port=9999")
                .withBean(AmqpConnectionDetails.class, () -> customDetails)
                .withBean(ConnectionOptionsCustomizer.class, () -> (connectionOptions) -> {
                    assertThat(connectionOptions.user()).isEqualTo("custom-user");
                    assertThat(connectionOptions.password()).isEqualTo("custom-password");
                    connectionOptionsCustomizerInvoked.set(true);
                }).run((context) -> {
                    assertThat(context).hasSingleBean(AmqpConnectionDetails.class);
                    assertThat(context).hasSingleBean(AmqpConnectionFactory.class);
                    assertThat(context).hasSingleBean(AmqpClient.class);
                    assertThat(context.getBean(AmqpConnectionDetails.class)).isSameAs(customDetails);
                    assertThat(connectionOptionsCustomizerInvoked).isTrue();
                });
    }

    private static void customizeConnectionOptions(ConnectionOptions connectionOptions,
            AtomicBoolean connectionOptionsCustomizerInvoked) {
        assertThat(connectionOptions.user()).isEqualTo("orders-user");
        assertThat(connectionOptions.password()).isEqualTo("orders-password");
        connectionOptions.requestTimeout(500, TimeUnit.MILLISECONDS);
        assertThat(connectionOptions.requestTimeout()).isEqualTo(500L);
        connectionOptionsCustomizerInvoked.set(true);
    }

}
