/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSetMetaData;
import java.sql.Types;

import org.hsqldb.jdbc.JDBCColumnMetaData;
import org.junit.jupiter.api.Test;

public class JDBCColumnMetaDataTest {
    @Test
    public void rendersPublicColumnFieldsInStringRepresentation() {
        JDBCColumnMetaData columnMetaData = new JDBCColumnMetaData();
        columnMetaData.catalogName = "catalog";
        columnMetaData.columnClassName = "java.lang.Integer";
        columnMetaData.columnDisplaySize = 11;
        columnMetaData.columnLabel = "identifier";
        columnMetaData.columnName = "ID";
        columnMetaData.columnType = Types.INTEGER;
        columnMetaData.precision = 10;
        columnMetaData.scale = 0;
        columnMetaData.schemaName = "PUBLIC";
        columnMetaData.tableName = "ACCOUNTS";
        columnMetaData.isAutoIncrement = true;
        columnMetaData.isCaseSensitive = false;
        columnMetaData.isCurrency = false;
        columnMetaData.isDefinitelyWritable = true;
        columnMetaData.isNullable = ResultSetMetaData.columnNoNulls;
        columnMetaData.isReadOnly = false;
        columnMetaData.isSearchable = true;
        columnMetaData.isSigned = true;
        columnMetaData.isWritable = true;

        String rendered = columnMetaData.toString();

        assertThat(rendered)
                .startsWith("[")
                .endsWith("]")
                .contains("catalogName=catalog")
                .contains("columnClassName=java.lang.Integer")
                .contains("columnDisplaySize=11")
                .contains("columnLabel=identifier")
                .contains("columnName=ID")
                .contains("columnType=" + Types.INTEGER)
                .contains("precision=10")
                .contains("scale=0")
                .contains("schemaName=PUBLIC")
                .contains("tableName=ACCOUNTS")
                .contains("isAutoIncrement=true")
                .contains("isCaseSensitive=false")
                .contains("isCurrency=false")
                .contains("isDefinitelyWritable=true")
                .contains("isNullable=" + ResultSetMetaData.columnNoNulls)
                .contains("isReadOnly=false")
                .contains("isSearchable=true")
                .contains("isSigned=true")
                .contains("isWritable=true");
    }
}
