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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises row-level data permission through `DataPermissionInterceptor` and a
 * `MultiDataPermissionHandler`, which appends a per-table, per-viewer condition to every injected
 * and annotated select, update and delete.
 *
 * An expense system stores every employee's claims in one table. Employees see and change only
 * their own claims, managers only the claims of the departments they lead, and auditors every
 * claim. The handler reads the signed-in viewer from a request-scoped holder and restricts the
 * claim table only, so wrapper conditions joined with `OR` stay inside the restriction, a joined
 * report filters the aliased claim table but not the department table, a sub-select is restricted
 * too, and a finance statement opts out with `@InterceptorIgnore`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseClaimDataPermissionTest {

    private static final Viewer ALICE = new Viewer("alice", Role.EMPLOYEE, Set.of());

    private static final Viewer BOB = new Viewer("bob", Role.EMPLOYEE, Set.of());

    private static final Viewer MARIA = new Viewer("maria", Role.MANAGER, Set.of(10L));

    private static final Viewer RALPH = new Viewer("ralph", Role.MANAGER, Set.of(20L, 30L));

    private static final Viewer OLGA = new Viewer("olga", Role.AUDITOR, Set.of());

    private JdbcDataSource dataSource;

    private SqlSessionFactory sessionFactory;

    @BeforeAll
    void buildSessionFactory() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:expense_claims;DB_CLOSE_DELAY=-1");
        Environment environment = new Environment("expenses", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new ClaimVisibilityHandler()));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ClaimMapper.class);
        configuration.addMapper(DepartmentMapper.class);
        sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void createSeededSchema() throws SQLException {
        execute("DROP TABLE IF EXISTS expense_claim");
        execute("DROP TABLE IF EXISTS department");
        execute("""
                CREATE TABLE department (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(40) NOT NULL
                )
                """);
        execute("""
                CREATE TABLE expense_claim (
                    id BIGINT PRIMARY KEY,
                    owner VARCHAR(20) NOT NULL,
                    department_id BIGINT NOT NULL,
                    title VARCHAR(60) NOT NULL,
                    amount DECIMAL(10, 2) NOT NULL,
                    status VARCHAR(12) NOT NULL
                )
                """);
        execute("INSERT INTO department (id, name) VALUES (10, 'Engineering'), (20, 'Sales'), (30, 'Legal')");
        execute("""
                INSERT INTO expense_claim (id, owner, department_id, title, amount, status) VALUES
                    (1, 'alice', 10, 'Conference ticket', 450.00, 'SUBMITTED'),
                    (2, 'alice', 10, 'Taxi to airport', 38.50, 'DRAFT'),
                    (3, 'bob', 10, 'Team lunch', 120.00, 'SUBMITTED'),
                    (4, 'carol', 20, 'Client dinner', 210.00, 'SUBMITTED'),
                    (5, 'dave', 20, 'Trade show booth', 1500.00, 'APPROVED'),
                    (6, 'erin', 30, 'Court filing fee', 75.00, 'SUBMITTED')
                """);
    }

    @AfterAll
    void shutDownDatabase() throws SQLException {
        execute("SHUTDOWN");
    }

    @Test
    void employeesSeeOnlyTheirOwnClaims() {
        List<String> aliceClaims = as(ALICE, mappers -> titles(mappers.claims()
                .selectList(new LambdaQueryWrapper<ExpenseClaim>().orderByAsc(ExpenseClaim::getTitle))));
        Long aliceCount = as(ALICE, mappers -> mappers.claims().selectCount(null));
        ExpenseClaim colleagueClaim = as(ALICE, mappers -> mappers.claims().selectById(3L));
        ExpenseClaim ownClaim = as(ALICE, mappers -> mappers.claims().selectById(1L));

        assertThat(aliceClaims).containsExactly("Conference ticket", "Taxi to airport");
        assertThat(aliceCount).isEqualTo(2L);
        assertThat(colleagueClaim).isNull();
        assertThat(ownClaim.getAmount()).isEqualByComparingTo("450.00");
        assertThat(ownClaim.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void orConditionsOfTheCallerStayInsideTheManagersDepartments() {
        List<String> pending = as(MARIA, mappers -> titles(mappers.claims().selectList(
                new LambdaQueryWrapper<ExpenseClaim>()
                        .eq(ExpenseClaim::getStatus, "SUBMITTED")
                        .or()
                        .eq(ExpenseClaim::getStatus, "DRAFT")
                        .orderByAsc(ExpenseClaim::getTitle))));
        List<String> regionalPending = as(RALPH, mappers -> titles(mappers.claims().selectList(
                new LambdaQueryWrapper<ExpenseClaim>()
                        .eq(ExpenseClaim::getStatus, "SUBMITTED")
                        .or()
                        .ge(ExpenseClaim::getAmount, new BigDecimal("1000"))
                        .orderByAsc(ExpenseClaim::getTitle))));
        Long auditorCount = as(OLGA, mappers -> mappers.claims().selectCount(null));

        assertThat(pending).containsExactly("Conference ticket", "Taxi to airport", "Team lunch");
        assertThat(regionalPending).containsExactly("Client dinner", "Court filing fee", "Trade show booth");
        assertThat(auditorCount).isEqualTo(6L);
    }

    @Test
    void updatesAndDeletionsReachOnlyPermittedClaims() throws SQLException {
        Integer approved = as(MARIA, mappers -> mappers.claims().update(null,
                new LambdaUpdateWrapper<ExpenseClaim>()
                        .set(ExpenseClaim::getStatus, "APPROVED")
                        .eq(ExpenseClaim::getStatus, "SUBMITTED")));
        assertThat(approved).isEqualTo(2);
        assertThat(statusOf(1L)).isEqualTo("APPROVED");
        assertThat(statusOf(3L)).isEqualTo("APPROVED");
        assertThat(statusOf(4L)).isEqualTo("SUBMITTED");
        assertThat(statusOf(6L)).isEqualTo("SUBMITTED");

        ExpenseClaim inflated = new ExpenseClaim();
        inflated.setId(1L);
        inflated.setAmount(new BigDecimal("4500.00"));
        Integer foreignEdit = as(BOB, mappers -> mappers.claims().updateById(inflated));
        assertThat(foreignEdit).isZero();

        Integer foreignDelete = as(ALICE, mappers -> mappers.claims().deleteById(3L));
        Integer withdrawnDrafts = as(ALICE, mappers -> mappers.claims().delete(
                new LambdaQueryWrapper<ExpenseClaim>().eq(ExpenseClaim::getStatus, "DRAFT")));
        assertThat(foreignDelete).isZero();
        assertThat(withdrawnDrafts).isEqualTo(1);

        assertThat(queryLong("SELECT COUNT(*) FROM expense_claim")).isEqualTo(5L);
        assertThat(queryLong("SELECT COUNT(*) FROM expense_claim WHERE id = 2")).isZero();
        assertThat(queryString("SELECT CAST(amount AS VARCHAR) FROM expense_claim WHERE id = 1"))
                .isEqualTo("450.00");
    }

    @Test
    void joinedReportsAndSubSelectsAreRestrictedOnTheClaimTable() {
        List<ClaimLine> engineeringReport = as(MARIA, mappers -> mappers.claims().claimReport());
        List<ClaimLine> regionalReport = as(RALPH, mappers -> mappers.claims().claimReport());

        assertThat(engineeringReport)
                .extracting(ClaimLine::getTitle, ClaimLine::getDepartmentName)
                .containsExactly(
                        tuple("Conference ticket", "Engineering"),
                        tuple("Taxi to airport", "Engineering"),
                        tuple("Team lunch", "Engineering"));
        assertThat(regionalReport)
                .extracting(ClaimLine::getTitle, ClaimLine::getDepartmentName)
                .containsExactly(
                        tuple("Client dinner", "Sales"),
                        tuple("Court filing fee", "Legal"),
                        tuple("Trade show booth", "Sales"));

        List<String> bobsDepartments = as(BOB, mappers -> mappers.departments().departmentsWithClaims());
        List<String> regionalDepartments = as(RALPH, mappers -> mappers.departments().departmentsWithClaims());
        List<String> allDepartments = as(BOB, mappers -> mappers.departments()
                .selectList(new LambdaQueryWrapper<Department>().orderByAsc(Department::getName))
                .stream()
                .map(Department::getName)
                .toList());
        assertThat(bobsDepartments).containsExactly("Engineering");
        assertThat(regionalDepartments).containsExactly("Legal", "Sales");
        assertThat(allDepartments).containsExactly("Engineering", "Legal", "Sales");
    }

    @Test
    void financeTotalsOptOutOfTheDataPermission() {
        BigDecimal companyTotal = as(ALICE, mappers -> mappers.claims().companyTotal());
        BigDecimal aliceTotal = as(ALICE, mappers -> mappers.claims().visibleTotal());
        BigDecimal regionalTotal = as(RALPH, mappers -> mappers.claims().visibleTotal());

        assertThat(companyTotal).isEqualByComparingTo("2393.50");
        assertThat(aliceTotal).isEqualByComparingTo("488.50");
        assertThat(regionalTotal).isEqualByComparingTo("1785.00");
    }

    /**
     * Serves one request for `viewer`: signs the viewer in, hands the work a session of its own and
     * signs the viewer out again once the request completes.
     */
    private <T> T as(Viewer viewer, Function<Mappers, T> work) {
        SignedInViewer.signIn(viewer);
        try (SqlSession session = sessionFactory.openSession(true)) {
            return work.apply(new Mappers(
                    session.getMapper(ClaimMapper.class),
                    session.getMapper(DepartmentMapper.class)));
        } finally {
            SignedInViewer.signOut();
        }
    }

    private static List<String> titles(List<ExpenseClaim> claims) {
        return claims.stream().map(ExpenseClaim::getTitle).toList();
    }

    private String statusOf(long claimId) throws SQLException {
        return queryString("SELECT status FROM expense_claim WHERE id = " + claimId);
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

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** What the signed-in viewer may see of the claims. */
    enum Role {
        EMPLOYEE,
        MANAGER,
        AUDITOR
    }

    /** A signed-in user and the departments a manager leads. */
    record Viewer(String userName, Role role, Set<Long> ledDepartments) {
    }

    /** Holds the viewer the current request runs for, as a security filter would bind it. */
    static final class SignedInViewer {

        private static final ThreadLocal<Viewer> CURRENT = new ThreadLocal<>();

        private SignedInViewer() {
        }

        static void signIn(Viewer viewer) {
            CURRENT.set(viewer);
        }

        static Viewer current() {
            Viewer viewer = CURRENT.get();
            assertThat(viewer).as("a statement ran outside of a signed-in request").isNotNull();
            return viewer;
        }

        static void signOut() {
            CURRENT.remove();
        }
    }

    /**
     * Restricts the claim table to the rows the signed-in viewer may see, qualified by the alias the
     * statement gives the table; every other table is left unrestricted.
     */
    static final class ClaimVisibilityHandler implements MultiDataPermissionHandler {

        @Override
        public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
            if (!"expense_claim".equalsIgnoreCase(table.getName())) {
                return null;
            }
            Table qualifier = new Table(table.getAlias() == null ? table.getName() : table.getAlias().getName());
            Viewer viewer = SignedInViewer.current();
            return switch (viewer.role()) {
                case EMPLOYEE -> new EqualsTo(new Column(qualifier, "owner"), new StringValue(viewer.userName()));
                case MANAGER -> {
                    List<Expression> departments = new ArrayList<>();
                    new TreeSet<>(viewer.ledDepartments()).forEach(id -> departments.add(new LongValue(id)));
                    yield new InExpression(new Column(qualifier, "department_id"), new ExpressionList(departments));
                }
                case AUDITOR -> null;
            };
        }
    }

    /** The mappers one request works with, all bound to the request's session. */
    record Mappers(ClaimMapper claims, DepartmentMapper departments) {
    }

    /** Claims visible to the viewer, plus a finance total that deliberately spans every claim. */
    interface ClaimMapper extends BaseMapper<ExpenseClaim> {

        @Select("""
                SELECT c.title, d.name AS department_name
                FROM expense_claim c JOIN department d ON d.id = c.department_id
                ORDER BY c.title
                """)
        List<ClaimLine> claimReport();

        @Select("SELECT COALESCE(SUM(amount), 0) FROM expense_claim")
        BigDecimal visibleTotal();

        @InterceptorIgnore(dataPermission = "true")
        @Select("SELECT SUM(amount) FROM expense_claim")
        BigDecimal companyTotal();
    }

    /** Departments, which every viewer may list. */
    interface DepartmentMapper extends BaseMapper<Department> {

        @Select("""
                SELECT d.name FROM department d
                WHERE d.id IN (SELECT c.department_id FROM expense_claim c)
                ORDER BY d.name
                """)
        List<String> departmentsWithClaims();
    }

    /** An expense an employee claims back from their department. */
    @TableName("expense_claim")
    public static class ExpenseClaim {

        @TableId(type = IdType.INPUT)
        private Long id;

        private String owner;

        private Long departmentId;

        private String title;

        private BigDecimal amount;

        private String status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** A department claims are booked against. */
    @TableName("department")
    public static class Department {

        @TableId(type = IdType.INPUT)
        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** A line of the claim report. */
    public static class ClaimLine {

        private String title;

        private String departmentName;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public void setDepartmentName(String departmentName) {
            this.departmentName = departmentName;
        }
    }
}
