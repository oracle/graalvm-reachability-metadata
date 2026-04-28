/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.Serializable;
import java.util.Objects;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableConverterAnonymous1Test {
    @Test
    void serializesCustomPutFieldsThroughSerializableConverter() {
        XStream xstream = newXStream();
        PutFieldsRecord record = new PutFieldsRecord("metadata", 7, new Details("active"));

        String xml = xstream.toXML(record);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("serialization=\"custom\"");
        assertThat(xml).contains("<name>metadata</name>");
        assertThat(xml).contains("<count>7</count>");
        assertThat(restored).isEqualTo(record);
    }

    private static XStream newXStream() {
        XStream xstream = new XStream();
        xstream.alias("put-fields-record", PutFieldsRecord.class);
        xstream.alias("details", Details.class);
        xstream.allowTypes(new Class[]{PutFieldsRecord.class, Details.class});
        return xstream;
    }

    public static final class PutFieldsRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private int count;
        private Details details;

        public PutFieldsRecord(String name, int count, Details details) {
            this.name = name;
            this.count = count;
            this.details = details;
        }

        private void writeObject(ObjectOutputStream stream) throws IOException {
            PutField fields = stream.putFields();
            fields.put("name", name);
            fields.put("count", count);
            fields.put("details", details);
            stream.writeFields();
        }

        private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
            GetField fields = stream.readFields();
            name = (String)fields.get("name", "");
            count = fields.get("count", 0);
            details = (Details)fields.get("details", null);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PutFieldsRecord)) {
                return false;
            }
            PutFieldsRecord that = (PutFieldsRecord)other;
            return count == that.count && Objects.equals(name, that.name) && Objects.equals(details, that.details);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, count, details);
        }
    }

    public static final class Details implements Serializable {
        private static final long serialVersionUID = 1L;

        private String label;

        public Details(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Details)) {
                return false;
            }
            Details details = (Details)other;
            return Objects.equals(label, details.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label);
        }
    }
}
