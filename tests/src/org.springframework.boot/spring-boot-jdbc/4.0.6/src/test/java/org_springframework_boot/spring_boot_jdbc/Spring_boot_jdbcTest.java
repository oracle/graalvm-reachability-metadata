/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jdbc;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_jdbcTest {

    @Test
    void dataSourceBuilderCreatesSimpleDriverDataSourceWithConnectionSettings() {
        SimpleDriverDataSource dataSource = DataSourceBuilder.create()
            .type(SimpleDriverDataSource.class)
            .url("jdbc:custom:orders")
            .username("orders-user")
            .password("orders-secret")
            .build();

        assertThat(dataSource.getUrl()).isEqualTo("jdbc:custom:orders");
        assertThat(dataSource.getUsername()).isEqualTo("orders-user");
        assertThat(dataSource.getPassword()).isEqualTo("orders-secret");
    }

    @Test
    void dataSourceBuilderDerivesSettingsFromExistingDataSourceAndAppliesOverrides() {
        SimpleDriverDataSource source = new SimpleDriverDataSource();
        source.setUrl("jdbc:custom:inventory");
        source.setUsername("source-user");
        source.setPassword("source-secret");

        DataSource dataSource = DataSourceBuilder.derivedFrom(source).username("derived-user").build();

        assertThat(dataSource).isInstanceOfSatisfying(SimpleDriverDataSource.class, (derived) -> {
            assertThat(derived.getUrl()).isEqualTo("jdbc:custom:inventory");
            assertThat(derived.getUsername()).isEqualTo("derived-user");
            assertThat(derived.getPassword()).isEqualTo("source-secret");
        });
    }

    @Test
    void dataSourceUnwrapperFindsNestedDelegatingDataSourceTarget() {
        SimpleDriverDataSource target = new SimpleDriverDataSource();
        target.setUrl("jdbc:custom:warehouse");
        DelegatingDataSource wrapped = new DelegatingDataSource(new DelegatingDataSource(target));

        SimpleDriverDataSource unwrapped = DataSourceUnwrapper.unwrap(wrapped, SimpleDriverDataSource.class);

        assertThat(unwrapped).isSameAs(target);
    }
}
