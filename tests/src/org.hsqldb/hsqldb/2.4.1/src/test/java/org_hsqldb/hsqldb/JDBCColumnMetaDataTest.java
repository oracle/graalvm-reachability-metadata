/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;

import org.hsqldb.jdbc.JDBCColumnMetaData;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

public class JDBCColumnMetaDataTest {
    @Test
    public void resultSetMetaDataToStringIncludesColumnMetaDataDescription() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setDatabase(inMemoryDatabase("column_metadata"));
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INTEGER PRIMARY KEY, NAME VARCHAR(40) NOT NULL)");
            statement.executeUpdate("INSERT INTO CUSTOMER VALUES (1, 'Ada')");

            try (ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM CUSTOMER")) {
                String description = resultSet.getMetaData().toString();

                assertThat(description)
                        .contains("column_1=")
                        .contains("columnName=ID")
                        .contains("columnLabel=ID")
                        .contains("column_2=")
                        .contains("columnName=NAME")
                        .contains("columnLabel=NAME");
            }
        }
    }

    @Test
    public void toStringIncludesPublicMetadataFields() {
        JDBCColumnMetaData metadata = new JDBCColumnMetaData();

        metadata.catalogName = "test_catalog";
        metadata.columnClassName = String.class.getName();
        metadata.columnDisplaySize = 128;
        metadata.columnLabel = "Name";
        metadata.columnName = "NAME";
        metadata.columnType = Types.VARCHAR;
        metadata.precision = 128;
        metadata.scale = 0;
        metadata.schemaName = "PUBLIC";
        metadata.tableName = "CUSTOMER";
        metadata.isAutoIncrement = false;
        metadata.isCaseSensitive = true;
        metadata.isCurrency = false;
        metadata.isDefinitelyWritable = true;
        metadata.isNullable = ResultSetMetaData.columnNullable;
        metadata.isReadOnly = false;
        metadata.isSearchable = true;
        metadata.isSigned = false;
        metadata.isWritable = true;

        String description = metadata.toString();

        assertThat(description)
                .startsWith("[")
                .endsWith("]")
                .contains("catalogName=test_catalog")
                .contains("columnClassName=java.lang.String")
                .contains("columnDisplaySize=128")
                .contains("columnLabel=Name")
                .contains("columnName=NAME")
                .contains("columnType=" + Types.VARCHAR)
                .contains("precision=128")
                .contains("schemaName=PUBLIC")
                .contains("tableName=CUSTOMER")
                .contains("isCaseSensitive=true")
                .contains("isNullable=" + ResultSetMetaData.columnNullable)
                .contains("isWritable=true");
    }

    private static String inMemoryDatabase(String prefix) {
        return "mem:" + prefix + "_" + Long.toUnsignedString(System.nanoTime()) + ";shutdown=true";
    }
}
