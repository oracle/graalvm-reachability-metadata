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
import java.sql.Types;
import oracle.jdbc.oracore.OracleTypeCOLLECTION;
import org.junit.jupiter.api.Test;

public class OracleTypeCOLLECTIONTest {
    @Test
    void retainsCollectionDefaultsAcrossSerialization() throws Exception {
        OracleTypeCOLLECTION original = new OracleTypeCOLLECTION("APP.TEST_COLLECTION", null);

        OracleTypeCOLLECTION restored = roundTrip(original);

        assertThat(restored.getTypeCode()).isEqualTo(Types.ARRAY);
        assertThat(restored.getElementType()).isNull();
        assertThat(restored.getUserCode()).isZero();
        assertThat(restored.getMaxLength()).isZero();
    }

    private static OracleTypeCOLLECTION roundTrip(OracleTypeCOLLECTION type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OracleTypeCOLLECTION) input.readObject();
        }
    }
}
