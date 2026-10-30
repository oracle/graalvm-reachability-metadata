/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;

final class JdbcTestSupport {
    private JdbcTestSupport() {
    }

    static Connection openConnection() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        source.setUser("sa");
        source.setPassword("");
        return source.getConnection();
    }
}
