/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import oracle.jdbc.OracleTypeMetaData;
import oracle.jdbc.oracore.OracleTypeADT;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;
import oracle.sql.TypeDescriptor;
import org.junit.jupiter.api.Test;

public class TypeDescriptorTest {
    @Test
    void mapsInternalTypeCodesToTheirNames() throws Exception {
        TypeDescriptor descriptor = new NumberTypeDescriptor();

        assertThat(descriptor.getTypeCode()).isEqualTo(TypeDescriptor.TYPECODE_NUMBER);
        assertThat(descriptor.getTypeCodeName()).isEqualTo("TYPECODE_NUMBER");
    }

    @Test
    void retainsNamedTypeInformationAcrossSerialization() throws Exception {
        byte[] typeId = {9, 8, 7};
        OracleTypeADT pickler = new OracleTypeADT(typeId, 3, 873, (short) 1, "APP.TEST_OBJECT");
        SQLName sqlName = new SQLName("APP", "TEST_OBJECT", null);
        TypeDescriptor original = new StructDescriptor(sqlName, pickler, null);

        TypeDescriptor restored = roundTrip(original);

        assertThat(restored.getName()).isEqualTo("APP.TEST_OBJECT");
        assertThat(restored.getKind()).isEqualTo(OracleTypeMetaData.Kind.STRUCT);
        assertThat(((OracleTypeADT) restored.getPickler()).getTOID()).containsExactly(typeId);
    }

    private static TypeDescriptor roundTrip(TypeDescriptor descriptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(descriptor);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (TypeDescriptor) input.readObject();
        }
    }

    private static final class NumberTypeDescriptor extends TypeDescriptor {
        private NumberTypeDescriptor() {
            super(TYPECODE_NUMBER);
        }
    }
}
