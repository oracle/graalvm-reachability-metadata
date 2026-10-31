/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Struct;
import oracle.xdb.XMLType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class OracleTypeOPAQUETest {
    @Test
    @Timeout(120)
    void materializesAnXmlAttributeInsideASqlObject() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement setup = connection.createStatement()) {
            setup.execute("create type forge_xml_envelope as object (id number, document sys.xmltype)");
            try (PreparedStatement statement = connection.prepareStatement(
                    "select forge_xml_envelope(7, XMLType(?)) from dual")) {
                statement.setString(1, "<shipment><destination>York</destination></shipment>");
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Struct envelope = (Struct) rows.getObject(1);
                    Object[] attributes = envelope.getAttributes();
                    assertThat(attributes).hasSize(2);
                    assertThat(attributes[0]).isEqualTo(BigDecimal.valueOf(7));
                    assertThat(attributes[1]).isInstanceOf(XMLType.class);
                    XMLType document = (XMLType) attributes[1];
                    try {
                        assertThat(document.getStringVal()).contains("<destination>York</destination>");
                    } finally {
                        document.close();
                    }
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                setup.execute("drop type forge_xml_envelope");
            }
        }
    }

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
