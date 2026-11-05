/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import oracle.jdbc.pool.OracleConnectionPoolDataSource;
import oracle.jdbc.pool.OraclePooledConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class OraclePooledConnectionTest {
    @Test
    @Timeout(120)
    void reconnectsASerializedPooledConnectionWithItsStatementCacheSettings() throws Exception {
        OracleConnectionPoolDataSource source = new OracleConnectionPoolDataSource();
        source.setURL(OracleDatabase.url());
        source.setConnectionProperties(OracleDatabase.properties());
        OraclePooledConnection original = (OraclePooledConnection) source.getPooledConnection();
        byte[] serialized;
        try {
            original.setStatementCacheSize(5);
            original.setImplicitCachingEnabled(true);
            try (Connection connection = original.getConnection()) {
                connection.getTypeMap();
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
                output.writeObject(original);
            }
            serialized = buffer.toByteArray();
        } finally {
            original.close();
        }

        OraclePooledConnection restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            restored = (OraclePooledConnection) input.readObject();
        }
        try {
            assertThat(restored.getStatementCacheSize()).isEqualTo(5);
            assertThat(restored.getImplicitCachingEnabled()).isTrue();
            try (Connection connection = restored.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("select 42 from dual")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(42);
                assertThat(rows.next()).isFalse();
            }
        } finally {
            restored.close();
        }
    }
}
