/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ibatis.common.jdbc.SimpleDataSource;
import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.client.SqlMapClientBuilder;

final class SqlMapFixture implements AutoCloseable {
    private final SqlMapClient client;
    private final SimpleDataSource source;

    SqlMapFixture(boolean lazy) throws SQLException {
        String configuration = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE sqlMapConfig PUBLIC "-//ibatis.apache.org//DTD SQL Map Config 2.0//EN"
                        "http://ibatis.apache.org/dtd/sql-map-config-2.dtd">
                <sqlMapConfig>
                    <settings lazyLoadingEnabled="%s" enhancementEnabled="false" useStatementNamespaces="true"/>
                    <transactionManager type="JDBC">
                        <dataSource type="SIMPLE">
                            <property name="JDBC.Driver" value="org.h2.Driver"/>
                            <property name="JDBC.ConnectionURL" value="jdbc:h2:mem:%s"/>
                            <property name="JDBC.Username" value="sa"/>
                            <property name="JDBC.Password" value=""/>
                            <property name="JDBC.DefaultAutoCommit" value="true"/>
                            <property name="Pool.MaximumIdleConnections" value="1"/>
                            <property name="Pool.TimeToWait" value="10000"/>
                        </dataSource>
                    </transactionManager>
                    <sqlMap resource="org_apache_ibatis/ibatis_sqlmap/catalog.xml"/>
                </sqlMapConfig>
                """.formatted(lazy, UUID.randomUUID());
        client = SqlMapClientBuilder.buildSqlMapClient(new StringReader(configuration));
        source = (SimpleDataSource) client.getDataSource();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE catalog_items (owner_id INT, item_order INT, item_name VARCHAR(30))");
            statement.executeUpdate("INSERT INTO catalog_items VALUES (7, 1, 'alpha'), (7, 2, 'beta')");
        }
    }

    SqlMapClient client() {
        return client;
    }

    void addItem(int order, String name) throws SQLException {
        try (Connection connection = source.getConnection();
                PreparedStatement insert = connection.prepareStatement("INSERT INTO catalog_items VALUES (7, ?, ?)")) {
            insert.setInt(1, order);
            insert.setString(2, name);
            insert.executeUpdate();
            connection.commit();
        }
    }

    @Override
    public void close() {
        source.forceCloseAll();
    }

    public static class Catalog {
        private int id;
        private List<String> names;
        private Set<String> uniqueNames;
        private String[] nameArray;
        private int[] orderArray;

        public Catalog() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        public Set<String> getUniqueNames() {
            return uniqueNames;
        }

        public void setUniqueNames(Set<String> uniqueNames) {
            this.uniqueNames = uniqueNames;
        }

        public String[] getNameArray() {
            return nameArray;
        }

        public void setNameArray(String[] nameArray) {
            this.nameArray = nameArray;
        }

        public int[] getOrderArray() {
            return orderArray;
        }

        public void setOrderArray(int[] orderArray) {
            this.orderArray = orderArray;
        }
    }
}
