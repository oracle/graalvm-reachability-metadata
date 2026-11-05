/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.driver.T2CConnection;
import oracle.jdbc.oracore.OracleTypeADT;
import oracle.jdbc.oracore.OracleTypeCOLLECTION;
import oracle.sql.ArrayDescriptor;
import oracle.sql.AttributeDescriptor;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;

/// Exposes the configuration-only OCI constructor; no native logon is performed.
final class OfflineConnection extends T2CConnection implements ConnectionNote {
    OfflineConnection() throws SQLException {
        this(new Properties());
    }

    OfflineConnection(Properties properties) throws SQLException {
        super("jdbc:oracle:oci:@", properties, null);
    }

    @Override
    public String note() {
        return "application connection";
    }

    StructDescriptor structDescriptor() throws SQLException {
        OracleTypeADT type = new OracleTypeADT(new AttributeDescriptor[0], this);
        type.setNames("APP", "CONTACT");
        return new StructDescriptor(new SQLName("APP", "CONTACT", this), type, this);
    }

    ArrayDescriptor arrayDescriptor() throws SQLException {
        // TDS describes a nested table of DATE with the element descriptor at offset 30.
        byte[] tds = ByteBuffer.allocate(31)
                .putInt(27).put((byte) 38).put((byte) 2).putShort((short) 0)
                .putShort((short) 1).put((byte) 0).put((byte) 41)
                .putShort((short) 0).putInt(0).put((byte) 28)
                .putInt(30).putInt(0).put((byte) 3).put((byte) 42)
                .put((byte) 0).put((byte) 2).array();
        OracleTypeADT root = new OracleTypeADT("APP.CONTACTS", this);
        root.init(tds, this);
        OracleTypeCOLLECTION type = (OracleTypeCOLLECTION) root.cleanup();
        return new ArrayDescriptor(new SQLName("APP", "CONTACTS", this), type, this);
    }
}
