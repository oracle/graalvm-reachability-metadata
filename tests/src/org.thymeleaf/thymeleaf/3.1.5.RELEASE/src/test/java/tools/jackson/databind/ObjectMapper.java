/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools.jackson.databind;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Map;

import tools.jackson.core.json.JsonWriteFeature;

public class ObjectMapper {

    private final JsonFactory factory = new JsonFactory();
    private DateFormat dateFormat;

    public ObjectMapper() {
    }

    public void findAndRegisterModules() {
    }

    public void findAndAddModules() {
    }

    public ObjectMapper configure(SerializationFeature feature, boolean state) {
        return this;
    }

    public ObjectMapper disable(SerializationFeature feature) {
        return this;
    }

    public JsonFactory getFactory() {
        return this.factory;
    }

    public void setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    public DateFormat getDateFormat() {
        return this.dateFormat;
    }

    public void writeValue(Writer writer, Object object) throws IOException {
        writeJson(writer, object);
    }

    private static void writeJson(Writer writer, Object object) throws IOException {
        if (object == null) {
            writer.write("null");
        } else if (object instanceof CharSequence) {
            writer.write('"');
            writer.write(object.toString());
            writer.write('"');
        } else if (object instanceof Number || object instanceof Boolean) {
            writer.write(object.toString());
        } else if (object instanceof Map<?, ?> map) {
            writeMap(writer, map);
        } else if (object instanceof Collection<?> collection) {
            writeCollection(writer, collection);
        } else {
            writer.write('"');
            writer.write(object.toString());
            writer.write('"');
        }
    }

    private static void writeMap(Writer writer, Map<?, ?> map) throws IOException {
        writer.write('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                writer.write(',');
            }
            writeJson(writer, entry.getKey().toString());
            writer.write(':');
            writeJson(writer, entry.getValue());
        }
        writer.write('}');
    }

    private static void writeCollection(Writer writer, Collection<?> collection) throws IOException {
        writer.write('[');
        boolean first = true;
        for (Object element : collection) {
            if (first) {
                first = false;
            } else {
                writer.write(',');
            }
            writeJson(writer, element);
        }
        writer.write(']');
    }

    public static final class JsonFactory {

        private boolean escapeNonAscii;

        public void configure(JsonWriteFeature feature, boolean state) {
            if (feature == JsonWriteFeature.ESCAPE_NON_ASCII) {
                this.escapeNonAscii = state;
            }
        }

        public boolean isEscapeNonAscii() {
            return this.escapeNonAscii;
        }
    }
}
