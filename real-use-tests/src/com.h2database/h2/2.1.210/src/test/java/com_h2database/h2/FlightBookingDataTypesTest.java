/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.api.ErrorCode;
import org.h2.api.Interval;
import org.h2.api.IntervalQualifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SQL data type system and its JDBC mappings: `ENUM` columns, `DOMAIN` types with checks,
 * `UUID`, `TIMESTAMP WITH TIME ZONE` read as `java.time` values, `INTERVAL` values read as
 * `org.h2.api.Interval`, `Duration`, and `Period`, `ARRAY` columns bound through
 * `java.sql.Array`, `JSON` values with `FORMAT JSON`, and `BLOB` and `CLOB` streams kept in a
 * database file across reopen.
 *
 * A flight booking service stores bookings with a cabin class, a validated seat code, departure
 * instants in local offsets, flight durations, meal requests, passenger preferences as JSON,
 * and scanned boarding passes, then reports on them the way its booking desk would.
 */
public class FlightBookingDataTypesTest {

    private static final String SCHEMA = """
            CREATE DOMAIN seat_code AS VARCHAR(3) CHECK (VALUE REGEXP '^[0-9]{1,2}[A-F]$');
            CREATE TABLE booking (
                id UUID PRIMARY KEY,
                passenger VARCHAR(80) NOT NULL,
                cabin ENUM('ECONOMY', 'PREMIUM', 'BUSINESS') NOT NULL,
                seat seat_code NOT NULL,
                departs TIMESTAMP WITH TIME ZONE NOT NULL,
                flight_time INTERVAL HOUR TO MINUTE NOT NULL,
                fare NUMERIC(9, 2) NOT NULL,
                meals VARCHAR(4) ARRAY[3],
                preferences JSON,
                boarding_pass BLOB,
                notes CLOB
            )
            """;

    @TempDir
    Path directory;

    private String url;

    private Connection connection;

    @BeforeEach
    void openBookings() throws SQLException {
        url = "jdbc:h2:" + directory.resolve("bookings").toAbsolutePath();
        connection = DriverManager.getConnection(url, "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute(SCHEMA);
        }
    }

    @AfterEach
    void closeBookings() throws SQLException {
        connection.close();
    }

