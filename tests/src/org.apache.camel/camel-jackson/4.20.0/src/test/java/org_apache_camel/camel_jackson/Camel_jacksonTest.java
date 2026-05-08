/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_jackson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.converter.JacksonTypeConverters;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.component.jackson.transform.JsonDataTypeTransformer;
import org.apache.camel.component.jackson.transform.JsonPojoDataTypeTransformer;
import org.apache.camel.component.jackson.transform.JsonStructDataTypeTransformer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.MimeType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Camel_jacksonTest {
    @Test
    void jacksonDataFormatMarshalsJsonViewAndContentTypeHeader() throws Exception {
        try (CamelContext camelContext = new DefaultCamelContext()) {
            JacksonDataFormat dataFormat = new JacksonDataFormat(Account.class, PublicView.class);
            dataFormat.setCamelContext(camelContext);
            dataFormat.start();
            try {
                Exchange exchange = new DefaultExchange(camelContext);
                Account account = new Account("A-1", "Checking", "internal-only");

                String json = marshalToString(dataFormat, exchange, account);

                assertThat(json).contains("\"id\":\"A-1\"");
                assertThat(json).contains("\"name\":\"Checking\"");
                assertThat(json).doesNotContain("internal-only");
                assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo(MimeType.JSON.type());
            } finally {
                dataFormat.stop();
            }
        }
    }

    @Test
    void jacksonDataFormatUnmarshalsHeaderSelectedTypeWithConfiguredFeatures() throws Exception {
        try (CamelContext camelContext = new DefaultCamelContext()) {
            ObjectMapper objectMapper = new ObjectMapper()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            JacksonDataFormat dataFormat = new JacksonDataFormat();
            dataFormat.setObjectMapper(objectMapper);
            dataFormat.setAllowUnmarshallType(true);
            dataFormat.setNamingStrategy("SNAKE_CASE");
            dataFormat.setCamelContext(camelContext);
            dataFormat.start();
            try {
                Exchange exchange = new DefaultExchange(camelContext);
                exchange.getMessage().setHeader(JacksonConstants.UNMARSHAL_TYPE, Customer.class.getName());
                byte[] json = """
                        {"customer_id":"C-7","display_name":"Ada Lovelace","ignored":"value"}
                        """.getBytes(StandardCharsets.UTF_8);

                Object unmarshalled = dataFormat.unmarshal(exchange, new ByteArrayInputStream(json));

                assertThat(unmarshalled).isInstanceOf(Customer.class);
                Customer customer = (Customer) unmarshalled;
                assertThat(customer.getCustomerId()).isEqualTo("C-7");
                assertThat(customer.getDisplayName()).isEqualTo("Ada Lovelace");
            } finally {
                dataFormat.stop();
            }
        }
    }

    @Test
    void listJacksonDataFormatUnmarshalsTypedCollectionsFromByteArrays() throws Exception {
        try (CamelContext camelContext = new DefaultCamelContext()) {
            ListJacksonDataFormat dataFormat = new ListJacksonDataFormat(LineItem.class);
            dataFormat.setCamelContext(camelContext);
            dataFormat.start();
            try {
                Exchange exchange = new DefaultExchange(camelContext);
                byte[] json = """
                        [{"sku":"pencil","quantity":3},{"sku":"notebook","quantity":2}]
                        """.getBytes(StandardCharsets.UTF_8);

                Object unmarshalled = dataFormat.unmarshal(exchange, json);

                assertThat(unmarshalled).isInstanceOf(List.class);
                assertThat((List<?>) unmarshalled)
                        .hasSize(2)
                        .allSatisfy(item -> assertThat(item).isInstanceOf(LineItem.class));
                LineItem first = (LineItem) ((List<?>) unmarshalled).get(0);
                LineItem second = (LineItem) ((List<?>) unmarshalled).get(1);
                assertThat(first.getSku()).isEqualTo("pencil");
                assertThat(first.getQuantity()).isEqualTo(3);
                assertThat(second.getSku()).isEqualTo("notebook");
                assertThat(second.getQuantity()).isEqualTo(2);
            } finally {
                dataFormat.stop();
            }
        }
    }

    @Test
    void registeredObjectMapperIsAutoDiscoveredForDataFormat() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("customMapper", objectMapper);

        try (CamelContext camelContext = new DefaultCamelContext(registry)) {
            JacksonDataFormat dataFormat = new JacksonDataFormat(Customer.class);
            dataFormat.setAutoDiscoverObjectMapper(true);
            dataFormat.setCamelContext(camelContext);
            dataFormat.start();
            try {
                Exchange exchange = new DefaultExchange(camelContext);
                String json = "{"
                        + "\"customerId\":\"C-8\","
                        + "\"displayName\":\"Grace Hopper\","
                        + "\"ignored\":true"
                        + "}";

                Object unmarshalled = dataFormat.unmarshal(exchange, json);

                assertThat(dataFormat.getObjectMapper()).isSameAs(objectMapper);
                assertThat(unmarshalled).isInstanceOf(Customer.class);
                Customer customer = (Customer) unmarshalled;
                assertThat(customer.getCustomerId()).isEqualTo("C-8");
                assertThat(customer.getDisplayName()).isEqualTo("Grace Hopper");
            } finally {
                dataFormat.stop();
            }
        }
    }

    @Test
    void jacksonTypeConverterHandlesJsonNodesAndPojoFallbackConversions() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
            camelContext.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonTypeConverters converters = new JacksonTypeConverters();

            JsonNode node = converters.toJsonNode("{\"answer\":42,\"enabled\":true}", exchange);
            Map<String, Object> map = converters.toMap(node, exchange);
            Integer integer = converters.toInteger(node.get("answer"), exchange);
            Boolean bool = converters.toBoolean(node.get("enabled"), exchange);

            assertThat(map).containsEntry("answer", 42).containsEntry("enabled", true);
            assertThat(integer).isEqualTo(42);
            assertThat(bool).isTrue();

            Event event = converters.convertTo(Event.class, exchange, "{\"code\":\"E-1\",\"priority\":5}", null);
            String eventJson = converters.convertTo(String.class, exchange, event, null);
            ByteBuffer eventBytes = converters.convertTo(ByteBuffer.class, exchange, event, null);

            assertThat(event.getCode()).isEqualTo("E-1");
            assertThat(event.getPriority()).isEqualTo(5);
            assertThat(eventJson).contains("\"code\":\"E-1\"", "\"priority\":5");
            assertThat(StandardCharsets.UTF_8.decode(eventBytes).toString())
                    .contains("\"code\":\"E-1\"", "\"priority\":5");
        }
    }

    @Test
    void directJacksonTypeConvertersUseSuppliedExchangeContext() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonTypeConverters converters = new JacksonTypeConverters();

            JsonNode node = converters.toJsonNode(Map.of("name", "Camel", "count", 2), exchange);
            byte[] bytes = converters.toByteArray(node, exchange);
            Map<String, Object> map = converters.toMap(node, exchange);

            assertThat(node.get("name").asText()).isEqualTo("Camel");
            assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\"count\":2");
            assertThat(map).containsEntry("name", "Camel").containsEntry("count", 2);
            assertThat(converters.toString(Json.mapper().getNodeFactory().textNode("plain"), exchange))
                    .isEqualTo("plain");
        }
    }

    @Test
    void jsonTransformersConvertBetweenJsonBytesStructsAndPojos() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            byte[] eventJson = "{\"code\":\"T-1\",\"priority\":9}".getBytes(StandardCharsets.UTF_8);
            exchange.getMessage().setBody(new ByteArrayInputStream(eventJson));

            new JsonStructDataTypeTransformer().transform(exchange.getMessage(), null, null);

            assertThat(exchange.getMessage().getBody()).isInstanceOf(JsonNode.class);
            assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo(MimeType.STRUCT.type());
            JsonNode struct = exchange.getMessage().getBody(JsonNode.class);
            assertThat(struct.get("code").asText()).isEqualTo("T-1");

            exchange.setProperty(SchemaHelper.CONTENT_CLASS, Event.class.getName());
            exchange.getMessage().setBody(new ByteArrayInputStream(eventJson));
            JsonPojoDataTypeTransformer pojoTransformer = new JsonPojoDataTypeTransformer();
            pojoTransformer.setCamelContext(camelContext);
            pojoTransformer.transform(exchange.getMessage(), null, null);

            assertThat(exchange.getMessage().getBody()).isInstanceOf(Event.class);
            assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo(MimeType.JAVA_OBJECT.type());
            Event event = exchange.getMessage().getBody(Event.class);
            assertThat(event.getCode()).isEqualTo("T-1");
            assertThat(event.getPriority()).isEqualTo(9);

            new JsonDataTypeTransformer().transform(exchange.getMessage(), null, null);

            assertThat(exchange.getMessage().getBody()).isInstanceOf(byte[].class);
            assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo(MimeType.JSON.type());
            assertThat(new String(exchange.getMessage().getBody(byte[].class), StandardCharsets.UTF_8))
                    .contains("\"code\":\"T-1\"", "\"priority\":9");
        }
    }

    @Test
    void jsonUtilityRecognizesObjectsArraysAndSplitsArrayElements() throws Exception {
        JsonNode array = Json.mapper().readTree("[{\"id\":1},{\"id\":2}]");

        assertThat(Json.isJson(" {\"id\":1} ")).isTrue();
        assertThat(Json.isJson(" [1,2] ")).isTrue();
        assertThat(Json.isJson("not-json")).isFalse();
        assertThat(Json.arrayToJsonBeans(array)).containsExactly("{\"id\":1}", "{\"id\":2}");
    }

    private static String marshalToString(
            JacksonDataFormat dataFormat, Exchange exchange, Object body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        dataFormat.marshal(exchange, body, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    public interface PublicView {
    }

    public interface InternalView extends PublicView {
    }

    public static class Account {
        private String id;
        private String name;
        private String secret;

        public Account() {
        }

        Account(String id, String name, String secret) {
            this.id = id;
            this.name = name;
            this.secret = secret;
        }

        @JsonView(PublicView.class)
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JsonView(PublicView.class)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonView(InternalView.class)
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Customer {
        private String customerId;
        private String displayName;

        public Customer() {
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class LineItem {
        private String sku;
        private int quantity;

        public LineItem() {
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static class Event {
        private String code;
        private int priority;

        public Event() {
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }
}
