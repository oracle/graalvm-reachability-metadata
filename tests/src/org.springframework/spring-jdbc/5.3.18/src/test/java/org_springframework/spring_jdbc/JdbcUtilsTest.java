/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.JdbcUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    @Test
    void resolvesStandardJdbcTypeNames() {
        assertThat(JdbcUtils.resolveTypeName(Types.INTEGER)).isEqualTo("INTEGER");
        assertThat(JdbcUtils.resolveTypeName(Types.VARCHAR)).isEqualTo("VARCHAR");
        assertThat(JdbcUtils.resolveTypeName(Types.TIMESTAMP_WITH_TIMEZONE)).isEqualTo("TIMESTAMP_WITH_TIMEZONE");
        assertThat(JdbcUtils.resolveTypeName(Integer.MIN_VALUE)).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void extractsNamedDatabaseMetadataAndReleasesConnections() throws Exception {
        JdbcTestSupport.TrackingDataSource source = new JdbcTestSupport.TrackingDataSource();
        String product = JdbcUtils.extractDatabaseMetaData(source, "getDatabaseProductName");
        assertThat(product).isEqualTo("H2");
        assertThat(source.lastConnection().isClosed()).isTrue();

        Boolean transactions = JdbcUtils.extractDatabaseMetaData(source, "supportsTransactions");
        assertThat(transactions).isTrue();
        assertThat(source.connectionCount()).isEqualTo(2);
        assertThat(source.lastConnection().isClosed()).isTrue();
    }
}
