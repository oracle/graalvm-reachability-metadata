/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_engine;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Camel_core_engineTest {
    @Test
    void routesMessagesThroughStartedCamelContext() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addComponent("direct", new DirectComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:uppercase")
                            .routeId("uppercase-route")
                            .group("core-engine")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                exchange.getMessage().setHeader("originalLength", body.length());
                                exchange.getMessage().setBody(body.toUpperCase(Locale.ROOT));
                            });
                }
            });

            context.start();

            assertThat(context.getRoutesSize()).isEqualTo(1);
            assertThat(context.getRouteIds()).containsExactly("uppercase-route");
            assertThat(context.getRoutesByGroup("core-engine")).hasSize(1);

            try (ProducerTemplate template = context.createProducerTemplate()) {
                Exchange result = template.request("direct:uppercase", exchange -> {
                    exchange.getMessage().setBody("camel");
                    exchange.getMessage().setHeader("requestId", "request-1");
                });

                assertThat(result.getException()).isNull();
                assertThat(result.getMessage().getBody(String.class)).isEqualTo("CAMEL");
                assertThat(result.getMessage().getHeader("originalLength", Integer.class)).isEqualTo(5);
                assertThat(result.getMessage().getHeader("requestId", String.class)).isEqualTo("request-1");
            }
        }
    }

    @Test
    void materializesRouteTemplateAndProcessesExchange() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addComponent("direct", new DirectComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("appendTemplate")
                            .templateParameter("entrypoint")
                            .from("direct:{{entrypoint}}")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                String suffix = exchange.getMessage().getHeader("suffix", String.class);
                                exchange.getMessage().setBody(body + suffix);
                            });
                }
            });

            String routeId = context.addRouteFromTemplate(
                    "templated-append-route", "appendTemplate", Map.of("entrypoint", "append"));
            context.start();

            assertThat(routeId).isEqualTo("templated-append-route");
            assertThat(context.getRouteTemplateDefinition("appendTemplate")).isNotNull();
            assertThat(context.getRouteDefinition("templated-append-route")).isNotNull();

            try (ProducerTemplate template = context.createProducerTemplate()) {
                String response = template.requestBodyAndHeader(
                        "direct:append", "core", "suffix", "-engine", String.class);

                assertThat(response).isEqualTo("core-engine");
            }
        }
    }

    @Test
    void resolvesPropertiesVariablesAndCoreTypeConverters() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Properties properties = new Properties();
            properties.setProperty("message.prefix", "Apache Camel");
            context.getPropertiesComponent().setInitialProperties(properties);
            context.setVariable("message.suffix", "core engine");
            context.start();

            assertThat(context.resolvePropertyPlaceholders("{{message.prefix}}"))
                    .isEqualTo("Apache Camel");
            assertThat(context.getVariable("message.suffix", String.class)).isEqualTo("core engine");
            assertThat(context.getTypeConverter().mandatoryConvertTo(Integer.class, "4190"))
                    .isEqualTo(4190);
        }
    }

    @Test
    void handlesRouteExceptionsWithOnExceptionClause() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addComponent("direct", new DirectComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    onException(IllegalArgumentException.class)
                            .handled(true)
                            .process(exchange -> {
                                Throwable caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                                exchange.getMessage().setHeader(
                                        "handledExceptionType", caught.getClass().getSimpleName());
                            })
                            .setBody().constant("recovered");

                    from("direct:validate")
                            .routeId("exception-handler-route")
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                if (body.isBlank()) {
                                    throw new IllegalArgumentException("body must not be blank");
                                }
                                exchange.getMessage().setBody("accepted:" + body);
                            });
                }
            });
            context.start();

            try (ProducerTemplate template = context.createProducerTemplate()) {
                Exchange accepted = template.request("direct:validate", exchange ->
                        exchange.getMessage().setBody("camel"));
                Exchange recovered = template.request("direct:validate", exchange ->
                        exchange.getMessage().setBody(" "));

                assertThat(accepted.getException()).isNull();
                assertThat(accepted.getMessage().getBody(String.class)).isEqualTo("accepted:camel");
                assertThat(recovered.getException()).isNull();
                assertThat(recovered.getMessage().getBody(String.class)).isEqualTo("recovered");
                assertThat(recovered.getMessage().getHeader("handledExceptionType", String.class))
                        .isEqualTo(IllegalArgumentException.class.getSimpleName());
            }
        }
    }

    @Test
    void removesRoutesAndComponentsFromRunningContext() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DirectComponent directComponent = new DirectComponent();
            context.addComponent("direct", directComponent);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:removable")
                            .routeId("removable-route")
                            .setBody(exchange -> "handled:" + exchange.getMessage().getBody(String.class));
                }
            });
            context.start();

            try (ProducerTemplate template = context.createProducerTemplate()) {
                assertThat(template.requestBody("direct:removable", "before", String.class))
                        .isEqualTo("handled:before");
            }

            context.getRouteController().stopRoute("removable-route");
            assertThat(context.removeRoute("removable-route")).isTrue();
            assertThat(context.getRoute("removable-route")).isNull();
            assertThat(context.removeComponent("direct")).isSameAs(directComponent);
            assertThat(context.hasComponent("direct")).isNull();
        }
    }
}
