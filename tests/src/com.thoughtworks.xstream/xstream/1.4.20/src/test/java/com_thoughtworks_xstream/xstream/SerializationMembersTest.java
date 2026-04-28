/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationMembersTest {
    @Test
    void invokesWriteReplaceAndReadResolveDuringRoundTrip() {
        XStream xstream = newXStream();

        String xml = xstream.toXML(new WriteReplacingValue("payload"));
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("replacement-payload");
        assertThat(restored).isEqualTo(new ReadResolvedValue("resolved-replacement-payload"));
    }

    @Test
    void invokesCustomSerializationMethodsAndReadsPersistentFieldsDeclaration() {
        XStream xstream = newXStream();

        String xml = xstream.toXML(new CustomSerializedValue("library", 20));
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("library");
        assertThat(xml).contains("20");
        assertThat(restored).isInstanceOfSatisfying(CustomSerializedValue.class, value ->
            assertThat(value.readObjectCalled).isTrue());
    }

    private static XStream newXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{
            WriteReplacingValue.class,
            ReplacementValue.class,
            ReadResolvedValue.class,
            CustomSerializedValue.class
        });
        return xstream;
    }

    public static final class WriteReplacingValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private String value;

        WriteReplacingValue(String value) {
            this.value = value;
        }

        private Object writeReplace() {
            return new ReplacementValue("replacement-" + value);
        }
    }

    public static final class ReplacementValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private String value;

        ReplacementValue(String value) {
            this.value = value;
        }

        private Object readResolve() {
            return new ReadResolvedValue("resolved-" + value);
        }
    }

    public static final class ReadResolvedValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private String value;

        ReadResolvedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReadResolvedValue)) {
                return false;
            }
            ReadResolvedValue that = (ReadResolvedValue)other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static final class CustomSerializedValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("name", String.class),
            new ObjectStreamField("count", Integer.TYPE)
        };

        private String name;
        private int count;
        private transient boolean readObjectCalled;

        CustomSerializedValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        private void writeObject(ObjectOutputStream stream) throws IOException {
            stream.defaultWriteObject();
        }

        private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
            stream.defaultReadObject();
            readObjectCalled = true;
        }
    }
}
