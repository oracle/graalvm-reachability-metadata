/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises the generic service layer of mybatis-plus, an `IService` implemented by extending
 * `ServiceImpl` over a Spring `SqlSessionTemplate` mapper, with its batch writes, its query and
 * update chain wrappers and its single-row, map and object readers.
 *
 * A university registrar enrolls students in courses: it imports a term's enrollments in
 * batches that receive assigned ids, merges a correction sheet of new and known enrollments,
 * confirms and drops enrollments through lambda chains, reads transcripts, credit totals and
 * single enrollments, withdraws enrollments by id, and imports a term inside a Spring
 * transaction that is rolled back as a whole when the import fails.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseEnrollmentServiceLayerTest {

    private JdbcDataSource dataSource;

    private EnrollmentService registrar;

    private TransactionTemplate transactions;

    @BeforeAll
    void wireRegistrar() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:course_enrollment;DB_CLOSE_DELAY=-1");

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(EnrollmentMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setGlobalConfig(globalConfig);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory sessionFactory = factoryBean.getObject();
        assertThat(sessionFactory).isNotNull();

        SqlSessionTemplate template = new SqlSessionTemplate(sessionFactory);
        registrar = new EnrollmentService(template.getMapper(EnrollmentMapper.class));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void openEmptyTerm() throws SQLException {
        execute("DROP TABLE IF EXISTS course_enrollment");
        execute("""
                CREATE TABLE course_enrollment (
                    id BIGINT PRIMARY KEY,
                    student VARCHAR(40) NOT NULL,
                    course_code VARCHAR(12) NOT NULL,
                    credits INT NOT NULL,
                    status VARCHAR(12) NOT NULL
                )
                """);
    }

    @AfterAll
    void shutDownDatabase() throws SQLException {
        execute("SHUTDOWN");
    }

    @Test
    void importsTermInBatchesWithAssignedIds() throws SQLException {
        List<Enrollment> term = List.of(
                Enrollment.of("ada", "CS101", 6),
                Enrollment.of("ada", "MA201", 4),
                Enrollment.of("grace", "CS101", 6),
                Enrollment.of("grace", "PH110", 3),
                Enrollment.of("linus", "MA201", 4));

        assertThat(registrar.saveBatch(term, 2)).isTrue();

        assertThat(term).extracting(Enrollment::getId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(registrar.getEntityClass()).isEqualTo(Enrollment.class);
        assertThat(registrar.count()).isEqualTo(5L);
        assertThat(queryLong("SELECT COUNT(*) FROM course_enrollment WHERE status = 'PENDING'"))
                .isEqualTo(5L);

        Enrollment stored = registrar.getById(term.get(3).getId());
        assertThat(stored.getStudent()).isEqualTo("grace");
        assertThat(stored.getCourseCode()).isEqualTo("PH110");
        assertThat(stored.getCredits()).isEqualTo(3);
        assertThat(registrar.listByIds(List.of(term.get(0).getId(), term.get(4).getId())))
                .extracting(Enrollment::getStudent, Enrollment::getCourseCode)
                .containsExactlyInAnyOrder(tuple("ada", "CS101"), tuple("linus", "MA201"));
    }

    @Test
    void mergesCorrectionSheetOfNewAndKnownEnrollments() {
        Enrollment adaCs = Enrollment.of("ada", "CS101", 6);
        Enrollment graceMa = Enrollment.of("grace", "MA201", 4);
        assertThat(registrar.saveBatch(List.of(adaCs, graceMa))).isTrue();

        Enrollment correctedAda = registrar.getById(adaCs.getId());
        correctedAda.setCredits(8);
        Enrollment newcomer = Enrollment.of("linus", "CS101", 6);
        Enrollment transfer = Enrollment.of("margaret", "PH110", 3);
        transfer.setId(9_000L);
        assertThat(registrar.saveOrUpdateBatch(List.of(correctedAda, newcomer, transfer))).isTrue();

        assertThat(newcomer.getId()).isNotNull();
        assertThat(registrar.count()).isEqualTo(4L);
        assertThat(registrar.getById(adaCs.getId()).getCredits()).isEqualTo(8);
        assertThat(registrar.getById(9_000L).getStudent()).isEqualTo("margaret");

        Enrollment graceCorrection = registrar.getById(graceMa.getId());
        graceCorrection.setStatus("CONFIRMED");
        Enrollment linusCorrection = registrar.getById(newcomer.getId());
        linusCorrection.setStatus("CONFIRMED");
        assertThat(registrar.updateBatchById(List.of(graceCorrection, linusCorrection))).isTrue();
        assertThat(registrar.lambdaQuery().eq(Enrollment::getStatus, "CONFIRMED").list())
                .extracting(Enrollment::getStudent)
                .containsExactlyInAnyOrder("grace", "linus");

        Enrollment single = Enrollment.of("barbara", "CS101", 6);
        assertThat(registrar.saveOrUpdate(single)).isTrue();
        single.setCredits(5);
        assertThat(registrar.saveOrUpdate(single)).isTrue();
        assertThat(registrar.getById(single.getId()).getCredits()).isEqualTo(5);
        assertThat(registrar.count()).isEqualTo(5L);
    }

    @Test
    void confirmsAndDropsEnrollmentsThroughLambdaChains() {
        importTerm();

        assertThat(registrar.confirmCourse("CS101")).isEqualTo(3L);
        assertThat(registrar.lambdaQuery()
                        .eq(Enrollment::getStatus, "CONFIRMED")
                        .orderByAsc(Enrollment::getStudent)
                        .list())
                .extracting(Enrollment::getStudent, Enrollment::getCourseCode)
                .containsExactly(tuple("ada", "CS101"), tuple("grace", "CS101"), tuple("linus", "CS101"));

        assertThat(registrar.lambdaUpdate()
                        .set(Enrollment::getStatus, "DROPPED")
                        .eq(Enrollment::getStudent, "grace")
                        .ne(Enrollment::getCourseCode, "CS101")
                        .update())
                .isTrue();
        assertThat(registrar.lambdaUpdate()
                        .set(Enrollment::getStatus, "DROPPED")
                        .eq(Enrollment::getStudent, "nobody")
                        .update())
                .isFalse();

        assertThat(registrar.lambdaQuery().eq(Enrollment::getStatus, "DROPPED").count()).isEqualTo(1L);
        assertThat(registrar.lambdaQuery().eq(Enrollment::getStatus, "PENDING").exists()).isTrue();
        Optional<Enrollment> dropped = registrar.lambdaQuery()
                .eq(Enrollment::getStatus, "DROPPED")
                .oneOpt();
        assertThat(dropped).map(Enrollment::getCourseCode).contains("PH110");
        assertThat(registrar.getOne(new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudent, "nobody")))
                .isNull();

        assertThat(registrar.query().select("student").eq("course_code", "MA201").orderByAsc("student").list())
                .extracting(Enrollment::getStudent, Enrollment::getCredits)
                .containsExactly(tuple("ada", null), tuple("linus", null));
        assertThat(registrar.update().setSql("credits = credits + 1").eq("course_code", "PH110").update())
                .isTrue();
        assertThat(registrar.lambdaQuery().eq(Enrollment::getCourseCode, "PH110").one().getCredits())
                .isEqualTo(4);
    }

    @Test
    void readsTranscriptsCreditTotalsAndSingleEnrollments() {
        importTerm();

        assertThat(registrar.transcript("ada"))
                .extracting(Enrollment::getCourseCode)
                .containsExactly("CS101", "MA201");
        assertThat(registrar.creditsOf("grace")).isEqualTo(9L);
        assertThat(registrar.creditsOf("nobody")).isZero();

        LambdaQueryWrapper<Enrollment> cs101 = new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseCode, "CS101")
                .orderByAsc(Enrollment::getStudent);
        assertThat(registrar.getOne(cs101, false).getStudent()).isEqualTo("ada");
        assertThatThrownBy(() -> registrar.getOne(new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseCode, "CS101")))
                .isInstanceOf(TooManyResultsException.class);

        Map<String, Object> linusMath = registrar.getMap(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudent, "linus")
                .eq(Enrollment::getCourseCode, "MA201"));
        assertThat(linusMath)
                .containsEntry("STUDENT", "linus")
                .containsEntry("CREDITS", 4);
        Integer physicsCredits = registrar.getObj(new LambdaQueryWrapper<Enrollment>()
                .select(Enrollment::getCredits)
                .eq(Enrollment::getCourseCode, "PH110"), value -> ((Number) value).intValue());
        assertThat(physicsCredits).isEqualTo(3);

        assertThat(registrar.listByMap(Map.of("student", "grace")))
                .extracting(Enrollment::getCourseCode)
                .containsExactlyInAnyOrder("CS101", "PH110");
        assertThat(registrar.listMaps(new LambdaQueryWrapper<Enrollment>()
                        .select(Enrollment::getCourseCode)
                        .groupBy(Enrollment::getCourseCode)
                        .orderByAsc(Enrollment::getCourseCode)))
                .extracting(row -> row.get("COURSE_CODE"))
                .containsExactly("CS101", "MA201", "PH110");
    }

    @Test
    void withdrawsEnrollmentsById() {
        List<Enrollment> term = importTerm();
        Long adaCs = term.get(0).getId();
        Long adaMa = term.get(1).getId();
        Long graceCs = term.get(2).getId();

        assertThat(registrar.removeById(adaCs)).isTrue();
        assertThat(registrar.removeById(adaCs)).isFalse();
        assertThat(registrar.removeBatchByIds(List.of(adaMa, graceCs), 1)).isTrue();
        assertThat(registrar.removeByMap(Map.of("student", "linus"))).isTrue();
        assertThat(registrar.remove(new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudent, "nobody")))
                .isFalse();

        assertThat(registrar.list())
                .extracting(Enrollment::getStudent, Enrollment::getCourseCode)
                .containsExactly(tuple("grace", "PH110"));
    }

    @Test
    void rollsBackTermImportWhenItFailsInsideTransaction() throws SQLException {
        importTerm();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            registrar.saveBatch(List.of(Enrollment.of("katherine", "CS101", 6), Enrollment.of("katherine", "MA201", 4)));
            registrar.lambdaUpdate().set(Enrollment::getStatus, "CLOSED").update();
            throw new IllegalStateException("Course catalogue changed during import");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registrar.count()).isEqualTo(6L);
        assertThat(registrar.lambdaQuery().eq(Enrollment::getStudent, "katherine").exists()).isFalse();
        assertThat(queryLong("SELECT COUNT(*) FROM course_enrollment WHERE status = 'CLOSED'")).isZero();

        transactions.executeWithoutResult(status ->
                registrar.saveBatch(List.of(Enrollment.of("katherine", "CS101", 6))));
        assertThat(registrar.transcript("katherine")).extracting(Enrollment::getCourseCode).containsExactly("CS101");
        assertThat(queryLong("SELECT COUNT(*) FROM course_enrollment")).isEqualTo(7L);
    }

    private List<Enrollment> importTerm() {
        List<Enrollment> term = new ArrayList<>(List.of(
                Enrollment.of("ada", "CS101", 6),
                Enrollment.of("ada", "MA201", 4),
                Enrollment.of("grace", "CS101", 6),
                Enrollment.of("grace", "PH110", 3),
                Enrollment.of("linus", "CS101", 6),
                Enrollment.of("linus", "MA201", 4)));
        assertThat(registrar.saveBatch(term)).isTrue();
        return term;
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** The registrar's enrollment mapper, injected into the service as its `baseMapper`. */
    interface EnrollmentMapper extends BaseMapper<Enrollment> {
    }

    /** The registrar's enrollment service: generic `IService` operations plus domain queries. */
    static class EnrollmentService extends ServiceImpl<EnrollmentMapper, Enrollment> implements IService<Enrollment> {

        EnrollmentService(EnrollmentMapper mapper) {
            this.baseMapper = mapper;
        }

        long confirmCourse(String courseCode) {
            lambdaUpdate()
                    .set(Enrollment::getStatus, "CONFIRMED")
                    .eq(Enrollment::getCourseCode, courseCode)
                    .eq(Enrollment::getStatus, "PENDING")
                    .update();
            return lambdaQuery()
                    .eq(Enrollment::getCourseCode, courseCode)
                    .eq(Enrollment::getStatus, "CONFIRMED")
                    .count();
        }

        List<Enrollment> transcript(String student) {
            return lambdaQuery()
                    .eq(Enrollment::getStudent, student)
                    .orderByAsc(Enrollment::getCourseCode)
                    .list();
        }

        long creditsOf(String student) {
            return transcript(student).stream().mapToLong(Enrollment::getCredits).sum();
        }
    }

    /** One student's enrollment in one course; the id is assigned before the row is inserted. */
    @TableName("course_enrollment")
    public static class Enrollment {

        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        private String student;

        private String courseCode;

        private Integer credits;

        private String status;

        static Enrollment of(String student, String courseCode, int credits) {
            Enrollment enrollment = new Enrollment();
            enrollment.setStudent(student);
            enrollment.setCourseCode(courseCode);
            enrollment.setCredits(credits);
            enrollment.setStatus("PENDING");
            return enrollment;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStudent() {
            return student;
        }

        public void setStudent(String student) {
            this.student = student;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public void setCourseCode(String courseCode) {
            this.courseCode = courseCode;
        }

        public Integer getCredits() {
            return credits;
        }

        public void setCredits(Integer credits) {
            this.credits = credits;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
