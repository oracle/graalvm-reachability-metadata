/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import org.h2.jdbcx.JdbcDataSource;

final class TestDataSources {
    private TestDataSources() {
    }

    static JdbcDataSource create(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
