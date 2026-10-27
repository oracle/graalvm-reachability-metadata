/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import oracle.jdbc.driver.T2CConnection;
import oracle.jdbc.oracore.OracleType;
import oracle.jdbc.oracore.OracleTypeOPAQUE;
import oracle.sql.OPAQUE;
import oracle.sql.OpaqueDescriptor;
import oracle.sql.SQLName;
import oracle.xdb.XMLType;
import org.junit.jupiter.api.Test;

public class OracleTypeOPAQUETest {
    @Test
    void restoresXmlTypeFromItsOpaqueImage() throws Exception {
        DetachedT2CConnection connection = new DetachedT2CConnection();
        OracleTypeOPAQUE oracleType = new OracleTypeOPAQUE("SYS.XMLTYPE", connection);
        SQLName sqlName = new SQLName("SYS", "XMLTYPE", connection);
        OpaqueDescriptor descriptor = new OpaqueDescriptor(sqlName, oracleType, connection);
        connection.putDescriptor(sqlName.getName(), descriptor);
        XMLType xml = XMLType.createXML(connection, "<message>oracle</message>");

        byte[] image = oracleType.linearize(xml);
        OPAQUE restored = (OPAQUE) oracleType.unlinearize(
                image, 0, null, OracleType.STYLE_DATUM, Map.of());

        assertThat(restored).isInstanceOf(XMLType.class);
        assertThat(((XMLType) restored).getStringVal())
                .contains("<message>oracle</message>");
    }

    private static final class DetachedT2CConnection extends T2CConnection {
        private DetachedT2CConnection() throws SQLException {
            super("jdbc:oracle:oci:@", new Properties(), null);
        }

        @Override
        public String getClientInfo(String name) {
            return null;
        }

        @Override
        public String getProtocolType() {
            return "thin";
        }

        @Override
        public short getVersionNumber() {
            return 12100;
        }

        @Override
        public short getDbCsId() {
            return 873;
        }
    }
}
