/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.jdbc_v2;

import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.ClickHouseDataType;
import com.clickhouse.data.Tuple;
import com.clickhouse.jdbc.DataSourceImpl;
import com.clickhouse.jdbc.Driver;
import com.clickhouse.jdbc.PreparedStatementImpl;
import com.clickhouse.jdbc.metadata.ResultSetMetaDataImpl;
import com.clickhouse.jdbc.types.Array;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverPropertyInfo;
import java.sql.JDBCType;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jdbc_v2Test {
    private static final String URL = "jdbc:clickhouse://localhost:8123/default";

    @Test
    void driverRecognizesClickHouseUrlsAndExposesConfiguration() throws Exception {
        Driver driver = new Driver();
        Properties properties = offlineProperties();
        properties.setProperty("user", "property-user");

        assertThat(driver.acceptsURL("jdbc:clickhouse://localhost:8123/default")).isTrue();
        assertThat(driver.acceptsURL("jdbc:ch://localhost:8123/default")).isTrue();
        assertThat(driver.acceptsURL("jdbc:postgresql://localhost:5432/postgres")).isFalse();
        assertThat(driver.connect("jdbc:postgresql://localhost:5432/postgres", properties)).isNull();
        assertThat(driver.jdbcCompliant()).isFalse();
        assertThat(driver.getMajorVersion()).isEqualTo(Driver.getDriverMajorVersion());
        assertThat(driver.getMinorVersion()).isEqualTo(Driver.getDriverMinorVersion());
        assertThat(Driver.chSettingKey("max_threads")).isEqualTo("clickhouse_setting_max_threads");
        assertThatThrownBy(driver::getParentLogger).isInstanceOf(SQLFeatureNotSupportedException.class);

        DriverPropertyInfo[] propertyInfos = driver.getPropertyInfo(
                "jdbc:clickhouse://localhost:8123/analytics?user=url-user&socket_timeout=250", properties);
        Map<String, String> valuesByName = new LinkedHashMap<>();
        for (DriverPropertyInfo propertyInfo : propertyInfos) {
            valuesByName.put(propertyInfo.name, propertyInfo.value);
        }

        assertThat(valuesByName)
                .containsEntry("database", "analytics")
                .containsEntry("user", "url-user")
                .containsEntry("socket_timeout", "250")
                .containsKey("server_time_zone");
    }

    @Test
    void dataSourceProvidesJdbcWrapperSemanticsAndConnections() throws Exception {
        DataSourceImpl dataSource = new DataSourceImpl(URL, offlineProperties());
        StringWriter sink = new StringWriter();
        PrintWriter logWriter = new PrintWriter(sink);

        dataSource.setLogWriter(logWriter);

        assertThat(dataSource.getLogWriter()).isSameAs(logWriter);
        assertThat(dataSource.isWrapperFor(DataSourceImpl.class)).isTrue();
        assertThat(dataSource.unwrap(DataSourceImpl.class)).isSameAs(dataSource);
        assertThatThrownBy(() -> dataSource.unwrap(String.class)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> dataSource.setLoginTimeout(1)).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(dataSource::getLoginTimeout).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(dataSource::getParentLogger).isInstanceOf(SQLFeatureNotSupportedException.class);

        try (Connection connection = dataSource.getConnection("user-from-call", "secret")) {
            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
        }
    }

    @Test
    void connectionExposesOfflineJdbcStateAndUnsupportedOperations() throws Exception {
        try (Connection connection = openConnection()) {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.isReadOnly()).isFalse();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_NONE);
            assertThat(connection.isValid(0)).isFalse();
            assertThat(connection.getSchema()).isEqualTo("default");
            assertThat(connection.getCatalog()).isNull();
            assertThat(connection.getWarnings() == null).isTrue();
            assertThat(connection.getHoldability()).isEqualTo(ResultSet.HOLD_CURSORS_OVER_COMMIT);
            assertThat(connection.isWrapperFor(Connection.class)).isTrue();
            assertThat(connection.unwrap(Connection.class)).isSameAs(connection);

            connection.setSchema("analytics");
            connection.setClientInfo("ApplicationName", "metadata-test");
            assertThat(connection.getSchema()).isEqualTo("analytics");
            assertThat(connection.getClientInfo("ApplicationName")).isEqualTo("metadata-test");
            assertThat(connection.getClientInfo()).containsEntry("ApplicationName", "metadata-test");

            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(metadata.getDatabaseProductName()).isEqualTo("ClickHouse");
            assertThat(metadata.getDriverName()).isEqualTo("ClickHouse JDBC Driver");
            assertThat(metadata.supportsTransactions()).isFalse();
            assertThat(metadata.supportsMultipleResultSets()).isFalse();
            assertThat(metadata.supportsColumnAliasing()).isTrue();

            assertThatThrownBy(() -> connection.setAutoCommit(false))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(connection::commit).isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(connection::rollback).isInstanceOf(SQLFeatureNotSupportedException.class);
            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
            assertThatThrownBy(() -> connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Test
    void statementMaintainsJdbcStateWithoutExecutingQueries() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            assertThat(statement.getConnection()).isSameAs(connection);
            assertThat(statement.getResultSet()).isNull();
            assertThat(statement.getUpdateCount()).isEqualTo(-1);
            assertThat(statement.getMoreResults()).isFalse();
            assertThat(statement.getWarnings() == null).isTrue();
            assertThat(statement.getFetchDirection()).isEqualTo(ResultSet.FETCH_FORWARD);
            assertThat(statement.getFetchSize()).isPositive();
            assertThat(statement.getResultSetConcurrency()).isEqualTo(ResultSet.CONCUR_READ_ONLY);
            assertThat(statement.getResultSetType()).isEqualTo(ResultSet.TYPE_FORWARD_ONLY);
            assertThat(statement.getResultSetHoldability()).isEqualTo(ResultSet.HOLD_CURSORS_OVER_COMMIT);
            assertThat(statement.isPoolable()).isFalse();
            assertThat(statement.isCloseOnCompletion()).isFalse();
            assertThat(statement.getGeneratedKeys()).isNull();
            assertThat(statement.getLargeUpdateCount()).isEqualTo(-1L);

            statement.setMaxRows(42);
            statement.setQueryTimeout(3);
            statement.setFetchSize(128);
            statement.setPoolable(true);
            statement.addBatch("INSERT INTO t VALUES (1)");
            statement.clearBatch();
            statement.clearWarnings();
            statement.closeOnCompletion();

            assertThat(statement.getMaxRows()).isEqualTo(42);
            assertThat(statement.getLargeMaxRows()).isEqualTo(42L);
            assertThat(statement.getQueryTimeout()).isEqualTo(3);
            assertThat(statement.getFetchSize()).isEqualTo(128);
            assertThat(statement.isPoolable()).isTrue();
            assertThat(statement.isWrapperFor(Statement.class)).isTrue();
            assertThat(statement.unwrap(Statement.class)).isSameAs(statement);

            statement.setFetchDirection(ResultSet.FETCH_REVERSE);
            statement.setMaxFieldSize(100);
            assertThat(statement.getFetchDirection()).isEqualTo(ResultSet.FETCH_FORWARD);
            assertThat(statement.getMaxFieldSize()).isEqualTo(100);
            assertThatThrownBy(() -> statement.setFetchDirection(-1)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void preparedStatementReportsParametersAndRejectsStatementSqlOverloads() throws Exception {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT ? AS first, ? AS second")) {
            ParameterMetaData parameterMetaData = statement.getParameterMetaData();

            assertThat(parameterMetaData.getParameterCount()).isEqualTo(2);
            assertThat(parameterMetaData.isNullable(1)).isEqualTo(ParameterMetaData.parameterNullableUnknown);
            assertThat(parameterMetaData.isSigned(1)).isFalse();
            assertThat(parameterMetaData.getPrecision(1)).isZero();
            assertThat(parameterMetaData.getScale(1)).isZero();
            assertThat(parameterMetaData.getParameterType(1)).isEqualTo(Types.OTHER);
            assertThat(parameterMetaData.getParameterTypeName(1)).isEqualTo("UNKNOWN");
            assertThat(parameterMetaData.getParameterClassName(1)).isEqualTo(Object.class.getName());
            assertThat(parameterMetaData.getParameterMode(1)).isEqualTo(ParameterMetaData.parameterModeIn);
            assertThatThrownBy(() -> parameterMetaData.getParameterType(0)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> parameterMetaData.getParameterType(3)).isInstanceOf(SQLException.class);

            statement.setInt(1, 7);
            statement.setString(2, "ClickHouse's JDBC");
            statement.clearParameters();
            statement.setObject(1, LocalDate.of(2024, 1, 2), JDBCType.DATE);
            statement.setObject(2, Arrays.asList("alpha", "beta"));
            assertThatThrownBy(() -> statement.setObject(2, Arrays.asList("gamma", "delta"), JDBCType.ARRAY))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("requires a parameter");
            assertThat(statement.isPoolable()).isTrue();
            assertThat(statement.isWrapperFor(PreparedStatement.class)).isTrue();
            assertThat(statement.unwrap(PreparedStatement.class)).isSameAs(statement);

            assertThatThrownBy(() -> statement.executeQuery("SELECT 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("SELECT 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("SELECT 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.addBatch("SELECT 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.setRowId(1, null)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.setRef(1, null)).isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Test
    void preparedStatementAcceptsCommonJavaValueTypes() throws Exception {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO t VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("one", 1);
            map.put("two", 2);

            statement.setBoolean(1, true);
            statement.setBigDecimal(2, new BigDecimal("123.45"));
            statement.setBytes(3, new byte[] {1, 2, 3});
            statement.setDate(4, java.sql.Date.valueOf(LocalDate.of(2024, 2, 3)));
            statement.setTime(5, java.sql.Time.valueOf(LocalTime.of(4, 5, 6)));
            statement.setTimestamp(6, java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 2, 3, 4, 5, 6, 7)));
            statement.setObject(7, OffsetDateTime.parse("2024-02-03T04:05:06Z"));
            statement.setObject(8, Instant.parse("2024-02-03T04:05:06Z"));
            statement.setObject(9, InetAddress.getByName("127.0.0.1"));
            statement.setArray(10, stringArray("x", "y"));
            statement.setObject(11, map);
            statement.setNull(12, Types.NULL);
            statement.addBatch();
        }
    }

    @Test
    void connectionCreatesJdbcArraysFromClickHouseTypeNames() throws Exception {
        try (Connection connection = openConnection()) {
            java.sql.Array stringArray = connection.createArrayOf("String", new Object[] {"alpha", "beta"});
            java.sql.Array integerArray = connection.createArrayOf("Int32", new Object[] {1, 2, 3});
            java.sql.Array emptyArray = connection.createArrayOf("Bool", new Object[0]);
            try {
                assertThat(stringArray.getBaseTypeName()).isEqualTo("String");
                assertThat(stringArray.getBaseType()).isEqualTo(Types.VARCHAR);
                assertThat((Object[]) stringArray.getArray()).containsExactly("alpha", "beta");

                assertThat(integerArray.getBaseTypeName()).isEqualTo("Int32");
                assertThat(integerArray.getBaseType()).isEqualTo(Types.INTEGER);
                assertThat((Object[]) integerArray.getArray()).containsExactly(1, 2, 3);

                assertThat(emptyArray.getBaseTypeName()).isEqualTo("Bool");
                assertThat(emptyArray.getBaseType()).isEqualTo(Types.BOOLEAN);
                assertThat((Object[]) emptyArray.getArray()).isEmpty();

                assertThatThrownBy(() -> connection.createArrayOf("NotAClickHouseType", new Object[] {"value"}))
                        .isInstanceOf(IllegalArgumentException.class);
            } finally {
                stringArray.free();
                integerArray.free();
                emptyArray.free();
            }
        }
    }

    @Test
    void resultSetMetadataDescribesClickHouseColumnsAsJdbcColumns() throws Exception {
        List<ClickHouseColumn> columns = List.of(
                ClickHouseColumn.of("id", "Int32"),
                ClickHouseColumn.of("amount", "Nullable(Decimal(12, 2))"),
                ClickHouseColumn.of("tags", "Array(String)"));
        Map<ClickHouseDataType, Class<?>> typeClassMap = new LinkedHashMap<>();
        typeClassMap.put(ClickHouseDataType.Int32, Integer.class);
        typeClassMap.put(ClickHouseDataType.Decimal, BigDecimal.class);
        typeClassMap.put(ClickHouseDataType.Array, Object[].class);

        ResultSetMetaData metadata = new ResultSetMetaDataImpl(
                columns, "analytics", "local", "events", typeClassMap);

        assertThat(metadata.getColumnCount()).isEqualTo(3);
        assertThat(metadata.getColumnName(1)).isEqualTo("id");
        assertThat(metadata.getColumnLabel(1)).isEqualTo("id");
        assertThat(metadata.getColumnType(1)).isEqualTo(Types.INTEGER);
        assertThat(metadata.getColumnTypeName(1)).isEqualTo("Int32");
        assertThat(metadata.isSigned(1)).isTrue();
        assertThat(metadata.isNullable(1)).isEqualTo(ResultSetMetaData.columnNoNulls);
        assertThat(metadata.getColumnClassName(1)).isEqualTo(Integer.class.getName());

        assertThat(metadata.getSchemaName(2)).isEqualTo("analytics");
        assertThat(metadata.getCatalogName(2)).isEqualTo("local");
        assertThat(metadata.getTableName(2)).isEqualTo("events");
        assertThat(metadata.getColumnType(2)).isEqualTo(Types.DECIMAL);
        assertThat(metadata.getColumnTypeName(2)).contains("Decimal");
        assertThat(metadata.getPrecision(2)).isEqualTo(12);
        assertThat(metadata.getScale(2)).isEqualTo(2);
        assertThat(metadata.isNullable(2)).isEqualTo(ResultSetMetaData.columnNullable);
        assertThat(metadata.getColumnClassName(2)).isEqualTo(BigDecimal.class.getName());

        assertThat(metadata.getColumnType(3)).isEqualTo(Types.ARRAY);
        assertThat(metadata.getColumnTypeName(3)).isEqualTo("Array(String)");
        assertThat(metadata.getColumnClassName(3)).isEqualTo(Object[].class.getName());
        assertThat(metadata.isAutoIncrement(3)).isFalse();
        assertThat(metadata.isReadOnly(3)).isTrue();
        assertThat(metadata.isWritable(3)).isFalse();
        assertThatThrownBy(() -> metadata.getColumnName(0)).isInstanceOf(SQLException.class);
    }

    @Test
    void clickHouseArrayAndTupleExposeValueObjects() throws Exception {
        Array array = stringArray("one", "two", "three");
        Tuple tuple = new Tuple("name", 42, true);

        assertThat(array.getBaseTypeName()).isEqualTo("String");
        assertThat(array.getBaseType()).isEqualTo(Types.VARCHAR);
        assertThat((Object[]) array.getArray()).containsExactly("one", "two", "three");
        assertThat((Object[]) array.getArray(1, 2)).containsExactly("two", "three");
        try (ResultSet resultSet = array.getResultSet()) {
            ResultSetMetaData metadata = resultSet.getMetaData();

            assertThat(metadata.getColumnCount()).isEqualTo(2);
            assertThat(metadata.getColumnName(1)).isEqualTo("INDEX");
            assertThat(metadata.getColumnName(2)).isEqualTo("VALUE");
            assertThat(resultSet.isBeforeFirst()).isTrue();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("INDEX")).isEqualTo(1);
            assertThat(resultSet.getString("VALUE")).isEqualTo("one");
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getObject(1)).isEqualTo(2);
            assertThat(resultSet.getObject(2, String.class)).isEqualTo("two");
        }
        assertThatThrownBy(() -> array.getArray(Map.of())).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(() -> array.getResultSet(Map.of())).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(() -> array.getArray(-1, 1)).isInstanceOf(SQLException.class);
        array.free();
        assertThatThrownBy(array::getArray).isInstanceOf(SQLException.class);

        assertThat(tuple.size()).isEqualTo(3);
        assertThat(tuple.getValue(0)).isEqualTo("name");
        assertThat(tuple.getValues()).containsExactly("name", 42, true);
        assertThat(tuple).hasToString("(name, 42, true)");
    }

    @Test
    void preparedStatementUtilityReplacesOnlyUnquotedQuestionMarks() {
        String sql = "SELECT ?, '?', \"?\", `?`, col FROM table WHERE id = ?";

        assertThat(PreparedStatementImpl.replaceQuestionMarks(sql, "NULL"))
                .isEqualTo("SELECT NULL, '?', \"?\", `?`, col FROM table WHERE id = NULL");
    }

    private static Connection openConnection() throws SQLException {
        return new Driver().connect(URL, offlineProperties());
    }

    private static Array stringArray(Object... elements) throws SQLException {
        return new Array(ClickHouseColumn.of("values", "Array(String)"), elements);
    }

    private static Properties offlineProperties() {
        Properties properties = new Properties();
        properties.setProperty("server_time_zone", TimeZone.getTimeZone("UTC").getID());
        properties.setProperty("disable_frameworks_detection", "true");
        properties.setProperty("connection_pool_enabled", "false");
        properties.setProperty("user", "default");
        properties.setProperty("password", "");
        return properties;
    }
}
