/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_amqp;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.CredentialsRefreshService;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory.AddressShuffleMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.CacheMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.CachingConnectionFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.DirectRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionFactoryBeanConfigurer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties.ContainerType;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_amqpTest {

    @Test
    void rabbitPropertiesDetermineConnectionValuesFromDefaultsSslAndAddresses() {
        RabbitProperties properties = new RabbitProperties();

        assertThat(properties.determineHost()).isEqualTo("localhost");
        assertThat(properties.determinePort()).isEqualTo(5672);
        assertThat(properties.determineAddresses()).containsExactly("localhost:5672");
        assertThat(properties.determineUsername()).isEqualTo("guest");
        assertThat(properties.determinePassword()).isEqualTo("guest");
        assertThat(properties.getSsl().determineEnabled()).isFalse();

        properties.getSsl().setEnabled(true);
        properties.setAddresses(
                List.of("amqps://alice:secret@rabbit-one.example.test/orders", "rabbit-two.example.test:5673"));

        assertThat(properties.determineHost()).isEqualTo("rabbit-one.example.test");
        assertThat(properties.determinePort()).isEqualTo(5671);
        assertThat(properties.determineAddresses()).containsExactly("rabbit-one.example.test:5671",
                "rabbit-two.example.test:5673");
        assertThat(properties.determineUsername()).isEqualTo("alice");
        assertThat(properties.determinePassword()).isEqualTo("secret");
        assertThat(properties.determineVirtualHost()).isEqualTo("orders");
        assertThat(properties.getSsl().determineEnabled()).isTrue();
    }

    @Test
    void rabbitPropertiesExposeNestedTemplateListenerCacheAndStreamSettings() {
        RabbitProperties properties = new RabbitProperties();
        properties.setHost("broker.example.test");
        properties.setPort(5678);
        properties.setVirtualHost("");
        properties.setRequestedHeartbeat(Duration.ofSeconds(9));
        properties.setConnectionTimeout(Duration.ofMillis(750));
        properties.setChannelRpcTimeout(Duration.ofSeconds(2));
        properties.setMaxInboundMessageBodySize(DataSize.ofKilobytes(128));
        properties.setAddressShuffleMode(AddressShuffleMode.INORDER);
        properties.getCache().getChannel().setSize(12);
        properties.getCache().getChannel().setCheckoutTimeout(Duration.ofMillis(25));
        properties.getCache().getConnection().setMode(CacheMode.CONNECTION);
        properties.getCache().getConnection().setSize(3);
        properties.getListener().setType(ContainerType.DIRECT);
        properties.getListener().getDirect().setConsumersPerQueue(4);
        properties.getListener().getStream().setNativeListener(true);
        properties.getTemplate().setExchange("events");
        properties.getTemplate().setRoutingKey("created");
        properties.getTemplate().setDefaultReceiveQueue("events.in");
        properties.getTemplate().setAllowedListPatterns(List.of("java.util.*"));
        properties.getTemplate().getRetry().setEnabled(true);
        properties.getTemplate().getRetry().setMaxRetries(5);
        properties.getStream().setHost("stream.example.test");
        properties.getStream().setPort(5553);
        properties.getStream().setName("orders-stream");

        assertThat(properties.determineAddresses()).containsExactly("broker.example.test:5678");
        assertThat(properties.determineVirtualHost()).isEqualTo("/");
        assertThat(properties.getRequestedHeartbeat()).isEqualTo(Duration.ofSeconds(9));
        assertThat(properties.getConnectionTimeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(properties.getChannelRpcTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getMaxInboundMessageBodySize()).isEqualTo(DataSize.ofKilobytes(128));
        assertThat(properties.getAddressShuffleMode()).isEqualTo(AddressShuffleMode.INORDER);
        assertThat(properties.getCache().getChannel().getSize()).isEqualTo(12);
        assertThat(properties.getCache().getChannel().getCheckoutTimeout()).isEqualTo(Duration.ofMillis(25));
        assertThat(properties.getCache().getConnection().getMode()).isEqualTo(CacheMode.CONNECTION);
        assertThat(properties.getCache().getConnection().getSize()).isEqualTo(3);
        assertThat(properties.getListener().getType()).isEqualTo(ContainerType.DIRECT);
        assertThat(properties.getListener().getDirect().getConsumersPerQueue()).isEqualTo(4);
        assertThat(properties.getListener().getStream().isNativeListener()).isTrue();
        assertThat(properties.getTemplate().getExchange()).isEqualTo("events");
        assertThat(properties.getTemplate().getRoutingKey()).isEqualTo("created");
        assertThat(properties.getTemplate().getDefaultReceiveQueue()).isEqualTo("events.in");
        assertThat(properties.getTemplate().getAllowedListPatterns()).containsExactly("java.util.*");
        assertThat(properties.getTemplate().getRetry().isEnabled()).isTrue();
        assertThat(properties.getTemplate().getRetry().getMaxRetries()).isEqualTo(5);
        assertThat(properties.getStream().getHost()).isEqualTo("stream.example.test");
        assertThat(properties.getStream().getPort()).isEqualTo(5553);
        assertThat(properties.getStream().getName()).isEqualTo("orders-stream");
    }

    @Test
    void rabbitPropertiesRejectCommaSeparatedHostWhenDeterminingAddresses() {
        RabbitProperties properties = new RabbitProperties();
        properties.setHost("one.example.test,two.example.test");

        assertThatThrownBy(properties::determineAddresses)
                .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                .hasMessageContaining("spring.rabbitmq.host")
                .hasMessageContaining("spring.rabbitmq.addresses");
    }

    @Test
    void rabbitConnectionDetailsDefaultsAndAddressRecordAreUsable() {
        RabbitConnectionDetails.Address first = new RabbitConnectionDetails.Address("first.example.test", 6001);
        RabbitConnectionDetails.Address second = new RabbitConnectionDetails.Address("second.example.test", 6002);
        RabbitConnectionDetails details = connectionDetails(List.of(first, second));

        assertThat(details.getAddresses()).containsExactly(first, second);
        assertThat(details.getFirstAddress()).isEqualTo(first);
        assertThat(details.getUsername()).isNull();
        assertThat(details.getPassword()).isNull();
        assertThat(details.getVirtualHost()).isNull();
        assertThat(details.getSslBundle()).isNull();
        assertThat(first.host()).isEqualTo("first.example.test");
        assertThat(first.port()).isEqualTo(6001);
    }

    @Test
    void rabbitTemplateConfigurerAppliesTemplateSettingsAndRetryCustomizers() {
        RabbitProperties properties = new RabbitProperties();
        properties.setPublisherReturns(true);
        properties.getTemplate().setReceiveTimeout(Duration.ofMillis(125));
        properties.getTemplate().setReplyTimeout(Duration.ofMillis(250));
        properties.getTemplate().setExchange("orders.exchange");
        properties.getTemplate().setRoutingKey("orders.created");
        properties.getTemplate().setDefaultReceiveQueue("orders.in");
        properties.getTemplate().setObservationEnabled(true);
        properties.getTemplate().getRetry().setEnabled(true);
        properties.getTemplate().getRetry().setInitialInterval(Duration.ofMillis(10));
        properties.getTemplate().getRetry().setMaxInterval(Duration.ofMillis(20));
        CapturingRabbitTemplate template = new CapturingRabbitTemplate();
        MessageConverter converter = new SimpleMessageConverter();
        RabbitTemplateConfigurer configurer = new RabbitTemplateConfigurer(properties);
        AtomicBoolean customizerInvoked = new AtomicBoolean();
        configurer.setMessageConverter(converter);
        configurer.setRetrySettingsCustomizers(List.of((settings) -> {
            customizerInvoked.set(true);
            settings.getRetryPolicySettings().setMaxRetries(2L);
        }));
        CachingConnectionFactory connectionFactory = newCachingConnectionFactory();

        try {
            configurer.configure(template, connectionFactory);
        } finally {
            connectionFactory.destroy();
        }

        assertThat(template.getMessageConverter()).isSameAs(converter);
        assertThat(template.receiveTimeout).isEqualTo(125L);
        assertThat(template.replyTimeout).isEqualTo(250L);
        assertThat(template.getExchange()).isEqualTo("orders.exchange");
        assertThat(template.getRoutingKey()).isEqualTo("orders.created");
        assertThat(template.getDefaultReceiveQueue()).isEqualTo("orders.in");
        assertThat(template.observationEnabled).isTrue();
        assertThat(template.retryTemplate).isNotNull();
        assertThat(customizerInvoked).isTrue();
        assertThat(template.isMandatoryFor(new Message(new byte[0], new MessageProperties()))).isTrue();
    }

    @Test
    void rabbitTemplateConfigurerUsesExplicitMandatoryFlagOverPublisherReturns() {
        RabbitProperties properties = new RabbitProperties();
        properties.setPublisherReturns(true);
        properties.getTemplate().setMandatory(false);
        CapturingRabbitTemplate template = new CapturingRabbitTemplate();
        CachingConnectionFactory connectionFactory = newCachingConnectionFactory();

        try {
            new RabbitTemplateConfigurer(properties).configure(template, connectionFactory);
        } finally {
            connectionFactory.destroy();
        }

        assertThat(template.isMandatoryFor(new Message(new byte[0], new MessageProperties()))).isFalse();
    }

    @Test
    void rabbitTemplateConfigurerReportsInvalidAllowedListConverter() {
        RabbitProperties properties = new RabbitProperties();
        properties.getTemplate().setAllowedListPatterns(List.of("java.util.*"));
        CapturingRabbitTemplate template = new CapturingRabbitTemplate();
        template.setMessageConverter(new PassThroughMessageConverter());
        CachingConnectionFactory connectionFactory = newCachingConnectionFactory();

        try {
            RabbitTemplateConfigurer configurer = new RabbitTemplateConfigurer(properties);

            assertThatThrownBy(() -> configurer.configure(template, connectionFactory))
                    .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                    .hasMessageContaining("spring.rabbitmq.template.allowed-list-patterns");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void cachingConnectionFactoryConfigurerAppliesConnectionFactorySettings() {
        RabbitProperties properties = new RabbitProperties();
        properties.setPublisherReturns(true);
        properties.setPublisherConfirmType(ConfirmType.CORRELATED);
        properties.setAddressShuffleMode(AddressShuffleMode.RANDOM);
        properties.getCache().getChannel().setSize(8);
        properties.getCache().getChannel().setCheckoutTimeout(Duration.ofMillis(50));
        RabbitConnectionDetails details = connectionDetails(
                List.of(new RabbitConnectionDetails.Address("rabbit-a.example.test", 6100),
                        new RabbitConnectionDetails.Address("rabbit-b.example.test", 6101)));
        CapturingCachingConnectionFactory connectionFactory = new CapturingCachingConnectionFactory();
        ConnectionNameStrategy nameStrategy = (factory) -> "orders-connection";
        CachingConnectionFactoryConfigurer configurer = new CachingConnectionFactoryConfigurer(properties, details);
        configurer.setConnectionNameStrategy(nameStrategy);

        try {
            configurer.configure(connectionFactory);
        } finally {
            connectionFactory.destroy();
        }

        assertThat(connectionFactory.addresses).isEqualTo("rabbit-a.example.test:6100,rabbit-b.example.test:6101");
        assertThat(connectionFactory.addressShuffleMode).isEqualTo(AddressShuffleMode.RANDOM);
        assertThat(connectionFactory.connectionNameStrategy).isSameAs(nameStrategy);
        assertThat(connectionFactory.publisherReturns).isTrue();
        assertThat(connectionFactory.publisherConfirmType).isEqualTo(ConfirmType.CORRELATED);
        assertThat(connectionFactory.channelCacheSize).isEqualTo(8);
        assertThat(connectionFactory.channelCheckoutTimeout).isEqualTo(50L);
        assertThat(connectionFactory.cacheMode).isEqualTo(CacheMode.CHANNEL);
    }

    @Test
    void rabbitConnectionFactoryBeanConfigurerUsesConnectionDetailsBeforeProperties() throws Exception {
        RabbitProperties properties = new RabbitProperties();
        properties.setHost("ignored.example.test");
        properties.setPort(9999);
        properties.setUsername("ignored-user");
        properties.setPassword("ignored-password");
        properties.setVirtualHost("ignored-vhost");
        properties.setRequestedHeartbeat(Duration.ofSeconds(7));
        properties.setRequestedChannelMax(32);
        properties.setConnectionTimeout(Duration.ofMillis(450));
        properties.setChannelRpcTimeout(Duration.ofMillis(650));
        properties.setMaxInboundMessageBodySize(DataSize.ofKilobytes(64));
        RabbitConnectionDetails details = connectionDetails("detail-user", "detail-password", "detail-vhost",
                List.of(new RabbitConnectionDetails.Address("details.example.test", 6200)));
        RabbitConnectionFactoryBean factoryBean = new RabbitConnectionFactoryBean();
        RabbitConnectionFactoryBeanConfigurer configurer = new RabbitConnectionFactoryBeanConfigurer(
                new DefaultResourceLoader(), properties, details);

        configurer.configure(factoryBean);
        factoryBean.afterPropertiesSet();

        com.rabbitmq.client.ConnectionFactory rabbitFactory = factoryBean.getRabbitConnectionFactory();
        assertThat(rabbitFactory.getHost()).isEqualTo("details.example.test");
        assertThat(rabbitFactory.getPort()).isEqualTo(6200);
        assertThat(rabbitFactory.getUsername()).isEqualTo("detail-user");
        assertThat(rabbitFactory.getPassword()).isEqualTo("detail-password");
        assertThat(rabbitFactory.getVirtualHost()).isEqualTo("detail-vhost");
        assertThat(rabbitFactory.getRequestedHeartbeat()).isEqualTo(7);
        assertThat(rabbitFactory.getRequestedChannelMax()).isEqualTo(32);
        assertThat(rabbitFactory.getConnectionTimeout()).isEqualTo(450);
        assertThat(rabbitFactory.getChannelRpcTimeout()).isEqualTo(650);
    }

    @Test
    void rabbitConnectionFactoryBeanConfigurerAppliesCredentialsProviderAndRefreshService() {
        RabbitProperties properties = new RabbitProperties();
        RabbitConnectionDetails details = connectionDetails(
                List.of(new RabbitConnectionDetails.Address("credentials.example.test", 6300)));
        CredentialsProvider credentialsProvider = new StaticCredentialsProvider("rotating-user", "rotating-password");
        CredentialsRefreshService credentialsRefreshService = new NoOpCredentialsRefreshService();
        CapturingRabbitConnectionFactoryBean factoryBean = new CapturingRabbitConnectionFactoryBean();
        RabbitConnectionFactoryBeanConfigurer configurer = new RabbitConnectionFactoryBeanConfigurer(
                new DefaultResourceLoader(), properties, details);

        configurer.setCredentialsProvider(credentialsProvider);
        configurer.setCredentialsRefreshService(credentialsRefreshService);
        configurer.configure(factoryBean);

        assertThat(factoryBean.credentialsProvider).isSameAs(credentialsProvider);
        assertThat(factoryBean.credentialsRefreshService).isSameAs(credentialsRefreshService);
        assertThat(factoryBean.credentialsProvider.getUsername()).isEqualTo("rotating-user");
        assertThat(factoryBean.credentialsProvider.getPassword()).isEqualTo("rotating-password");
    }

    @Test
    void rabbitConnectionFactoryBeanConfigurerAppliesSslSettings() {
        RabbitProperties properties = new RabbitProperties();
        RabbitProperties.Ssl ssl = properties.getSsl();
        ssl.setEnabled(true);
        ssl.setAlgorithm("TLSv1.3");
        ssl.setKeyStore("classpath:client.p12");
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyStorePassword("key-password");
        ssl.setKeyStoreAlgorithm("SunX509");
        ssl.setTrustStore("classpath:trust.p12");
        ssl.setTrustStoreType("PKCS12");
        ssl.setTrustStorePassword("trust-password");
        ssl.setTrustStoreAlgorithm("SunX509");
        ssl.setValidateServerCertificate(false);
        ssl.setVerifyHostname(true);
        RabbitConnectionDetails details = connectionDetails(
                List.of(new RabbitConnectionDetails.Address("secure.example.test", 5671)));
        CapturingRabbitConnectionFactoryBean factoryBean = new CapturingRabbitConnectionFactoryBean();
        RabbitConnectionFactoryBeanConfigurer configurer = new RabbitConnectionFactoryBeanConfigurer(
                new DefaultResourceLoader(), properties, details);

        configurer.configure(factoryBean);

        assertThat(factoryBean.useSsl).isTrue();
        assertThat(factoryBean.sslAlgorithm).isEqualTo("TLSv1.3");
        assertThat(factoryBean.keyStore).isEqualTo("classpath:client.p12");
        assertThat(factoryBean.keyStoreType).isEqualTo("PKCS12");
        assertThat(factoryBean.keyStorePassphrase).isEqualTo("key-password");
        assertThat(factoryBean.keyStoreAlgorithm).isEqualTo("SunX509");
        assertThat(factoryBean.trustStore).isEqualTo("classpath:trust.p12");
        assertThat(factoryBean.trustStoreType).isEqualTo("PKCS12");
        assertThat(factoryBean.trustStorePassphrase).isEqualTo("trust-password");
        assertThat(factoryBean.trustStoreAlgorithm).isEqualTo("SunX509");
        assertThat(factoryBean.skipServerCertificateValidation).isTrue();
        assertThat(factoryBean.enableHostnameVerification).isTrue();
    }

    @Test
    void simpleListenerContainerFactoryConfigurerAppliesCommonSimpleAndRetrySettings() {
        RabbitProperties properties = new RabbitProperties();
        RabbitProperties.SimpleContainer simple = properties.getListener().getSimple();
        simple.setAutoStartup(false);
        simple.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        simple.setPrefetch(5);
        simple.setDefaultRequeueRejected(false);
        simple.setIdleEventInterval(Duration.ofMillis(75));
        simple.setMissingQueuesFatal(false);
        simple.setDeBatchingEnabled(false);
        simple.setForceStop(true);
        simple.setObservationEnabled(true);
        simple.setConcurrency(2);
        simple.setMaxConcurrency(4);
        simple.setBatchSize(10);
        simple.setConsumerBatchEnabled(true);
        simple.getRetry().setEnabled(true);
        simple.getRetry().setMaxRetries(2);
        CapturingSimpleRabbitListenerContainerFactory factory = new CapturingSimpleRabbitListenerContainerFactory();
        Executor executor = Runnable::run;
        SimpleRabbitListenerContainerFactoryConfigurer configurer = new SimpleRabbitListenerContainerFactoryConfigurer(
                properties);
        CachingConnectionFactory connectionFactory = newCachingConnectionFactory();
        configurer.setTaskExecutor(executor);

        try {
            configurer.configure(factory, connectionFactory);
        } finally {
            connectionFactory.destroy();
        }

        assertCommonListenerSettings(factory, connectionFactory, executor);
        assertThat(factory.concurrentConsumers).isEqualTo(2);
        assertThat(factory.maxConcurrentConsumers).isEqualTo(4);
        assertThat(factory.batchSize).isEqualTo(10);
        assertThat(factory.consumerBatchEnabled).isTrue();
        assertThat(factory.adviceChain).isNotEmpty();
    }

    @Test
    void directListenerContainerFactoryConfigurerAppliesCommonAndDirectSettings() {
        RabbitProperties properties = new RabbitProperties();
        RabbitProperties.DirectContainer direct = properties.getListener().getDirect();
        direct.setAutoStartup(false);
        direct.setAcknowledgeMode(AcknowledgeMode.AUTO);
        direct.setPrefetch(6);
        direct.setDefaultRequeueRejected(true);
        direct.setIdleEventInterval(Duration.ofMillis(80));
        direct.setMissingQueuesFatal(true);
        direct.setDeBatchingEnabled(false);
        direct.setForceStop(true);
        direct.setObservationEnabled(true);
        direct.setConsumersPerQueue(3);
        DirectRabbitListenerContainerFactory factory = new DirectRabbitListenerContainerFactory();
        Executor executor = Runnable::run;
        DirectRabbitListenerContainerFactoryConfigurer configurer = new DirectRabbitListenerContainerFactoryConfigurer(
                properties);
        CachingConnectionFactory connectionFactory = newCachingConnectionFactory();
        configurer.setTaskExecutor(executor);

        try {
            configurer.configure(factory, connectionFactory);
            SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
            endpoint.setId("direct-orders");
            endpoint.setQueueNames("orders.in");
            endpoint.setMessageListener((message) -> {
                throw new AssertionError("Listener should not be invoked");
            });
            DirectMessageListenerContainer container = factory.createListenerContainer(endpoint);

            try {
                assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
                assertThat(container.isAutoStartup()).isFalse();
                assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
                assertThat(container.getQueueNames()).containsExactly("orders.in");
            } finally {
                container.destroy();
            }
        } finally {
            connectionFactory.destroy();
        }
    }

    private static RabbitConnectionDetails connectionDetails(List<RabbitConnectionDetails.Address> addresses) {
        return connectionDetails(null, null, null, addresses);
    }

    private static RabbitConnectionDetails connectionDetails(String username, String password, String virtualHost,
            List<RabbitConnectionDetails.Address> addresses) {
        return new RabbitConnectionDetails() {

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public String getPassword() {
                return password;
            }

            @Override
            public String getVirtualHost() {
                return virtualHost;
            }

            @Override
            public List<RabbitConnectionDetails.Address> getAddresses() {
                return addresses;
            }

        };
    }

    private static CachingConnectionFactory newCachingConnectionFactory() {
        return new CachingConnectionFactory(new com.rabbitmq.client.ConnectionFactory());
    }

    private static void assertCommonListenerSettings(CapturingListenerSettings factory,
            CachingConnectionFactory connectionFactory, Executor executor) {
        assertThat(factory.connectionFactory()).isSameAs(connectionFactory);
        assertThat(factory.autoStartup()).isFalse();
        assertThat(factory.acknowledgeMode()).isNotNull();
        assertThat(factory.prefetchCount()).isGreaterThan(0);
        assertThat(factory.defaultRequeueRejected()).isNotNull();
        assertThat(factory.idleEventInterval()).isGreaterThan(0L);
        assertThat(factory.deBatchingEnabled()).isFalse();
        assertThat(factory.forceStop()).isTrue();
        assertThat(factory.observationEnabled()).isTrue();
        assertThat(factory.taskExecutor()).isSameAs(executor);
    }

    private static class CapturingRabbitTemplate extends RabbitTemplate {

        private long receiveTimeout;

        private long replyTimeout;

        private boolean observationEnabled;

        private RetryTemplate retryTemplate;

        @Override
        public void setReceiveTimeout(long receiveTimeout) {
            super.setReceiveTimeout(receiveTimeout);
            this.receiveTimeout = receiveTimeout;
        }

        @Override
        public void setReplyTimeout(long replyTimeout) {
            super.setReplyTimeout(replyTimeout);
            this.replyTimeout = replyTimeout;
        }

        @Override
        public void setObservationEnabled(boolean observationEnabled) {
            super.setObservationEnabled(observationEnabled);
            this.observationEnabled = observationEnabled;
        }

        @Override
        public void setRetryTemplate(RetryTemplate retryTemplate) {
            super.setRetryTemplate(retryTemplate);
            this.retryTemplate = retryTemplate;
        }

    }

    private static class CapturingRabbitConnectionFactoryBean extends RabbitConnectionFactoryBean {

        private CredentialsProvider credentialsProvider;

        private CredentialsRefreshService credentialsRefreshService;

        private boolean useSsl;

        private String sslAlgorithm;

        private String keyStore;

        private String keyStoreType;

        private String keyStorePassphrase;

        private String keyStoreAlgorithm;

        private String trustStore;

        private String trustStoreType;

        private String trustStorePassphrase;

        private String trustStoreAlgorithm;

        private boolean skipServerCertificateValidation;

        private boolean enableHostnameVerification;

        @Override
        public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
            super.setCredentialsProvider(credentialsProvider);
            this.credentialsProvider = credentialsProvider;
        }

        @Override
        public void setCredentialsRefreshService(CredentialsRefreshService credentialsRefreshService) {
            super.setCredentialsRefreshService(credentialsRefreshService);
            this.credentialsRefreshService = credentialsRefreshService;
        }

        @Override
        public void setUseSSL(boolean useSsl) {
            super.setUseSSL(useSsl);
            this.useSsl = useSsl;
        }

        @Override
        public void setSslAlgorithm(String sslAlgorithm) {
            super.setSslAlgorithm(sslAlgorithm);
            this.sslAlgorithm = sslAlgorithm;
        }

        @Override
        public void setKeyStore(String keyStore) {
            super.setKeyStore(keyStore);
            this.keyStore = keyStore;
        }

        @Override
        public void setKeyStoreType(String keyStoreType) {
            super.setKeyStoreType(keyStoreType);
            this.keyStoreType = keyStoreType;
        }

        @Override
        public void setKeyStorePassphrase(String keyStorePassphrase) {
            super.setKeyStorePassphrase(keyStorePassphrase);
            this.keyStorePassphrase = keyStorePassphrase;
        }

        @Override
        public void setKeyStoreAlgorithm(String keyStoreAlgorithm) {
            super.setKeyStoreAlgorithm(keyStoreAlgorithm);
            this.keyStoreAlgorithm = keyStoreAlgorithm;
        }

        @Override
        public void setTrustStore(String trustStore) {
            super.setTrustStore(trustStore);
            this.trustStore = trustStore;
        }

        @Override
        public void setTrustStoreType(String trustStoreType) {
            super.setTrustStoreType(trustStoreType);
            this.trustStoreType = trustStoreType;
        }

        @Override
        public void setTrustStorePassphrase(String trustStorePassphrase) {
            super.setTrustStorePassphrase(trustStorePassphrase);
            this.trustStorePassphrase = trustStorePassphrase;
        }

        @Override
        public void setTrustStoreAlgorithm(String trustStoreAlgorithm) {
            super.setTrustStoreAlgorithm(trustStoreAlgorithm);
            this.trustStoreAlgorithm = trustStoreAlgorithm;
        }

        @Override
        public void setSkipServerCertificateValidation(boolean skipServerCertificateValidation) {
            super.setSkipServerCertificateValidation(skipServerCertificateValidation);
            this.skipServerCertificateValidation = skipServerCertificateValidation;
        }

        @Override
        public void setEnableHostnameVerification(boolean enableHostnameVerification) {
            super.setEnableHostnameVerification(enableHostnameVerification);
            this.enableHostnameVerification = enableHostnameVerification;
        }

    }

    private static class CapturingCachingConnectionFactory extends CachingConnectionFactory {

        private String addresses;

        private AddressShuffleMode addressShuffleMode;

        private ConnectionNameStrategy connectionNameStrategy;

        private boolean publisherReturns;

        private ConfirmType publisherConfirmType;

        private int channelCacheSize;

        private long channelCheckoutTimeout;

        private CacheMode cacheMode;

        private int connectionCacheSize;

        @Override
        public void setAddresses(String addresses) {
            super.setAddresses(addresses);
            this.addresses = addresses;
        }

        @Override
        public void setAddressShuffleMode(AddressShuffleMode addressShuffleMode) {
            super.setAddressShuffleMode(addressShuffleMode);
            this.addressShuffleMode = addressShuffleMode;
        }

        @Override
        public void setConnectionNameStrategy(ConnectionNameStrategy connectionNameStrategy) {
            super.setConnectionNameStrategy(connectionNameStrategy);
            this.connectionNameStrategy = connectionNameStrategy;
        }

        @Override
        public void setPublisherReturns(boolean publisherReturns) {
            super.setPublisherReturns(publisherReturns);
            this.publisherReturns = publisherReturns;
        }

        @Override
        public void setPublisherConfirmType(ConfirmType confirmType) {
            super.setPublisherConfirmType(confirmType);
            this.publisherConfirmType = confirmType;
        }

        @Override
        public void setChannelCacheSize(int sessionCacheSize) {
            super.setChannelCacheSize(sessionCacheSize);
            this.channelCacheSize = sessionCacheSize;
        }

        @Override
        public void setChannelCheckoutTimeout(long channelCheckoutTimeout) {
            super.setChannelCheckoutTimeout(channelCheckoutTimeout);
            this.channelCheckoutTimeout = channelCheckoutTimeout;
        }

        @Override
        public void setCacheMode(CacheMode cacheMode) {
            super.setCacheMode(cacheMode);
            this.cacheMode = cacheMode;
        }

        @Override
        public void setConnectionCacheSize(int connectionCacheSize) {
            super.setConnectionCacheSize(connectionCacheSize);
            this.connectionCacheSize = connectionCacheSize;
        }

    }

    private interface CapturingListenerSettings {

        ConnectionFactory connectionFactory();

        Boolean autoStartup();

        AcknowledgeMode acknowledgeMode();

        Integer prefetchCount();

        Boolean defaultRequeueRejected();

        Long idleEventInterval();

        Boolean deBatchingEnabled();

        Boolean forceStop();

        Boolean observationEnabled();

        Executor taskExecutor();

    }

    private static class CapturingSimpleRabbitListenerContainerFactory extends SimpleRabbitListenerContainerFactory
            implements CapturingListenerSettings {

        private ConnectionFactory connectionFactory;

        private Boolean autoStartup;

        private AcknowledgeMode acknowledgeMode;

        private Integer prefetchCount;

        private Boolean defaultRequeueRejected;

        private Long idleEventInterval;

        private Boolean missingQueuesFatal;

        private Boolean deBatchingEnabled;

        private Boolean forceStop;

        private Boolean observationEnabled;

        private Executor taskExecutor;

        private Advice[] adviceChain = new Advice[0];

        private Integer concurrentConsumers;

        private Integer maxConcurrentConsumers;

        private Integer batchSize;

        private boolean consumerBatchEnabled;

        @Override
        public void setConnectionFactory(ConnectionFactory connectionFactory) {
            super.setConnectionFactory(connectionFactory);
            this.connectionFactory = connectionFactory;
        }

        @Override
        public void setAutoStartup(Boolean autoStartup) {
            super.setAutoStartup(autoStartup);
            this.autoStartup = autoStartup;
        }

        @Override
        public void setAcknowledgeMode(AcknowledgeMode acknowledgeMode) {
            super.setAcknowledgeMode(acknowledgeMode);
            this.acknowledgeMode = acknowledgeMode;
        }

        @Override
        public void setPrefetchCount(Integer prefetchCount) {
            super.setPrefetchCount(prefetchCount);
            this.prefetchCount = prefetchCount;
        }

        @Override
        public void setDefaultRequeueRejected(Boolean defaultRequeueRejected) {
            super.setDefaultRequeueRejected(defaultRequeueRejected);
            this.defaultRequeueRejected = defaultRequeueRejected;
        }

        @Override
        public void setIdleEventInterval(Long idleEventInterval) {
            super.setIdleEventInterval(idleEventInterval);
            this.idleEventInterval = idleEventInterval;
        }

        @Override
        public void setMissingQueuesFatal(Boolean missingQueuesFatal) {
            super.setMissingQueuesFatal(missingQueuesFatal);
            this.missingQueuesFatal = missingQueuesFatal;
        }

        @Override
        public void setDeBatchingEnabled(Boolean deBatchingEnabled) {
            super.setDeBatchingEnabled(deBatchingEnabled);
            this.deBatchingEnabled = deBatchingEnabled;
        }

        @Override
        public void setForceStop(boolean forceStop) {
            super.setForceStop(forceStop);
            this.forceStop = forceStop;
        }

        @Override
        public void setObservationEnabled(boolean observationEnabled) {
            super.setObservationEnabled(observationEnabled);
            this.observationEnabled = observationEnabled;
        }

        @Override
        public void setTaskExecutor(Executor taskExecutor) {
            super.setTaskExecutor(taskExecutor);
            this.taskExecutor = taskExecutor;
        }

        @Override
        public void setAdviceChain(Advice... adviceChain) {
            super.setAdviceChain(adviceChain);
            this.adviceChain = adviceChain;
        }

        @Override
        public void setConcurrentConsumers(Integer concurrentConsumers) {
            super.setConcurrentConsumers(concurrentConsumers);
            this.concurrentConsumers = concurrentConsumers;
        }

        @Override
        public void setMaxConcurrentConsumers(Integer maxConcurrentConsumers) {
            super.setMaxConcurrentConsumers(maxConcurrentConsumers);
            this.maxConcurrentConsumers = maxConcurrentConsumers;
        }

        @Override
        public void setBatchSize(Integer batchSize) {
            super.setBatchSize(batchSize);
            this.batchSize = batchSize;
        }

        @Override
        public void setConsumerBatchEnabled(boolean consumerBatchEnabled) {
            super.setConsumerBatchEnabled(consumerBatchEnabled);
            this.consumerBatchEnabled = consumerBatchEnabled;
        }

        @Override
        public ConnectionFactory connectionFactory() {
            return this.connectionFactory;
        }

        @Override
        public Boolean autoStartup() {
            return this.autoStartup;
        }

        @Override
        public AcknowledgeMode acknowledgeMode() {
            return this.acknowledgeMode;
        }

        @Override
        public Integer prefetchCount() {
            return this.prefetchCount;
        }

        @Override
        public Boolean defaultRequeueRejected() {
            return this.defaultRequeueRejected;
        }

        @Override
        public Long idleEventInterval() {
            return this.idleEventInterval;
        }

        @Override
        public Boolean deBatchingEnabled() {
            return this.deBatchingEnabled;
        }

        @Override
        public Boolean forceStop() {
            return this.forceStop;
        }

        @Override
        public Boolean observationEnabled() {
            return this.observationEnabled;
        }

        @Override
        public Executor taskExecutor() {
            return this.taskExecutor;
        }

    }

    private static final class StaticCredentialsProvider implements CredentialsProvider {

        private final String username;

        private final String password;

        private StaticCredentialsProvider(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String getUsername() {
            return this.username;
        }

        @Override
        public String getPassword() {
            return this.password;
        }

    }

    private static final class NoOpCredentialsRefreshService implements CredentialsRefreshService {

        private CredentialsProvider unregisteredCredentialsProvider;

        private String unregisteredRegistrationId;

        @Override
        public String register(CredentialsProvider credentialsProvider, Callable<Boolean> refreshAction) {
            return "registration";
        }

        @Override
        public void unregister(CredentialsProvider credentialsProvider, String registrationId) {
            this.unregisteredCredentialsProvider = credentialsProvider;
            this.unregisteredRegistrationId = registrationId;
        }

        @Override
        public boolean isApproachingExpiration(Duration timeBeforeExpiration) {
            return false;
        }

    }

    private static final class PassThroughMessageConverter implements MessageConverter {

        @Override
        public Message toMessage(Object object, MessageProperties messageProperties) {
            return new Message(object.toString().getBytes(StandardCharsets.UTF_8), messageProperties);
        }

        @Override
        public Object fromMessage(Message message) {
            return message.getBody();
        }

    }

}
