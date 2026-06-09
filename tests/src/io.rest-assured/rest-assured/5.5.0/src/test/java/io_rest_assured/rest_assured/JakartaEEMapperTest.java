/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.validation.Schema;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.JakartaEEMapper;
import io.restassured.mapper.ObjectMapperType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.PropertyException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEventHandler;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class JakartaEEMapperTest {
    @Test
    void resolvesGeneratedGroovyClassLiteralHelper() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(JakartaEEMapper.class.getName());

        assertThat(resolvedClass).isEqualTo(JakartaEEMapper.class);
    }

    @Test
    void resolvesGeneratedGroovyClassLiteralHelperThroughGroovyInvocation() {
        Object resolvedClass = InvokerHelper.invokeStaticMethod(
                JakartaEEMapper.class,
                "class$",
                new Object[] {JakartaEEMapper.class.getName()});

        assertThat(resolvedClass).isEqualTo(JakartaEEMapper.class);
    }

    @Test
    void resolvesGeneratedGroovyClassLiteralHelperThroughAccessibleMethod() throws Exception {
        Method classHelper = JakartaEEMapper.class.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);

        Object resolvedClass = classHelper.invoke(null, JakartaEEMapper.class.getName());

        assertThat(resolvedClass).isEqualTo(JakartaEEMapper.class);
    }

    @Test
    void serializesXmlWithExplicitJakartaEeObjectMapper() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payload", JakartaEEMapperTest::handlePayload);
        server.start();
        RestAssured.reset();

        try {
            RestAssuredConfig config = RestAssuredConfig.config().objectMapperConfig(
                    objectMapperConfig().jakartaEEObjectMapperFactory((type, charset) -> new TestJaxbContext()));

            int statusCode = given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType("application/xml; charset=UTF-8")
                    .body(new Payload("request", 7), ObjectMapperType.JAKARTA_EE)
                    .when()
                    .post("/payload")
                    .statusCode();

            assertThat(statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(JakartaEEMapper.class, MethodHandles.lookup());
        MethodHandle classHelper = privateLookup.findStatic(
                JakartaEEMapper.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handlePayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean jakartaMapperWasUsed = "POST".equals(exchange.getRequestMethod())
                    && body.contains("<payload encoding=\"UTF-8\">")
                    && body.contains("<message>request</message>")
                    && body.contains("<count>7</count>");
            byte[] responseBytes = (jakartaMapperWasUsed ? "accepted" : "unexpected")
                    .getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    jakartaMapperWasUsed ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    public static class Payload {
        public final String message;
        public final int count;

        Payload(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    private static final class TestJaxbContext extends JAXBContext {
        @Override
        public Unmarshaller createUnmarshaller() {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public Marshaller createMarshaller() {
            return new TestMarshaller();
        }
    }

    private static final class TestMarshaller implements Marshaller {
        private final Map<String, Object> properties = new HashMap<>();

        @Override
        public void marshal(Object object, Result result) {
            throw new UnsupportedOperationException("Result marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, OutputStream outputStream) throws JAXBException {
            try {
                outputStream.write(xmlFor((Payload) object).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new JAXBException(e);
            }
        }

        @Override
        public void marshal(Object object, File file) {
            throw new UnsupportedOperationException("File marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, Writer writer) throws JAXBException {
            try {
                writer.write(xmlFor((Payload) object));
            } catch (IOException e) {
                throw new JAXBException(e);
            }
        }

        @Override
        public void marshal(Object object, ContentHandler handler) {
            throw new UnsupportedOperationException("SAX marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, Node node) {
            throw new UnsupportedOperationException("DOM marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, XMLStreamWriter writer) {
            throw new UnsupportedOperationException("XMLStreamWriter marshalling is not used by this test");
        }

        @Override
        public void marshal(Object object, XMLEventWriter writer) {
            throw new UnsupportedOperationException("XMLEventWriter marshalling is not used by this test");
        }

        @Override
        public Node getNode(Object object) {
            throw new UnsupportedOperationException("DOM node access is not used by this test");
        }

        @Override
        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }

        @Override
        public Object getProperty(String name) throws PropertyException {
            if (properties.containsKey(name)) {
                return properties.get(name);
            }
            throw new PropertyException(name);
        }

        @Override
        public void setEventHandler(ValidationEventHandler handler) {
        }

        @Override
        public ValidationEventHandler getEventHandler() {
            return null;
        }

        @Override
        public <A extends XmlAdapter<?, ?>> void setAdapter(A adapter) {
        }

        @Override
        public <A extends XmlAdapter<?, ?>> void setAdapter(Class<A> type, A adapter) {
        }

        @Override
        public <A extends XmlAdapter<?, ?>> A getAdapter(Class<A> type) {
            return null;
        }

        @Override
        public void setAttachmentMarshaller(AttachmentMarshaller attachmentMarshaller) {
        }

        @Override
        public AttachmentMarshaller getAttachmentMarshaller() {
            return null;
        }

        @Override
        public void setSchema(Schema schema) {
        }

        @Override
        public Schema getSchema() {
            return null;
        }

        @Override
        public void setListener(Marshaller.Listener listener) {
        }

        @Override
        public Marshaller.Listener getListener() {
            return null;
        }

        private String xmlFor(Payload payload) {
            return "<payload encoding=\"" + properties.get(JAXB_ENCODING) + "\">"
                    + "<message>" + payload.message + "</message>"
                    + "<count>" + payload.count + "</count>"
                    + "</payload>";
        }
    }
}
