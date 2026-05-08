/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Camel_core_engineTest {
    @Test
    void routesMessagesThroughDefaultCamelContextWithCustomComponent() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("memory", new InMemoryComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("memory:input")
                            .routeId("uppercase-route")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                String tenant = exchange.getMessage().getHeader("tenant", String.class);
                                exchange.setProperty("originalBody", body);
                                exchange.getMessage().setHeader("processed", true);
                                exchange.getMessage().setBody(tenant + ":" + body.toUpperCase(Locale.ROOT));
                            })
                            .to("memory:result");
                }
            });

            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            try {
                template.start();
                Object reply = template.requestBodyAndHeader("memory:input", "camel", "tenant", "eu");

                assertThat(reply).isEqualTo("eu:CAMEL");
                assertThat(context.getRouteDefinition("uppercase-route")).isNotNull();
                assertThat(context.getRoute("uppercase-route")).isNotNull();

                InMemoryEndpoint result = (InMemoryEndpoint) context.getEndpoint("memory:result");
                assertThat(result.receivedMessages())
                        .singleElement()
                        .satisfies(message -> {
                            assertThat(message.body()).isEqualTo("eu:CAMEL");
                            assertThat(message.headers()).containsEntry("processed", true);
                        });
            } finally {
                template.stop();
            }
        } finally {
            context.close();
        }
    }

    @Test
    void contentBasedRouterSendsMessagesToMatchingBranch() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("memory", new InMemoryComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("memory:orders")
                            .routeId("priority-router")
                            .choice()
                            .when(header("priority").isEqualTo("gold"))
                            .setHeader("tier", constant("premium"))
                            .to("memory:premium")
                            .otherwise()
                            .setHeader("tier", constant("standard"))
                            .to("memory:standard")
                            .end();
                }
            });

            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            try {
                template.start();
                template.sendBodyAndHeader("memory:orders", "order-1", "priority", "gold");
                template.sendBodyAndHeader("memory:orders", "order-2", "priority", "silver");

                InMemoryEndpoint premium = (InMemoryEndpoint) context.getEndpoint("memory:premium");
                assertThat(premium.receivedMessages())
                        .singleElement()
                        .satisfies(message -> {
                            assertThat(message.body()).isEqualTo("order-1");
                            assertThat(message.headers()).containsEntry("priority", "gold");
                            assertThat(message.headers()).containsEntry("tier", "premium");
                        });

                InMemoryEndpoint standard = (InMemoryEndpoint) context.getEndpoint("memory:standard");
                assertThat(standard.receivedMessages())
                        .singleElement()
                        .satisfies(message -> {
                            assertThat(message.body()).isEqualTo("order-2");
                            assertThat(message.headers()).containsEntry("priority", "silver");
                            assertThat(message.headers()).containsEntry("tier", "standard");
                        });
            } finally {
                template.stop();
            }
        } finally {
            context.close();
        }
    }

    @Test
    void routeTemplatesMaterializeRunnableRoutesBeforeStartup() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("memory", new InMemoryComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("templatedProcessor")
                            .templateParameter("inputName")
                            .from("memory:{{inputName}}")
                            .routeId("{{inputName}}-route")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                exchange.getMessage().setBody("template:" + body);
                            });
                }
            });

            String routeId = context.addRouteFromTemplate(
                    "generated-orders-route", "templatedProcessor", Map.of("inputName", "orders"));
            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            try {
                template.start();
                assertThat(routeId).isNotBlank();
                assertThat(context.getRouteTemplateDefinition("templatedProcessor")).isNotNull();
                assertThat(context.getRouteDefinition(routeId)).isNotNull();
                assertThat(template.requestBody("memory:orders", "payload")).isEqualTo("template:payload");
            } finally {
                template.stop();
            }
        } finally {
            context.close();
        }
    }

    @Test
    void dumpRoutesStrategyWritesYamlModelForConfiguredRoutes(@TempDir Path tempDir) throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("memory", new InMemoryComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("dumpTemplate")
                            .templateParameter("name")
                            .from("memory:{{name}}")
                            .routeId("dump-template-{{name}}");

                    from("memory:dumpInput")
                            .routeId("dump-route")
                            .process(exchange -> exchange.getMessage().setHeader("dumped", true));
                }
            });

            Path output = tempDir.resolve("camel-routes.yaml");
            DefaultDumpRoutesStrategy strategy = new DefaultDumpRoutesStrategy();
            strategy.setCamelContext(context);
            strategy.setInclude("routes,route-templates");
            strategy.setLog(false);
            strategy.setResolvePlaceholders(false);
            strategy.setOutput(output.toString());
            strategy.dumpRoutes("yaml");

            assertThat(Files.exists(output)).isTrue();
            String yaml = Files.readString(output, StandardCharsets.UTF_8);
            assertThat(yaml)
                    .contains("routeTemplates")
                    .contains("dump-route")
                    .contains("memory:dumpInput")
                    .contains("process");
        } finally {
            context.close();
        }
    }

    @Test
    void propertiesComponentAndCoreTypeConvertersAreAvailable() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            Properties properties = new Properties();
            properties.setProperty("engine.name", "core-engine");
            context.getPropertiesComponent().setInitialProperties(properties);
            context.start();

            assertThat(context.getPropertiesComponent().parseUri("camel-{{engine.name}}"))
                    .isEqualTo("camel-core-engine");
            assertThat(context.getTypeConverter().mandatoryConvertTo(Integer.class, "4190")).isEqualTo(4190);
            assertThat(context.getTypeConverter().mandatoryConvertTo(Boolean.class, "true")).isTrue();
            assertThat(context.getTypeConverter().mandatoryConvertTo(String.class, new StringBuilder("converted")))
                    .isEqualTo("converted");
        } finally {
            context.close();
        }
    }

    @Test
    void onExceptionHandlerRecoversFailedExchange() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("memory", new InMemoryComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    onException(IllegalArgumentException.class)
                            .handled(true)
                            .process(exchange -> {
                                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                                exchange.getMessage().setHeader("failureHandled", true);
                                exchange.getMessage().setBody("recovered:" + exception.getMessage());
                            })
                            .to("memory:errors");

                    from("memory:validation")
                            .routeId("validation-route")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                throw new IllegalArgumentException("invalid " + body);
                            })
                            .to("memory:accepted");
                }
            });

            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            try {
                template.start();
                assertThat(template.requestBody("memory:validation", "payload")).isEqualTo("recovered:invalid payload");

                InMemoryEndpoint errors = (InMemoryEndpoint) context.getEndpoint("memory:errors");
                assertThat(errors.receivedMessages())
                        .singleElement()
                        .satisfies(message -> {
                            assertThat(message.body()).isEqualTo("recovered:invalid payload");
                            assertThat(message.headers()).containsEntry("failureHandled", true);
                        });

                InMemoryEndpoint accepted = (InMemoryEndpoint) context.getEndpoint("memory:accepted");
                assertThat(accepted.receivedMessages()).isEmpty();
            } finally {
                template.stop();
            }
        } finally {
            context.close();
        }
    }

    private static final class InMemoryComponent extends DefaultComponent {
        private final Map<String, InMemoryEndpoint> endpoints = new ConcurrentHashMap<>();

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return endpoints.computeIfAbsent(remaining, key -> new InMemoryEndpoint(uri, this));
        }
    }

    private static final class InMemoryEndpoint extends DefaultEndpoint {
        private final List<RecordedMessage> receivedMessages = new CopyOnWriteArrayList<>();
        private volatile InMemoryConsumer consumer;

        private InMemoryEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() {
            return new InMemoryProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new InMemoryConsumer(this, processor);
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        private List<RecordedMessage> receivedMessages() {
            return new ArrayList<>(receivedMessages);
        }

        private void record(Exchange exchange) {
            receivedMessages.add(new RecordedMessage(
                    exchange.getMessage().getBody(), new LinkedHashMap<>(exchange.getMessage().getHeaders())));
        }
    }

    private static final class InMemoryProducer extends DefaultProducer {
        private final InMemoryEndpoint endpoint;

        private InMemoryProducer(InMemoryEndpoint endpoint) {
            super(endpoint);
            this.endpoint = endpoint;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            InMemoryConsumer activeConsumer = endpoint.consumer;
            if (activeConsumer == null) {
                endpoint.record(exchange);
                return;
            }
            activeConsumer.getProcessor().process(exchange);
        }
    }

    private static final class InMemoryConsumer extends DefaultConsumer {
        private final InMemoryEndpoint endpoint;

        private InMemoryConsumer(InMemoryEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
            this.endpoint = endpoint;
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            endpoint.consumer = this;
        }

        @Override
        protected void doStop() throws Exception {
            endpoint.consumer = null;
            super.doStop();
        }
    }

    private record RecordedMessage(Object body, Map<String, Object> headers) {
    }
}
