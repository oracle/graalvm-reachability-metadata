/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises dynamic table names through `DynamicTableNameInnerInterceptor` and a
 * `TableNameHandler`, which rewrite the table names of injected and annotated statements before
 * they reach the database, with a hook that runs once per rewritten statement.
 *
 * A utility bills electricity meters per month and keeps each closed month's readings in its own
 * `meter_reading_yyyyMM` table next to the open month's `meter_reading` table. The billing code
 * routes the next statement to a month through a thread-local that the interceptor hook clears,
 * records and reads readings per month, corrects and voids readings without touching other
 * months, joins the routed readings with the shared `meter` table, and audits the open month
 * through a statement that opts out of the rewrite with `@InterceptorIgnore`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeterBillingDynamicTableNameTest {

    private static final YearMonth JANUARY = YearMonth.of(2024, 1);

    private static final YearMonth FEBRUARY = YearMonth.of(2024, 2);

    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    private static final ThreadLocal<YearMonth> BILLING_MONTH = new ThreadLocal<>();

    private final List<String> rewrittenTables = new ArrayList<>();

    private JdbcDataSource dataSource;

    private SqlSessionFactory sessionFactory;

    private SqlSession session;

    private MeterReadingMapper readings;

    @BeforeAll
    void buildSessionFactory() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:meter_billing;DB_CLOSE_DELAY=-1");
        Environment environment = new Environment("billing", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableNameHandler monthlyTables = (sql, tableName) -> {
            YearMonth month = BILLING_MONTH.get();
            if (month == null || !"meter_reading".equals(tableName)) {
                return tableName;
            }
            String monthTable = tableName + "_" + month.format(MONTH_SUFFIX);
            rewrittenTables.add(monthTable);
            return monthTable;
        };
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DynamicTableNameInnerInterceptor(BILLING_MONTH::remove, monthlyTables));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(MeterReadingMapper.class);
        sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void openBillingPeriods() throws SQLException {
        for (String table : List.of("meter_reading", "meter_reading_202401", "meter_reading_202402")) {
            execute("DROP TABLE IF EXISTS " + table);
            execute("""
                    CREATE TABLE %s (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        meter_code VARCHAR(20) NOT NULL,
                        kwh INT NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """.formatted(table));
        }
        execute("DROP TABLE IF EXISTS meter");
        execute("CREATE TABLE meter (code VARCHAR(20) PRIMARY KEY, district VARCHAR(40) NOT NULL)");
        execute("INSERT INTO meter VALUES ('M-100', 'Harbour'), ('M-200', 'Harbour'), ('M-300', 'Uptown')");
        rewrittenTables.clear();
        session = sessionFactory.openSession(true);
        readings = session.getMapper(MeterReadingMapper.class);
    }

    @AfterEach
    void closeSession() {
        BILLING_MONTH.remove();
        session.close();
    }

    @AfterAll
    void shutDownDatabase() throws SQLException {
        execute("SHUTDOWN");
    }

    @Test
    void readingsLandInTheTableOfTheirBillingMonth() throws SQLException {
        MeterReading januaryHarbour = record(JANUARY, "M-100", 310);
        MeterReading januaryUptown = record(JANUARY, "M-300", 120);
        MeterReading februaryHarbour = record(FEBRUARY, "M-100", 280);
        MeterReading openMonth = new MeterReading("M-200", 45, "ESTIMATED");
        assertThat(readings.insert(openMonth)).isEqualTo(1);

        assertThat(januaryHarbour.getId()).isEqualTo(1L);
        assertThat(januaryUptown.getId()).isEqualTo(2L);
        assertThat(februaryHarbour.getId()).isEqualTo(1L);
        assertThat(openMonth.getId()).isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading_202401")).isEqualTo(2L);
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading_202402")).isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading")).isEqualTo(1L);
        assertThat(rewrittenTables).containsExactly(
                "meter_reading_202401", "meter_reading_202401", "meter_reading_202402");
    }

    @Test
    void queriesReadOnlyTheRoutedMonth() {
        record(JANUARY, "M-100", 310);
        record(JANUARY, "M-200", 95);
        record(FEBRUARY, "M-200", 88);
        readings.insert(new MeterReading("M-100", 12, "ESTIMATED"));

        assertThat(inMonth(JANUARY, () -> readings.selectById(1L)))
                .extracting(MeterReading::getMeterCode, MeterReading::getKwh)
                .containsExactly("M-100", 310);
        assertThat(inMonth(FEBRUARY, () -> readings.selectById(1L)))
                .extracting(MeterReading::getMeterCode, MeterReading::getKwh)
                .containsExactly("M-200", 88);
        assertThat(readings.selectById(1L))
                .extracting(MeterReading::getMeterCode, MeterReading::getKwh)
                .containsExactly("M-100", 12);

        LambdaQueryWrapper<MeterReading> meterTwoHundred = new LambdaQueryWrapper<MeterReading>()
                .eq(MeterReading::getMeterCode, "M-200");
        assertThat(inMonth(JANUARY, () -> readings.selectList(meterTwoHundred)))
                .extracting(MeterReading::getKwh)
                .containsExactly(95);
        assertThat(inMonth(FEBRUARY, () -> readings.selectList(meterTwoHundred)))
                .extracting(MeterReading::getKwh)
                .containsExactly(88);
        assertThat(readings.selectList(meterTwoHundred)).isEmpty();
        assertThat(inMonth(JANUARY, () -> readings.selectCount(null))).isEqualTo(2L);
        assertThat(inMonth(FEBRUARY, () -> readings.selectCount(null))).isEqualTo(1L);
    }

    @Test
    void correctionsAndVoidsStayInsideTheRoutedMonth() throws SQLException {
        MeterReading january = record(JANUARY, "M-300", 140);
        record(FEBRUARY, "M-300", 150);
        record(FEBRUARY, "M-100", 260);

        LambdaUpdateWrapper<MeterReading> confirmUptown = new LambdaUpdateWrapper<MeterReading>()
                .set(MeterReading::getStatus, "ACTUAL")
                .eq(MeterReading::getMeterCode, "M-300");
        assertThat(inMonth(FEBRUARY, () -> readings.update(null, confirmUptown))).isEqualTo(1);

        january.setKwh(145);
        january.setStatus("CORRECTED");
        assertThat(inMonth(JANUARY, () -> readings.updateById(january))).isEqualTo(1);

        assertThat(inMonth(FEBRUARY, () -> readings.delete(new LambdaQueryWrapper<MeterReading>()
                .eq(MeterReading::getMeterCode, "M-100")))).isEqualTo(1);

        assertThat(queryString("SELECT status || ':' || kwh FROM meter_reading_202401 WHERE meter_code = 'M-300'"))
                .isEqualTo("CORRECTED:145");
        assertThat(queryString("SELECT status || ':' || kwh FROM meter_reading_202402 WHERE meter_code = 'M-300'"))
                .isEqualTo("ACTUAL:150");
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading_202402")).isEqualTo(1L);

        assertThat(inMonth(JANUARY, () -> readings.deleteById(january.getId()))).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading_202401")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM meter_reading_202402")).isEqualTo(1L);
    }

    @Test
    void joinedStatementRoutesOnlyTheMonthlyTable() {
        record(JANUARY, "M-100", 300);
        record(JANUARY, "M-200", 100);
        record(JANUARY, "M-300", 50);
        record(FEBRUARY, "M-200", 70);
        record(FEBRUARY, "M-300", 90);

        assertThat(inMonth(JANUARY, () -> readings.usageByDistrict()))
                .extracting(DistrictUsage::getDistrict, DistrictUsage::getTotalKwh)
                .containsExactly(tuple("Harbour", 400L), tuple("Uptown", 50L));
        assertThat(inMonth(FEBRUARY, () -> readings.usageByDistrict()))
                .extracting(DistrictUsage::getDistrict, DistrictUsage::getTotalKwh)
                .containsExactly(tuple("Harbour", 70L), tuple("Uptown", 90L));
        assertThat(readings.usageByDistrict()).isEmpty();
        assertThat(rewrittenTables).endsWith("meter_reading_202401", "meter_reading_202402");
    }

    @Test
    void hookEndsTheRoutingAfterOneStatement() throws SQLException {
        int written = inMonth(JANUARY, () -> {
            int january = readings.insert(new MeterReading("M-100", 333, "ACTUAL"));
            int openMonth = readings.insert(new MeterReading("M-100", 7, "ESTIMATED"));
            return january + openMonth;
        });

        assertThat(written).isEqualTo(2);
        assertThat(BILLING_MONTH.get()).isNull();
        assertThat(queryString("SELECT status FROM meter_reading_202401")).isEqualTo("ACTUAL");
        assertThat(queryString("SELECT status FROM meter_reading")).isEqualTo("ESTIMATED");
        assertThat(rewrittenTables).containsExactly("meter_reading_202401");
    }

    @Test
    void auditStatementIgnoresTheRoutingAndReadsTheOpenMonth() {
        record(JANUARY, "M-100", 310);
        record(JANUARY, "M-200", 95);
        readings.insert(new MeterReading("M-300", 18, "ESTIMATED"));

        assertThat(inMonth(JANUARY, () -> readings.countOpenMonth())).isEqualTo(1L);
        assertThat(rewrittenTables).containsExactly("meter_reading_202401", "meter_reading_202401");
        assertThat(BILLING_MONTH.get()).isEqualTo(JANUARY);
    }

    private MeterReading record(YearMonth month, String meterCode, int kwh) {
        MeterReading reading = new MeterReading(meterCode, kwh, "ACTUAL");
        assertThat(inMonth(month, () -> readings.insert(reading))).isEqualTo(1);
        return reading;
    }

    /** Routes the next statement to the month's table; the interceptor hook ends the routing. */
    private static <T> T inMonth(YearMonth month, Supplier<T> billingWork) {
        BILLING_MONTH.set(month);
        return billingWork.get();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    interface MeterReadingMapper extends BaseMapper<MeterReading> {

        @Select("""
                SELECT m.district AS district, SUM(r.kwh) AS total_kwh
                FROM meter_reading r JOIN meter m ON m.code = r.meter_code
                GROUP BY m.district ORDER BY m.district
                """)
        List<DistrictUsage> usageByDistrict();

        @InterceptorIgnore(dynamicTableName = "true")
        @Select("SELECT COUNT(*) FROM meter_reading")
        long countOpenMonth();
    }

    /** One meter's consumption in a billing month; `status` is `ACTUAL`, `ESTIMATED` or `CORRECTED`. */
    @TableName("meter_reading")
    public static class MeterReading {

        @TableId(type = IdType.AUTO)
        private Long id;

        private String meterCode;

        private Integer kwh;

        private String status;

        public MeterReading() {
        }

        MeterReading(String meterCode, Integer kwh, String status) {
            this.meterCode = meterCode;
            this.kwh = kwh;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getMeterCode() {
            return meterCode;
        }

        public void setMeterCode(String meterCode) {
            this.meterCode = meterCode;
        }

        public Integer getKwh() {
            return kwh;
        }

        public void setKwh(Integer kwh) {
            this.kwh = kwh;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** A district's summed consumption for the routed billing month. */
    public static class DistrictUsage {

        private String district;

        private Long totalKwh;

        public String getDistrict() {
            return district;
        }

        public void setDistrict(String district) {
            this.district = district;
        }

        public Long getTotalKwh() {
            return totalKwh;
        }

        public void setTotalKwh(Long totalKwh) {
            this.totalKwh = totalKwh;
        }
    }
}
