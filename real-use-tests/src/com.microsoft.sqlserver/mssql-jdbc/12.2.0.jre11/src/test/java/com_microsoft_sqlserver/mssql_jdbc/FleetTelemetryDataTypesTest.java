/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.Geography;
import com.microsoft.sqlserver.jdbc.Geometry;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import com.microsoft.sqlserver.jdbc.SQLServerResultSet;
import microsoft.sql.DateTimeOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.transform.dom.DOMSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/// Stores and reads SQL Server-specific column types through the driver's own
/// type API: spatial `Geography` and `Geometry`, `DATETIMEOFFSET`, `XML` via
/// `SQLXML`, `UNIQUEIDENTIFIER`, `MONEY`, and `sql_variant`.
///
/// The scenario is a fleet telemetry service that records vehicle positions,
/// finds depots within a radius, tracks depot yard layouts as planar shapes,
/// keeps trip timestamps with their local offsets, and stores XML inspection
/// reports and loosely typed vehicle attributes, against a real SQL Server.
public class FleetTelemetryDataTypesTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "FleetTypes#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-types-" + UUID.randomUUID();

    private static final int WGS84 = 4326;

    private static SQLServerDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setApplicationName("fleet-telemetry");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void vehiclePositionRoundTripsAsGeographyPoint() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.positions (vehicle, location) VALUES (?, ?)")) {
            SQLServerPreparedStatement sqlServerInsert = insert.unwrap(SQLServerPreparedStatement.class);
            sqlServerInsert.setString(1, "TRUCK-7");
            sqlServerInsert.setGeography(2, Geography.point(52.3676, 4.9041, WGS84));
            assertThat(sqlServerInsert.executeUpdate()).isEqualTo(1);
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT location, location.Lat, location.Long
                        FROM dbo.positions WHERE vehicle = ?
                        """)) {
            select.setString(1, "TRUCK-7");
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Geography location = rows.unwrap(SQLServerResultSet.class).getGeography(1);
                assertThat(location.STGeographyType()).isEqualTo("POINT");
                assertThat(location.getSrid()).isEqualTo(WGS84);
                assertThat(location.getLatitude()).isCloseTo(52.3676, within(1e-9));
                assertThat(location.getLongitude()).isCloseTo(4.9041, within(1e-9));
                assertThat(rows.getDouble(2)).isCloseTo(52.3676, within(1e-9));
                assertThat(rows.getDouble(3)).isCloseTo(4.9041, within(1e-9));
            }
        }
    }

    @Test
    void depotsWithinRadiusAreFoundFromWellKnownTextRoute() throws Exception {
        Geography route = Geography.STGeomFromText("LINESTRING(4.9041 52.3676, 4.4777 51.9244)", WGS84);
        assertThat(route.STNumPoints()).isEqualTo(2);

        Map<String, Long> nearby = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT name, CAST(ROUND(site.STDistance(?) / 1000, 0) AS BIGINT) AS km
                        FROM dbo.depots
                        WHERE site.STDistance(?) <= 20000
                        ORDER BY km, name
                        """)) {
            SQLServerPreparedStatement sqlServerSelect = select.unwrap(SQLServerPreparedStatement.class);
            sqlServerSelect.setGeography(1, route);
            sqlServerSelect.setGeography(2, route);
            try (ResultSet rows = sqlServerSelect.executeQuery()) {
                while (rows.next()) {
                    nearby.put(rows.getString("name"), rows.getLong("km"));
                }
            }
        }

        assertThat(nearby).containsOnlyKeys("Amsterdam West", "Rotterdam Port");
        assertThat(nearby.get("Amsterdam West")).isLessThanOrEqualTo(5L);
    }

    @Test
    void yardLayoutIsStoredAsPlanarPolygon() throws Exception {
        Geometry yard = Geometry.STGeomFromText("POLYGON((0 0, 40 0, 40 25, 0 25, 0 0))", 0);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.yards (depot, layout) VALUES (?, ?)")) {
            SQLServerPreparedStatement sqlServerInsert = insert.unwrap(SQLServerPreparedStatement.class);
            sqlServerInsert.setString(1, "Rotterdam Port");
            sqlServerInsert.setGeometry(2, yard);
            sqlServerInsert.executeUpdate();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT layout, layout.STArea(), layout.STContains(geometry::Point(10, 10, 0))
                        FROM dbo.yards WHERE depot = ?
                        """)) {
            select.setString(1, "Rotterdam Port");
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Geometry layout = rows.unwrap(SQLServerResultSet.class).getGeometry(1);
                assertThat(layout.STGeometryType()).isEqualTo("POLYGON");
                assertThat(layout.STNumPoints()).isEqualTo(5);
                assertThat(layout.STAsText()).isEqualTo("POLYGON((0 0, 40 0, 40 25, 0 25, 0 0))");
                assertThat(rows.getDouble(2)).isEqualTo(1000.0);
                assertThat(rows.getBoolean(3)).isTrue();
            }
        }
    }

    @Test
    void tripTimesKeepTheirLocalOffset() throws Exception {
        OffsetDateTime departure = OffsetDateTime.of(2024, 3, 15, 8, 30, 0, 0, ZoneOffset.ofHours(1));
        OffsetDateTime arrival = OffsetDateTime.of(2024, 3, 15, 12, 45, 0, 0, ZoneOffset.ofHours(-4));
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.trips (trip_id, departed_at, arrived_at) VALUES (?, ?, ?)")) {
            SQLServerPreparedStatement sqlServerInsert = insert.unwrap(SQLServerPreparedStatement.class);
            sqlServerInsert.setInt(1, 501);
            sqlServerInsert.setDateTimeOffset(2,
                    DateTimeOffset.valueOf(Timestamp.from(departure.toInstant()), 60));
            sqlServerInsert.setObject(3, arrival);
            sqlServerInsert.executeUpdate();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT departed_at, arrived_at, DATEDIFF(MINUTE, departed_at, arrived_at),
                               CAST(SWITCHOFFSET(arrived_at, '+00:00') AS DATETIME2)
                        FROM dbo.trips WHERE trip_id = ?
                        """)) {
            select.setInt(1, 501);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                DateTimeOffset departed = rows.unwrap(SQLServerResultSet.class).getDateTimeOffset(1);
                assertThat(departed.getMinutesOffset()).isEqualTo(60);
                assertThat(departed.getOffsetDateTime()).isEqualTo(departure);
                assertThat(rows.getObject(2, OffsetDateTime.class)).isEqualTo(arrival);
                assertThat(rows.getInt(3)).isEqualTo(9 * 60 + 15);
                assertThat(rows.getObject(4, LocalDateTime.class)).isEqualTo(LocalDateTime.of(2024, 3, 15, 16, 45));
            }
        }
    }

    @Test
    void inspectionReportIsStoredAsXmlAndQueriedOnTheServer() throws Exception {
        UUID reportId = UUID.fromString("6f1c2b0e-3d4a-4e5f-9a8b-7c6d5e4f3a2b");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.inspections (report_id, vehicle, report, repair_cost) VALUES (?, ?, ?, ?)")) {
            SQLXML report = connection.createSQLXML();
            report.setString("""
                    <inspection vehicle="TRUCK-7">
                      <check name="brakes" status="pass"/>
                      <check name="tyres" status="fail"/>
                      <check name="lights" status="pass"/>
                    </inspection>
                    """);
            SQLServerPreparedStatement sqlServerInsert = insert.unwrap(SQLServerPreparedStatement.class);
            sqlServerInsert.setUniqueIdentifier(1, reportId.toString());
            sqlServerInsert.setString(2, "TRUCK-7");
            sqlServerInsert.setSQLXML(3, report);
            sqlServerInsert.setMoney(4, new BigDecimal("1249.9900"));
            sqlServerInsert.executeUpdate();
            report.free();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT report_id, report, repair_cost,
                               report.value('count(/inspection/check[@status="fail"])', 'INT')
                        FROM dbo.inspections WHERE vehicle = ?
                        """)) {
            select.setString(1, "TRUCK-7");
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(UUID.fromString(rows.getString(1))).isEqualTo(reportId);

                SQLXML stored = rows.getSQLXML(2);
                Document document = (Document) stored.getSource(DOMSource.class).getNode();
                Element root = document.getDocumentElement();
                assertThat(root.getTagName()).isEqualTo("inspection");
                assertThat(root.getAttribute("vehicle")).isEqualTo("TRUCK-7");
                NodeList checks = root.getElementsByTagName("check");
                List<String> failed = new ArrayList<>();
                for (int i = 0; i < checks.getLength(); i++) {
                    Element check = (Element) checks.item(i);
                    if ("fail".equals(check.getAttribute("status"))) {
                        failed.add(check.getAttribute("name"));
                    }
                }
                assertThat(checks.getLength()).isEqualTo(3);
                assertThat(failed).containsExactly("tyres");
                stored.free();

                assertThat(rows.getBigDecimal(3)).isEqualByComparingTo(new BigDecimal("1249.99"));
                assertThat(rows.getInt(4)).isEqualTo(1);
            }
        }
    }

    @Test
    void looselyTypedAttributesKeepTheirBaseTypeInSqlVariant() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.addBatch("INSERT INTO dbo.vehicle_attributes VALUES ('TRUCK-7', 'axles', CAST(3 AS INT))");
            statement.addBatch("INSERT INTO dbo.vehicle_attributes VALUES ('TRUCK-7', 'colour', CAST(N'red' AS NVARCHAR(20)))");
            statement.addBatch("INSERT INTO dbo.vehicle_attributes VALUES ('TRUCK-7', 'payload_t', CAST(18.5 AS DECIMAL(5, 1)))");
            assertThat(statement.executeBatch()).containsExactly(1, 1, 1);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, String> baseTypes = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT attribute, value, CAST(SQL_VARIANT_PROPERTY(value, 'BaseType') AS NVARCHAR(30))
                        FROM dbo.vehicle_attributes WHERE vehicle = ? ORDER BY attribute
                        """)) {
            select.setString(1, "TRUCK-7");
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    attributes.put(rows.getString(1), rows.getObject(2));
                    baseTypes.put(rows.getString(1), rows.getString(3));
                }
            }
        }

        assertThat(attributes.get("axles")).isEqualTo(3);
        assertThat(attributes.get("colour")).isEqualTo("red");
        assertThat(attributes.get("payload_t")).isInstanceOfSatisfying(BigDecimal.class,
                payload -> assertThat(payload).isEqualByComparingTo(new BigDecimal("18.5")));
        assertThat(baseTypes).containsExactly(Map.entry("axles", "int"), Map.entry("colour", "nvarchar"),
                Map.entry("payload_t", "decimal"));
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.positions (
                    id INT IDENTITY PRIMARY KEY,
                    vehicle NVARCHAR(20) NOT NULL,
                    location GEOGRAPHY NOT NULL)
                """,
                """
                CREATE TABLE dbo.depots (
                    name NVARCHAR(50) PRIMARY KEY,
                    site GEOGRAPHY NOT NULL)
                """,
                """
                INSERT INTO dbo.depots (name, site) VALUES
                    ('Amsterdam West', geography::Point(52.3700, 4.8800, 4326)),
                    ('Rotterdam Port', geography::Point(51.9000, 4.4000, 4326)),
                    ('Utrecht Hub', geography::Point(52.0907, 5.1214, 4326)),
                    ('Eindhoven South', geography::Point(51.4416, 5.4697, 4326))
                """,
                """
                CREATE TABLE dbo.yards (
                    depot NVARCHAR(50) PRIMARY KEY,
                    layout GEOMETRY NOT NULL)
                """,
                """
                CREATE TABLE dbo.trips (
                    trip_id INT PRIMARY KEY,
                    departed_at DATETIMEOFFSET(3) NOT NULL,
                    arrived_at DATETIMEOFFSET(3) NOT NULL)
                """,
                """
                CREATE TABLE dbo.inspections (
                    report_id UNIQUEIDENTIFIER PRIMARY KEY,
                    vehicle NVARCHAR(20) NOT NULL,
                    report XML NOT NULL,
                    repair_cost MONEY NOT NULL)
                """,
                """
                CREATE TABLE dbo.vehicle_attributes (
                    vehicle NVARCHAR(20) NOT NULL,
                    attribute NVARCHAR(30) NOT NULL,
                    value SQL_VARIANT NOT NULL,
                    PRIMARY KEY (vehicle, attribute))
                """);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("SQL Server did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
