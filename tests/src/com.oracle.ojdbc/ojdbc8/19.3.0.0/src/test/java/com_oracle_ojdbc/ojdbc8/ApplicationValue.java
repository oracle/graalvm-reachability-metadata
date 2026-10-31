/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import java.sql.Connection;
import oracle.jdbc.OracleData;
import oracle.jdbc.OracleDataFactory;
import oracle.sql.Datum;

public final class ApplicationValue implements OracleData, OracleDataFactory {
    private Datum value;
    private int sqlType;

    public ApplicationValue() {
    }

    @Override
    public OracleData create(Object source, int type) {
        value = (Datum) source;
        sqlType = type;
        return this;
    }

    @Override
    public Object toJDBCObject(Connection connection) {
        return value;
    }

    public byte[] bytes() {
        return value.shareBytes();
    }

    public int sqlType() {
        return sqlType;
    }
}
