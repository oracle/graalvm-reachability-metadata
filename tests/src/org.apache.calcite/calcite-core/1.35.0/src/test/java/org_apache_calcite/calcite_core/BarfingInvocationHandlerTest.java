/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.apache.calcite.sql.SqlUtil;
import org.junit.jupiter.api.Test;

public class BarfingInvocationHandlerTest {
    @Test
    void databaseMetadataProxyDelegatesSupportedMethodsToHandler() throws SQLException {
        DatabaseMetaData metadata = databaseMetaData("Calcite", "`");

        assertThat(metadata.getDatabaseProductName()).isEqualTo("Calcite");
        assertThat(metadata.getIdentifierQuoteString()).isEqualTo("`");
    }

    @Test
    void databaseMetadataProxyRejectsUnsupportedMethods() {
        DatabaseMetaData metadata = databaseMetaData("Calcite", "`");

        assertThatThrownBy(metadata::getDriverName)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("java.sql.DatabaseMetaData.getDriverName()");
    }

    private static DatabaseMetaData databaseMetaData(String productName, String quoteString) {
        SqlUtil.DatabaseMetaDataInvocationHandler handler = new SqlUtil.DatabaseMetaDataInvocationHandler(
                productName,
                quoteString);
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                handler);
    }
}
