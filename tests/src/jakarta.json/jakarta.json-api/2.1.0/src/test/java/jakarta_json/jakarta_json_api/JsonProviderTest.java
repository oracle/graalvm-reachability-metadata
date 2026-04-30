/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_json.jakarta_json_api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import org.junit.jupiter.api.Test;

public class JsonProviderTest {
    @Test
    void providerCreatesConfiguredProviderWithPublicNoArgumentConstructor() {
        System.setProperty(JsonProvider.JSONP_PROVIDER_FACTORY, ConfiguredJsonProvider.class.getName());

        JsonProvider provider = JsonProvider.provider();
        JsonProvider secondProvider = JsonProvider.provider();

        assertInstanceOf(ConfiguredJsonProvider.class, provider);
        assertInstanceOf(ConfiguredJsonProvider.class, secondProvider);
        assertNotSame(provider, secondProvider);
    }

    public static class ConfiguredJsonProvider extends JsonProvider {
        public ConfiguredJsonProvider() {
        }

        @Override
        public JsonParser createParser(Reader reader) {
            throw unsupportedOperation();
        }

        @Override
        public JsonParser createParser(InputStream in) {
            throw unsupportedOperation();
        }

        @Override
        public JsonParserFactory createParserFactory(Map<String, ?> config) {
            throw unsupportedOperation();
        }

        @Override
        public JsonGenerator createGenerator(Writer writer) {
            throw unsupportedOperation();
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out) {
            throw unsupportedOperation();
        }

        @Override
        public JsonGeneratorFactory createGeneratorFactory(Map<String, ?> config) {
            throw unsupportedOperation();
        }

        @Override
        public JsonReader createReader(Reader reader) {
            throw unsupportedOperation();
        }

        @Override
        public JsonReader createReader(InputStream in) {
            throw unsupportedOperation();
        }

        @Override
        public JsonWriter createWriter(Writer writer) {
            throw unsupportedOperation();
        }

        @Override
        public JsonWriter createWriter(OutputStream out) {
            throw unsupportedOperation();
        }

        @Override
        public JsonWriterFactory createWriterFactory(Map<String, ?> config) {
            throw unsupportedOperation();
        }

        @Override
        public JsonReaderFactory createReaderFactory(Map<String, ?> config) {
            throw unsupportedOperation();
        }

        @Override
        public JsonObjectBuilder createObjectBuilder() {
            throw unsupportedOperation();
        }

        @Override
        public JsonArrayBuilder createArrayBuilder() {
            throw unsupportedOperation();
        }

        @Override
        public JsonBuilderFactory createBuilderFactory(Map<String, ?> config) {
            throw unsupportedOperation();
        }

        private static UnsupportedOperationException unsupportedOperation() {
            return new UnsupportedOperationException("Only provider discovery is exercised by this test");
        }
    }
}
