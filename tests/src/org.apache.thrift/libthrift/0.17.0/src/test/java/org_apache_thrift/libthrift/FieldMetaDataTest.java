/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_thrift.libthrift;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TFieldRequirementType;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import org.junit.jupiter.api.Test;

public class FieldMetaDataTest {
    @Test
    void getStructMetaDataMapInstantiatesGeneratedStructToRegisterMetadata() {
        Map<? extends TFieldIdEnum, FieldMetaData> metadata =
                FieldMetaData.getStructMetaDataMap(GeneratedStruct.class);

        assertThat(metadata).hasSize(1);
        FieldMetaData nameMetadata = metadata.get(GeneratedField.NAME);
        assertThat(nameMetadata).isNotNull();
        assertThat(nameMetadata.fieldName).isEqualTo("name");
        assertThat(nameMetadata.requirementType).isEqualTo(TFieldRequirementType.DEFAULT);
        assertThat(nameMetadata.valueMetaData.type).isEqualTo(TType.STRING);
    }

    public enum GeneratedField implements TFieldIdEnum {
        NAME((short) 1, "name");

        private final short thriftFieldId;
        private final String fieldName;

        GeneratedField(short thriftFieldId, String fieldName) {
            this.thriftFieldId = thriftFieldId;
            this.fieldName = fieldName;
        }

        @Override
        public short getThriftFieldId() {
            return thriftFieldId;
        }

        @Override
        public String getFieldName() {
            return fieldName;
        }
    }

    public static class GeneratedStruct implements TBase<GeneratedStruct, GeneratedField> {
        private String name;

        static {
            Map<GeneratedField, FieldMetaData> metadata = new EnumMap<>(GeneratedField.class);
            metadata.put(
                    GeneratedField.NAME,
                    new FieldMetaData(
                            GeneratedField.NAME.getFieldName(),
                            TFieldRequirementType.DEFAULT,
                            new FieldValueMetaData(TType.STRING)));
            FieldMetaData.addStructMetaDataMap(GeneratedStruct.class, metadata);
        }

        @Override
        public GeneratedField fieldForId(int fieldId) {
            if (fieldId == GeneratedField.NAME.getThriftFieldId()) {
                return GeneratedField.NAME;
            }
            return null;
        }

        @Override
        public boolean isSet(GeneratedField field) {
            return field == GeneratedField.NAME && name != null;
        }

        @Override
        public Object getFieldValue(GeneratedField field) {
            if (field == GeneratedField.NAME) {
                return name;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        public void setFieldValue(GeneratedField field, Object value) {
            if (field != GeneratedField.NAME) {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
            name = (String) value;
        }

        @Override
        public GeneratedStruct deepCopy() {
            GeneratedStruct copy = new GeneratedStruct();
            copy.name = name;
            return copy;
        }

        @Override
        public void clear() {
            name = null;
        }

        @Override
        public void read(TProtocol iprot) throws TException {
            throw new UnsupportedOperationException("Protocol read is not needed for metadata registration");
        }

        @Override
        public void write(TProtocol oprot) throws TException {
            throw new UnsupportedOperationException("Protocol write is not needed for metadata registration");
        }

        @Override
        public int compareTo(GeneratedStruct other) {
            if (name == null && other.name == null) {
                return 0;
            }
            if (name == null) {
                return -1;
            }
            if (other.name == null) {
                return 1;
            }
            return name.compareTo(other.name);
        }
    }
}
