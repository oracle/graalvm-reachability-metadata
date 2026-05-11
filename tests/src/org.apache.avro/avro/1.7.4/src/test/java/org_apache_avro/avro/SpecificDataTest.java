/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.util.Collections;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpecificDataTest {
    private static final String PACKAGE_NAME = "org_apache_avro.avro";
    private static final String TEST_CLASS_NAMESPACE = PACKAGE_NAME + ".SpecificDataTest$";

    @Test
    void getClassLoadsImplementationNamedBySpecificSchema() {
        SpecificData specificData = new SpecificData(SpecificDataTest.class.getClassLoader());

        Class<?> implementationClass = specificData.getClass(WeatherRecord.SCHEMA$);

        assertSame(WeatherRecord.class, implementationClass);
    }

    @Test
    void getSchemaReadsSpecificSchemaField() {
        SpecificData specificData = new SpecificData(SpecificDataTest.class.getClassLoader());

        Schema schema = specificData.getSchema(WeatherRecord.class);

        assertSame(WeatherRecord.SCHEMA$, schema);
        assertEquals("WeatherRecord", schema.getName());
        assertEquals(TEST_CLASS_NAMESPACE, schema.getNamespace());
    }

    @Test
    void getProtocolReadsSpecificProtocolField() {
        SpecificData specificData = new SpecificData(SpecificDataTest.class.getClassLoader());

        Protocol protocol = specificData.getProtocol(WeatherService.class);

        assertSame(WeatherService.PROTOCOL, protocol);
        assertEquals("WeatherService", protocol.getName());
        assertEquals(PACKAGE_NAME, protocol.getNamespace());
    }

    @Test
    void newInstanceInvokesSchemaConstructableConstructor() {
        Object instance = SpecificData.newInstance(SchemaConstructableRecord.class, WeatherRecord.SCHEMA$);

        SchemaConstructableRecord record = assertInstanceOf(SchemaConstructableRecord.class, instance);
        assertSame(WeatherRecord.SCHEMA$, record.schema);
    }

    public interface WeatherService {
        Protocol PROTOCOL = Protocol.parse("""
                {"protocol":"WeatherService","namespace":"org_apache_avro.avro","types":[],"messages":{}}
                """);
    }

    public static final class WeatherRecord {
        public static final Schema SCHEMA$ = createWeatherRecordSchema();
    }

    public static final class SchemaConstructableRecord implements SpecificData.SchemaConstructable {
        private final Schema schema;

        private SchemaConstructableRecord(Schema schema) {
            this.schema = schema;
        }
    }

    private static Schema createWeatherRecordSchema() {
        Schema schema = Schema.createRecord("WeatherRecord", null, TEST_CLASS_NAMESPACE, false);
        Schema.Field temperatureField = new Schema.Field("temperature", Schema.create(Schema.Type.INT), null, null);
        schema.setFields(Collections.singletonList(temperatureField));
        return schema;
    }
}
