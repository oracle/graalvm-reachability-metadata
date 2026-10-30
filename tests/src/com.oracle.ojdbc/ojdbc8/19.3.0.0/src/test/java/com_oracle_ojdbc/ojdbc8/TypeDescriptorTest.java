/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.OracleTypeMetaData;
import oracle.jdbc.oracore.OracleTypeADT;
import oracle.jdbc.oracore.Util;
import oracle.sql.CharacterSet;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;
import oracle.sql.TypeDescriptor;
import org.junit.jupiter.api.Test;

public class TypeDescriptorTest {
    @Test
    void describesAndTransportsTheIdentityOfAStructuredSqlType() throws Exception {
        byte[] identifier = {11, 12, 13};
        OracleTypeADT type = new OracleTypeADT(identifier, 3,
                CharacterSet.AL32UTF8_CHARSET, (short) 1, "APP.POSTAL_ADDRESS");
        TypeDescriptor descriptor = new StructDescriptor(new SQLName("APP", "POSTAL_ADDRESS", null), type, null);

        TypeDescriptor number = new NumberDescriptor();
        assertThat(number.getTypeCode()).isEqualTo(TypeDescriptor.TYPECODE_NUMBER);
        assertThat(number.getTypeCodeName()).isEqualTo("TYPECODE_NUMBER");
        TypeDescriptor restored = (TypeDescriptor) Util.deserializeObject(Util.serializeObject(descriptor));

        assertThat(restored.getName()).isEqualTo("APP.POSTAL_ADDRESS");
        assertThat(restored.getSchemaName()).isEqualTo("APP");
        assertThat(restored.getTypeName()).isEqualTo("POSTAL_ADDRESS");
        assertThat(restored.getKind()).isEqualTo(OracleTypeMetaData.Kind.STRUCT);
        assertThat(restored.getPickler().getTypeVersion()).isEqualTo(3);
        assertThat(((OracleTypeADT) restored.getPickler()).getTOID()).containsExactly(identifier);
    }

    private static final class NumberDescriptor extends TypeDescriptor {
        private NumberDescriptor() {
            super(TYPECODE_NUMBER);
        }
    }
}
