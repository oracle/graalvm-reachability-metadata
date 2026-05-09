/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ReflectDataTest {
    @Test
    void setFieldAndGetFieldAccessPrivateRecordField() {
        ReflectData reflectData = new ReflectData(ReflectDataTest.class.getClassLoader());
        MutableRecord record = new MutableRecord();

        reflectData.setField(record, "name", 0, "Ada Lovelace");
        reflectData.setField(record, "score", 1, 42);

        assertEquals("Ada Lovelace", reflectData.getField(record, "name", 0));
        assertEquals(42, reflectData.getField(record, "score", 1));
    }

    @Test
    void getSchemaDiscoversRecordFieldsAcrossClassHierarchy() {
        ReflectData reflectData = new ReflectData(ReflectDataTest.class.getClassLoader());

        Schema schema = reflectData.getSchema(ChildRecord.class);

        assertEquals(Schema.Type.RECORD, schema.getType());
        assertNotNull(schema.getField("parentId"));
        assertNotNull(schema.getField("childName"));
        assertNotNull(schema.getField("nickname"));
        assertEquals(Schema.Type.UNION, schema.getField("nickname").schema().getType());
    }

    @Test
    void getProtocolDiscoversInterfaceMethods() {
        ReflectData reflectData = new ReflectData(ReflectDataTest.class.getClassLoader());

        Protocol protocol = reflectData.getProtocol(GreetingService.class);

        assertEquals("GreetingService", protocol.getName());
        assertNotNull(protocol.getMessages().get("greet"));
        assertNotNull(protocol.getMessages().get("reset"));
    }

    @Test
    void getClassUsesJavaClassPropertyAndCreatesArrayClass() {
        ReflectData reflectData = new ReflectData(ReflectDataTest.class.getClassLoader());
        Schema stringSchema = Schema.create(Schema.Type.STRING);
        stringSchema.addProp("java-class", StringBuilder.class.getName());
        Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));

        assertSame(StringBuilder.class, reflectData.getClass(stringSchema));
        assertSame(String[].class, reflectData.getClass(arraySchema));
    }

    private interface GreetingService {
        String greet();

        void reset();
    }

    private static final class MutableRecord {
        private String name;
        private int score;
    }

    private static class ParentRecord {
        private long parentId;
    }

    private static final class ChildRecord extends ParentRecord {
        private String childName;

        @Nullable
        private String nickname;
    }
}
