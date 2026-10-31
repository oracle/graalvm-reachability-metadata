/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import oracle.xdb.XMLType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class OracleTypeOPAQUETest {
    @Test
    @Timeout(120)
    void materializesAnXmlColumnAsAnXmlType() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                PreparedStatement statement = connection.prepareStatement("select XMLType(?) from dual")) {
            statement.setString(1, "<delivery><city>Oxford</city><items>3</items></delivery>");
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Object value = rows.getObject(1);
                assertThat(value).isInstanceOf(XMLType.class);
                XMLType xml = (XMLType) value;
                try {
                    assertThat(xml.getStringVal()).contains("<city>Oxford</city>", "<items>3</items>");
                } finally {
                    xml.close();
                }
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
