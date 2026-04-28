/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.interceptor.AbstractQueryReport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractQueryReportTest {

    @Test
    void wrapsPreparedStatementsWithQueryReportingProxy() throws Exception {
        TestQueryReport queryReport = new TestQueryReport();
        queryReport.setThreshold(Long.MAX_VALUE);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:abstract_query_report");
                PreparedStatement setupStatement = connection.prepareStatement("""
                        CREATE TABLE query_report_item (
                            id INT NOT NULL PRIMARY KEY,
                            name VARCHAR(255)
                        )
                        """)) {
            setupStatement.executeUpdate();

            insertItem(connection);

            String query = "SELECT name FROM query_report_item WHERE id = ?";
            try (PreparedStatement delegate = connection.prepareStatement(query);
                    PreparedStatement statement = queryReport.wrapPreparedStatement(delegate, query)) {
                statement.setInt(1, 1);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString(1)).isEqualTo("reported-query");
                    assertThat(resultSet.next()).isFalse();
                }

                assertThat(statement.isClosed()).isFalse();
            }
        }

        assertThat(queryReport.preparedSql).containsExactly("SELECT name FROM query_report_item WHERE id = ?");
        assertThat(queryReport.executedSql).containsExactly("SELECT name FROM query_report_item WHERE id = ?");
        assertThat(queryReport.failedSql).isEmpty();
    }

    private void insertItem(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO query_report_item(id, name)
                VALUES (?, ?)
                """)) {
            statement.setInt(1, 1);
            statement.setString(2, "reported-query");
            statement.executeUpdate();
        }
    }

    private static final class TestQueryReport extends AbstractQueryReport {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<String> executedSql = new ArrayList<>();
        private final List<String> failedSql = new ArrayList<>();

        private PreparedStatement wrapPreparedStatement(PreparedStatement statement, String sql)
                throws NoSuchMethodException {
            Method method = Connection.class.getMethod("prepareStatement", String.class);
            Object[] args = {sql};
            return (PreparedStatement) createStatement(null, method, args, statement, 0);
        }

        @Override
        protected void prepareStatement(String sql, long time) {
            preparedSql.add(sql);
        }

        @Override
        protected void prepareCall(String query, long time) {
            throw new AssertionError("No callable statements are prepared by this test");
        }

        @Override
        protected String reportQuery(String query, Object[] args, String name, long start, long delta) {
            String sql = super.reportQuery(query, args, name, start, delta);
            executedSql.add(sql);
            return sql;
        }

        @Override
        protected String reportFailedQuery(String query, Object[] args, String name, long start, Throwable throwable) {
            String sql = super.reportFailedQuery(query, args, name, start, throwable);
            failedSql.add(sql);
            return sql;
        }

        @Override
        public void closeInvoked() {
            // This test wraps statements directly, so connection close events are not observed.
        }
    }
}
