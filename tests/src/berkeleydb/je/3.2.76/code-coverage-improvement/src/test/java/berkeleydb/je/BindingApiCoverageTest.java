/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.SerialInput;
import com.sleepycat.bind.serial.SerialSerialBinding;
import com.sleepycat.bind.serial.SerialSerialKeyCreator;
import com.sleepycat.bind.serial.TupleSerialBinding;
import com.sleepycat.bind.serial.TupleSerialKeyCreator;
import com.sleepycat.bind.serial.TupleSerialMarshalledBinding;
import com.sleepycat.bind.serial.TupleSerialMarshalledKeyCreator;
import com.sleepycat.bind.tuple.MarshalledTupleEntry;
import com.sleepycat.bind.tuple.MarshalledTupleKeyEntity;
import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.bind.tuple.TupleTupleBinding;
import com.sleepycat.bind.tuple.TupleTupleKeyCreator;
import com.sleepycat.bind.tuple.TupleTupleMarshalledBinding;
import com.sleepycat.bind.tuple.TupleTupleMarshalledKeyCreator;
import com.sleepycat.je.DatabaseEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BindingApiCoverageTest {

    @Test
    void serialAndTupleSerialBindingsRoundTripTheirKeyAndDataParts() throws Exception {
        Catalog catalog = new Catalog();
        SerialBinding serialBinding = new SerialBinding(catalog, String.class);
        assertThat(serialBinding.getBaseClass()).isEqualTo(String.class);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        SerialSerialBinding pair = new SerialSerialBinding(catalog, String.class, String.class) {
            @Override
            public Object entryToObject(Object keyObject, Object dataObject) {
                return keyObject + ":" + dataObject;
            }

            @Override
            public Object objectToKey(Object object) {
                return "key-" + object;
            }

            @Override
            public Object objectToData(Object object) {
                return "data-" + object;
            }
        };
        pair.objectToKey("value", key);
        pair.objectToData("value", data);
        assertThat(pair.entryToObject(key, data)).isEqualTo("key-value:data-value");
        assertThat(new SerialSerialBinding(serialBinding, serialBinding) {
            @Override
            public Object entryToObject(Object keyObject, Object dataObject) {
                return keyObject;
            }

            @Override
            public Object objectToKey(Object object) {
                return object;
            }

            @Override
            public Object objectToData(Object object) {
                return object;
            }
        }).isNotNull();

        TupleSerialBinding tupleSerial = new TupleSerialBinding(catalog, String.class) {
            @Override
            public Object entryToObject(TupleInput input, Object dataObject) {
                return input.readString() + ":" + dataObject;
            }

            @Override
            public void objectToKey(Object object, TupleOutput output) {
                output.writeString("key-" + object);
            }

            @Override
            public Object objectToData(Object object) {
                return "data-" + object;
            }
        };
        tupleSerial.objectToKey("value", key);
        tupleSerial.objectToData("value", data);
        assertThat(tupleSerial.entryToObject(key, data)).isEqualTo("key-value:data-value");
        assertThat(new TupleSerialBinding(serialBinding) {
            @Override
            public Object entryToObject(TupleInput input, Object dataObject) {
                return dataObject;
            }

            @Override
            public void objectToKey(Object object, TupleOutput output) {
                output.writeString(String.valueOf(object));
            }

            @Override
            public Object objectToData(Object object) {
                return object;
            }
        }).isNotNull();
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(serialized);
        objectOutput.writeObject("stream");
        objectOutput.close();
        assertThat(new SerialInput(new ByteArrayInputStream(serialized.toByteArray()), catalog))
                .isNotNull();
    }

    @Test
    void keyCreatorsExtractAndNullifySecondaryKeys() throws Exception {
        Catalog catalog = new Catalog();
        SerialSerialKeyCreator serialCreator = new SerialSerialKeyCreator(
                catalog, String.class, String.class, String.class) {
            @Override
            public Object createSecondaryKey(Object primaryKey, Object data) {
                return "secondary-" + data;
            }
        };
        assertThat(serialCreator.createSecondaryKey("primary", "data")).isEqualTo("secondary-data");
        DatabaseEntry serialPrimary = new DatabaseEntry();
        DatabaseEntry serialData = new DatabaseEntry();
        new SerialBinding(catalog, String.class).objectToEntry("primary", serialPrimary);
        DatabaseEntry serialIndex = new DatabaseEntry();
        new SerialBinding(catalog, String.class).objectToEntry("data", serialData);
        assertThat(serialCreator.createSecondaryKey(null, serialPrimary, serialData, serialIndex))
                .isTrue();
        assertThat(serialCreator.nullifyForeignKey(null, serialData)).isFalse();
        assertThat(serialCreator.nullifyForeignKey("data")).isNull();
        assertThat(new SerialSerialKeyCreator(new SerialBinding(catalog, String.class),
                new SerialBinding(catalog, String.class), new SerialBinding(catalog, String.class)) {
            @Override
            public Object createSecondaryKey(Object primaryKey, Object data) {
                return data;
            }
        }).isNotNull();

        TupleSerialBinding tupleBinding = new TupleSerialBinding(catalog, String.class) {
            @Override
            public Object entryToObject(TupleInput input, Object dataObject) {
                return dataObject;
            }

            @Override
            public void objectToKey(Object object, TupleOutput output) {
                output.writeString(String.valueOf(object));
            }

            @Override
            public Object objectToData(Object object) {
                return object;
            }
        };
        TupleSerialKeyCreator tupleCreator = new TupleSerialKeyCreator(
                new SerialBinding(catalog, String.class)) {
            @Override
            public boolean createSecondaryKey(TupleInput input, Object data, TupleOutput output) {
                output.writeString(String.valueOf(data));
                return true;
            }
        };
        assertThat(tupleCreator.createSecondaryKey(new TupleInput(new TupleOutput()), "data",
                new TupleOutput())).isTrue();
        DatabaseEntry tuplePrimary = new DatabaseEntry();
        DatabaseEntry tupleData = new DatabaseEntry();
        DatabaseEntry tupleIndex = new DatabaseEntry();
        new SerialBinding(catalog, String.class).objectToEntry("data", tupleData);
        TupleBase.inputToEntry(new TupleInput(new TupleOutput()), tuplePrimary);
        assertThat(tupleCreator.createSecondaryKey(null, tuplePrimary, tupleData, tupleIndex))
                .isTrue();
        assertThat(tupleCreator.nullifyForeignKey(null, tupleData)).isFalse();
        TupleSerialKeyCreator classCatalogCreator = new TupleSerialKeyCreator(catalog, String.class) {
            @Override
            public boolean createSecondaryKey(TupleInput input, Object data, TupleOutput output) {
                output.writeString(String.valueOf(data));
                return true;
            }
        };
        assertThat(classCatalogCreator).isNotNull();
        assertThat(tupleCreator.nullifyForeignKey("data")).isNull();
        TupleSerialMarshalledBinding marshalled = new TupleSerialMarshalledBinding(catalog,
                MarshalledValue.class);
        MarshalledValue marshalledValue = new MarshalledValue("tuple");
        DatabaseEntry marshalledKey = new DatabaseEntry();
        DatabaseEntry marshalledData = new DatabaseEntry();
        marshalled.objectToKey(marshalledValue, marshalledKey);
        marshalled.objectToData(marshalledValue, marshalledData);
        assertThat(marshalled.entryToObject(TupleBase.entryToInput(marshalledKey),
                marshalledValue)).isNotNull();
        TupleSerialMarshalledKeyCreator marshalledCreator =
                new TupleSerialMarshalledKeyCreator(marshalled, "secondary");
        MarshalledValue value = new MarshalledValue("value");
        assertThat(marshalledCreator.createSecondaryKey(TupleBase.entryToInput(marshalledKey),
                value, new TupleOutput())).isTrue();
        assertThat(marshalledCreator.nullifyForeignKey(value)).isSameAs(value);
    }

    @Test
    void tupleTupleBindingsAndCreatorsUseTupleEntries() throws Exception {
        com.sleepycat.bind.serial.SerialBase serialBase = new com.sleepycat.bind.serial.SerialBase();
        serialBase.setSerialBufferSize(128);
        assertThat(serialBase.getSerialBufferSize()).isEqualTo(128);
        TupleTupleBinding binding = new TupleTupleBinding() {
            @Override
            public Object entryToObject(TupleInput key, TupleInput data) {
                return key.readString() + ":" + data.readString();
            }

            @Override
            public void objectToKey(Object object, TupleOutput output) {
                output.writeString("key-" + object);
            }

            @Override
            public void objectToData(Object object, TupleOutput output) {
                output.writeString("data-" + object);
            }
        };
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        binding.objectToKey("value", key);
        binding.objectToData("value", data);
        assertThat(binding.entryToObject(key, data)).isEqualTo("key-value:data-value");

        TupleTupleKeyCreator creator = new TupleTupleKeyCreator() {
            @Override
            public boolean createSecondaryKey(TupleInput primaryKey, TupleInput data,
                    TupleOutput indexKey) {
                indexKey.writeString("index");
                return true;
            }
        };
        assertThat(creator.createSecondaryKey(TupleBase.entryToInput(key), TupleBase.entryToInput(data),
                new TupleOutput())).isTrue();
        assertThat(creator.createSecondaryKey(null, key, data, new DatabaseEntry())).isTrue();
        assertThat(creator.nullifyForeignKey(TupleBase.entryToInput(key), new TupleOutput())).isFalse();
        assertThat(creator.nullifyForeignKey(null, data)).isFalse();
        TupleTupleMarshalledBinding marshalled = new TupleTupleMarshalledBinding(MarshalledValue.class);
        DatabaseEntry marshalledKey = new DatabaseEntry();
        DatabaseEntry marshalledData = new DatabaseEntry();
        marshalled.objectToKey(new MarshalledValue("key"), marshalledKey);
        marshalled.objectToData(new MarshalledValue("data"), marshalledData);
        assertThat(marshalled.entryToObject(marshalledKey, marshalledData)).isNotNull();
        TupleTupleMarshalledKeyCreator marshalledCreator =
                new TupleTupleMarshalledKeyCreator(marshalled, "secondary");
        assertThat(marshalledCreator.createSecondaryKey(TupleBase.entryToInput(marshalledKey),
                TupleBase.entryToInput(marshalledData), new TupleOutput())).isTrue();
        assertThat(marshalledCreator.nullifyForeignKey(TupleBase.entryToInput(marshalledKey),
                new TupleOutput())).isTrue();
    }

    private static final class Catalog implements ClassCatalog {
        private final Map<String, byte[]> ids = new HashMap<>();
        private final Map<ByteKey, ObjectStreamClass> formats = new HashMap<>();
        private int nextId;

        @Override
        public void close() {
        }

        @Override
        public byte[] getClassID(ObjectStreamClass classDesc) {
            byte[] id = ids.get(classDesc.getName());
            if (id == null) {
                id = new byte[] {(byte) ++nextId};
                ids.put(classDesc.getName(), id);
                formats.put(new ByteKey(id), classDesc);
            }
            return id;
        }

        @Override
        public ObjectStreamClass getClassFormat(byte[] classID) {
            return formats.get(new ByteKey(classID));
        }
    }

    private static final class ByteKey {
        private final byte[] bytes;

        ByteKey(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(bytes);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof ByteKey
                    && java.util.Arrays.equals(bytes, ((ByteKey) object).bytes);
        }
    }

    public static final class MarshalledValue
            implements Serializable, MarshalledTupleEntry, MarshalledTupleKeyEntity {
        private static final long serialVersionUID = 1L;
        private String value;

        public MarshalledValue() {
        }

        MarshalledValue(String value) {
            this.value = value;
        }

        @Override
        public void marshalEntry(TupleOutput output) {
            output.writeString(value);
        }

        @Override
        public void unmarshalEntry(TupleInput input) {
            value = input.readString();
        }

        @Override
        public void marshalPrimaryKey(TupleOutput output) {
            output.writeString(value);
        }

        @Override
        public void unmarshalPrimaryKey(TupleInput input) {
            value = input.readString();
        }

        @Override
        public boolean marshalSecondaryKey(String fieldName, TupleOutput output) {
            output.writeString(value);
            return true;
        }

        @Override
        public boolean nullifyForeignKey(String fieldName) {
            value = null;
            return true;
        }
    }
}
