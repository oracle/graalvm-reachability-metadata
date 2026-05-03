/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_json.jakarta_json_api;

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

import static org.assertj.core.api.Assertions.assertThat;

public class JsonProviderTest {
    @Test
    void providerInstantiatesConfiguredProviderClass() {
        System.setProperty(JsonProvider.JSONP_PROVIDER_FACTORY, TestJsonProvider.class.getName());
        TestJsonProvider.constructed = false;

        JsonProvider provider = JsonProvider.provider();

        assertThat(provider).isInstanceOf(TestJsonProvider.class);
        assertThat(TestJsonProvider.constructed).isTrue();
    }

    public static class TestJsonProvider extends JsonProvider {
        private static boolean constructed;

        public TestJsonProvider() {
            constructed = true;
        }

        @Override
        public JsonParser createParser(Reader reader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonParser createParser(InputStream in) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonParserFactory createParserFactory(Map<String, ?> config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonGenerator createGenerator(Writer writer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonGeneratorFactory createGeneratorFactory(Map<String, ?> config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonReader createReader(Reader reader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonReader createReader(InputStream in) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonWriter createWriter(Writer writer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonWriter createWriter(OutputStream out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonWriterFactory createWriterFactory(Map<String, ?> config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonReaderFactory createReaderFactory(Map<String, ?> config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonObjectBuilder createObjectBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonArrayBuilder createArrayBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonBuilderFactory createBuilderFactory(Map<String, ?> config) {
            throw new UnsupportedOperationException();
        }
    }
}
