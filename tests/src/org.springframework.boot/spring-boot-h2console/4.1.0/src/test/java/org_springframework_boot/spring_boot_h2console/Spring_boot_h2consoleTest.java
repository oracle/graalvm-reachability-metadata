/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_h2console;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.server.web.JakartaWebServlet;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.h2console.autoconfigure.H2ConsoleAutoConfiguration;
import org.springframework.boot.h2console.autoconfigure.H2ConsoleProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Spring_boot_h2consoleTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(H2ConsoleAutoConfiguration.class));

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(H2ConsoleAutoConfiguration.class));

    @Test
    void h2ConsolePropertiesExposeDefaultsAndValidatePath() {
        H2ConsoleProperties properties = new H2ConsoleProperties();

        assertThat(properties.getPath()).isEqualTo("/h2-console");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getSettings().isTrace()).isFalse();
        assertThat(properties.getSettings().isWebAllowOthers()).isFalse();
        assertThat(properties.getSettings().getWebAdminPassword()).isNull();

        properties.setEnabled(true);
        properties.setPath("/database-console");
        properties.getSettings().setTrace(true);
        properties.getSettings().setWebAllowOthers(true);
        properties.getSettings().setWebAdminPassword("admin-secret");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPath()).isEqualTo("/database-console");
        assertThat(properties.getSettings().isTrace()).isTrue();
        assertThat(properties.getSettings().isWebAllowOthers()).isTrue();
        assertThat(properties.getSettings().getWebAdminPassword()).isEqualTo("admin-secret");

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setPath(null))
                .withMessageContaining("'path' must not be null");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setPath("/"))
                .withMessageContaining("'path' must have length greater than 1");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setPath("h2-console"))
                .withMessageContaining("'path' must start with '/'");
    }

    @Test
    void autoConfigurationDoesNotCreateConsoleWhenConsoleIsDisabled() {
        this.contextRunner.run((context) -> {
            assertThat(context).doesNotHaveBean(H2ConsoleProperties.class);
            assertThat(context).doesNotHaveBean("h2Console");
            assertThat(context).doesNotHaveBean(ServletRegistrationBean.class);
        });
    }

    @Test
    void enabledConsoleDoesNotCreateRegistrationForNonWebApplication() {
        this.nonWebContextRunner.withPropertyValues("spring.h2.console.enabled=true").run((context) -> {
            assertThat(context).doesNotHaveBean(H2ConsoleProperties.class);
            assertThat(context).doesNotHaveBean("h2Console");
            assertThat(context).doesNotHaveBean(ServletRegistrationBean.class);
        });
    }

    @Test
    void enabledConsoleCreatesRegistrationForDefaultPath() {
        this.contextRunner.withPropertyValues("spring.h2.console.enabled=true").run((context) -> {
            assertThat(context).hasSingleBean(H2ConsoleProperties.class);
            assertThat(context).hasSingleBean(ServletRegistrationBean.class);

            ServletRegistrationBean<?> registration = context.getBean(ServletRegistrationBean.class);
            H2ConsoleProperties properties = context.getBean(H2ConsoleProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getPath()).isEqualTo("/h2-console");
            assertThat(registration.getServlet()).isInstanceOf(JakartaWebServlet.class);
            assertThat(registration.getServletName()).isEqualTo("h2Console");
            assertThat(registration.getUrlMappings()).containsExactly("/h2-console/*");
            assertThat(registration.getInitParameters()).isEmpty();
        });
    }

    @Test
    void enabledConsoleBindsPathAndSettingsIntoServletRegistration() {
        this.contextRunner
                .withPropertyValues("spring.h2.console.enabled=true", "spring.h2.console.path=/database/",
                        "spring.h2.console.settings.trace=true",
                        "spring.h2.console.settings.web-allow-others=true",
                        "spring.h2.console.settings.web-admin-password=admin-secret")
                .run((context) -> {
                    H2ConsoleProperties properties = context.getBean(H2ConsoleProperties.class);
                    ServletRegistrationBean<?> registration = context.getBean(ServletRegistrationBean.class);

                    assertThat(properties.getPath()).isEqualTo("/database/");
                    assertThat(properties.getSettings().isTrace()).isTrue();
                    assertThat(properties.getSettings().isWebAllowOthers()).isTrue();
                    assertThat(properties.getSettings().getWebAdminPassword()).isEqualTo("admin-secret");
                    assertThat(registration.getUrlMappings()).containsExactly("/database/*");
                    assertThat(registration.getServlet()).isInstanceOf(JakartaWebServlet.class);
                    assertThat(registration.getServletName()).isEqualTo("h2Console");
                    assertThat(registration.getInitParameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
                            "trace", "", "webAllowOthers", "", "webAdminPassword", "admin-secret"));
                });
    }

    @Test
    void customPathWithoutTrailingSlashUsesWildcardChildMapping() {
        this.contextRunner
                .withPropertyValues("spring.h2.console.enabled=true", "spring.h2.console.path=/tools/h2")
                .run((context) -> {
                    ServletRegistrationBean<?> registration = context.getBean(ServletRegistrationBean.class);

                    assertThat(registration.getUrlMappings()).containsExactly("/tools/h2/*");
                    assertThat(registration.toString()).contains("/tools/h2/*");
                });
    }

    @Test
    void enabledConsoleInspectsAvailableDataSourcesForStartupLogging() {
        this.contextRunner.withPropertyValues("spring.h2.console.enabled=true")
                .withUserConfiguration(TestDataSourceConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasBean("h2ConsoleLogger");

                    CountingDataSource dataSource = context.getBean(CountingDataSource.class);
                    assertThat(dataSource.getConnectionCount()).isPositive();
                });
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestDataSourceConfiguration {

        @Bean
        public CountingDataSource h2DataSource() {
            return new CountingDataSource("jdbc:h2:mem:h2consolelogger");
        }

    }

    static final class CountingDataSource implements DataSource {

        private final JdbcDataSource delegate = new JdbcDataSource();

        private final AtomicInteger connectionCount = new AtomicInteger();

        CountingDataSource(String url) {
            this.delegate.setURL(url);
            this.delegate.setUser("sa");
            this.delegate.setPassword("");
        }

        int getConnectionCount() {
            return this.connectionCount.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            this.connectionCount.incrementAndGet();
            return this.delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            this.connectionCount.incrementAndGet();
            return this.delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return this.delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            this.delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            this.delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return this.delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return this.delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return this.delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return this.delegate.isWrapperFor(iface);
        }

    }

}