    @Test
    void departuresAndFlightTimesMapToJavaTimeAndIntervals() throws SQLException {
        OffsetDateTime departs = OffsetDateTime.of(2026, 3, 29, 23, 30, 0, 0,
                ZoneOffset.ofHours(2));
        UUID id = book(new Booking("Ana", "BUSINESS", "2A", departs,
                Interval.ofHoursMinutes(11, 45),
                new BigDecimal("2450.00"), new String[] {"VGML"}, null));

        try (PreparedStatement select = connection.prepareStatement("""
                SELECT departs, flight_time, departs + flight_time AS arrives,
                       departs AT TIME ZONE '+00:00' AS departs_utc,
                       EXTRACT(MINUTE FROM flight_time) AS minutes
                FROM booking WHERE id = ?
                """)) {
            select.setObject(1, id);
            try (ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(departs, rows.getObject("departs", OffsetDateTime.class));
                Interval flightTime = rows.getObject("flight_time", Interval.class);
                assertEquals(IntervalQualifier.HOUR_TO_MINUTE, flightTime.getQualifier());
                assertEquals(11, flightTime.getHours());
                assertEquals(45, flightTime.getMinutes());
                assertEquals(Duration.ofMinutes(705),
                        rows.getObject("flight_time", Duration.class));
                assertEquals(OffsetDateTime.of(2026, 3, 30, 11, 15, 0, 0, ZoneOffset.ofHours(2)),
                        rows.getObject("arrives", OffsetDateTime.class));
                assertEquals(OffsetDateTime.of(2026, 3, 29, 21, 30, 0, 0, ZoneOffset.UTC),
                        rows.getObject("departs_utc", OffsetDateTime.class));
                assertEquals(45, rows.getInt("minutes"));
            }
        }

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT INTERVAL '1-6' YEAR TO MONTH AS validity,
                               CAST(DATE '2026-03-29' + INTERVAL '1-6' YEAR TO MONTH AS DATE)
                                   AS expires
                        """)) {
            assertTrue(rows.next());
            assertEquals(Period.of(1, 6, 0), rows.getObject("validity", Period.class));
            assertEquals(LocalDate.of(2027, 9, 29), rows.getObject("expires", LocalDate.class));
        }
    }

    @Test
    void cabinsSortByDeclarationOrderAndSeatsFollowTheDomain() throws SQLException {
        OffsetDateTime departs = OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        book(new Booking("Bea", "ECONOMY", "31C", departs, Interval.ofHoursMinutes(2, 5),
                new BigDecimal("89.90"), null, null));
        book(new Booking("Cid", "BUSINESS", "1A", departs, Interval.ofHoursMinutes(2, 5),
                new BigDecimal("640.00"), null, null));
        book(new Booking("Dee", "PREMIUM", "12F", departs, Interval.ofHoursMinutes(2, 5),
                new BigDecimal("210.00"), null, null));

        List<String> manifest = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT passenger, cabin FROM booking ORDER BY cabin DESC, passenger")) {
            while (rows.next()) {
                manifest.add(rows.getString("passenger") + "/" + rows.getString("cabin"));
            }
        }
        assertEquals(List.of("Cid/BUSINESS", "Dee/PREMIUM", "Bea/ECONOMY"), manifest);

        SQLException unknownCabin = assertThrows(SQLException.class, () -> book(new Booking("Eve",
                "FIRST", "2B", departs, Interval.ofHoursMinutes(2, 5), BigDecimal.TEN, null,
                null)));
        assertEquals(ErrorCode.ENUM_VALUE_NOT_PERMITTED, unknownCabin.getErrorCode());

        SQLException badSeat = assertThrows(SQLException.class, () -> book(new Booking("Eve",
                "ECONOMY", "40Z", departs, Interval.ofHoursMinutes(2, 5), BigDecimal.TEN, null,
                null)));
        assertEquals(ErrorCode.CHECK_CONSTRAINT_VIOLATED_1, badSeat.getErrorCode());

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM booking")) {
            assertTrue(rows.next());
            assertEquals(3, rows.getInt(1));
        }
    }

    @Test
    void mealRequestsAreStoredAsArrays() throws SQLException {
        OffsetDateTime departs = OffsetDateTime.of(2026, 6, 10, 14, 0, 0, 0,
                ZoneOffset.ofHours(-4));
        UUID ana = book(new Booking("Ana", "ECONOMY", "20A", departs, Interval.ofHoursMinutes(7, 0),
                new BigDecimal("399.00"), new String[] {"VGML", "LFML"}, null));
        book(new Booking("Ben", "ECONOMY", "20B", departs, Interval.ofHoursMinutes(7, 0),
                new BigDecimal("399.00"), new String[] {"KSML"}, null));
        book(new Booking("Cal", "ECONOMY", "20C", departs, Interval.ofHoursMinutes(7, 0),
                new BigDecimal("399.00"), new String[0], null));

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT meals, CARDINALITY(meals) FROM booking WHERE id = ?")) {
            select.setObject(1, ana);
            try (ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                Array meals = rows.getArray(1);
                assertArrayEquals(new Object[] {"VGML", "LFML"}, (Object[]) meals.getArray());
                assertEquals(2, rows.getInt(2));
            }
        }

        List<String> vegetarians = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT passenger FROM booking
                        WHERE ARRAY_CONTAINS(meals, 'VGML') OR ARRAY_CONTAINS(meals, 'KSML')
                        ORDER BY passenger
                        """)) {
            while (rows.next()) {
                vegetarians.add(rows.getString(1));
            }
        }
        assertEquals(List.of("Ana", "Ben"), vegetarians);

        List<String> galleyOrder = new ArrayList<>();
        try (PreparedStatement unnest = connection.prepareStatement(
                "SELECT code, COUNT(*) FROM UNNEST(?) AS t(code) GROUP BY code ORDER BY code")) {
            unnest.setArray(1, connection.createArrayOf("VARCHAR",
                    new String[] {"VGML", "KSML", "VGML", "LFML"}));
            try (ResultSet rows = unnest.executeQuery()) {
                while (rows.next()) {
                    galleyOrder.add(rows.getString(1) + "x" + rows.getInt(2));
                }
            }
        }
        assertEquals(List.of("KSMLx1", "LFMLx1", "VGMLx2"), galleyOrder);

        SQLException tooMany = assertThrows(SQLException.class, () -> book(new Booking("Dan",
                "ECONOMY", "21A", departs, Interval.ofHoursMinutes(7, 0), BigDecimal.TEN,
                new String[] {"VGML", "LFML", "KSML", "DBML"}, null)));
        assertEquals(ErrorCode.VALUE_TOO_LONG_2, tooMany.getErrorCode());
    }

    @Test
    void preferencesAreValidatedAndComposedAsJson() throws SQLException {
        OffsetDateTime departs = OffsetDateTime.of(2026, 7, 4, 9, 15, 0, 0, ZoneOffset.ofHours(1));
        UUID ana = book(new Booking("Ana", "PREMIUM", "10A", departs,
                Interval.ofHoursMinutes(3, 30),
                new BigDecimal("320.00"), null, "{ \"window\" : true, \"tags\" : [ \"quiet\" ] }"));
        book(new Booking("Ben", "PREMIUM", "10B", departs, Interval.ofHoursMinutes(3, 30),
                new BigDecimal("320.00"), null, "[\"aisle\"]"));

        try (PreparedStatement select = connection.prepareStatement("""
                SELECT preferences, preferences IS JSON OBJECT,
                       JSON_OBJECT('seat': seat, 'cabin': CAST(cabin AS VARCHAR),
                                   'prefs': preferences)
                FROM booking WHERE id = ?
                """)) {
            select.setObject(1, ana);
            try (ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("{\"window\":true,\"tags\":[\"quiet\"]}", rows.getString(1));
                assertTrue(rows.getBoolean(2));
                assertEquals("{\"seat\":\"10A\",\"cabin\":\"PREMIUM\","
                        + "\"prefs\":{\"window\":true,\"tags\":[\"quiet\"]}}",
                        rows.getString(3));
            }
        }

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT JSON_ARRAYAGG(passenger ORDER BY passenger), COUNT(*) FILTER (
                            WHERE preferences IS JSON ARRAY)
                        FROM booking
                        """)) {
            assertTrue(rows.next());
            assertEquals("[\"Ana\",\"Ben\"]", rows.getString(1));
            assertEquals(1, rows.getInt(2));
        }

        SQLException malformed = assertThrows(SQLException.class, () -> book(new Booking("Cal",
                "PREMIUM", "10C", departs, Interval.ofHoursMinutes(3, 30), BigDecimal.TEN, null,
                "{\"window\": tru")));
        assertEquals(ErrorCode.DATA_CONVERSION_ERROR_1, malformed.getErrorCode());
    }

    @Test
    void boardingPassesAndNotesSurviveAsLobsAcrossReopen() throws SQLException, IOException {
        byte[] scan = new byte[256 * 1024];
        for (int i = 0; i < scan.length; i++) {
            scan[i] = (byte) (i * 31 + 7);
        }
        String notes = "Wheelchair at gate. ".repeat(4_000);
        OffsetDateTime departs = OffsetDateTime.of(2026, 8, 20, 6, 45, 0, 0, ZoneOffset.ofHours(9));
        UUID id = book(new Booking("Ana", "BUSINESS", "3D", departs,
                Interval.ofHoursMinutes(13, 10),
                new BigDecimal("5100.00"), null, null));

        try (PreparedStatement attach = connection.prepareStatement(
                "UPDATE booking SET boarding_pass = ?, notes = ? WHERE id = ?")) {
            Blob blob = connection.createBlob();
            blob.setBytes(1, scan);
            attach.setBlob(1, blob);
            attach.setCharacterStream(2, new StringReader(notes));
            attach.setObject(3, id);
            assertEquals(1, attach.executeUpdate());
        }
        connection.close();

        connection = DriverManager.getConnection(url, "sa", "");
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT boarding_pass, notes, OCTET_LENGTH(boarding_pass), CHAR_LENGTH(notes)
                FROM booking WHERE id = ?
                """)) {
            select.setObject(1, id);
            try (ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                Blob blob = rows.getBlob(1);
                assertEquals(scan.length, blob.length());
                try (InputStream stream = blob.getBinaryStream()) {
                    assertArrayEquals(scan, stream.readAllBytes());
                }
                Clob clob = rows.getClob(2);
                assertEquals(notes.length(), clob.length());
                assertEquals("Wheelchair at gate.", clob.getSubString(1, 19));
                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder text = new StringBuilder();
                    char[] buffer = new char[8192];
                    for (int read = reader.read(buffer); read >= 0; read = reader.read(buffer)) {
                        text.append(buffer, 0, read);
                    }
                    assertEquals(notes, text.toString());
                }
                assertEquals(scan.length, rows.getLong(3));
                assertEquals(notes.length(), rows.getLong(4));
            }
        }
    }

    @Test
    void resultMetadataDescribesEveryColumnType() throws SQLException {
        UUID generated;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT RANDOM_UUID()")) {
            assertTrue(rows.next());
            generated = rows.getObject(1, UUID.class);
        }
        assertEquals(4, generated.version());

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM booking")) {
            assertFalse(rows.next());
            ResultSetMetaData metaData = rows.getMetaData();
            List<String> types = new ArrayList<>();
            for (int column = 1; column <= metaData.getColumnCount(); column++) {
                types.add(metaData.getColumnLabel(column) + ":"
                        + metaData.getColumnTypeName(column));
            }
            assertEquals(List.of("ID:UUID", "PASSENGER:CHARACTER VARYING",
                    "CABIN:ENUM('ECONOMY', 'PREMIUM', 'BUSINESS')",
                    "SEAT:CHARACTER VARYING", "DEPARTS:TIMESTAMP WITH TIME ZONE",
                    "FLIGHT_TIME:INTERVAL HOUR TO MINUTE", "FARE:NUMERIC",
                    "MEALS:CHARACTER VARYING(4) ARRAY", "PREFERENCES:JSON",
                    "BOARDING_PASS:BINARY LARGE OBJECT", "NOTES:CHARACTER LARGE OBJECT"), types);
            assertEquals(OffsetDateTime.class.getName(), metaData.getColumnClassName(5));
            assertEquals(Interval.class.getName(), metaData.getColumnClassName(6));
        }

        try (Statement statement = connection.createStatement();
                ResultSet domains = statement.executeQuery("""
                SELECT DOMAIN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM INFORMATION_SCHEMA.DOMAINS
                """)) {
            assertTrue(domains.next());
            assertEquals("SEAT_CODE", domains.getString(1));
            assertEquals("CHARACTER VARYING", domains.getString(2));
            assertEquals(3, domains.getInt(3));
            assertFalse(domains.next());
        }
    }

    private UUID book(Booking booking) throws SQLException {
        UUID id = UUID.nameUUIDFromBytes(booking.passenger().concat(booking.seat())
                .getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO booking (id, passenger, cabin, seat, departs, flight_time, fare, meals,
                                     preferences)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ? FORMAT JSON)
                """)) {
            insert.setObject(1, id);
            insert.setString(2, booking.passenger());
            insert.setString(3, booking.cabin());
            insert.setString(4, booking.seat());
            insert.setObject(5, booking.departs());
            insert.setObject(6, booking.flightTime());
            insert.setBigDecimal(7, booking.fare());
            if (booking.meals() == null) {
                insert.setNull(8, Types.ARRAY);
            } else {
                insert.setArray(8, connection.createArrayOf("VARCHAR", booking.meals()));
            }
            insert.setString(9, booking.preferences());
            insert.executeUpdate();
        }
        return id;
    }

    /** One booking as the booking desk enters it. */
    record Booking(String passenger, String cabin, String seat, OffsetDateTime departs,
            Interval flightTime, BigDecimal fare, String[] meals, String preferences) {
    }
}
