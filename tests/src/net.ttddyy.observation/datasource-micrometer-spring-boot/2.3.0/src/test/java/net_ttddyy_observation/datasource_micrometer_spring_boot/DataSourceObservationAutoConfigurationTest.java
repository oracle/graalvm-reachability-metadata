/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer_spring_boot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ProxyJdbcObject;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationAutoConfiguration;
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationBeanPostProcessor;
import net.ttddyy.observation.boot.autoconfigure.DataSourceProxyCreationStrategy;
import net.ttddyy.observation.boot.autoconfigure.ProxyDataSourceBuilderCustomizer;
import net.ttddyy.observation.boot.event.JdbcEvent;
import net.ttddyy.observation.boot.event.JdbcMethodExecutionEvent;
import net.ttddyy.observation.boot.event.JdbcQueryExecutionEvent;
import net.ttddyy.observation.tracing.ConnectionContext;
import net.ttddyy.observation.tracing.QueryContext;
import net.ttddyy.observation.tracing.ResultSetContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationListener;

import static org.assertj.core.api.Assertions.assertThat;

/// Drives the auto-configuration and the `DataSource` bean post-processor it registers.
///
/// With the defaults, the `DataSource` bean is wrapped by a Spring AOP proxy and JDBC work through
/// it produces observations, Spring events and query log records. The post-processor also honours
/// the concrete proxy type, builder customizers, excluded bean names, a creation strategy and the
/// switch that disables the starter.
///
/// Every application context starts once in [#startApplicationContexts()], so the instrumented
/// code is reached through a single call path; the tests below assert what those runs recorded.
/// Tracing export is switched off here; the tracing handlers are covered by
/// [DataSourceObservationAutoConfigurationInnerOnTraceTypeConditionTest].
public class DataSourceObservationAutoConfigurationTest {

    private static final String QUERY_LOGGER_NAME = "net_ttddyy_observation.query-log";

    private static final String INSERT_SQL = "INSERT INTO book(title) VALUES (?)";

    private static final String SELECT_SQL = "SELECT title FROM book ORDER BY id";

    private static final Logger QUERY_LOGGER = Logger.getLogger(QUERY_LOGGER_NAME);

    private static final RecordingObservationHandler OBSERVATIONS = new RecordingObservationHandler();

    private static final RecordingEventListener EVENTS = new RecordingEventListener();

    private static final List<String> LOGGED_QUERIES = new CopyOnWriteArrayList<>();

    private static final List<String> SELECTED_TITLES = new ArrayList<>();

    private static final List<String> CUSTOMIZED = new CopyOnWriteArrayList<>();

    private static final List<String> CONSULTED = new CopyOnWriteArrayList<>();

    private static final JdbcDataSource OBSERVED_TARGET = h2DataSource("observed");

    private static final JdbcDataSource CONCRETE_TARGET = h2DataSource("concrete");

    private static final JdbcDataSource EXCLUDED_TARGET = h2DataSource("excluded");

    private static final JdbcDataSource STRATEGY_TARGET = h2DataSource("strategy");

    private static final JdbcDataSource DISABLED_TARGET = h2DataSource("disabled");

    private static Object observedBean;

    private static Object observedUnwrappedTarget;

    private static boolean observedJdkAopProxy;

    private static long generatedKey;

    private static Object concreteBean;

    private static Object concreteUnwrappedTarget;

    private static boolean concreteConnectionProxied;

    private static int concreteQueryResult;

    private static Object excludedBean;

    private static int excludedPostProcessors;

    private static Object strategyBean;

    private static Object disabledBean;

    private static int disabledPostProcessors;

