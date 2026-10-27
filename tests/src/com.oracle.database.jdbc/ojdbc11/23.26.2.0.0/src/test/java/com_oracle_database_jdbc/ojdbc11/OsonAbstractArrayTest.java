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
import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonValue;
import org.junit.jupiter.api.Test;

public class OsonAbstractArrayTest {
    @Test
    void createsTypedArraysFromBinaryJson() {
        OracleJsonFactory factory = new OracleJsonFactory();
        ByteArrayOutputStream binaryJson = new ByteArrayOutputStream();
        try (OracleJsonGenerator generator = factory.createJsonBinaryGenerator(binaryJson)) {
            generator.writeStartArray().write("first").write("second").writeEnd();
        }

        OracleJsonValue value =
                factory.createJsonBinaryValue(new ByteArrayInputStream(binaryJson.toByteArray()));
        assertThat(value).isInstanceOf(OracleJsonArray.class);
        OracleJsonArray array = (OracleJsonArray) value;

        OracleJsonValue[] values = array.toArray(new OracleJsonValue[0]);

        assertThat(values).hasSize(2);
        assertThat(array.getString(0)).isEqualTo("first");
        assertThat(array.getString(1)).isEqualTo("second");
    }
}
