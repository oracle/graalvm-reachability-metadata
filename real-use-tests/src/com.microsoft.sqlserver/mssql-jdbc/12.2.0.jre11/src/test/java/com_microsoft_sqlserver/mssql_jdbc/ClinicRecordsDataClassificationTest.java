/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerResultSet;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.dataclassification.ColumnSensitivity;
import com.microsoft.sqlserver.jdbc.dataclassification.InformationType;
import com.microsoft.sqlserver.jdbc.dataclassification.Label;
import com.microsoft.sqlserver.jdbc.dataclassification.SensitivityClassification;
import com.microsoft.sqlserver.jdbc.dataclassification.SensitivityClassification.SensitivityRank;
import com.microsoft.sqlserver.jdbc.dataclassification.SensitivityProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// Reads SQL Server data classification through the driver's result-set sensitivity
/// metadata: per-column labels, information types, and sensitivity ranks.
///
/// The scenario is a clinic records service that tags its patient columns with
/// `ADD SENSITIVITY CLASSIFICATION` and lets each query's classification decide
/// which values an export masks, so a new or reclassified column is protected
/// without changing application code.
public class ClinicRecordsDataClassificationTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Clinic#Records2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-classification-" + UUID.randomUUID();

    private static final String CONFIDENTIAL_ID = "331F0B13-76B5-2F1B-A77B-DEF5A73C73C2";

    private static final String HIGHLY_CONFIDENTIAL_ID = "B82CE05B-60A9-4CF3-8A8A-D6A0BB76E903";

    private static final String NATIONAL_ID_TYPE_ID = "6F5A11A7-08B1-19C3-59E5-8C89CF4F8444";

    private static final String HEALTH_TYPE_ID = "6E2C5B18-97CF-3073-27AB-F12F87493DA7";

    private static final String CONTACT_TYPE_ID = "5C503E21-22C6-81FA-620B-F369B8EC38D1";

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
        dataSource.setApplicationName("clinic-records");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void patientQueryCarriesLabelsInformationTypesAndRanksPerColumn() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT patient_id, full_name, national_id, diagnosis, phone, ward FROM dbo.patients")) {
            SensitivityClassification classification = rows.unwrap(ISQLServerResultSet.class)
                    .getSensitivityClassification();

            assertThat(classification).isNotNull();
            assertThat(classification.getLabels()).extracting(Label::getName)
                    .containsExactlyInAnyOrder("Confidential", "Highly Confidential");
            assertThat(classification.getInformationTypes())
                    .extracting(InformationType::getName, InformationType::getId)
                    .contains(
                            tuple("National ID", NATIONAL_ID_TYPE_ID),
                            tuple("Health", HEALTH_TYPE_ID),
                            tuple("Contact Info", CONTACT_TYPE_ID));
            assertThat(classification.getSensitivityRank()).isEqualTo(SensitivityRank.CRITICAL.getValue());

            Map<String, ColumnTag> tags = ColumnTag.of(rows);
            assertThat(tags.get("patient_id")).isNull();
            assertThat(tags.get("ward")).isNull();
            assertThat(tags.get("full_name"))
                    .isEqualTo(new ColumnTag("Confidential", CONFIDENTIAL_ID, "Contact Info", SensitivityRank.MEDIUM));
            assertThat(tags.get("national_id")).isEqualTo(new ColumnTag("Highly Confidential",
                    HIGHLY_CONFIDENTIAL_ID, "National ID", SensitivityRank.CRITICAL));
            assertThat(tags.get("diagnosis")).isEqualTo(
                    new ColumnTag("Highly Confidential", HIGHLY_CONFIDENTIAL_ID, "Health", SensitivityRank.HIGH));
            assertThat(tags.get("phone"))
                    .isEqualTo(new ColumnTag("Confidential", CONFIDENTIAL_ID, "Contact Info", SensitivityRank.LOW));
        }
    }

    @Test
    void exportMasksColumnsTheQueryClassifiesAsHighOrAbove() throws Exception {
        List<Map<String, String>> export;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT p.full_name, p.diagnosis, v.visit_date, v.notes
                        FROM dbo.patients p JOIN dbo.visits v ON v.patient_id = p.patient_id
                        WHERE p.ward = ? ORDER BY v.visit_date""")) {
            query.setString(1, "Cardiology");
            try (ResultSet rows = query.executeQuery()) {
                export = MaskingExporter.export(rows, SensitivityRank.HIGH);
            }
        }

        assertThat(export).containsExactly(
                Map.of("full_name", "Ada Lovelace", "diagnosis", "***", "visit_date", "2024-03-01", "notes", "***"),
                Map.of("full_name", "Ada Lovelace", "diagnosis", "***", "visit_date", "2024-04-12", "notes", "***"));
    }

    @Test
    void queryOverUnclassifiedColumnsReportsNoSensitiveColumns() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT ward, COUNT(*) AS patients FROM dbo.patients "
                        + "GROUP BY ward ORDER BY ward")) {
            assertThat(ColumnTag.of(rows)).isEmpty();
            assertThat(MaskingExporter.export(rows, SensitivityRank.LOW)).containsExactly(
                    Map.of("ward", "Cardiology", "patients", "1"),
                    Map.of("ward", "Oncology", "patients", "2"));
        }
    }

    @Test
    void storedProcedureResultSetIsClassifiedLikeTheTableItReads() throws Exception {
        try (Connection connection = dataSource.getConnection();
                CallableStatement lookup = connection.prepareCall("{call dbo.patient_card(?)}")) {
            lookup.setInt(1, 2);
            try (ResultSet rows = lookup.executeQuery()) {
                Map<String, ColumnTag> tags = ColumnTag.of(rows);
                assertThat(tags.keySet()).containsExactlyInAnyOrder("full_name", "national_id");
                assertThat(tags.get("national_id").rank()).isEqualTo(SensitivityRank.CRITICAL);

                assertThat(MaskingExporter.export(rows, SensitivityRank.CRITICAL))
                        .containsExactly(Map.of("full_name", "Grace Hopper", "national_id", "***", "ward", "Oncology"));
            }
        }
    }

    @Test
    void reclassifyingAColumnChangesWhatTheNextQueryMasks() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE dbo.referrals (referral_id INT PRIMARY KEY, clinic NVARCHAR(64), "
                    + "referrer_email NVARCHAR(128))");
            statement.execute("INSERT INTO dbo.referrals VALUES (1, 'North Clinic', 'gp@north.example')");

            String query = "SELECT clinic, referrer_email FROM dbo.referrals";
            try (ResultSet rows = statement.executeQuery(query)) {
                assertThat(MaskingExporter.export(rows, SensitivityRank.MEDIUM))
                        .containsExactly(Map.of("clinic", "North Clinic", "referrer_email", "gp@north.example"));
            }

            statement.execute("""
                    ADD SENSITIVITY CLASSIFICATION TO dbo.referrals.referrer_email
                    WITH (LABEL = 'Confidential', LABEL_ID = '%s', INFORMATION_TYPE = 'Contact Info',
                          INFORMATION_TYPE_ID = '%s', RANK = MEDIUM)""".formatted(CONFIDENTIAL_ID, CONTACT_TYPE_ID));
            try (ResultSet rows = statement.executeQuery(query)) {
                assertThat(ColumnTag.of(rows)).containsOnlyKeys("referrer_email");
                assertThat(MaskingExporter.export(rows, SensitivityRank.MEDIUM))
                        .containsExactly(Map.of("clinic", "North Clinic", "referrer_email", "***"));
            }

            statement.execute("DROP SENSITIVITY CLASSIFICATION FROM dbo.referrals.referrer_email");
            try (ResultSet rows = statement.executeQuery(query)) {
                assertThat(ColumnTag.of(rows)).isEmpty();
            }
        }
    }

    /// The label, information type, and rank a query reports for one column.
    record ColumnTag(String label, String labelId, String informationType, SensitivityRank rank) {

        /// Maps each classified column of the current result set to its tag, keyed by column label.
        static Map<String, ColumnTag> of(ResultSet rows) throws SQLException {
            Map<String, ColumnTag> tags = new LinkedHashMap<>();
            SensitivityClassification classification = rows.unwrap(ISQLServerResultSet.class)
                    .getSensitivityClassification();
            if (classification == null) {
                return tags;
            }
            ResultSetMetaData metaData = rows.getMetaData();
            List<ColumnSensitivity> columns = classification.getColumnSensitivities();
            assertThat(columns).hasSize(metaData.getColumnCount());
            for (int i = 0; i < columns.size(); i++) {
                List<SensitivityProperty> properties = columns.get(i).getSensitivityProperties();
                if (properties.isEmpty()) {
                    continue;
                }
                SensitivityProperty property = properties.get(0);
                tags.put(metaData.getColumnLabel(i + 1), new ColumnTag(property.getLabel().getName(),
                        property.getLabel().getId(), property.getInformationType().getName(),
                        rankOf(property.getSensitivityRank())));
            }
            return tags;
        }

        private static SensitivityRank rankOf(int value) {
            for (SensitivityRank rank : SensitivityRank.values()) {
                if (rank.getValue() == value) {
                    return rank;
                }
            }
            throw new IllegalArgumentException("Unknown sensitivity rank " + value);
        }
    }

    /// Renders result rows as strings, masking every column ranked at or above a threshold.
    static final class MaskingExporter {

        private MaskingExporter() {
        }

        static List<Map<String, String>> export(ResultSet rows, SensitivityRank threshold) throws SQLException {
            Map<String, ColumnTag> tags = ColumnTag.of(rows);
            ResultSetMetaData metaData = rows.getMetaData();
            List<Map<String, String>> export = new ArrayList<>();
            while (rows.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 1; column <= metaData.getColumnCount(); column++) {
                    String name = metaData.getColumnLabel(column);
                    ColumnTag tag = tags.get(name);
                    boolean masked = tag != null && tag.rank().getValue() >= threshold.getValue();
                    row.put(name, masked ? "***" : rows.getString(column));
                }
                export.add(row);
            }
            return export;
        }
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.patients (
                    patient_id INT PRIMARY KEY,
                    full_name NVARCHAR(100) NOT NULL,
                    national_id CHAR(11) NOT NULL,
                    diagnosis NVARCHAR(200) NULL,
                    phone VARCHAR(20) NULL,
                    ward NVARCHAR(32) NOT NULL)
                """,
                """
                CREATE TABLE dbo.visits (
                    visit_id INT PRIMARY KEY,
                    patient_id INT NOT NULL REFERENCES dbo.patients (patient_id),
                    visit_date DATE NOT NULL,
                    notes NVARCHAR(400) NULL)
                """,
                """
                INSERT INTO dbo.patients VALUES
                    (1, 'Ada Lovelace', '123-45-6789', 'Arrhythmia', '555-0101', 'Cardiology'),
                    (2, 'Grace Hopper', '987-65-4321', 'Lymphoma', '555-0102', 'Oncology'),
                    (3, 'Alan Turing', '555-12-3456', NULL, NULL, 'Oncology')
                """,
                """
                INSERT INTO dbo.visits VALUES
                    (10, 1, '2024-03-01', 'Prescribed beta blockers'),
                    (11, 1, '2024-04-12', 'Follow-up ECG normal'),
                    (12, 2, '2024-02-20', 'Started chemotherapy')
                """,
                classify("dbo.patients.full_name", "Confidential", CONFIDENTIAL_ID, "Contact Info", CONTACT_TYPE_ID,
                        "MEDIUM"),
                classify("dbo.patients.national_id", "Highly Confidential", HIGHLY_CONFIDENTIAL_ID, "National ID",
                        NATIONAL_ID_TYPE_ID, "CRITICAL"),
                classify("dbo.patients.diagnosis", "Highly Confidential", HIGHLY_CONFIDENTIAL_ID, "Health",
                        HEALTH_TYPE_ID, "HIGH"),
                classify("dbo.patients.phone", "Confidential", CONFIDENTIAL_ID, "Contact Info", CONTACT_TYPE_ID,
                        "LOW"),
                classify("dbo.visits.notes", "Highly Confidential", HIGHLY_CONFIDENTIAL_ID, "Health", HEALTH_TYPE_ID,
                        "HIGH"),
                """
                CREATE PROCEDURE dbo.patient_card @patient_id INT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    SELECT full_name, national_id, ward FROM dbo.patients WHERE patient_id = @patient_id;
                END
                """);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
    }

    private static String classify(String column, String label, String labelId, String informationType,
            String informationTypeId, String rank) {
        return """
                ADD SENSITIVITY CLASSIFICATION TO %s
                WITH (LABEL = '%s', LABEL_ID = '%s', INFORMATION_TYPE = '%s', INFORMATION_TYPE_ID = '%s',
                      RANK = %s)""".formatted(column, label, labelId, informationType, informationTypeId, rank);
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
