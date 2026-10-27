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
import oracle.jdbc.oracore.OracleTypeUPT;
import org.junit.jupiter.api.Test;

public class OracleTypeUPTTest {
    @Test
    void retainsUnresolvedTypeStateAcrossSerialization() throws Exception {
        OracleTypeUPT original = new OracleTypeUPT("APP.TEST_UPT", null);

        OracleTypeUPT restored = roundTrip(original);

        assertThat(restored.getRealType()).isNull();
        assertThat(restored.isObjectType()).isFalse();
        assertThat(restored.toDatum(null, null)).isNull();
    }

    private static OracleTypeUPT roundTrip(OracleTypeUPT type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OracleTypeUPT) input.readObject();
        }
    }
}
