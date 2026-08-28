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
import oracle.jdbc.oracore.OracleTypeADT;
import org.junit.jupiter.api.Test;

public class OracleTypeADTTest {
    @Test
    void retainsTypeIdentityAcrossSerialization() throws Exception {
        byte[] typeId = {1, 3, 5, 7};
        OracleTypeADT original = new OracleTypeADT(typeId, 4, 873, (short) 1, "APP.TEST_OBJECT");

        OracleTypeADT restored = roundTrip(original);

        assertThat(restored.getTOID()).containsExactly(typeId);
        assertThat(restored.getTypeVersion()).isEqualTo(4);
        assertThat(restored.getCharSet()).isEqualTo(873);
        assertThat(restored.getCharSetForm()).isEqualTo(1);
    }

    private static OracleTypeADT roundTrip(OracleTypeADT type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OracleTypeADT) input.readObject();
        }
    }
}
