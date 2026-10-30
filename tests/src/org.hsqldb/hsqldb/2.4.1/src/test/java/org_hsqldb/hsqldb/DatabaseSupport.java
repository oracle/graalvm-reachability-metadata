/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;

final class DatabaseSupport {
    private DatabaseSupport() {
    }

    static Connection memory() throws SQLException {
        return connect("jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=true");
    }

    static Connection connect(String url) throws SQLException {
        JDBCDataSource source = new JDBCDataSource();
        source.setUrl(url);
        source.setUser("SA");
        source.setPassword("");
        return source.getConnection();
    }
}
