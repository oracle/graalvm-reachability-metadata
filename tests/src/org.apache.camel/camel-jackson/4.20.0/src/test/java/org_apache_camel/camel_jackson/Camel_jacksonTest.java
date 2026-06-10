/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_jackson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.component.jackson.converter.JacksonTypeConverters;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Camel_jacksonTest {
    @Test
    void marshalsMapToJsonAndSetsContentTypeHeader() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonDataFormat dataFormat = new JacksonDataFormat();
            dataFormat.setPrettyPrint(true);
            start(dataFormat, camelContext);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Camel");
            body.put("active", true);
            body.put("attempts", 3);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                dataFormat.marshal(exchange, body, output);
            } finally {
                dataFormat.stop();
            }

            String json = output.toString(StandardCharsets.UTF_8);
            assertThat(json).contains(System.lineSeparator()).contains("\"name\" : \"Camel\"");
            assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("application/json");
        }
    }

    @Test
    void unmarshalsObjectAndArrayPayloadsDirectlyThroughDataFormats() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonDataFormat mapFormat = new JacksonDataFormat();
            mapFormat.useMap();
            start(mapFormat, camelContext);
            ListJacksonDataFormat listFormat = start(new ListJacksonDataFormat(LinkedHashMap.class), camelContext);

            try {
                Object object = mapFormat.unmarshal(exchange, new ByteArrayInputStream(bytes("""
                        {"id":"route-1","retries":2,"enabled":true}
                        """)));
                Object list = listFormat.unmarshal(exchange, bytes("""
                        [{"id":"route-1"},{"id":"route-2"}]
                        """));

                assertThat(object).isInstanceOf(LinkedHashMap.class);
                assertThat((Map<String, Object>) object)
                        .containsEntry("id", "route-1")
                        .containsEntry("retries", 2)
                        .containsEntry("enabled", true);
                assertThat(list).isInstanceOf(List.class);
                assertThat((List<Map<String, Object>>) list)
                        .extracting(entry -> entry.get("id"))
                        .containsExactly("route-1", "route-2");
            } finally {
                listFormat.stop();
                mapFormat.stop();
            }
        }
    }

    @Test
    void supportsHeaderSelectedUnmarshalTypeAndMultipleInputKinds() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonDataFormat dataFormat = start(new JacksonDataFormat(LinkedHashMap.class), camelContext);
            dataFormat.setAllowUnmarshallType(true);
            exchange.getIn().setHeader(JacksonConstants.UNMARSHAL_TYPE, TreeMap.class.getName());

            try {
                Object fromString = dataFormat.unmarshal(exchange, "{\"b\":2,\"a\":1}");
                Object fromReader = dataFormat.unmarshal(exchange, new StringReader("{\"name\":\"reader\"}"));
                JsonNode jsonNode = dataFormat.getObjectMapper().readTree("{\"name\":\"node\"}");
                Object fromJsonNode = dataFormat.unmarshal(exchange, jsonNode);

                assertThat(fromString).isInstanceOf(TreeMap.class);
                assertThat(((Map<String, Object>) fromString).keySet()).containsExactly("a", "b");
                assertThat(fromReader).isInstanceOf(TreeMap.class);
                assertThat((Map<String, Object>) fromReader).containsEntry("name", "reader");
                assertThat(fromJsonNode).isInstanceOf(TreeMap.class);
                assertThat((Map<String, Object>) fromJsonNode).containsEntry("name", "node");
            } finally {
                dataFormat.stop();
            }
        }
    }

    @Test
    void convertsBetweenJsonNodeAndCamelFriendlyTypes() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonTypeConverters converters = new JacksonTypeConverters();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("answer", 42);
            source.put("flag", true);
            source.put("ratio", 2.5D);

            JsonNode node = converters.toJsonNode(source, exchange);
            String rendered = converters.toString(node, exchange);
            byte[] renderedBytes = converters.toByteArray(node, exchange);
            InputStream renderedStream = converters.toInputStream(node, exchange);
            Reader renderedReader = converters.toReader(node, exchange);
            Map<String, Object> convertedMap = converters.toMap(node, exchange);

            assertThat(node.path("answer").asInt()).isEqualTo(42);
            assertThat(rendered).contains("\"answer\" : 42");
            assertThat(converters.toJsonNode(renderedBytes, exchange).path("flag").asBoolean()).isTrue();
            assertThat(converters.toJsonNode(renderedStream, exchange).path("ratio").asDouble()).isEqualTo(2.5D);
            assertThat(converters.toJsonNode(renderedReader, exchange).path("answer").asInt()).isEqualTo(42);
            assertThat(convertedMap).containsEntry("answer", 42).containsEntry("flag", true);
            assertThat(converters.toInteger(node.path("answer"), exchange)).isEqualTo(42);
            assertThat(converters.toBoolean(node.path("flag"), exchange)).isTrue();
            assertThat(converters.toDouble(node.path("ratio"), exchange)).isEqualTo(2.5D);
        }
    }

    @Test
    void typeConverterHonorsGlobalOptionsForJsonRenderingAndParsing() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
            camelContext.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
            Exchange exchange = new DefaultExchange(camelContext);
            JacksonTypeConverters converters = new JacksonTypeConverters();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("component", "jackson");
            source.put("count", 4);

            String json = converters.convertTo(String.class, exchange, source, null);
            byte[] jsonBytes = converters.convertTo(byte[].class, exchange, source, null);
            Map<String, Object> parsedFromString = converters.convertTo(Map.class, exchange, json, null);
            Map<String, Object> parsedFromStream = converters.convertTo(
                    Map.class, exchange, new ByteArrayInputStream(jsonBytes), null);

            assertThat(json).contains("\"component\":\"jackson\"");
            assertThat(parsedFromString).containsEntry("component", "jackson").containsEntry("count", 4);
            assertThat(parsedFromStream).containsEntry("component", "jackson").containsEntry("count", 4);
        }
    }

    private static <T extends JacksonDataFormat> T start(T dataFormat, DefaultCamelContext camelContext) {
        dataFormat.setCamelContext(camelContext);
        dataFormat.start();
        return dataFormat;
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