    @BeforeAll
    static void startApplicationContexts() throws SQLException {
        try (Connection connection = OBSERVED_TARGET.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE book (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(100))");
        }
        QUERY_LOGGER.setLevel(Level.ALL);
        QUERY_LOGGER.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                LOGGED_QUERIES.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(OBSERVATIONS);

        runner(OBSERVED_TARGET)
                .withPropertyValues(
                        "jdbc.event.enabled=true",
                        "jdbc.datasource-proxy.include-parameter-values=true",
                        "jdbc.datasource-proxy.logging=jul",
                        "jdbc.datasource-proxy.multiline=false",
                        "jdbc.datasource-proxy.query.enable-logging=true",
                        "jdbc.datasource-proxy.query.log-level=INFO",
                        "jdbc.datasource-proxy.query.logger-name=" + QUERY_LOGGER_NAME)
                .withBean(ObservationRegistry.class, () -> registry)
                .withBean(RecordingEventListener.class, () -> EVENTS)
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    observedBean = dataSource;
                    observedJdkAopProxy = AopUtils.isJdkDynamicProxy(dataSource);
                    if (dataSource instanceof ProxyJdbcObject proxyJdbcObject) {
                        observedUnwrappedTarget = proxyJdbcObject.getTarget();
                    }
                    runJdbcWork(dataSource);
                });

        ProxyDataSourceBuilderCustomizer customizer = (builder, dataSource, beanName, dataSourceName) ->
                CUSTOMIZED.add(beanName + "=" + dataSourceName.toLowerCase());
        runner(CONCRETE_TARGET)
                .withPropertyValues("jdbc.datasource-proxy.type=concrete")
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .withBean(ProxyDataSourceBuilderCustomizer.class, () -> customizer)
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    concreteBean = dataSource;
                    if (dataSource instanceof ProxyDataSource proxyDataSource) {
                        concreteUnwrappedTarget = proxyDataSource.getDataSource();
                    }
                    try (Connection connection = dataSource.getConnection();
                            Statement statement = connection.createStatement();
                            ResultSet result = statement.executeQuery("SELECT 40 + 2")) {
                        concreteConnectionProxied = connection instanceof ProxyJdbcObject;
                        result.next();
                        concreteQueryResult = result.getInt(1);
                    }
                });

        runner(EXCLUDED_TARGET)
                .withPropertyValues("jdbc.excluded-data-source-bean-names=dataSource")
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> {
                    excludedBean = context.getBean(DataSource.class);
                    excludedPostProcessors = context.getBeanNamesForType(DataSourceObservationBeanPostProcessor.class).length;
                });

        DataSourceProxyCreationStrategy strategy = (dataSource, beanName) -> {
            CONSULTED.add(beanName);
            return false;
        };
        runner(STRATEGY_TARGET)
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .withBean(DataSourceProxyCreationStrategy.class, () -> strategy)
                .run(context -> strategyBean = context.getBean(DataSource.class));

        runner(DISABLED_TARGET)
                .withPropertyValues("jdbc.datasource-proxy.enabled=false")
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> {
                    disabledBean = context.getBean(DataSource.class);
                    disabledPostProcessors = context.getBeanNamesForType(DataSourceObservationBeanPostProcessor.class).length;
                });
    }

    private static ApplicationContextRunner runner(JdbcDataSource dataSource) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceObservationAutoConfiguration.class))
                .withPropertyValues("management.tracing.export.enabled=false")
                .withBean("dataSource", DataSource.class, () -> dataSource);
    }

    private static JdbcDataSource h2DataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void runJdbcWork(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "Native Image");
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    generatedKey = keys.getLong(1);
                }
            }
            try (Statement select = connection.createStatement(); ResultSet rows = select.executeQuery(SELECT_SQL)) {
                while (rows.next()) {
                    SELECTED_TITLES.add(rows.getString(1));
                }
            }
        }
    }

    @Test
    void wrapsDataSourceBeanInSpringAopProxy() {
        assertThat(observedBean).isInstanceOf(ProxyJdbcObject.class);
        assertThat(observedJdkAopProxy).isTrue();
        assertThat(observedUnwrappedTarget).isSameAs(OBSERVED_TARGET);
    }

    @Test
    void runsJdbcWorkThroughTheProxy() {
        assertThat(generatedKey).isPositive();
        assertThat(SELECTED_TITLES).containsExactly("Native Image");
    }

    @Test
    void recordsConnectionQueryAndResultSetObservations() {
        assertThat(OBSERVATIONS.contexts(ConnectionContext.class)).isNotEmpty();

        List<QueryContext> queries = OBSERVATIONS.contexts(QueryContext.class);
        assertThat(queries).extracting(QueryContext::getQueries).contains(List.of(INSERT_SQL), List.of(SELECT_SQL));
        assertThat(queries).allSatisfy(query -> assertThat(query.getDataSourceName()).isEqualToIgnoringCase("observed"));
        QueryContext insert = queries.stream().filter(query -> query.getQueries().contains(INSERT_SQL)).findFirst().orElseThrow();
        assertThat(insert.getParams()).anySatisfy(params -> assertThat(params).contains("Native Image"));

        List<ResultSetContext> resultSets = OBSERVATIONS.contexts(ResultSetContext.class);
        assertThat(resultSets).extracting(ResultSetContext::getCount).contains(1);
    }

    @Test
    void publishesQueryAndMethodExecutionEvents() {
        List<JdbcQueryExecutionEvent> queryEvents = EVENTS.events(JdbcQueryExecutionEvent.class);
        List<JdbcQueryExecutionEvent> selectEvents = queryEvents.stream()
                .filter(event -> event.getQueryInfoList().stream().map(QueryInfo::getQuery).anyMatch(SELECT_SQL::equals))
                .toList();
        assertThat(selectEvents).extracting(JdbcQueryExecutionEvent::isBefore).containsExactly(true, false);
        assertThat(selectEvents.get(1).getExecInfo().isSuccess()).isTrue();

        assertThat(EVENTS.events(JdbcMethodExecutionEvent.class))
                .extracting(event -> event.getExecutionContext().getMethod().getName())
                .contains("getConnection", "prepareStatement", "createStatement");
    }

    @Test
    void logsExecutedQueriesThroughJavaUtilLogging() {
        assertThat(LOGGED_QUERIES).anySatisfy(message -> assertThat(message).contains(INSERT_SQL));
        assertThat(LOGGED_QUERIES).anySatisfy(message -> assertThat(message).contains(SELECT_SQL));
    }

    @Test
    void wrapsDataSourceInConcreteProxyAndAppliesCustomizers() {
        assertThat(concreteBean).isInstanceOf(ProxyDataSource.class);
        assertThat(concreteUnwrappedTarget).isSameAs(CONCRETE_TARGET);
        assertThat(concreteConnectionProxied).isTrue();
        assertThat(concreteQueryResult).isEqualTo(42);
        assertThat(CUSTOMIZED).containsExactly("dataSource=concrete");
    }

    @Test
    void leavesExcludedDataSourceBeansUnwrapped() {
        assertThat(excludedPostProcessors).isEqualTo(1);
        assertThat(excludedBean).isSameAs(EXCLUDED_TARGET);
    }

    @Test
    void consultsTheProxyCreationStrategy() {
        assertThat(strategyBean).isSameAs(STRATEGY_TARGET);
        assertThat(CONSULTED).containsExactly("dataSource");
    }

    @Test
    void backsOffWhenDisabled() {
        assertThat(disabledPostProcessors).isZero();
        assertThat(disabledBean).isSameAs(DISABLED_TARGET);
    }

    /// Collects every stopped observation context.
    static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

        private final List<Observation.Context> stopped = new CopyOnWriteArrayList<>();

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.add(context);
        }

        <T extends Observation.Context> List<T> contexts(Class<T> type) {
            return stopped.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    /// Collects the JDBC events the auto-configured publishing listener sends.
    static final class RecordingEventListener implements ApplicationListener<JdbcEvent> {

        private final List<JdbcEvent> received = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(JdbcEvent event) {
            received.add(event);
        }

        <T extends JdbcEvent> List<T> events(Class<T> type) {
            return received.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
}
